-- Somente leitura. Executar no SQL Editor do Neon e conferir antes da migração.
SELECT table_name, column_name, column_default, is_identity, character_maximum_length
FROM information_schema.columns WHERE table_schema='public'
ORDER BY table_name, ordinal_position;
SELECT conrelid::regclass::text AS tabela, conname, pg_get_constraintdef(oid) AS regra
FROM pg_constraint WHERE connamespace='public'::regnamespace ORDER BY tabela,conname;
