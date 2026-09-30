package com.example.mercadopreso.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable

fun BottomBar(
    selected: String,
    cartCount: Int,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == "home",
            onClick = { onNavigate("home") },
            icon = { Icon(Icons.Outlined.Home, null) },
            label = { Text("Início", fontSize = 10.sp) }
        )
        NavigationBarItem(
            selected = selected == "buscar",
            onClick = { onNavigate("buscar") },
            icon = { Icon(Icons.Outlined.Search, null) },
            label = { Text("Buscar", fontSize = 10.sp) }
        )
        NavigationBarItem(
            selected = selected == "carrinho",
            onClick = { onNavigate("carrinho") },
            icon = {
                BadgedBox(
                    badge = {
                        if (cartCount > 0) Badge { Text(cartCount.toString()) }
                    }
                ) {
                    Icon(Icons.Outlined.ShoppingCart, null)
                }
            },
            label = { Text("Carrinho", fontSize = 10.sp) }
        )
        NavigationBarItem(
            selected = selected == "perfil",
            onClick = { onNavigate("perfil") },
            icon = { Icon(Icons.Outlined.Person, null) },
            label = { Text("Perfil", fontSize = 10.sp) }
        )
    }
}
