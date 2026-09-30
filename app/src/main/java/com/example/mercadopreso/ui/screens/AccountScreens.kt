package com.example.mercadopreso.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mercadopreso.data.*
import com.example.mercadopreso.data.model.Endereco
import com.example.mercadopreso.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onBack: () -> Unit, onDone: () -> Unit, action: Action) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(if (register) "Criar conta" else "Entrar", onBack) }
        item {
            Notice(
                if (com.example.mercadopreso.data.ApiClient.enabled)
                    "Conta online. Entre com a mesma conta em outro aparelho para acessar seus dados."
                else
                    "Conta local de teste. Seus dados ficam neste aparelho; ainda não existe sincronização entre dispositivos."
            )
        }
        if (register)
            item {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        item {
            OutlinedTextField(
                email,
                { email = it },
                label = { Text("E-mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation =
                    if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                            "Mostrar ou ocultar senha",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    action {
                        if (register) AppStore.register(name, email, password)
                        else AppStore.login(email, password)
                        password = ""
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (register) "Criar conta e entrar" else "Entrar")
            }
            TextButton(
                onClick = {
                    register = !register
                    password = ""
                }
            ) {
                Text(if (register) "Já tenho uma conta" else "Criar uma conta")
            }
        }
    }
}

@Composable
fun ProfileScreen(navigate: (String) -> Unit, action: Action) {
    val user = AppStore.user
    var edit by remember { mutableStateOf(false) }
    var name by remember(user.nome) { mutableStateOf(user.nome) }
    if (edit)
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text("Editar perfil") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Nome") }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        action {
                            AppStore.editProfile(name)
                            edit = false
                        }
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Cancelar") } },
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header("Meu perfil") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp).animateContentSize()) {
                    Text(
                        user.nome,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (AppStore.loggedIn) user.email
                        else "Entre para concluir pedidos e salvar endereços."
                    )
                    Button(
                        onClick = { if (AppStore.loggedIn) edit = true else navigate("login") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text(if (AppStore.loggedIn) "Editar perfil" else "Entrar ou criar conta")
                    }
                }
            }
        }
        item {
            ProfileButton(
                "Meus pedidos",
                "${user.pedidos.size} pedidos",
                { navigate(if (AppStore.loggedIn) "pedidos" else "login") },
            )
        }
        item {
            ProfileButton(
                "Favoritos",
                "${user.favoritos.size} produtos salvos",
                { navigate("favoritos") },
            )
        }
        item {
            ProfileButton(
                "Endereços",
                "${user.enderecos.size} endereços",
                { navigate(if (AppStore.loggedIn) "enderecos" else "login") },
            )
        }
        item {
            ProfileButton(
                "Configurações",
                "Animações, alertas e senha",
                { navigate("configuracoes") },
            )
        }
        if (AppStore.loggedIn)
            item {
                OutlinedButton(
                    onClick = { action { AppStore.logout() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sair da conta")
                }
            }
        item { Notice("Versão de teste: nenhum pagamento ou envio de mercadoria é realizado.") }
    }
}

