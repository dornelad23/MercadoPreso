package com.example.mercadopreso.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mercadopreso.data.*
import com.example.mercadopreso.ui.components.*

@Composable
fun CartScreen(navigate: (String) -> Unit, action: Action) {
    var editAddress by remember { mutableStateOf(false) }
    var address by
        remember(AppStore.user.id) { mutableStateOf(AppStore.user.enderecos.firstOrNull()) }
    val cart = AppStore.user.carrinho
    val quote = address?.let { ShippingService.quotes(it.uf, cart).first() }
    if (editAddress)
        AddressEditor(
            address,
            { chosen ->
                action {
                    if (AppStore.loggedIn) AppStore.saveAddress(chosen)
                    address = chosen
                    editAddress = false
                }
            },
            { editAddress = false },
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header("Seu carrinho")
            Text("${AppStore.count} itens", color = Color.Gray)
        }
        if (cart.isEmpty())
            item {
                EmptyState(
                    "Seu carrinho está vazio",
                    "Encontre seu próximo produto favorito.",
                    "Ver produtos",
                    { navigate("home") },
                )
            }
        items(cart.entries.toList(), key = { it.key }) { entry ->
            val p = ProductRepository.find(entry.key)
            if (p != null)
                Card(Modifier.fillMaxWidth().animateContentSize()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Photo(p.imagem, p.nome, Modifier.size(76.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.nome, fontWeight = FontWeight.Bold)
                            Text(money(p.preco * entry.value))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { action { AppStore.decrease(p.id) } }) {
                                    Icon(Icons.Outlined.Remove, "Diminuir quantidade")
                                }
                                Text(entry.value.toString())
                                IconButton(onClick = { action { AppStore.add(p) } }) {
                                    Icon(Icons.Outlined.Add, "Aumentar quantidade")
                                }
                            }
                        }
                        IconButton(onClick = { action { AppStore.remove(p.id) } }) {
                            Icon(Icons.Outlined.DeleteOutline, "Excluir produto")
                        }
                    }
                }
        }
        if (cart.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Entrega", fontWeight = FontWeight.Bold)
                        Text(address?.completo ?: "Consulte seu CEP para ver o endereço real.")
                        OutlinedButton(onClick = { editAddress = true }) {
                            Text(if (address == null) "Consultar CEP" else "Alterar endereço")
                        }
                        quote?.let { Text("${it.nome}: ${money(it.valor)} · ${it.prazo}") }
                    }
                }
            }
            item {
                Notice(
                    "O endereço vem do ViaCEP. O preço e o prazo do frete são estimativas de teste, sem cotação de transportadora."
                )
            }
            item {
                TotalRow("Subtotal", money(AppStore.subtotal))
                TotalRow("Frete estimado", quote?.let { money(it.valor) } ?: "Consulte o CEP")
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                TotalRow("Total estimado", money(AppStore.subtotal + (quote?.valor ?: 0.0)))
                Button(
                    onClick = {
                        if (!AppStore.loggedIn) navigate("login")
                        else if (address == null) editAddress = true
                        else
                            action {
                                AppStore.saveAddress(requireNotNull(address))
                                navigate("checkout")
                            }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(
                        if (!AppStore.loggedIn) "Entrar para continuar"
                        else "Continuar para pagamento"
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CheckoutScreen(navigate: (String) -> Unit, action: Action) {
    if (!AppStore.loggedIn) {
        EmptyState(
            "Entre na sua conta",
            "Sua compra será registrada na conta local.",
            "Entrar",
            { navigate("login") },
        )
        return
    }
    if (AppStore.user.carrinho.isEmpty()) {
        EmptyState(
            "Carrinho vazio",
            "Adicione produtos antes de continuar.",
            "Ver produtos",
            { navigate("home") },
        )
        return
    }
    var addressId by rememberSaveable { mutableStateOf(AppStore.user.enderecos.firstOrNull()?.id) }
    val address =
        AppStore.user.enderecos.firstOrNull { it.id == addressId }
            ?: AppStore.user.enderecos.firstOrNull()
    var newAddress by remember { mutableStateOf(false) }
    var quoteIndex by rememberSaveable { mutableIntStateOf(0) }
    var payment by rememberSaveable { mutableStateOf("Pix") }
    var installments by rememberSaveable { mutableIntStateOf(1) }
    var holder by remember { mutableStateOf("") }
    var lastFour by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val quotes = address?.let { ShippingService.quotes(it.uf, AppStore.user.carrinho) }.orEmpty()
    val selected = quotes.getOrNull(quoteIndex)
    val valid =
        address != null &&
            selected != null &&
            (payment != "Cartão de crédito" || (holder.trim().length >= 2 && lastFour.length == 4))
    if (newAddress)
        AddressEditor(
            onSave = { chosen ->
                action {
                    AppStore.saveAddress(chosen)
                    addressId = chosen.id
                    newAddress = false
                }
            },
            onCancel = { newAddress = false },
        )
    if (confirm)
        AlertDialog(
            onDismissRequest = { if (!submitted) confirm = false },
            title = { Text("Confirmar pagamento de teste?") },
            text = {
                Text(
                    "Nenhum dinheiro será cobrado. O pedido será marcado como Pago (teste), com ${payment.lowercase()} como forma escolhida."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!submitted && valid) {
                            submitted = true
                            action {
                                try {
                                    val id =
                                        AppStore.checkout(
                                            requireNotNull(address),
                                            requireNotNull(selected),
                                            payment,
                                            installments,
                                        )
                                    confirm = false
                                    holder = ""
                                    lastFour = ""
                                    navigate("pedido:$id")
                                } finally {
                                    submitted = false
                                }
                            }
                        }
                    },
                    enabled = !submitted && valid,
                ) {
                    Text("Confirmar teste")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, enabled = !submitted) { Text("Voltar") }
            },
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header("Pagamento", { navigate("carrinho") })
            Notice(
                "AMBIENTE DE TESTE · Não use dados reais de cartão. A confirmação abaixo não realiza cobrança."
            )
        }
        item { Text("1. Endereço de entrega", fontWeight = FontWeight.Bold) }
        items(AppStore.user.enderecos, key = { it.id }) { a ->
            Card(onClick = { addressId = a.id }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(a.id == address?.id, { addressId = a.id })
                    Text(a.completo, Modifier.weight(1f))
                }
            }
        }
        item { OutlinedButton(onClick = { newAddress = true }) { Text("Adicionar endereço") } }
        item { Text("2. Entrega estimada", fontWeight = FontWeight.Bold) }
        items(quotes.indices.toList()) { index ->
            val q = quotes[index]
            Card(onClick = { quoteIndex = index }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(index == quoteIndex, { quoteIndex = index })
                    Column {
                        Text("${q.nome} · ${money(q.valor)}")
                        Text(q.prazo, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Text("3. Forma de pagamento", fontWeight = FontWeight.Bold)
            listOf("Pix", "Cartão de crédito", "Boleto").forEach { method ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(payment == method, { payment = method })
                    Text(method)
                }
            }
        }
        if (payment == "Cartão de crédito")
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.animateContentSize(),
                ) {
                    Text(
                        "Use um nome e final de cartão fictícios para testar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        holder,
                        { holder = it },
                        label = { Text("Nome fictício do titular") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        lastFour,
                        { lastFour = it.filter { c -> c in '0'..'9' }.take(4) },
                        label = { Text("4 dígitos fictícios (ex.: 4242)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Parcelas: ${installments}x", Modifier.weight(1f))
                        IconButton(
                            onClick = { installments = (installments - 1).coerceAtLeast(1) }
                        ) {
                            Icon(Icons.Outlined.Remove, "Menos parcelas")
                        }
                        IconButton(
                            onClick = { installments = (installments + 1).coerceAtMost(12) }
                        ) {
                            Icon(Icons.Outlined.Add, "Mais parcelas")
                        }
                    }
                    Text(
                        "${installments}x de ${money((AppStore.subtotal + (selected?.valor ?: 0.0)) / installments)} sem juros neste teste"
                    )
                }
            }
        if (payment != "Cartão de crédito")
            item {
                Notice(
                    if (payment == "Pix")
                        "Pix de teste: nenhuma chave ou QR Code de cobrança será emitido."
                    else "Boleto de teste: nenhum boleto bancário será emitido."
                )
            }
        item {
            TotalRow("Produtos", money(AppStore.subtotal))
            TotalRow("Frete estimado", selected?.let { money(it.valor) } ?: "Escolha um endereço")
            TotalRow("Total", money(AppStore.subtotal + (selected?.valor ?: 0.0)))
            Button(
                onClick = { confirm = true },
                enabled = valid && !submitted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Confirmar pagamento de teste")
            }
        }
    }
}

@Composable
fun OrdersScreen(navigate: (String) -> Unit) {
    val orders = AppStore.user.pedidos
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header("Meus pedidos", { navigate("perfil") }) }
        if (orders.isEmpty())
            item {
                EmptyState(
                    "Nenhum pedido ainda",
                    "Conclua uma compra de teste para acompanhar aqui.",
                    "Ver produtos",
                    { navigate("home") },
                )
            }
        items(orders, key = { it.id }) { order ->
            Card(onClick = { navigate("pedido:${order.id}") }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(order.id, fontWeight = FontWeight.Bold)
                    Text(date(order.criado), color = Color.Gray)
                    Text("${order.itens.sumOf { it.quantidade }} itens · ${money(order.total)}")
                    Text(
                        order.status,
                        color =
                            if (order.status == "Cancelado") MaterialTheme.colorScheme.error
                            else Color(0xFF16823A),
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Ver detalhes →")
                }
            }
        }
    }
}

