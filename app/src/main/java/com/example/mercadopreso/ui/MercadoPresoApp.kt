package com.example.mercadopreso.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mercadopreso.data.AppStore
import com.example.mercadopreso.ui.components.Action
import com.example.mercadopreso.ui.screens.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun MercadoPresoApp() {
    var route by rememberSaveable { mutableStateOf("home") }
    var catalogReturn by rememberSaveable { mutableStateOf("home") }
    var loginReturn by rememberSaveable { mutableStateOf("perfil") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val roots = listOf("home", "buscar", "carrinho", "perfil")
    val navigate: (String) -> Unit = { next ->
        if (next.startsWith("produto:")) catalogReturn = route
        if (next == "login") loginReturn = route
        route = next
    }
    val back: () -> Unit = {
        route =
            when {
                route.startsWith("produto:") -> catalogReturn
                route.startsWith("pedido:") -> "pedidos"
                route == "checkout" -> "carrinho"
                route == "login" -> loginReturn
                route == "promocoes" -> "home"
                route in listOf("pedidos", "favoritos", "enderecos", "configuracoes") -> "perfil"
                else -> "home"
            }
    }
    val actionMutex = remember { Mutex() }
    var busy by remember { mutableStateOf(false) }
    val action: Action = { block ->
        scope.launch {
            var message: String? = null
            actionMutex.withLock {
                busy = true
                try {
                    val before = route
                    block()
                    if (AppStore.user.alertas && route == before) message = "Alteração salva"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = e.message ?: "Não foi possível concluir. Tente novamente."
                } finally {
                    busy = false
                }
            }
            message?.let { snackbar.showSnackbar(it) }
        }
    }
    LaunchedEffect(Unit) {
        AppStore.loadError?.let { snackbar.showSnackbar(it) }
        var failed = false
        actionMutex.withLock {
            busy = true
            try {
                AppStore.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
        if (failed)
            snackbar.showSnackbar(
                "Não foi possível atualizar a API. Confira sua conexão e tente novamente."
            )
    }
    BackHandler(enabled = route != "home", onBack = back)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (com.example.mercadopreso.data.ApiClient.enabled) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Modo online",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { action { AppStore.refresh() } }, enabled = !busy) {
                            Text("Atualizar")
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            if (route in roots)
                NavigationBar {
                    val names = listOf("Início", "Buscar", "Carrinho", "Perfil")
                    val icons =
                        listOf(
                            Icons.Outlined.Home,
                            Icons.Outlined.Search,
                            Icons.Outlined.ShoppingCart,
                            Icons.Outlined.Person,
                        )
                    roots.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = route == tab,
                            onClick = { navigate(tab) },
                            icon = {
                                if (tab == "carrinho")
                                    BadgedBox(
                                        badge = {
                                            if (AppStore.count > 0)
                                                Badge { Text(AppStore.count.toString()) }
                                        }
                                    ) {
                                        Icon(icons[index], names[index])
                                    }
                                else Icon(icons[index], names[index])
                            },
                            label = { Text(names[index], fontSize = 11.sp) },
                        )
                    }
                }
        },
    ) { padding ->
        AnimatedContent(
            targetState = route,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                if (AppStore.user.animacoes)
                    (fadeIn(tween(200)) + slideInHorizontally(tween(230)) { it / 12 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -it / 12 })
                else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            },
            label = "screen",
        ) { screen ->
            when {
                screen in listOf("home", "buscar", "favoritos", "promocoes") ->
                    CatalogScreen(screen, navigate, action)
                screen.startsWith("produto:") ->
                    ProductScreen(
                        screen.substringAfter(':').toIntOrNull() ?: -1,
                        navigate,
                        back,
                        action,
                    )
                screen == "carrinho" -> CartScreen(navigate, action)
                screen == "perfil" -> ProfileScreen(navigate, action)
                screen == "login" ->
                    LoginScreen(
                        back,
                        {
                            route =
                                if (loginReturn == "carrinho" || loginReturn == "checkout")
                                    "carrinho"
                                else if (loginReturn.startsWith("produto:")) loginReturn
                                else "perfil"
                        },
                        action,
                    )
                screen == "enderecos" -> AddressesScreen(back, action)
                screen == "configuracoes" -> SettingsScreen(back, action)
                screen == "checkout" -> CheckoutScreen(navigate, action)
                screen == "pedidos" -> OrdersScreen(navigate)
                screen.startsWith("pedido:") ->
                    OrderScreen(screen.substringAfter(':'), navigate, action)
                else -> CatalogScreen("home", navigate, action)
            }
        }
    }
}
