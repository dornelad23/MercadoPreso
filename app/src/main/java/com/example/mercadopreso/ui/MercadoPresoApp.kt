package com.example.mercadopreso.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.mercadopreso.data.CartManager
import com.example.mercadopreso.ui.components.BottomBar
import com.example.mercadopreso.ui.screens.*

@Composable

fun MercadoPresoApp() {
    var splash by rememberSaveable { mutableStateOf(true) }
    var screen by rememberSaveable { mutableStateOf("home") }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var session by remember { mutableStateOf<UserSession?>(null) }
    if (splash) {
        SplashScreen { splash = false }
        return
    }
    BackHandler(enabled = selected != null || screen != "home") {
        if (selected != null) selected = null else screen = "home"
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (selected == null && screen != "promocoes") {
                BottomBar(screen, CartManager.quantidadeTotal()) { screen = it }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            val productId = selected
            if (productId != null) {
                ProductScreen(
                    productId = productId,
                    onBack = { selected = null },
                    onCart = { selected = null; screen = "carrinho" }
                )
            } else {
                when (screen) {
                    "home", "buscar" -> HomeScreen(
                        onProduct = { selected = it },
                        onPromotions = { screen = "promocoes" },
                        onCart = { screen = "carrinho" }
                    )
                    "carrinho" -> CartScreen()
                    "perfil" -> ProfileScreen(
                        session,
                        { name, email -> session = UserSession(name, email) },
                        { session = null }
                    )
                    "promocoes" -> PromotionsScreen(
                        onBack = { screen = "home" },
                        onProduct = { selected = it }
                    )
                }
            }
        }
    }
}
