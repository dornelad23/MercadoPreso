"""TEST_DATABASE_URL deve apontar exclusivamente para um banco descartável de testes."""

import os
import uuid
import json
import subprocess
import sys
from pathlib import Path
import psycopg
import pytest
from fastapi.testclient import TestClient
import main

URL = os.getenv("TEST_DATABASE_URL")
pytestmark = pytest.mark.skipif(
    not URL, reason="Banco isolado de testes não configurado"
)
client = TestClient(main.app)


@pytest.fixture(autouse=True)
def database(monkeypatch):
    monkeypatch.setenv("DATABASE_URL", URL)
    main._login_attempts.clear()
    with psycopg.connect(URL, autocommit=True) as conn:
        # Schema drop permitido SOMENTE no URL de teste explícito.
        conn.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        for file in ["test_schema.sql", "001_sessions.sql", "002_app.sql"]:
            conn.execute(Path(__file__).with_name(file).read_text())
        conn.execute("INSERT INTO categorias(nome,status) VALUES ('Teste','ativo')")
        conn.execute(
            "INSERT INTO produtos(id_categoria,nome,preco,estoque,status) VALUES (1,'Produto A',10.00,2,'ativo'),(1,'Produto B',20.00,1,'ativo')"
        )
        conn.execute(
            "INSERT INTO mp_produto_meta(id_produto,peso_kg) VALUES (1,1),(2,2)"
        )


def account(email="a@example.com"):
    r = client.post(
        "/auth/register", json=dict(nome="Samuel", email=email, senha="Senha1234")
    )
    assert r.status_code == 201, r.text
    return {"Authorization": "Bearer " + r.json()["access_token"]}


def mutate(headers, kind, **payload):
    return client.post(
        "/app/mutate", headers=headers, json=dict(kind=kind, payload=payload)
    )


def address(headers):
    r = mutate(
        headers,
        "address",
        cep="01001000",
        logradouro="Praça da Sé",
        numero="10",
        cidade="São Paulo",
        estado="SP",
    )
    assert r.status_code == 200, r.text
    return r.json()["user"]["enderecos"][0]["id"]


def checkout(headers, id, key=None):
    return mutate(
        headers,
        "checkout",
        endereco=id,
        chave=key or str(uuid.uuid4()),
        frete=12.20,
        modalidade="Econômico · estimativa",
        pagamento="Pix",
        parcelas=1,
    )


def test_full_account_cart_review_preferences_and_new_session():
    h = account()
    assert mutate(h, "add", id=1).json()["user"]["carrinho"] == {"1": 1}
    assert mutate(h, "favorite", id=1).json()["user"]["favoritos"] == [1]
    assert (
        mutate(h, "settings", animacoes=False, alertas=False).json()["user"][
            "animacoes"
        ]
        is False
    )
    assert (
        mutate(h, "profile", nome="Samuel Dornelas").json()["user"]["nome"]
        == "Samuel Dornelas"
    )
    assert (
        mutate(h, "review", id=1, nota=5, comentario="Muito bom").json()["reviews"][0][
            "nota"
        ]
        == 5
    )
    address(h)
    assert (
        mutate(h, "password", current="Senha1234", next="Outra1234").status_code == 200
    )
    r = client.post("/auth/login", json=dict(email="a@example.com", senha="Outra1234"))
    assert r.status_code == 200, r.text
    fresh = {"Authorization": "Bearer " + r.json()["access_token"]}
    s = client.get("/app/state", headers=fresh).json()
    assert s["user"]["carrinho"] == {"1": 1}
    assert s["user"]["nome"] == "Samuel Dornelas"
    assert s["user"]["enderecos"]
    assert client.post("/auth/logout", headers=fresh).status_code == 204
    assert client.get("/app/state", headers=fresh).status_code == 401


def test_foreign_addresses_and_orders_are_blocked():
    a = account()
    aid = address(a)
    mutate(a, "add", id=1)
    order = checkout(a, aid).json()["order_id"]
    b = account("b@example.com")
    assert mutate(b, "delete_address", id=aid).status_code == 404
    assert mutate(b, "cancel", id=order).status_code == 404
    mutate(b, "add", id=1)
    assert checkout(b, aid).status_code == 422
    assert client.get("/app/state", headers=b).json()["user"]["pedidos"] == []


