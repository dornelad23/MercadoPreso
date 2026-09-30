package com.example.mercadopreso

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mercadopreso.data.*
import com.example.mercadopreso.data.model.Endereco
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreTest {
    private suspend fun expectIllegal(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }

    private lateinit var context: Context
    private val address =
        Endereco(
            cep = "01001000",
            rua = "Praça da Sé",
            numero = "10",
            bairro = "Sé",
            cidade = "São Paulo",
            uf = "SP",
        )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("mercado_preso_v4", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ProductRepository.init(context)
        AppStore.init(context)
    }

    @Test
    fun catalogContainsFiftyUniqueProductPhotos() = runBlocking {
        assertEquals(50, ProductRepository.produtos.size)
        assertEquals(50, ProductRepository.produtos.map { it.id }.toSet().size)
        assertEquals(50, ProductRepository.produtos.map { it.imagem }.toSet().size)
    }

    @Test
    fun accountCartFavoritesAddressesSettingsAndPasswordSurviveReload() = runBlocking {
        val product = ProductRepository.produtos.first()
        AppStore.add(product)
        AppStore.favorite(product.id)
        AppStore.register("Samuel", "samuel@example.com", "teste123")
        AppStore.saveAddress(address)
        AppStore.settings(false, false)
        AppStore.changePassword("teste123", "nova123")
        AppStore.init(context)
        assertTrue(AppStore.loggedIn)
        assertEquals(1, AppStore.count)
        assertTrue(product.id in AppStore.user.favoritos)
        assertEquals(address, AppStore.user.enderecos.single())
        assertFalse(AppStore.user.animacoes)
        AppStore.logout()
        AppStore.login("samuel@example.com", "nova123")
        assertEquals("Samuel", AppStore.user.nome)
    }

    @Test
    fun accountsDoNotShareOrdersOrFavorites() = runBlocking {
        AppStore.register("Samuel", "samuel@example.com", "teste123")
        val p = ProductRepository.produtos.first()
        AppStore.favorite(p.id)
        AppStore.logout()
        AppStore.register("Outro", "outro@example.com", "teste123")
        assertTrue(AppStore.user.favoritos.isEmpty())
        assertTrue(AppStore.user.pedidos.isEmpty())
        AppStore.logout()
        AppStore.login("samuel@example.com", "teste123")
        assertTrue(p.id in AppStore.user.favoritos)
    }

    @Test
    fun checkoutPersistsSnapshotClearsCartAndRestoresStockOnCancellation() = runBlocking {
        AppStore.register("Samuel", "samuel@example.com", "teste123")
        val p = ProductRepository.produtos.first()
        val initialStock = AppStore.available(p)
        AppStore.add(p)
        val quote = ShippingService.quotes(address.uf, AppStore.user.carrinho).first()
        val id = AppStore.checkout(address, quote, "Pix", 1)
        assertEquals(0, AppStore.count)
        assertEquals(initialStock - 1, AppStore.available(p))
        AppStore.init(context)
        val order = AppStore.user.pedidos.single()
        assertEquals(id, order.id)
        assertEquals("Pago (teste)", order.status)
        assertEquals(p.preco + quote.valor, order.total, 0.001)
        expectIllegal { AppStore.checkout(address, quote, "Pix", 1) }
        assertEquals(1, AppStore.user.pedidos.size)
        AppStore.cancel(id)
        assertEquals(initialStock, AppStore.available(p))
    }

    @Test
    fun staleFreightQuoteCannotCreateOrder() = runBlocking {
        AppStore.register("Samuel", "samuel@example.com", "teste123")
        val p = ProductRepository.produtos.first { !it.freteGratis }
        AppStore.add(p)
        val oldQuote = ShippingService.quotes("SP", AppStore.user.carrinho).first()
        AppStore.add(p)
        expectIllegal { AppStore.checkout(address, oldQuote, "Pix", 1) }
        assertEquals(2, AppStore.count)
        assertTrue(AppStore.user.pedidos.isEmpty())
    }

    @Test
    fun shippedOrderCannotBeCancelledAndTimelineSurvivesReload() = runBlocking {
        AppStore.register("Samuel", "samuel@example.com", "teste123")
        AppStore.add(ProductRepository.produtos.first())
        val quote = ShippingService.quotes("SP", AppStore.user.carrinho).first()
        val id = AppStore.checkout(address, quote, "Boleto", 1)
        AppStore.advance(id)
        AppStore.advance(id)
        expectIllegal { AppStore.cancel(id) }
        AppStore.advance(id)
        AppStore.init(context)
        assertEquals("Entregue (teste)", AppStore.user.pedidos.single().status)
        assertEquals(4, AppStore.user.pedidos.single().eventos.size)
    }

    @Test
    fun wrongPasswordDoesNotOpenAnotherAccount() = runBlocking {
        AppStore.register("Samuel", "samuel@example.com", "teste123")
        AppStore.logout()
        expectIllegal { AppStore.login("samuel@example.com", "errada123") }
        assertFalse(AppStore.loggedIn)
    }
}
