"""Importação opcional do catálogo 4.0 somente se produtos E categorias estiverem vazios."""

import json
import os
import sys
from decimal import Decimal
from pathlib import Path
import psycopg
from psycopg.rows import dict_row
from preflight import inspect

if __name__ == "__main__":
    if "--empty-only" not in sys.argv:
        sys.exit("Use --empty-only para importar apenas em catálogo vazio.")
    if not os.getenv("DATABASE_URL"):
        sys.exit("Configure DATABASE_URL no servidor.")
    source = Path(__file__).parents[1] / "app/src/main/assets/produtos.json"
    if not source.exists():
        source = Path("/app/catalog/produtos.json")
    items = json.loads(source.read_text())
    try:
        with psycopg.connect(
            os.environ["DATABASE_URL"], row_factory=dict_row, connect_timeout=10
        ) as conn:
            conn.execute("LOCK TABLE produtos,categorias IN EXCLUSIVE MODE")
            errors, _ = inspect(conn)
            if errors:
                sys.exit(
                    "Esquema incompatível; execute preflight.py antes de importar."
                )
            if (
                conn.execute("SELECT 1 FROM produtos LIMIT 1").fetchone()
                or conn.execute("SELECT 1 FROM categorias LIMIT 1").fetchone()
            ):
                sys.exit("Catálogo existente preservado. Importação não executada.")
            categories = {}
            for name in sorted({p["categoria"] for p in items}):
                categories[name] = conn.execute(
                    "INSERT INTO categorias(nome,status) VALUES (%s,%s) RETURNING id_categoria",
                    (name, os.getenv("CATEGORY_ACTIVE_STATUS", "ativo")),
                ).fetchone()["id_categoria"]
            for p in items:
                id = conn.execute(
                    """INSERT INTO produtos(id_categoria,nome,descricao,preco,estoque,imagem,status,data_cadastro)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,now()) RETURNING id_produto""",
                    (
                        categories[p["categoria"]],
                        p["nome"],
                        p["descricao"],
                        Decimal(str(p["preco"])),
                        p["estoque"],
                        p["fotoFonte"],
                        os.getenv("PRODUCT_ACTIVE_STATUS", "ativo"),
                    ),
                ).fetchone()["id_produto"]
                conn.execute(
                    """INSERT INTO mp_produto_meta(id_produto,peso_kg,frete_gratis,preco_original,avaliacao,vendas)
                    VALUES (%s,%s,%s,%s,%s,%s)""",
                    (
                        id,
                        Decimal(str(p["pesoKg"])),
                        p["freteGratis"],
                        (
                            Decimal(str(p["precoOriginal"]))
                            if p["precoOriginal"]
                            else None
                        ),
                        Decimal(str(p["avaliacao"])),
                        p["vendas"],
                    ),
                )
        print(f"{len(items)} produtos importados. IDs gerados pelo banco.")
    except psycopg.Error:
        sys.exit(
            "Falha na importação; a transação foi revertida. Confira esquema e configuração."
        )
