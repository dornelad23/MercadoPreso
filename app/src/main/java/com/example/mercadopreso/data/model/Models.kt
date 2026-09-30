package com.example.mercadopreso.data.model

data class Produto(
    val id: Int,
    val nome: String,
    val descricao: String,
    val preco: Double,
    val categoria: String,
    val avaliacao: Double,
    val vendas: Int,
    val imagem: String,
    val estoque: Int,
    val freteGratis: Boolean,
    val precoOriginal: Double?,
    val pesoKg: Double,
) {
    val emPromocao
        get() = precoOriginal != null && precoOriginal > preco

    val desconto
        get() = precoOriginal?.let { ((it - preco) / it * 100).toInt() } ?: 0
}

data class Endereco(
    val id: String = java.util.UUID.randomUUID().toString(),
    val cep: String,
    val rua: String,
    val numero: String = "",
    val complemento: String = "",
    val bairro: String,
    val cidade: String,
    val uf: String,
) {
    val completo
        get() =
            listOf(rua, numero, complemento, bairro, "$cidade - $uf", cep)
                .filter { it.isNotBlank() }
                .joinToString(", ")
}

data class LinhaPedido(
    val produtoId: Int,
    val nome: String,
    val imagem: String,
    val preco: Double,
    val quantidade: Int,
)

data class EventoPedido(val status: String, val data: Long)

data class Pedido(
    val id: String,
    val criado: Long,
    val itens: List<LinhaPedido>,
    val endereco: Endereco,
    val frete: Double,
    val modalidade: String,
    val pagamento: String,
    val parcelas: Int,
    val eventos: List<EventoPedido>,
    val teste: Boolean = true,
) {
    val subtotal
        get() = itens.sumOf { it.preco * it.quantidade }

    val total
        get() = subtotal + frete

    val status
        get() = eventos.last().status
}

data class Review(
    val produtoId: Int,
    val userId: String,
    val nome: String,
    val nota: Int,
    val comentario: String,
)

data class Usuario(
    val id: String,
    val nome: String,
    val email: String,
    val salt: String = "",
    val passwordHash: String = "",
    val carrinho: Map<Int, Int> = emptyMap(),
    val favoritos: Set<Int> = emptySet(),
    val enderecos: List<Endereco> = emptyList(),
    val pedidos: List<Pedido> = emptyList(),
    val animacoes: Boolean = true,
    val alertas: Boolean = true,
)

data class EstadoApp(
    val usuarios: List<Usuario> = listOf(Usuario("guest", "Visitante", "")),
    val ativo: String = "guest",
    val reviews: List<Review> = emptyList(),
)

data class Cotacao(val nome: String, val valor: Double, val prazo: String)
