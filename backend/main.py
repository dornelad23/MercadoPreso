"""API inicial; não executa migrações automaticamente."""

import hashlib
import re
import os
import secrets
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from decimal import Decimal

import psycopg
from psycopg.rows import dict_row
from argon2 import PasswordHasher
from argon2.exceptions import VerificationError, InvalidHashError
from fastapi import FastAPI, Depends, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from threading import Lock
import time
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field

app = FastAPI(title="Mercado Preso", version="0.2.0")
bearer = HTTPBearer()
hasher = PasswordHasher()
_login_attempts = {}
_attempt_lock = Lock()
_dummy_hash = hasher.hash("dummy-password-for-timing-only")


@app.middleware("http")
async def auth_throttle(request: Request, call_next):
    if request.url.path in ["/auth/login", "/auth/register"]:
        # Não confia em X-Forwarded-For recebido do cliente. Configure proxy confiável no Uvicorn.
        ip = request.client.host if request.client else "unknown"
        now = time.monotonic()
        with _attempt_lock:
            if len(_login_attempts) > 10000:
                expired = [
                    key
                    for key, times in _login_attempts.items()
                    if not times or times[-1] < now - 60
                ]
                for key in expired:
                    _login_attempts.pop(key, None)
            times = [t for t in _login_attempts.get(ip, []) if t > now - 60]
            if len(times) >= 10:
                return JSONResponse(
                    {"detail": "Muitas tentativas. Aguarde um minuto."},
                    status_code=429,
                    headers={"Retry-After": "60"},
                )
            times.append(now)
            _login_attempts[ip] = times
    return await call_next(request)


@app.exception_handler(psycopg.Error)
async def database_error(request, exc):
    # Não revela consultas, credenciais ou regras internas nas respostas.
    return JSONResponse(
        {"detail": "Falha ao consultar o banco. Confira a configuração da API."},
        status_code=503,
    )


@app.exception_handler(ValidationError)
async def invalid_payload(request, exc):
    return JSONResponse(
        {"detail": "Dados inválidos. Confira os campos informados."}, status_code=422
    )


@contextmanager
def db():
    url = os.getenv("DATABASE_URL")
    if not url:
        raise HTTPException(503, "Banco não configurado")
    with psycopg.connect(url, row_factory=dict_row, connect_timeout=10) as conn:
        yield conn


class Credentials(BaseModel):
    email: str = Field(min_length=3, max_length=254)
    senha: str = Field(min_length=8, max_length=128)


class Register(Credentials):
    nome: str = Field(min_length=2, max_length=100)


class Address(BaseModel):
    cep: str = Field(pattern=r"^\d{8}$")
    logradouro: str = Field(min_length=1, max_length=150)
    numero: str = Field(min_length=1, max_length=20)
    complemento: str = Field(default="", max_length=100)
    bairro: str = Field(default="", max_length=100)
    cidade: str = Field(min_length=1, max_length=100)
    estado: str = Field(pattern=r"^[A-Z]{2}$")


def token_hash(value):
    return hashlib.sha256(value.encode()).hexdigest()


def session(conn, user_id):
    token = secrets.token_urlsafe(48)
    expires = datetime.now(timezone.utc) + timedelta(days=7)
    conn.execute(
        "INSERT INTO mp_sessoes(token_hash,id_usuario,expira_em) VALUES (%s,%s,%s)",
        (token_hash(token), user_id, expires),
    )
    return {"access_token": token, "token_type": "bearer", "expires_at": expires}


