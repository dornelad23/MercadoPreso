# API do Mercado Preso 4.1

Integração preparada para o esquema `mercado_preso` informado. Nenhuma migração é
executada ao iniciar a API. A validação no Neon e a hospedagem ainda estão pendentes.

O Android envia as operações ao servidor quando `MERCADO_API_URL` está configurada.
Sem essa propriedade, mantém a demonstração local 4.0. Não importa automaticamente
contas, senhas ou pedidos locais como registros do banco.

## Conferir o Neon

1. Configure `DATABASE_URL` como variável de ambiente somente no servidor, com SSL.
2. Execute `python preflight.py` ou `schema_report.sql` no SQL Editor do Neon.
3. Confira chaves primárias/estrangeiras, IDs automáticos, CHECKs e campos obrigatórios
   adicionais. `senha_hash` deve comportar Argon2 (recomendado varchar(255) ou text).
4. Configure os valores de status em `.env.example` conforme os CHECKs do seu banco.
5. Depois de conferir a compatibilidade, aplique `001_sessions.sql` e `002_app.sql`.

As migrações adicionam sessões, dados de carrinho/favoritos/preferências, metadados
para os produtos, avaliações e snapshots de pedidos. Não apagam tabelas existentes.
Usuários, clientes, produtos, endereços, pedidos, itens e pagamentos usam as tabelas
originais. Não execute `test_schema.sql` no seu banco: é exclusivo dos testes.

Senhas novas usam Argon2. Hashes antigos de outros algoritmos não são convertidos;
contas antigas exigem estratégia de migração/redefinição antes de conseguir entrar.
A API lista pedidos anteriores para consulta, sem alterar seu pagamento/entrega.

## Rodar a API

```sh
cd backend
python -m venv .venv
# Linux: source .venv/bin/activate
# PowerShell: .\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

O programa lê variáveis de ambiente; `.env.example` não é carregado automaticamente.
Documentação local: http://localhost:8000/docs. `/health` verifica o processo, não a
conexão com o banco. `/app/catalog` verifica a consulta ao catálogo.

Para hospedar, use um serviço compatível com Python ou Docker, configure a conexão
Neon nos segredos do serviço e publique a API com HTTPS. Construção Docker na raiz:

```sh
docker build -f backend/Dockerfile -t mercado-preso-api .
```

O limitador básico de login permite 10 tentativas/minuto por IP/processo. Em produção
com múltiplas instâncias, use limitador compartilhado no proxy/Redis. Restrinja tamanho
de requisições no proxy e configure somente proxies confiáveis no Uvicorn.

## Ativar no Android

Após hospedar a API, adicione ao `gradle.properties` local (ou passe com `-P`):

```properties
MERCADO_API_URL=https://SUA-API
```

```sh
./gradlew assembleDebug -PMERCADO_API_URL=https://SUA-API
```

No PowerShell: ` .\gradlew.bat assembleDebug -PMERCADO_API_URL=https://SUA-API`.
A URL é pública; nenhuma senha do Neon entra no APK. O token de sessão é armazenado
criptografado usando Android Keystore. Operações de rede rodam fora da thread da tela.
O botão Atualizar recupera a conta e o catálogo de outra sessão/aparelho. Sem conexão,
alterações autenticadas falham visivelmente; não são marcadas como salvas localmente.
Sair sem rede remove o token do aparelho, mas a sessão do servidor expira em sete dias.

## Catálogo de 50 produtos

```sh
python seed_catalog.py --empty-only
```

Importação opcional e transacional, permitida apenas se produtos e categorias estiverem
vazios. Usa IDs gerados pelo banco e URLs das fotografias da versão 4.0. O catálogo
existente nunca é sobrescrito. Produtos existentes sem metadados usam peso estimado
padrão de 1 kg até preencher `mp_produto_meta`.

## Persistência e pagamento

`/auth/register`, `/auth/login`, `/auth/logout`, `/me`, `/app/catalog`, `/app/state`
e `/app/mutate` atendem o app. Mutação tem `{kind, payload}`; os dados da conta são
obtidos exclusivamente pelo token autenticado. Carrinho do visitante pode ser unido
ao carrinho da conta, mas pedidos e preços locais não são aceitos como autoridade.

Checkout calcula preços e frete no servidor, bloqueia estoque em ordem crescente de
produto e grava pedido, itens, pagamento de teste, snapshot e limpeza do carrinho em
uma transação. Chave UUID por cliente impede repetir o mesmo pedido após uma tentativa.
Cancelamento devolve estoque uma única vez e é recusado após envio. Eventos de entrega
de demonstração ficam no snapshot e também atualizam o status configurado de pedidos.

Pagamento permanece DEMONSTRAÇÃO: não cobra dinheiro, não recebe número completo do
cartão e não grava uma confirmação de pagamento real. No banco, pagamento fica no status
configurado para teste (`pendente` por padrão), sem data_pagamento e com código TESTE-.
`mp_pedido_meta.teste` identifica a simulação. Desativar `DEMO_PAYMENTS` bloqueia novas
compras até integrar um provedor real. Frete continua estimativa e ViaCEP consulta CEPs;
ainda faltam origem e contrato/API de transportadora para uma cotação real.

## Testes

```sh
python -m pip install pytest httpx
python -m pytest -q test_api.py
```

`test_database.py` exige `TEST_DATABASE_URL` apontando exclusivamente para banco
DESCARTÁVEL: seus fixtures apagam e recriam o schema public. Nunca use a URL Neon real
nesse teste. Testamos contratos HTTP, isolamento, persistência entre sessões, rollback,
idempotência, estoque/cancelamento, linha do tempo, importação e preflight com PGlite
(PostgreSQL embarcado). Esses testes não substituem validação no Neon e teste de
concorrência em PostgreSQL convencional. O Android também verifica a resposta real do
checkout gerada nos testes da API por meio de `api_checkout.json`.
