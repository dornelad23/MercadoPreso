"""Persistência do app e checkout de demonstração: valores e estoque calculados no servidor."""

import os
import time
from decimal import Decimal, ROUND_HALF_EVEN
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from psycopg.types.json import Jsonb
from main import db, current_user, hasher, Address

router = APIRouter()
UF = set(
    "AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split()
)


class Mutation(BaseModel):
    kind: str
    payload: dict = Field(default_factory=dict)


def ensure(conn, client):
    conn.execute(
        "INSERT INTO mp_dados_cliente(id_cliente) VALUES (%s) ON CONFLICT DO NOTHING",
        (client,),
    )
    # Um cliente tem somente uma mutação/checkout por vez, inclusive entre diferentes aparelhos.
    return conn.execute(
        "SELECT * FROM mp_dados_cliente WHERE id_cliente=%s FOR UPDATE", (client,)
    ).fetchone()


def catalog(conn):
    rows = conn.execute(
        """SELECT p.*,c.nome categoria,m.peso_kg,m.frete_gratis,m.preco_original,
        m.avaliacao,m.vendas FROM produtos p JOIN categorias c USING(id_categoria)
        LEFT JOIN mp_produto_meta m USING(id_produto) WHERE p.status=%s ORDER BY id_produto""",
        (os.getenv("PRODUCT_ACTIVE_STATUS", "ativo"),),
    ).fetchall()
    return [
        dict(
            id=p["id_produto"],
            nome=p["nome"],
            descricao=p["descricao"] or "",
            preco=float(p["preco"]),
            categoria=p["categoria"],
            imagem=p["imagem"] or "",
            estoque=p["estoque"],
            pesoKg=float(p["peso_kg"] or 1),
            freteGratis=bool(p["frete_gratis"]),
            precoOriginal=float(p["preco_original"]) if p["preco_original"] else None,
            avaliacao=float(p["avaliacao"] or 0),
            vendas=p["vendas"] or 0,
        )
        for p in rows
    ]


def address_json(row):
    return dict(
        id=str(row["id_endereco"]),
        cep=row["cep"],
        rua=row["logradouro"],
        numero=row["numero"] or "",
        complemento=row["complemento"] or "",
        bairro=row["bairro"] or "",
        cidade=row["cidade"],
        uf=row["estado"],
    )


def state(conn, user):
    client = user["id_cliente"]
    data = ensure(conn, client)
    identity = conn.execute(
        "SELECT nome,email FROM usuarios WHERE id_usuario=%s", (user["id_usuario"],)
    ).fetchone()
    addresses = [
        address_json(a)
        for a in conn.execute(
            "SELECT * FROM enderecos WHERE id_cliente=%s ORDER BY id_endereco DESC",
            (client,),
        )
    ]
    orders = [
        r["snapshot"]
        for r in conn.execute(
            "SELECT snapshot FROM mp_pedido_meta WHERE id_cliente=%s ORDER BY id_pedido DESC",
            (client,),
        )
    ]
    legacy = conn.execute(
        """SELECT p.* FROM pedidos p LEFT JOIN mp_pedido_meta m USING(id_pedido)
        WHERE p.id_cliente=%s AND m.id_pedido IS NULL ORDER BY p.id_pedido DESC""",
        (client,),
    ).fetchall()
    for p in legacy:
        a = conn.execute(
            "SELECT * FROM enderecos WHERE id_endereco=%s", (p["id_endereco_entrega"],)
        ).fetchone()
        if not a:
            continue
        rows = conn.execute(
            """SELECT i.*,pr.nome,pr.imagem FROM itens_pedido i JOIN produtos pr USING(id_produto)
            WHERE i.id_pedido=%s ORDER BY i.id_item""",
            (p["id_pedido"],),
        ).fetchall()
        payment = conn.execute(
            "SELECT metodo FROM pagamentos WHERE id_pedido=%s ORDER BY id_pagamento DESC LIMIT 1",
            (p["id_pedido"],),
        ).fetchone()
        timestamp = (
            int(
                p["data_pedido"]
                .replace(tzinfo=__import__("datetime").timezone.utc)
                .timestamp()
                * 1000
            )
            if p["data_pedido"]
            else 0
        )
        subtotal = sum(
            (r["preco_unitario"] * r["quantidade"] for r in rows), Decimal(0)
        )
        orders.append(
            dict(
                id=str(p["id_pedido"]),
                criado=timestamp,
                itens=[
                    dict(
                        produtoId=r["id_produto"],
                        nome=r["nome"],
                        imagem=r["imagem"] or "",
                        preco=float(r["preco_unitario"]),
                        quantidade=r["quantidade"],
                    )
                    for r in rows
                ],
                endereco=address_json(a),
                frete=float(p["valor_total"] - subtotal),
                modalidade="Pedido anterior",
                pagamento=payment["metodo"] if payment else "Não informado",
                parcelas=1,
                teste=False,
                eventos=[dict(status=p["status"] or "Não informado", data=timestamp)],
            )
        )
    orders.sort(key=lambda p: p["criado"], reverse=True)
    reviews = [
        dict(
            produtoId=r["id_produto"],
            userId=str(r["id_usuario"]),
            nome=r["nome"],
            nota=r["nota"],
            comentario=r["comentario"],
        )
        for r in conn.execute(
            """SELECT a.*,u.id_usuario,u.nome FROM mp_avaliacoes a
            JOIN clientes c USING(id_cliente) JOIN usuarios u USING(id_usuario) ORDER BY a.id_produto,a.id_cliente"""
        )
    ]
    return dict(
        user=dict(
            id=str(user["id_usuario"]),
            nome=identity["nome"],
            email=identity["email"],
            salt="",
            hash="",
            carrinho=data["carrinho"],
            favoritos=data["favoritos"],
            enderecos=addresses,
            pedidos=orders,
            animacoes=data["animacoes"],
            alertas=data["alertas"],
        ),
        reviews=reviews,
        products=catalog(conn),
    )


