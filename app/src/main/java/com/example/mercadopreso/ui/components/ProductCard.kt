package com.example.mercadopreso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mercadopreso.data.CartManager
import com.example.mercadopreso.data.model.Produto
import java.util.Locale

@Composable fun ProductCard(produto: Produto,
    onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable {
            onClick()
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(145.dp).background(Color(0xFFF0F0F0),
                    RoundedCornerShape(12.dp)),
                Alignment.Center) {
                Text(produto.emoji,
                    fontSize = 52.sp)
                if(produto.emPromocao) Surface(Modifier.align(Alignment.TopStart),
                    color = Color(0xFF101010),
                    shape = RoundedCornerShape(bottomEnd = 8.dp)) {
                    Text(text = "-${produto.desconto}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(7.dp))
                }
                IconButton( {
                    },
                    Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Outlined.FavoriteBorder,
                        "Favoritar")
                }
            }
            Text(produto.nome,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 9.dp))
            Text("★ ${produto.avaliacao}  •  ${produto.vendas} vendidos",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp))
            if(produto.precoOriginal != null)Text(String.format(Locale("pt",
                        "BR"),
                    "R$ %.2f",
                    produto.precoOriginal),
                color = Color.Gray,
                fontSize = 10.sp,
                style = LocalTextStyle.current.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough))
            Text(String.format(Locale("pt",
                        "BR"),
                    "R$ %.2f",
                    produto.preco),
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp)
            Text(if(produto.freteGratis) "Frete grátis" else "Frete calculado no CEP",
                color = if(produto.freteGratis)Color(0xFF16823A)else Color.Gray,
                fontSize = 10.sp)
            IconButton( {
                    CartManager.adicionar(produto)
                },
                Modifier.align(Alignment.End).size(38.dp)) {
                Icon(Icons.Outlined.AddShoppingCart,
                    "Adicionar")
            }
        }
    }
}
