package com.example.mercadopreso.data.model

data class ItemCarrinho(
    val produto: Produto,
    val quantidade: Int
) {
    val subtotal: Double
    get() = produto.preco * quantidade
}