def current_user(auth: HTTPAuthorizationCredentials = Depends(bearer)):
    with db() as conn:
        user = conn.execute(
            """SELECT u.id_usuario,u.nome,u.email,c.id_cliente
            FROM mp_sessoes s JOIN usuarios u USING(id_usuario)
            JOIN clientes c USING(id_usuario)
            WHERE s.token_hash=%s AND s.expira_em>now() AND u.status=%s""",
            (token_hash(auth.credentials), os.getenv("USER_ACTIVE_STATUS", "ativo")),
        ).fetchone()
    if not user:
        raise HTTPException(401, "Sessão inválida ou expirada")
    user["token_hash"] = token_hash(auth.credentials)
    return user


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/auth/register", status_code=201)
def register(body: Register):
    email = body.email.strip().lower()
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise HTTPException(422, "E-mail inválido")
    if len(body.nome.strip()) < 2:
        raise HTTPException(422, "Informe seu nome")
    password = hasher.hash(body.senha)
    with db() as conn:
        # Serializa cadastros pelo e-mail mesmo quando a tabela antiga não tem UNIQUE.
        conn.execute("SELECT pg_advisory_xact_lock(hashtextextended(%s,0))", (email,))
        if conn.execute(
            "SELECT 1 FROM usuarios WHERE lower(email)=%s", (email,)
        ).fetchone():
            raise HTTPException(409, "E-mail já cadastrado")
        user = conn.execute(
            """INSERT INTO usuarios(nome,email,senha_hash,status,data_cadastro)
            VALUES (%s,%s,%s,%s,now()) RETURNING id_usuario""",
            (
                body.nome.strip(),
                email,
                password,
                os.getenv("USER_ACTIVE_STATUS", "ativo"),
            ),
        ).fetchone()
        conn.execute(
            "INSERT INTO clientes(id_usuario) VALUES (%s)", (user["id_usuario"],)
        )
        return session(conn, user["id_usuario"])


@app.post("/auth/login")
def login(body: Credentials):
    with db() as conn:
        user = conn.execute(
            "SELECT * FROM usuarios WHERE lower(email)=%s AND status=%s",
            (body.email.strip().lower(), os.getenv("USER_ACTIVE_STATUS", "ativo")),
        ).fetchone()
        valid = False
        if user:
            try:
                valid = hasher.verify(user["senha_hash"], body.senha)
            except (VerificationError, InvalidHashError):
                pass
        if not user:
            try:
                hasher.verify(_dummy_hash, body.senha)
            except VerificationError:
                pass
        if not valid:
            raise HTTPException(401, "E-mail ou senha inválidos")
        if hasher.check_needs_rehash(user["senha_hash"]):
            conn.execute(
                "UPDATE usuarios SET senha_hash=%s WHERE id_usuario=%s",
                (hasher.hash(body.senha), user["id_usuario"]),
            )
        conn.execute(
            "UPDATE usuarios SET ultimo_acesso=now() WHERE id_usuario=%s",
            (user["id_usuario"],),
        )
        return session(conn, user["id_usuario"])


@app.post("/auth/logout", status_code=204)
def logout(
    user=Depends(current_user), auth: HTTPAuthorizationCredentials = Depends(bearer)
):
    with db() as conn:
        conn.execute(
            "DELETE FROM mp_sessoes WHERE token_hash=%s",
            (token_hash(auth.credentials),),
        )


@app.get("/me")
def me(user=Depends(current_user)):
    return {k: v for k, v in user.items() if k != "token_hash"}


@app.get("/products")
def products():
    with db() as conn:
        return conn.execute(
            """SELECT p.id_produto,p.nome,p.descricao,p.preco,p.estoque,p.imagem,
            c.nome AS categoria FROM produtos p JOIN categorias c USING(id_categoria)
            WHERE p.status=%s ORDER BY p.id_produto""",
            (os.getenv("PRODUCT_ACTIVE_STATUS", "ativo"),),
        ).fetchall()


@app.get("/addresses")
def addresses(user=Depends(current_user)):
    with db() as conn:
        return conn.execute(
            "SELECT * FROM enderecos WHERE id_cliente=%s ORDER BY id_endereco",
            (user["id_cliente"],),
        ).fetchall()


@app.post("/addresses", status_code=201)
def add_address(body: Address, user=Depends(current_user)):
    with db() as conn:
        return conn.execute(
            """INSERT INTO enderecos(id_cliente,cep,logradouro,numero,complemento,bairro,cidade,estado)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s) RETURNING *""",
            (
                user["id_cliente"],
                body.cep,
                body.logradouro,
                body.numero,
                body.complemento,
                body.bairro,
                body.cidade,
                body.estado,
            ),
        ).fetchone()


@app.delete("/addresses/{address_id}", status_code=204)
def delete_address(address_id: int, user=Depends(current_user)):
    with db() as conn:
        try:
            found = conn.execute(
                "DELETE FROM enderecos WHERE id_endereco=%s AND id_cliente=%s RETURNING id_endereco",
                (address_id, user["id_cliente"]),
            ).fetchone()
        except psycopg.errors.ForeignKeyViolation:
            raise HTTPException(409, "Endereço utilizado em pedido")
        if not found:
            raise HTTPException(404, "Endereço não encontrado")


from store_api import router

app.include_router(router)
