# API inicial do Mercado Preso

Base preparada para o esquema informado. Ainda não conectada ao Neon nem ao Android.
Não substitui a implementação completa de carrinho, pagamento e pedidos.

## Antes de conectar

Execute `schema_report.sql` no SQL Editor do Neon. É necessário conferir as chaves,
valores de status, tamanho de senha_hash (Argon2 exige aproximadamente 97 caracteres),
IDs automáticos de usuarios/clientes/enderecos e campos obrigatórios com defaults.
Não execute a migração se as chaves existentes forem diferentes.
Não muda nem converte hashes de senha antigos: contas antigas com outro algoritmo
precisarão de uma estratégia de migração ou redefinição de senha.

Depois de conferir, execute `001_sessions.sql`. É uma tabela adicional para sessões;
nenhuma tabela existente é apagada. Nunca coloque DATABASE_URL no aplicativo Android.

## Executar no servidor

```sh
cd backend
python -m venv .venv
# Linux: source .venv/bin/activate
# PowerShell: .\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

Defina DATABASE_URL nas variáveis do servidor usando a conexão Neon com SSL.
USER_ACTIVE_STATUS e PRODUCT_ACTIVE_STATUS devem corresponder aos valores reais
permitidos pelas suas tabelas (o padrão é `ativo`). `.env.example` é apenas modelo;
o programa lê variáveis de ambiente, não carrega `.env` automaticamente.

```sh
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Documentação local: http://localhost:8000/docs.
Produção requer HTTPS, limitação de tentativas no login/cadastro e configuração
segura do serviço de hospedagem. Esta base ainda não está pronta para publicação.

## Endpoints

- POST /auth/register e /auth/login: retornam token de sessão de sete dias.
- POST /auth/logout: revoga o token recebido.
- GET /me: perfil autenticado.
- GET /products: catálogo do banco.
- GET/POST /addresses e DELETE /addresses/{id}: endereços do cliente autenticado.

Envie `Authorization: Bearer TOKEN` nos endpoints privados. O banco guarda apenas
hash do token. Senhas novas usam Argon2. IDs de outros clientes não autorizam acesso.
As gravações usam transações e parâmetros SQL.

## Verificação

```sh
python -m pip install pytest httpx
python -m pytest -q
```

Os testes verificam contratos HTTP, validação e hash. Não verificam SQL com banco real.
Para integrar a versão 4.0, é necessário ter seus arquivos atuais; o main do GitHub
consultado contém a versão inicial. Próximas etapas: compatibilidade do esquema,
validação real no Neon, carrinho/favoritos/pedidos transacionais e conexão Android.
Pagamento seguirá em modo teste até contratar e integrar um provedor.
