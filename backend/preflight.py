"""Relatório somente leitura; nunca imprime a conexão ou dados pessoais."""

import os
import sys
import psycopg
from psycopg.rows import dict_row

EXPECTED = {
    "usuarios": [
        "id_usuario",
        "nome",
        "email",
        "senha_hash",
        "status",
        "data_cadastro",
        "ultimo_acesso",
    ],
    "clientes": ["id_cliente", "id_usuario"],
    "enderecos": [
        "id_endereco",
        "id_cliente",
        "cep",
        "logradouro",
        "numero",
        "complemento",
        "bairro",
        "cidade",
        "estado",
    ],
    "categorias": ["id_categoria", "nome", "descricao", "status"],
    "produtos": [
        "id_produto",
        "id_categoria",
        "nome",
        "descricao",
        "preco",
        "estoque",
        "imagem",
        "status",
    ],
    "pedidos": [
        "id_pedido",
        "id_cliente",
        "id_endereco_entrega",
        "data_pedido",
        "status",
        "valor_total",
    ],
    "itens_pedido": [
        "id_item",
        "id_pedido",
        "id_produto",
        "quantidade",
        "preco_unitario",
        "subtotal",
    ],
    "pagamentos": [
        "id_pagamento",
        "id_pedido",
        "metodo",
        "valor",
        "status",
        "codigo_transacao",
    ],
}


def inspect(conn):
    rows = conn.execute(
        """SELECT table_name,column_name,column_default,is_identity,character_maximum_length,is_nullable
        FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position"""
    ).fetchall()
    columns = {(r["table_name"], r["column_name"]): r for r in rows}
    errors = []
    for table, names in EXPECTED.items():
        for name in names:
            if (table, name) not in columns:
                errors.append(f"Falta {table}.{name}")
        id_column = columns.get((table, names[0]))
        if (
            id_column
            and not id_column["column_default"]
            and id_column["is_identity"] != "YES"
        ):
            errors.append(f"{table}.{names[0]} não possui geração automática")
    password = columns.get(("usuarios", "senha_hash"))
    if (
        password
        and password["character_maximum_length"]
        and password["character_maximum_length"] < 100
    ):
        errors.append(
            "usuarios.senha_hash precisa suportar Argon2 (recomendado varchar(255) ou text)"
        )
    constraints = conn.execute(
        """SELECT conrelid::regclass::text tabela,conname,pg_get_constraintdef(oid) regra
        FROM pg_constraint WHERE connamespace='public'::regnamespace ORDER BY tabela,conname"""
    ).fetchall()
    return errors, constraints


if __name__ == "__main__":
    if not os.getenv("DATABASE_URL"):
        sys.exit("Configure DATABASE_URL no servidor.")
    try:
        with psycopg.connect(
            os.environ["DATABASE_URL"], row_factory=dict_row, connect_timeout=10
        ) as conn:
            conn.execute("SET TRANSACTION READ ONLY")
            errors, constraints = inspect(conn)
            for item in constraints:
                print(item["tabela"], item["conname"], item["regra"])
            for item in errors:
                print("BLOQUEIO:", item)
            print(
                "Confira também os CHECKs e os status permitidos antes de aplicar as migrações."
            )
            sys.exit(1 if errors else 0)
    except psycopg.Error:
        sys.exit("Falha ao consultar o esquema. Confira a configuração do servidor.")
