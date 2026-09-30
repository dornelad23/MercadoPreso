# Conexão Neon — versão 4.1

O cliente HTTPS e a API já estão no projeto. Consulte `backend/README.md` para preflight,
migrações aditivas e configuração. A implantação da API e a validação no Neon ainda
estão pendentes; não existe endereço de servidor ativo embutido nesta entrega.

Não coloque DATABASE_URL, senha PostgreSQL nem chave de provedor de pagamento no APK.
Defina MERCADO_API_URL somente depois de obter o endereço HTTPS do servidor.

Dados locais da versão 4.0 continuam no modo demonstração. Contas e pedidos locais não
são convertidos silenciosamente em registros reais. Para o modo online, cadastre uma
conta na API (ou implemente migração de hashes antigos do banco após inspecioná-los).

Pagamento e frete ainda são demonstração/estimativa. Confirmação visual de teste não
representa dinheiro recebido ou envio por transportadora.