@Composable
fun OrderScreen(id: String, navigate: (String) -> Unit, action: Action) {
    val order = AppStore.user.pedidos.firstOrNull { it.id == id }
    if (order == null) {
        EmptyState(
            "Pedido não encontrado",
            "Confira a conta utilizada na compra.",
            "Voltar",
            { navigate("pedidos") },
        )
        return
    }
    var cancel by remember { mutableStateOf(false) }
    if (cancel)
        AlertDialog(
            onDismissRequest = { cancel = false },
            title = { Text("Cancelar pedido de teste?") },
            text = {
                Text(
                    "Os produtos voltam a ficar disponíveis no estoque. Nenhuma cobrança foi realizada."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        action {
                            AppStore.cancel(id)
                            cancel = false
                        }
                    }
                ) {
                    Text("Cancelar pedido")
                }
            },
            dismissButton = { TextButton(onClick = { cancel = false }) { Text("Manter") } },
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header("Acompanhar pedido", { navigate("pedidos") })
            Text(order.id, fontWeight = FontWeight.Bold)
            Notice(
                if (order.teste)
                    "Pedido de teste. Acompanhamento demonstrativo; não existe envio ou rastreio de transportadora."
                else
                    "Pedido anterior do banco. Consulte o status registrado; esta tela não altera seu pagamento ou entrega."
            )
        }
        item {
            Card(Modifier.fillMaxWidth().animateContentSize()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${order.status}", style = MaterialTheme.typography.titleLarge)
                    order.eventos.forEach { event ->
                        Text("✓ ${event.status}\n${date(event.data)}")
                    }
                }
            }
        }
        if (order.teste && order.status !in listOf("Cancelado", "Entregue (teste)"))
            item {
                Button(
                    onClick = { action { AppStore.advance(id) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Simular próxima etapa da entrega")
                }
                if (order.status in listOf("Pago (teste)", "Em preparação"))
                    OutlinedButton(
                        onClick = { cancel = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancelar pedido de teste")
                    }
            }
        item { Text("Produtos", fontWeight = FontWeight.Bold) }
        items(order.itens, key = { it.produtoId }) { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Photo(line.imagem, line.nome, Modifier.size(65.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(line.nome)
                    Text("${line.quantidade} × ${money(line.preco)}")
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Entrega: ${order.endereco.completo}")
            Text(order.modalidade)
            Text("Pagamento: ${order.pagamento} · ${order.parcelas}x · teste")
            TotalRow("Subtotal", money(order.subtotal))
            TotalRow("Frete estimado", money(order.frete))
            TotalRow("Total", money(order.total))
        }
    }
}
