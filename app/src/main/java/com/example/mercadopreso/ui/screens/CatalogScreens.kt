package com.example.mercadopreso.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mercadopreso.data.*
import com.example.mercadopreso.ui.components.*

@Composable
fun CatalogScreen(mode: String, navigate: (String) -> Unit, action: Action) {
    var query by rememberSaveable(mode) { mutableStateOf("") }
    var category by rememberSaveable(mode) { mutableStateOf("Todos") }
    var sort by rememberSaveable(mode) { mutableStateOf("Destaques") }
    val filtered =
        ProductRepository.produtos
            .filter {
                (mode != "favoritos" || it.id in AppStore.user.favoritos) &&
                    (mode != "promocoes" || it.emPromocao) &&
                    (category == "Todos" || it.categoria == category) &&
                    (query.isBlank() ||
                        it.nome.contains(query.trim(), true) ||
                        it.categoria.contains(query.trim(), true))
            }
            .let { list ->
                when (sort) {
                    "Menor preço" -> list.sortedBy { it.preco }
                    "Maior preço" -> list.sortedByDescending { it.preco }
                    else -> list
                }
            }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Header(
                when (mode) {
                    "buscar" -> "Buscar produtos"
                    "favoritos" -> "Favoritos"
                    "promocoes" -> "Ofertas presas"
                    else -> "MERCADO PRESO"
                },
                if (mode in listOf("favoritos", "promocoes"))
                    ({ navigate(if (mode == "favoritos") "perfil" else "home") })
                else null
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (mode == "home")
                    Text(
                        "Preços que você não consegue escapar.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                OutlinedTextField(
                    query,
                    { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar entre 50 produtos") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }
                )
            }
        }
        if (mode == "home")
            item {
                Card(
                    onClick = { navigate("promocoes") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(Color(0xFF101010))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("OFERTAS PRESAS", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Seu próximo favorito está aqui.",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Conheça as ofertas →",
                            color = Color(0xFFBDBDBD),
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ProductRepository.categorias) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) }
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("Destaques", "Menor preço", "Maior preço")) { option ->
                    FilterChip(sort == option, { sort = option }, { Text(option) })
                }
            }
            Text(
                "${filtered.size} produtos",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        if (filtered.isEmpty())
            item { EmptyState("Nenhum produto encontrado", "Tente outra busca ou categoria.") }
        items(filtered.chunked(2), key = { it.first().id }) { row ->
            Row(
                Modifier.padding(horizontal = 16.dp).animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { p ->
                    Box(Modifier.weight(1f)) {
                        ProductCard(p, { navigate("produto:${p.id}") }, action)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ProductScreen(id: Int, navigate: (String) -> Unit, onBack: () -> Unit, action: Action) {
    val p = ProductRepository.find(id)
    if (p == null) {
        EmptyState("Produto não encontrado", "Volte ao catálogo.", "Voltar", onBack)
        return
    }
    var reviewDialog by remember { mutableStateOf(false) }
    var comment by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableIntStateOf(5) }
    val reviews = AppStore.state.reviews.filter { it.produtoId == id }
    if (reviewDialog)
        AlertDialog(
            onDismissRequest = { reviewDialog = false },
            title = { Text("Avaliar produto") },
            text = {
                Column {
                    Row {
                        (1..5).forEach { n ->
                            TextButton(
                                onClick = { rating = n },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (n <= rating) "★" else "☆",
                                    color = Color(0xFFFFB400),
                                    fontSize = 25.sp
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        comment,
                        { comment = it.take(500) },
                        label = { Text("Seu comentário") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        action {
                            AppStore.review(id, rating, comment)
                            reviewDialog = false
                            comment = ""
                        }
                    }
                ) {
                    Text("Publicar")
                }
            },
            dismissButton = { TextButton(onClick = { reviewDialog = false }) { Text("Cancelar") } }
        )
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Header("Detalhes", onBack)
            Photo(p.imagem, p.nome, Modifier.fillMaxWidth().height(280.dp))
        }
        item {
            Text(
                p.nome,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text("★ ${p.avaliacao} · ${p.vendas} vendas demonstrativas", color = Color.Gray)
            Text(money(p.preco), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            AnimatedVisibility(visible = p.emPromocao) {
                Text("${p.desconto}% de desconto", color = Color(0xFF16823A))
            }
            Text(p.descricao, modifier = Modifier.padding(top = 10.dp))
            Text(
                "${AppStore.available(p)} unidades disponíveis neste teste",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        item {
            Button(
                onClick = {
                    action {
                        AppStore.add(p)
                        navigate("carrinho")
                    }
                },
                enabled = AppStore.available(p) > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Adicionar ao carrinho")
            }
            OutlinedButton(
                onClick = { action { AppStore.favorite(id) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (id in AppStore.user.favoritos) "Remover dos favoritos"
                    else "Salvar nos favoritos"
                )
            }
        }
        item {
            Notice(
                "Consulte seu CEP no carrinho. Endereço real via ViaCEP; valores de entrega estimados nesta versão de teste."
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Avaliações (${reviews.size})", fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = { if (AppStore.loggedIn) reviewDialog = true else navigate("login") }
                ) {
                    Text("Avaliar")
                }
            }
        }
        if (reviews.isEmpty())
            item { Text("Seja o primeiro a avaliar este produto.", color = Color.Gray) }
        items(reviews, key = { it.userId }) { review ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("${review.nome} · ${review.nota}/5 ★", fontWeight = FontWeight.Bold)
                    Text(review.comentario)
                }
            }
        }
    }
}
