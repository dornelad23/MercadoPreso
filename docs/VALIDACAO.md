# Validação da versão 4.1

- assembleDebug: passou, APK de demonstração local gerado.
- testDebugUnitTest: 8 testes Android passaram (7 do armazenamento local e 1 do contrato de checkout da API).
- lintDebug: passou, sem erros. Alertas detalhados no relatório gerado pelo Gradle.
- test_api.py: 5 testes HTTP/validação/hash passaram.
- test_database.py: 8 testes passaram em PGlite (PostgreSQL embarcado), incluindo isolamento, persistência, idempotência, estoque, rollback, linha do tempo, importação segura e preflight.

Um teste encontrou uma falha transitória de conexão do servidor de testes PGlite; a nova execução passou. Esse ambiente não valida concorrência em PostgreSQL convencional.

A versão foi compilada sem URL de API publicada. O teste Android usa resposta real de checkout gerada pelos testes do backend, sem transporte HTTPS fim a fim.

Ainda pendentes: inspecionar os CHECKs, chaves e defaults reais do Neon, aplicar migrações nesse banco, publicar servidor HTTPS, configurar sua URL no Android e validar em aparelho com esse servidor. Nenhuma alteração foi aplicada no banco existente.
