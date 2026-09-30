package com.example.mercadopreso.data

import androidx.compose.runtime.mutableStateListOf
import com.example.mercadopreso.data.model.ItemCarrinho
import com.example.mercadopreso.data.model.Produto

object CartManager {
    val itens = mutableStateListOf<ItemCarrinho>()
    fun adicionar(p: Produto) {
        if(p.estoque <= 0)return
        val i = itens.indexOfFirst {
            it.produto.id == p.id
        }
        if(i >= 0)itens[i] = itens[i].copy(quantidade = (itens[i].quantidade+1).coerceAtMost(p.estoque)) else itens.add(ItemCarrinho(p,
                1))
    }
    fun remover(p: Produto) {
        val i = itens.indexOfFirst {
            it.produto.id == p.id
        }
        if(i<0)return
        if(itens[i].quantidade>1)itens[i] = itens[i].copy(quantidade = itens[i].quantidade-1)else itens.removeAt(i)
    }
    fun excluir(p: Produto) = itens.removeAll {
        it.produto.id == p.id
    }
    fun quantidadeTotal() = itens.sumOf {
        it.quantidade
    }
    fun subtotal() = itens.sumOf {
        it.subtotal
    }
    fun validarCep(cep: String) = cep.filter(Char::isDigit).length == 8 && !cep.filter(Char::isDigit).startsWith("00000")
    fun frete(cep: String): Double {
        val n = cep.filter(Char::isDigit)
        if(!validarCep(n))return 0.0
        return when(n.take(2).toInt()) {
            in 1..5 -> 8.90
            in 6..15 -> 12.90
            in 16..30 -> 18.90
            in 31..49 -> 21.90
            else -> 26.90
        }
    }
    fun endereco(cep: String): String? {
        val n = cep.filter(Char::isDigit)
        if(!validarCep(n))return null
        return when(n.take(2).toInt()) {
            in 1..5 -> "Rua das Compras, 120 • Centro, São Paulo - SP"
            in 6..15 -> "Av. Mercado Preso, 450 • Vila Mariana, São Paulo - SP"
            in 16..30 -> "Rua do Comércio, 88 • Tatuapé, São Paulo - SP"
            in 31..49 -> "Av. dos Produtos, 210 • Campinas - SP"
            else -> "Rua da Entrega, 75 • Rio de Janeiro - RJ"
        }
    }
}
