# Correções — Mercado Preso 3.0

- Código Kotlin organizado em várias linhas, com indentação.
- Corrigido o parêntese excedente da página inicial.
- Corrigidos argumentos de cor nas chamadas de Text e argumento modifier no Card clicável.
- Aba Buscar usa o catálogo com busca e filtros já existentes.
- Botão Sair da conta agora executa o logout; senha fica oculta no campo.
- Conteúdo respeita o espaçamento do Scaffold e o botão Voltar do Android.
- Grade inicial usa linhas na lista, sem altura fixa ou rolagem vertical aninhada.
- Alterar o CEP limpa o endereço e o frete anteriores.
- Carrinho aplica frete zero quando todos os produtos possuem frete grátis.
- Novas avaliações atualizam a lista exibida.
- Corrigida a posição do botão de favoritos; ele continua demonstrativo.
- Produtos sem estoque não podem ser adicionados ao carrinho.

## Validação e limites

Foram verificados estaticamente os delimitadores do Kotlin, a estrutura dos XMLs e os pontos corrigidos. Não foi feita compilação nem execução no emulador: o ambiente não tem Gradle/Android SDK. O projeto recebido também não inclui Gradle Wrapper. Abra no Android Studio com JDK 17, SDK 35 e Gradle 8.10.2 para sincronizar, compilar e testar.

Login, endereço e frete continuam simulados. Sessão, carrinho e avaliações não possuem armazenamento persistente. Favoritos e opções secundárias do perfil continuam demonstrativos. O pagamento continua fora do escopo original. A sessão não é preservada ao recriar a Activity.

## Ajuste adicional

Corrigido o item de espaçamento que estava fora do escopo da LazyColumn em CartScreen. O resumo do pedido agora também permanece dentro do bloco de carrinho preenchido.

## Espaçamento dos literais

Corrigido o else que estava colado ao texto da estrela em ProductScreen, causando Unsupported [literal prefixes and suffixes]. Todas as telas foram verificadas para identificar palavras coladas ao fechamento de strings.
