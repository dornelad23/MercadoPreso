package com.example.mercadopreso.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import com.example.mercadopreso.data.ProductRepository
import com.example.mercadopreso.ui.components.ProductCard

@Composable fun HomeScreen(onProduct: (Int) -> Unit,
    onPromotions: () -> Unit,
    onCart: () -> Unit) {
    var query by remember {
        mutableStateOf("")
    }
    var category by remember {
        mutableStateOf("Todos")
    }
    val cart = CartManager.quantidadeTotal()
    val filtered = ProductRepository.produtos.filter {
        (category == "Todos" || it.categoria == category) && (query.isBlank() || it.nome.contains(query,
                true) || it.categoria.contains(query,
                true))
    }
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MERCADO PRESO",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold)
                        Text("Preços que você não consegue escapar.",
                            fontSize = 11.sp,
                            color = Color.Gray)
                    }
                    IconButton(onCart) {
                        BadgedBox(badge = {
                                if(cart>0)Badge {
                                    Text(cart.toString())
                                }
                        }) {
                            Icon(Icons.Outlined.ShoppingCart,
                                "Carrinho")
                        }
                    }
                }
                OutlinedTextField(query,
                    {
                        query = it
                    },
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    placeholder = {
                        Text("Buscar produtos...")
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search,
                            null)
                })
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(18.dp).height(175.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(Color(0xFF101010)),
                onClick = onPromotions) {
                Row(Modifier.fillMaxSize().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("OFERTAS PRESAS",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp)
                        Text("Até 50% OFF",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            modifier = Modifier.padding(top = 7.dp))
                        Text("Os melhores preços ficaram presos aqui.",
                            color = Color(0xFFBDBDBD),
                            fontSize = 12.sp)
                        Text("VER PROMOÇÕES  →",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 14.dp))
                    }
                    Text("🏷️",
                        fontSize = 50.sp)
                }
            }
        }
        item {
            Text("Categorias",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp,
                    bottom = 18.dp)) {
                items(ProductRepository.categorias) {
                    cat -> FilterChip(category == cat,
                        {
                            category = cat
                        },
                        {
                            Text(cat)
                    })
                }
            }
        }
        item {
            Text("Ofertas do dia",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp))
            LazyRow(contentPadding = PaddingValues(18.dp,
                    10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ProductRepository.produtos.filter {
                        it.emPromocao
                    }.take(4)) {
                    p -> Box(Modifier.width(190.dp)) {
                        ProductCard(p) {
                            onProduct(p.id)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Todos os produtos",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} itens",
                    color = Color.Gray,
                    fontSize = 11.sp)
            }
        }
        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(180.dp),
                    Alignment.Center) {
                    Text("Nenhum produto encontrado.",
                        color = Color.Gray)
                }
            }
        } else {
            items(filtered.chunked(2),
                key = {
                    it.first().id
            }) {
                row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp,
                        vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach {
                        product ->
                        Box(Modifier.weight(1f)) {
                            ProductCard(product) {
                                onProduct(product.id)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
