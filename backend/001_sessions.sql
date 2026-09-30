-- Executar somente após verificar PK de usuarios e geração automática dos IDs existentes.
BEGIN;
CREATE TABLE IF NOT EXISTS mp_sessoes (
    token_hash varchar(64) PRIMARY KEY,
    id_usuario integer NOT NULL REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    expira_em timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS mp_sessoes_usuario_idx ON mp_sessoes(id_usuario);
COMMIT;
