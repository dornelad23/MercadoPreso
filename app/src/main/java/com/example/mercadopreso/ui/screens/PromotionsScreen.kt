package com.example.mercadopreso.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mercadopreso.data.ProductRepository
import com.example.mercadopreso.ui.components.ProductCard

@Composable fun PromotionsScreen(onBack: () -> Unit,
    onProduct: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            IconButton(onBack) {
                Icon(Icons.Outlined.ArrowBack,
                    "Voltar")
            }
            Column(Modifier.padding(top = 10.dp)) {
                Text("Ofertas Presas",
                    style = MaterialTheme.typography.titleLarge)
                Text("Produtos em promoção",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = ProductRepository.produtos.filter {
            it.emPromocao
        }
        LazyVerticalGrid(GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list,
                key = {
                    it.id
            }) {
                p -> ProductCard(p) {
                    onProduct(p.id)
                }
            }
        }
    }
}
