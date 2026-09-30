package com.example.mercadopreso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.mercadopreso.data.AppStore
import com.example.mercadopreso.data.ProductRepository
import com.example.mercadopreso.ui.MercadoPresoApp
import com.example.mercadopreso.ui.theme.MercadoPresoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProductRepository.init(applicationContext)
        AppStore.init(applicationContext)
        setContent { MercadoPresoTheme { MercadoPresoApp() } }
    }
}
