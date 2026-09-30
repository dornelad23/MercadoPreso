package com.example.mercadopreso

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mercadopreso.data.AppStore
import com.example.mercadopreso.data.ProductRepository
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fixture gerada pelo checkout real da API em PostgreSQL embarcado de teste. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApiStateTest {
    @Test
    fun serverCheckoutResponseLoadsWithoutLocalPasswordOrLostOrderFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProductRepository.init(context)
        AppStore.init(context)
        val fixture =
            javaClass.classLoader!!
                .getResourceAsStream("api_checkout.json")!!
                .bufferedReader()
                .use { it.readText() }
        AppStore.applyRemote(JSONObject(fixture))
        assertTrue(AppStore.loggedIn)
        assertEquals("a@example.com", AppStore.user.email)
        assertEquals("", AppStore.user.passwordHash)
        assertEquals(0, AppStore.count)
        val order = AppStore.user.pedidos.single()
        assertTrue(order.teste)
        assertEquals("Pago (teste)", order.status)
        assertEquals("01001000", order.endereco.cep)
        assertEquals(22.20, order.total, 0.001)
        assertEquals(1, ProductRepository.find(1)!!.estoque)
    }
}
