package com.example.mercadopreso.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mercadopreso.data.CartManager
import java.util.Locale

private fun money(v: Double) = String.format(Locale("pt",
        "BR"),
    "R$ %.2f",
    v)

@Composable fun CartScreen() {
    var cep by remember {
        mutableStateOf("")
    }
    var address by remember {
        mutableStateOf<String?>(null)
    }
    var freight by remember {
        mutableStateOf<Double?>(null)
    }
    var error by remember {
        mutableStateOf<String?>(null)
    }
    val items = CartManager.itens
    val subtotal = CartManager.subtotal()
    val deliveryFreight = freight?.let {
        if(items.all {
                item -> item.produto.freteGratis
        })0.0 else it
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(18.dp)) {
        Text("Seu carrinho",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold)
        Text("${CartManager.quantidadeTotal()} itens",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp,
                bottom = 13.dp))
        if(items.isEmpty())Box(Modifier.fillMaxSize(),
            Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛒",
                    fontSize = 55.sp)
                Text("Seu carrinho está vazio",
                    fontWeight = FontWeight.Bold)
                Text("Adicione produtos para começar.",
                    color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items,
                    key = {
                        it.produto.id
                }) {
                    item -> Card(colors = CardDefaults.cardColors(Color.White),
                        shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(68.dp).background(Color(0xFFF0F0F0),
                                    RoundedCornerShape(10.dp)),
                                Alignment.Center) {
                                Text(item.produto.emoji,
                                    fontSize = 33.sp)
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(item.produto.nome,
                                    fontWeight = FontWeight.SemiBold)
                                Text(money(item.produto.preco),
                                    fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton( {
                                            CartManager.remover(item.produto)
                                        },
                                        Modifier.size(30.dp)) {
                                        Icon(Icons.Outlined.Remove,
                                            null)
                                    }
                                    Text(item.quantidade.toString())
                                    IconButton( {
                                            CartManager.adicionar(item.produto)
                                        },
                                        Modifier.size(30.dp)) {
                                        Icon(Icons.Outlined.Add,
                                            null)
                                    }
                                }
                            }
                            IconButton( {
                                    CartManager.excluir(item.produto)
                            }) {
                                Icon(Icons.Outlined.DeleteOutline,
                                    "Remover")
                            }
                        }
                    }
                }
                item {
                    FreightBox(cep,
                        {
                            cep = it
                            error = null
                            address = null
                            freight = null
                        },
                        {
                            if(CartManager.validarCep(cep)) {
                                address = CartManager.endereco(cep)
                                freight = CartManager.frete(cep)
                                error = null
                            } else {
                                address = null
                                freight = null
                                error = "CEP inválido. Digite exatamente 8 números."
                            }
                        },
                        address,
                        deliveryFreight,
                        error,
                        items.all {
                            it.produto.freteGratis
                    })
                }
                item {
                    Spacer(Modifier.height(5.dp))
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal",
                        color = Color.Gray)
                    Text(money(subtotal))
                }
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Frete",
                        color = Color.Gray)
                    Text(deliveryFreight?.let(::money) ?: "Calcule acima")
                }
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold)
                    Text(money(subtotal+(deliveryFreight ?: 0.0)),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold)
                }
                Button( {
                    },
                    Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp),
                    enabled = address != null,
                    shape = RoundedCornerShape(13.dp)) {
                    Text(if(address == null) "Calcule o frete para continuar" else "Continuar pedido")
                }
                Text("Pagamento não é realizado nesta versão.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