@Composable
private fun ProfileButton(title: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
fun AddressEditor(initial: Endereco? = null, onSave: (Endereco) -> Unit, onCancel: () -> Unit) {
    var cep by remember(initial?.id) { mutableStateOf(initial?.cep ?: "") }
    var resolved by remember(initial?.id) { mutableStateOf(initial) }
    var number by remember(initial?.id) { mutableStateOf(initial?.numero ?: "") }
    var extra by remember(initial?.id) { mutableStateOf(initial?.complemento ?: "") }
    var street by remember(initial?.id) { mutableStateOf(initial?.rua ?: "") }
    var district by remember(initial?.id) { mutableStateOf(initial?.bairro ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initial == null) "Novo endereço" else "Editar endereço") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    cep,
                    {
                        job?.cancel()
                        busy = false
                        cep = it.filter { ch -> ch in '0'..'9' }.take(8)
                        resolved = null
                        error = null
                        street = ""
                        district = ""
                    },
                    label = { Text("CEP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val requested = cep
                        busy = true
                        error = null
                        job =
                            scope.launch {
                                try {
                                    val found = ShippingService.address(requested)
                                    if (cep == requested) {
                                        resolved = found.copy(id = initial?.id ?: found.id)
                                        street = found.rua
                                        district = found.bairro
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    if (cep == requested)
                                        error =
                                            e.message ?: "Verifique sua conexão e tente novamente."
                                } finally {
                                    if (cep == requested) busy = false
                                }
                            }
                    },
                    enabled = !busy && cep.length == 8,
                ) {
                    Text(if (busy) "Consultando…" else "Consultar CEP real")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                resolved?.let { address ->
                    Text("${address.cidade} - ${address.uf}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        street,
                        { street = it },
                        label = { Text("Rua") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        district,
                        { district = it },
                        label = { Text("Bairro") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        number,
                        { number = it },
                        label = { Text("Número ou S/N") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        extra,
                        { extra = it },
                        label = { Text("Complemento (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    resolved?.let {
                        onSave(
                            it.copy(
                                rua = street.trim(),
                                bairro = district.trim(),
                                numero = number.trim(),
                                complemento = extra.trim(),
                            )
                        )
                    }
                },
                enabled = resolved != null && number.isNotBlank() && street.isNotBlank() && !busy,
            ) {
                Text("Usar endereço")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
    )
}

@Composable
fun AddressesScreen(onBack: () -> Unit, action: Action) {
    var show by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Endereco?>(null) }
    if (show)
        AddressEditor(
            editing,
            { address ->
                action {
                    AppStore.saveAddress(address)
                    show = false
                }
            },
            { show = false },
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header("Endereços", onBack)
            Button(
                onClick = {
                    editing = null
                    show = true
                }
            ) {
                Text("Adicionar endereço")
            }
        }
        if (AppStore.user.enderecos.isEmpty())
            item {
                EmptyState("Nenhum endereço salvo", "Consulte seu CEP para cadastrar um endereço.")
            }
        items(AppStore.user.enderecos, key = { it.id }) { address ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(address.completo)
                    Row {
                        TextButton(
                            onClick = {
                                editing = address
                                show = true
                            }
                        ) {
                            Text("Editar")
                        }
                        TextButton(onClick = { action { AppStore.deleteAddress(address.id) } }) {
                            Text("Excluir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, action: Action) {
    var change by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    if (change)
        AlertDialog(
            onDismissRequest = {
                change = false
                current = ""
                next = ""
            },
            title = { Text("Alterar senha") },
            text = {
                Column {
                    OutlinedTextField(
                        current,
                        { current = it },
                        label = { Text("Senha atual") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        next,
                        { next = it },
                        label = { Text("Nova senha") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        action {
                            AppStore.changePassword(current, next)
                            change = false
                            current = ""
                            next = ""
                        }
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        change = false
                        current = ""
                        next = ""
                    }
                ) {
                    Text("Cancelar")
                }
            },
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header("Configurações", onBack) }
        item {
            Row {
                Text("Animações de navegação e fotos", Modifier.weight(1f))
                Switch(
                    AppStore.user.animacoes,
                    { value -> action { AppStore.settings(animations = value) } },
                )
            }
        }
        item {
            Row {
                Text("Avisos de sucesso no aplicativo", Modifier.weight(1f))
                Switch(
                    AppStore.user.alertas,
                    { value -> action { AppStore.settings(alerts = value) } },
                )
            }
        }
        if (AppStore.loggedIn)
            item { Button(onClick = { change = true }) { Text("Alterar senha") } }
        item {
            Notice(
                if (com.example.mercadopreso.data.ApiClient.enabled)
                    "Mercado Preso 4.1 · Conta online\nContas e pedidos são salvos online. O pagamento continua de teste: nenhum valor é cobrado. As etapas de entrega são simuladas manualmente."
                else
                    "Mercado Preso 4.1 · Teste local\nContas e pedidos são salvos neste aparelho. Nenhum valor é cobrado. As etapas de entrega são simuladas manualmente."
            )
        }
    }
}