def bad(message, status=422):
    raise HTTPException(status, message)


def integer(value):
    if isinstance(value, bool) or isinstance(value, float):
        bad("Número inválido")
    if isinstance(value, str) and not value.isdigit():
        bad("Número inválido")
    try:
        return int(value)
    except (TypeError, ValueError):
        bad("Número inválido")


def quotes(uf, cart, products):
    if uf not in UF:
        bad("Estado inválido")
    items = []
    for id, q in cart.items():
        if int(id) not in products:
            bad("Produto indisponível", 409)
        items.append((products[int(id)], int(q)))
    weight = sum((Decimal(str(p["pesoKg"])) * q for p, q in items), Decimal(0))
    free = bool(items) and all(p["freteGratis"] for p, q in items)
    base = Decimal(
        "9.90"
        if uf == "SP"
        else (
            "16.90"
            if uf in ["RJ", "MG", "ES", "PR"]
            else "22.90" if uf in ["SC", "RS", "GO", "DF", "MS", "MT"] else "29.90"
        )
    )
    value = (
        Decimal(0)
        if free
        else (base + weight * Decimal("2.30")).quantize(
            Decimal(".01"), rounding=ROUND_HALF_EVEN
        )
    )
    return {
        "Econômico · estimativa": value,
        "Expresso · estimativa": (value * Decimal("1.5")).quantize(
            Decimal(".01"), rounding=ROUND_HALF_EVEN
        ),
    }


@router.get("/app/catalog")
def public_catalog():
    with db() as conn:
        return {"products": catalog(conn)}


@router.get("/app/state")
def get_state(user=Depends(current_user)):
    with db() as conn:
        return state(conn, user)