def test_checkout_idempotency_prices_payment_and_cancel_restore_stock():
    h = account()
    aid = address(h)
    mutate(h, "add", id=1)
    key = str(uuid.uuid4())
    first = checkout(h, aid, key)
    assert first.status_code == 200, first.text
    if os.getenv("EXPORT_ANDROID_FIXTURE"):
        Path(os.environ["EXPORT_ANDROID_FIXTURE"]).write_text(
            json.dumps(first.json(), ensure_ascii=False)
        )
    order = first.json()["order_id"]
    second = checkout(h, aid, key)
    assert second.status_code == 200, second.text
    assert order == second.json()["order_id"]
    snap = second.json()["user"]["pedidos"][0]
    assert snap["itens"][0]["preco"] == 10
    assert snap["eventos"][0]["status"] == "Pago (teste)"
    with psycopg.connect(URL) as conn:
        assert conn.execute("SELECT count(*) FROM pedidos").fetchone()[0] == 1
        assert (
            conn.execute("SELECT estoque FROM produtos WHERE id_produto=1").fetchone()[
                0
            ]
            == 1
        )
        assert conn.execute(
            "SELECT status,data_pagamento FROM pagamentos"
        ).fetchone() == ("pendente", None)
    assert mutate(h, "cancel", id=order).status_code == 200
    assert mutate(h, "cancel", id=order).status_code == 409
    with psycopg.connect(URL) as conn:
        assert (
            conn.execute("SELECT estoque FROM produtos WHERE id_produto=1").fetchone()[
                0
            ]
            == 2
        )


def test_stale_quote_and_stock_failure_roll_back_whole_order():
    h = account()
    aid = address(h)
    mutate(h, "add", id=1)
    r = mutate(
        h,
        "checkout",
        endereco=aid,
        chave=str(uuid.uuid4()),
        frete=0,
        modalidade="Econômico · estimativa",
        pagamento="Pix",
        parcelas=1,
    )
    assert r.status_code == 409
    mutate(h, "add", id=2)
    with psycopg.connect(URL) as conn:
        conn.execute("UPDATE produtos SET estoque=0 WHERE id_produto=2")
    r = mutate(
        h,
        "checkout",
        endereco=aid,
        chave=str(uuid.uuid4()),
        frete=16.80,
        modalidade="Econômico · estimativa",
        pagamento="Pix",
        parcelas=1,
    )
    assert r.status_code == 409, r.text
    with psycopg.connect(URL) as conn:
        assert (
            conn.execute("SELECT estoque FROM produtos WHERE id_produto=1").fetchone()[
                0
            ]
            == 2
        )
        assert conn.execute("SELECT count(*) FROM pedidos").fetchone()[0] == 0


def test_demo_timeline_rejects_cancel_after_shipping():
    h = account()
    aid = address(h)
    mutate(h, "add", id=1)
    order = checkout(h, aid).json()["order_id"]
    assert mutate(h, "advance", id=order).status_code == 200
    assert mutate(h, "advance", id=order).status_code == 200
    assert mutate(h, "cancel", id=order).status_code == 409
    r = mutate(h, "advance", id=order)
    assert r.json()["user"]["pedidos"][0]["eventos"][-1]["status"] == "Entregue (teste)"


def test_malformed_address_and_bad_password_do_not_write():
    h = account()
    assert mutate(h, "address", cep="x").status_code == 422
    assert mutate(h, "password", current="wrong", next="Outra1234").status_code == 403
    assert (
        client.post(
            "/auth/login", json=dict(email="a@example.com", senha="Outra1234")
        ).status_code
        == 401
    )


def test_catalog_seed_preserves_existing_and_imports_only_empty_database():
    with psycopg.connect(URL) as conn:
        conn.execute("TRUNCATE produtos,categorias RESTART IDENTITY CASCADE")
    env = dict(os.environ, DATABASE_URL=URL)
    script = str(Path(__file__).with_name("seed_catalog.py"))
    first = subprocess.run(
        [sys.executable, script, "--empty-only"],
        env=env,
        capture_output=True,
        text=True,
    )
    assert first.returncode == 0, first.stdout + first.stderr
    rows = client.get("/app/catalog").json()["products"]
    assert len(rows) == 50
    assert len({r["imagem"] for r in rows}) == 50
    assert all(r["imagem"].startswith("https://") for r in rows)
    second = subprocess.run(
        [sys.executable, script, "--empty-only"],
        env=env,
        capture_output=True,
        text=True,
    )
    assert second.returncode != 0
    assert len(client.get("/app/catalog").json()["products"]) == 50


def test_preflight_detects_missing_automatic_ids():
    from preflight import inspect
    from psycopg.rows import dict_row

    with psycopg.connect(URL, row_factory=dict_row) as conn:
        assert inspect(conn)[0] == []
        conn.execute("ALTER TABLE usuarios ALTER COLUMN id_usuario DROP IDENTITY")
        assert any("usuarios.id_usuario" in e for e in inspect(conn)[0])
