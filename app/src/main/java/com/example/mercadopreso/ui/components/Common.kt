package com.example.mercadopreso.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.mercadopreso.data.AppStore
import com.example.mercadopreso.data.model.Produto
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

typealias Action = (suspend () -> Unit) -> Unit

fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

fun date(value: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(value))

@Composable
fun Photo(url: String, title: String, modifier: Modifier = Modifier) {
    var retry by remember(url) { mutableIntStateOf(0) }
    key(url, retry) {
        SubcomposeAsyncImage(
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(AppStore.user.animacoes)
                    .build(),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = modifier.background(Color.White),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.dp)
                }
            },
            error = {
                Column(
                    Modifier.fillMaxSize().clickable { retry++ }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Outlined.BrokenImage, null, tint = Color.Gray)
                    Text("Foto indisponível", fontSize = 10.sp, color = Color.Gray)
                    Text("Toque para tentar", fontSize = 10.sp)
                }
            }
        )
    }
}

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null)
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = if (onBack == null) 16.dp else 0.dp).weight(1f)
        )
    }
}

@Composable
fun Notice(text: String) {
    Surface(
        color = Color(0xFFFFF2D7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = Color(0xFF614B1E))
    }
}

@Composable
fun EmptyState(title: String, detail: String, button: String? = null, onClick: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Inventory2, null, Modifier.size(48.dp), tint = Color.Gray)
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail, color = Color.Gray)
        if (button != null) Button(onClick = onClick) { Text(button) }
    }
}

@Composable
fun ProductCard(p: Produto, onProduct: () -> Unit, action: Action) {
    val favorite = p.id in AppStore.user.favoritos
    val heart by
        animateColorAsState(if (favorite) Color(0xFFD32F2F) else Color.Gray, label = "favorite")
    Card(
        onClick = onProduct,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                Photo(p.imagem, p.nome, Modifier.fillMaxWidth().height(150.dp))
                if (p.emPromocao)
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            "-${p.desconto}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                IconButton(
                    onClick = { action { AppStore.favorite(p.id) } },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        if (favorite) "Remover dos favoritos" else "Favoritar",
                        tint = heart
                    )
                }
            }
            Text(
                p.nome,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp
            )
            Text("★ ${p.avaliacao} · ${p.categoria}", fontSize = 10.sp, color = Color.Gray)
            Text(money(p.preco), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                if (p.freteGratis) "Frete grátis no teste" else "Consultar entrega",
                fontSize = 10.sp,
                color = Color(0xFF16823A)
            )
            FilledTonalButton(
                onClick = { action { AppStore.add(p) } },
                enabled = AppStore.available(p) > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.AddShoppingCart, null, Modifier.size(17.dp))
                Text(" Adicionar", fontSize = 11.sp)
            }
        }
    }
}