@router.post("/app/mutate")
def mutate(body: Mutation, user=Depends(current_user)):
    with db() as conn:
        client = user["id_cliente"]
        data = ensure(conn, client)
        p = body.payload
        kind = body.kind
        cart = dict(data["carrinho"])
        favorites = set(data["favoritos"])
        products = {x["id"]: x for x in catalog(conn)}
        if kind in ["add", "decrease", "remove", "favorite"]:
            id = integer(p.get("id"))
            key = str(id)
            if id not in products:
                bad("Produto indisponível", 404)
            q = integer(cart.get(key, 0))
            if kind == "add":
                if q >= products[id]["estoque"]:
                    bad("Estoque insuficiente", 409)
                cart[key] = q + 1
            elif kind == "decrease":
                if q > 1:
                    cart[key] = q - 1
                else:
                    cart.pop(key, None)
            elif kind == "remove":
                cart.pop(key, None)
            elif id in favorites:
                favorites.remove(id)
            else:
                favorites.add(id)
            conn.execute(
                "UPDATE mp_dados_cliente SET carrinho=%s,favoritos=%s WHERE id_cliente=%s",
                (Jsonb(cart), Jsonb(sorted(favorites)), client),
            )
        elif kind == "merge_guest":
            # Não recebe perfil, preço, pagamento ou pedidos locais como autoridade.
            incoming = p.get("carrinho", {})
            incoming_favorites = p.get("favoritos", [])
            if (
                not isinstance(incoming, dict)
                or len(incoming) > 200
                or not isinstance(incoming_favorites, list)
                or len(incoming_favorites) > 200
            ):
                bad("Carrinho inválido")
            for raw, q in incoming.items():
                id = integer(raw)
                q = integer(q)
                if id not in products or q < 1:
                    continue
                cart[str(id)] = max(
                    integer(cart.get(str(id), 0)), min(q, products[id]["estoque"])
                )
            favorites.update(
                integer(id) for id in incoming_favorites if integer(id) in products
            )
            conn.execute(
                "UPDATE mp_dados_cliente SET carrinho=%s,favoritos=%s WHERE id_cliente=%s",
                (Jsonb(cart), Jsonb(sorted(favorites)), client),
            )
        elif kind == "profile":
            name = str(p.get("nome", "")).strip()
            if not 2 <= len(name) <= 100:
                bad("Informe seu nome")
            conn.execute(
                "UPDATE usuarios SET nome=%s WHERE id_usuario=%s",
                (name, user["id_usuario"]),
            )
        elif kind == "password":
            old = str(p.get("current", ""))
            new = str(p.get("next", ""))
            if not 8 <= len(new) <= 128:
                bad("Use uma senha de 8 a 128 caracteres")
            row = conn.execute(
                "SELECT senha_hash FROM usuarios WHERE id_usuario=%s FOR UPDATE",
                (user["id_usuario"],),
            ).fetchone()
            try:
                hasher.verify(row["senha_hash"], old)
            except Exception:
                bad("Senha atual incorreta", 403)
            conn.execute(
                "UPDATE usuarios SET senha_hash=%s WHERE id_usuario=%s",
                (hasher.hash(new), user["id_usuario"]),
            )
            conn.execute(
                "DELETE FROM mp_sessoes WHERE id_usuario=%s AND token_hash<>%s",
                (user["id_usuario"], user["token_hash"]),
            )
        elif kind == "settings":
            if not isinstance(p.get("animacoes"), bool) or not isinstance(
                p.get("alertas"), bool
            ):
                bad("Preferência inválida")
            conn.execute(
                "UPDATE mp_dados_cliente SET animacoes=%s,alertas=%s WHERE id_cliente=%s",
                (p["animacoes"], p["alertas"], client),
            )
        elif kind == "address":
            a = Address.model_validate(p)
            if a.estado not in UF:
                bad("Estado inválido")
            raw_id = p.get("id", "")
            id = int(raw_id) if str(raw_id).isdigit() else None
            if id:
                found = conn.execute(
                    """UPDATE enderecos SET cep=%s,logradouro=%s,numero=%s,complemento=%s,bairro=%s,cidade=%s,estado=%s
                    WHERE id_endereco=%s AND id_cliente=%s RETURNING id_endereco""",
                    (
                        a.cep,
                        a.logradouro,
                        a.numero,
                        a.complemento,
                        a.bairro,
                        a.cidade,
                        a.estado,
                        id,
                        client,
                    ),
                ).fetchone()
                if not found:
                    bad("Endereço não encontrado", 404)
            else:
                conn.execute(
                    """INSERT INTO enderecos(id_cliente,cep,logradouro,numero,complemento,bairro,cidade,estado)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s)""",
                    (
                        client,
                        a.cep,
                        a.logradouro,
                        a.numero,
                        a.complemento,
                        a.bairro,
                        a.cidade,
                        a.estado,
                    ),
                )
        elif kind == "delete_address":
            id = integer(p.get("id"))
            if conn.execute(
                "SELECT 1 FROM pedidos WHERE id_endereco_entrega=%s AND id_cliente=%s",
                (id, client),
            ).fetchone():
                bad("Endereço utilizado em pedido", 409)
            if not conn.execute(
                "DELETE FROM enderecos WHERE id_endereco=%s AND id_cliente=%s RETURNING id_endereco",
                (id, client),
            ).fetchone():
                bad("Endereço não encontrado", 404)
        elif kind == "review":
            id = integer(p.get("id"))
            rating = integer(p.get("nota"))
            text = str(p.get("comentario", "")).strip()
            if (
                id not in products
                or rating not in range(1, 6)
                or not 1 <= len(text) <= 2000
            ):
                bad("Avaliação inválida")
            conn.execute(
                """INSERT INTO mp_avaliacoes VALUES (%s,%s,%s,%s) ON CONFLICT(id_cliente,id_produto)
                DO UPDATE SET nota=EXCLUDED.nota,comentario=EXCLUDED.comentario""",
                (client, id, rating, text),
            )
        elif kind == "checkout":
            if os.getenv("DEMO_PAYMENTS", "true").lower() != "true":
                bad("Provedor de pagamento ainda não configurado", 503)
            try:
                key = UUID(str(p.get("chave")))
            except (ValueError, TypeError):
                bad("Chave de pedido inválida")
            existing = conn.execute(
                "SELECT id_pedido FROM mp_pedido_meta WHERE id_cliente=%s AND chave=%s",
                (client, key),
            ).fetchone()
            if existing:
                return dict(state(conn, user), order_id=str(existing["id_pedido"]))
            if not cart:
                bad("Carrinho vazio", 409)
            a = conn.execute(
                "SELECT * FROM enderecos WHERE id_cliente=%s AND id_endereco=%s",
                (client, integer(p.get("endereco"))),
            ).fetchone()
            if not a or not a["numero"]:
                bad("Complete seu endereço")
            method = p.get("pagamento")
            installments = integer(p.get("parcelas", 1))
            if method not in [
                "Pix",
                "Cartão de crédito",
                "Boleto",
            ] or installments not in range(1, 13):
                bad("Pagamento inválido")
            if method != "Cartão de crédito":
                installments = 1
            calculated = quotes(a["estado"], cart, products)
            mode = p.get("modalidade")
            if mode not in calculated:
                bad("Modalidade inválida")
            freight = calculated[mode]
            try:
                requested = Decimal(str(p.get("frete")))
            except Exception:
                bad("Frete inválido")
            if freight != requested:
                bad("O carrinho mudou. Consulte o frete novamente", 409)
            lines = []
            total = freight
            for raw, q in sorted(cart.items(), key=lambda x: int(x[0])):
                id = int(raw)
                q = int(q)
                row = conn.execute(
                    "SELECT * FROM produtos WHERE id_produto=%s FOR UPDATE", (id,)
                ).fetchone()
                if (
                    not row
                    or row["status"] != os.getenv("PRODUCT_ACTIVE_STATUS", "ativo")
                    or q < 1
                    or row["estoque"] < q
                ):
                    bad("Estoque insuficiente", 409)
                total += row["preco"] * q
                lines.append(
                    dict(
                        produtoId=id,
                        nome=row["nome"],
                        imagem=row["imagem"] or "",
                        preco=float(row["preco"]),
                        quantidade=q,
                    )
                )
                conn.execute(
                    "UPDATE produtos SET estoque=estoque-%s WHERE id_produto=%s",
                    (q, id),
                )
            # Status do banco configurable; UI mostra explicitamente que é demonstração.
            status = os.getenv("ORDER_INITIAL_STATUS", "pendente")
            order = conn.execute(
                """INSERT INTO pedidos(id_cliente,id_endereco_entrega,data_pedido,status,valor_total)
                VALUES (%s,%s,now(),%s,%s) RETURNING id_pedido""",
                (client, a["id_endereco"], status, total),
            ).fetchone()
            id = order["id_pedido"]
            for line in lines:
                price = Decimal(str(line["preco"]))
                conn.execute(
                    """INSERT INTO itens_pedido(id_pedido,id_produto,quantidade,preco_unitario,subtotal)
                    VALUES (%s,%s,%s,%s,%s)""",
                    (
                        id,
                        line["produtoId"],
                        line["quantidade"],
                        price,
                        price * line["quantidade"],
                    ),
                )
            conn.execute(
                """INSERT INTO pagamentos(id_pedido,metodo,valor,status,codigo_transacao)
                VALUES (%s,%s,%s,%s,%s)""",
                (
                    id,
                    method,
                    total,
                    os.getenv("PAYMENT_DEMO_STATUS", "pendente"),
                    "TESTE-" + str(key),
                ),
            )
            now = int(time.time() * 1000)
            snapshot = dict(
                id=str(id),
                teste=True,
                criado=now,
                itens=lines,
                endereco=address_json(a),
                frete=float(freight),
                modalidade=mode,
                pagamento=method,
                parcelas=installments,
                eventos=[dict(status="Pago (teste)", data=now)],
            )
            conn.execute(
                "INSERT INTO mp_pedido_meta(id_pedido,id_cliente,chave,snapshot) VALUES (%s,%s,%s,%s)",
                (id, client, key, Jsonb(snapshot)),
            )
            conn.execute(
                "UPDATE mp_dados_cliente SET carrinho='{}' WHERE id_cliente=%s",
                (client,),
            )
            return dict(state(conn, user), order_id=str(id))
        elif kind in ["advance", "cancel"]:
            row = conn.execute(
                "SELECT * FROM mp_pedido_meta WHERE id_pedido=%s AND id_cliente=%s FOR UPDATE",
                (integer(p.get("id")), client),
            ).fetchone()
            if not row:
                bad("Pedido não encontrado", 404)
            snap = row["snapshot"]
            status = snap["eventos"][-1]["status"]
            if kind == "advance":
                if (
                    not row["teste"]
                    or os.getenv("DEMO_PAYMENTS", "true").lower() != "true"
                ):
                    bad("Operação apenas para testes", 403)
                next_status = {
                    "Pago (teste)": "Em preparação",
                    "Em preparação": "Enviado (teste)",
                    "Enviado (teste)": "Entregue (teste)",
                }.get(status)
                if not next_status:
                    bad("Pedido já concluído", 409)
            else:
                if status not in ["Pago (teste)", "Em preparação"]:
                    bad("Pedido não pode ser cancelado", 409)
                next_status = "Cancelado"
                for line in sorted(snap["itens"], key=lambda x: x["produtoId"]):
                    conn.execute(
                        "UPDATE produtos SET estoque=estoque+%s WHERE id_produto=%s",
                        (line["quantidade"], line["produtoId"]),
                    )
                conn.execute(
                    "UPDATE pedidos SET status=%s WHERE id_pedido=%s",
                    (
                        os.getenv("ORDER_CANCELLED_STATUS", "cancelado"),
                        row["id_pedido"],
                    ),
                )
                conn.execute(
                    "UPDATE pagamentos SET status=%s WHERE id_pedido=%s",
                    (
                        os.getenv("PAYMENT_CANCELLED_STATUS", "cancelado"),
                        row["id_pedido"],
                    ),
                )
            if kind == "advance":
                env_name = {
                    "Em preparação": "ORDER_PREPARING_STATUS",
                    "Enviado (teste)": "ORDER_SHIPPED_STATUS",
                    "Entregue (teste)": "ORDER_DELIVERED_STATUS",
                }[next_status]
                default = {
                    "Em preparação": "processando",
                    "Enviado (teste)": "enviado",
                    "Entregue (teste)": "entregue",
                }[next_status]
                conn.execute(
                    "UPDATE pedidos SET status=%s WHERE id_pedido=%s",
                    (os.getenv(env_name, default), row["id_pedido"]),
                )
            snap["eventos"].append(
                dict(status=next_status, data=int(time.time() * 1000))
            )
            conn.execute(
                "UPDATE mp_pedido_meta SET snapshot=%s WHERE id_pedido=%s",
                (Jsonb(snap), row["id_pedido"]),
            )
        else:
            bad("Operação desconhecida")
        return state(conn, user)
