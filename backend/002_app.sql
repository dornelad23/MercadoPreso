-- Migração aditiva; conferir schema_report.sql primeiro. IDs existentes devem ser automáticos.
BEGIN;
CREATE TABLE IF NOT EXISTS mp_dados_cliente (
 id_cliente integer PRIMARY KEY REFERENCES clientes(id_cliente) ON DELETE CASCADE,
 carrinho jsonb NOT NULL DEFAULT '{}', favoritos jsonb NOT NULL DEFAULT '[]',
 animacoes boolean NOT NULL DEFAULT true, alertas boolean NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS mp_produto_meta (
 id_produto integer PRIMARY KEY REFERENCES produtos(id_produto),
 peso_kg numeric NOT NULL DEFAULT 1 CHECK(peso_kg>0), frete_gratis boolean NOT NULL DEFAULT false,
 preco_original numeric, avaliacao numeric NOT NULL DEFAULT 0, vendas integer NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS mp_avaliacoes (
 id_cliente integer REFERENCES clientes(id_cliente) ON DELETE CASCADE,
 id_produto integer REFERENCES produtos(id_produto), nota integer NOT NULL CHECK(nota BETWEEN 1 AND 5),
 comentario text NOT NULL, PRIMARY KEY(id_cliente,id_produto)
);
CREATE TABLE IF NOT EXISTS mp_pedido_meta (
 id_pedido integer PRIMARY KEY REFERENCES pedidos(id_pedido),
 id_cliente integer NOT NULL REFERENCES clientes(id_cliente),
 chave uuid NOT NULL, snapshot jsonb NOT NULL, teste boolean NOT NULL DEFAULT true,
 UNIQUE(id_cliente,chave)
);
COMMIT;
