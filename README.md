# Integração com Neon preparada

A versão 4.1 inclui API Python e cliente HTTPS no Android. Confira [backend/README.md](backend/README.md)
para validar o esquema existente e configurar a hospedagem. **Neon ainda não foi validado e a API
não foi publicada nesta entrega.** Sem `MERCADO_API_URL`, o app continua no modo local abaixo.
Pagamento e frete permanecem teste/estimativa. Nenhuma credencial do banco pertence ao APK.

# Mercado Preso 4.1

Aplicativo Android em Kotlin e Jetpack Compose. Ambiente de teste local: não cobra dinheiro e não envia mercadorias.

## Funcionalidades

- 50 produtos em português, com fotos correspondentes incluídas no APK para uso offline.
- Busca, categorias, ordenação por preço, promoções e favoritos.
- Cadastro e login locais, nome editável, alteração de senha e logout.
- Carrinho persistente por conta, quantidades e controle de estoque local.
- Endereços reais por CEP usando ViaCEP, com número e complemento informados pelo usuário.
- Entrega econômica/expressa estimada, claramente identificada como estimativa.
- Checkout com Pix, cartão ou boleto de teste, parcelas e confirmação explícita sem cobrança.
- Pedidos persistentes com detalhes, total, histórico e avanço manual das etapas de entrega no ambiente de teste.
- Cancelamento antes do envio, devolvendo o estoque local.
- Avaliações persistentes por produto e conta.
- Animações de navegação, fotos, favoritos, expansão de cartões e mudanças de formulário; opção de reduzir navegação e fotos.
- Preferência de avisos de sucesso; erros continuam sendo apresentados.

## Abrir e compilar

Abra a pasta do repositório no Android Studio. Use JDK 17, SDK 35, Android Gradle Plugin 8.8.0 e Gradle 8.10.2. Não atualize as dependências individualmente sem verificar a compatibilidade.

Linux/macOS:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Testar o fluxo

1. Adicione produtos e marque favoritos.
2. Crie uma conta com nome, e-mail e senha de teste (6 caracteres ou mais).
3. No carrinho, consulte um CEP real e complete rua/número. CEPs gerais podem não retornar rua; o campo pode ser completado manualmente.
4. Continue para pagamento, escolha endereço, entrega e forma de pagamento.
5. Para cartão, informe somente nome e quatro dígitos fictícios. O app não coleta número completo nem CVV.
6. Confirme o pagamento de teste; o carrinho é esvaziado após o pedido ser gravado.
7. Veja o pedido em Perfil → Meus pedidos e use “Simular próxima etapa da entrega”.
8. Feche e reabra o app: conta, pedidos, favoritos, endereços e configurações permanecem.

## Limites desta versão

- Contas e dados são locais. Usar o mesmo e-mail em outro aparelho não sincroniza os dados.
- O endereço é consultado online. Não há resposta falsa quando o ViaCEP falha.
- O frete é estimado, com regras de teste por UF e peso ilustrativo. Não é cotação de Correios ou transportadora. É necessário CEP de origem e conta de integração para cotações reais.
- Pagamentos e entrega são testes, conforme a opção escolhida. “Pago (teste)” não significa dinheiro recebido. Pix e boleto reais não são emitidos.
- Preços, vendas, avaliações iniciais, pesos e estoque são demonstrativos. O catálogo não é um inventário real de vendedor.
- As fotos e os nomes correspondem aos produtos da base DummyJSON. O catálogo JSON guarda a URL de origem de cada foto em `fotoFonte`.
- Não há integração com Neon nesta etapa. Não coloque a senha do banco no aplicativo. Veja `docs/NEON.md`.
- Desinstalar o app ou limpar seus dados apaga o armazenamento local. Backup automático foi desativado para as contas de teste.

## Fontes

- Catálogo e imagens: https://dummyjson.com/docs/products e https://github.com/Ovi/DummyJSON
- Endereços: https://viacep.com.br/
- Carregamento de fotos: Coil 2.6.0.

A licença MIT da fonte do catálogo está incluída em `docs/DUMMYJSON_LICENSE.txt`. Marcas e imagens de produtos pertencem aos respectivos titulares; substituir por materiais autorizados do vendedor antes de lançar uma loja comercial.
