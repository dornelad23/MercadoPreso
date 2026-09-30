package com.example.mercadopreso.data.model

data class Produto(
    val id: Int,
    val nome: String,
    val descricao: String,
    val preco: Double,
    val categoria: String,
    val avaliacao: Double,
    val vendas: Int,
    val emoji: String,
    val freteGratis: Boolean = false,
    val precoOriginal: Double? = null,
    val estoque: Int = 10
) {
    val emPromocao get() = precoOriginal != null && precoOriginal > preco
    val desconto get() = if (emPromocao) (((precoOriginal!! - preco) / precoOriginal) * 100).toInt() else 0
}
