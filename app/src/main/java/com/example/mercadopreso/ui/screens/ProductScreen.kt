package com.example.mercadopreso.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mercadopreso.data.CartManager
import com.example.mercadopreso.data.ProductRepository
import com.example.mercadopreso.data.model.Review
import java.util.Locale

private fun money(v: Double) = String.format(Locale("pt",
        "BR"),
    "R$ %.2f",
    v)

@Composable fun FreightBox(cep: String,
    onCep: (String) -> Unit,
    onCalc: () -> Unit,
    address: String?,
    frete: Double?,
    error: String?,
    free: Boolean = false) {
    Card(Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalShipping,
                    null)
                Spacer(Modifier.width(8.dp))
                Text("Calcular entrega",
                    fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(cep,
                {
                    onCep(it.filter(Char::isDigit).take(8))
                },
                Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = {
                    Text("CEP")
                },
                placeholder = {
                    Text("00000-000")
                },
                isError = error != null,
                supportingText = {
                    Text(error ?: "Digite os 8 números do CEP")
                },
                trailingIcon = {
                    TextButton(onCalc) {
                        Text("Calcular")
                    }
            })
            if(address != null && frete != null) {
                Text("Endereço encontrado",
                    fontSize = 11.sp,
                    color = Color.Gray)
                Text(address,
                    fontWeight = FontWeight.SemiBold)
                Text(if(free) "Frete grátis" else "Frete: ${money(frete)}",
                    color = Color(0xFF16823A),
                    fontWeight = FontWeight.Bold)
                Text("Entrega estimada: 3 a 7 dias úteis",
                    fontSize = 12.sp,
                    color = Color.Gray)
            }
        }
    }
}

@Composable fun ProductScreen(productId: Int,
    onBack: () -> Unit,
    onCart: () -> Unit) {
    val p = ProductRepository.find(productId) ?: return
    var cep by remember {
        mutableStateOf("")
    }
    var address by remember {
        mutableStateOf<String?>(null)
    }
    var frete by remember {
        mutableStateOf<Double?>(null)
    }
    var error by remember {
        mutableStateOf<String?>(null)
    }
    var reviews by remember {
        mutableStateOf(ProductRepository.reviews(p.id).toList())
    }
    var show by remember {
        mutableStateOf(false)
    }
    var text by remember {
        mutableStateOf("")
    }
    var rating by remember {
        mutableStateOf(5)
    }
    if(show)AlertDialog(onDismissRequest = {
            show = false
        },
        title = {
            Text("Avaliar produto")
        },
        text = {
            Column {
                Row {
                    (1..5).forEach {
                        n -> TextButton( {
                                rating = n
                        }) {
                            Text(if(n <= rating) "★" else "☆",
                                fontSize = 27.sp,
                                color = Color(0xFFFFB400))
                        }
                    }
                }
                OutlinedTextField(text,
                    {
                        text = it
                    },
                    Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = {
                        Text("Conte o que achou...")
                })
            }
        },
        confirmButton = {
            TextButton( {
                    if(text.isNotBlank()) {
                        ProductRepository.addReview(p.id,
                            Review("Você",
                                rating,
                                text))
                        reviews = ProductRepository.reviews(p.id).toList()
                        text = ""
                        show = false
                    }
            }) {
                Text("Publicar")
            }
        },
        dismissButton = {
            TextButton( {
                    show = false
            }) {
                Text("Cancelar")
            }
    })
    fun calc() {
        if(!CartManager.validarCep(cep)) {
            error = "CEP inválido. Digite exatamente 8 números."
            address = null
            frete = null
        } else {
            error = null
            address = CartManager.endereco(cep)
            frete = if(p.freteGratis)0.0 else CartManager.frete(cep)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) {
                Icon(Icons.Outlined.ArrowBack,
                    "Voltar")
            }
            Text("Detalhes do produto",
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onCart) {
                Icon(Icons.Outlined.ShoppingCart,
                    "Carrinho")
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFFF0F0F0),
                        RoundedCornerShape(20.dp)),
                    Alignment.Center) {
                    Text(p.emoji,
                        fontSize = 100.sp)
                }
            }
            item {
                if(p.emPromocao)Text("-${p.desconto}% OFF",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0xFF101010),
                        RoundedCornerShape(7.dp)).padding(7.dp))
                Text(p.nome,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("★ ${p.avaliacao}  •  ${reviews.size} avaliações  •  ${p.vendas} vendidos",
                    color = Color.Gray)
                if(p.precoOriginal != null)Text(money(p.precoOriginal),
                    color = Color.Gray,
                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.LineThrough))
                Text(money(p.preco),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold)
                Text("em até 12x sem juros (simulação) ",
                    color = Color(0xFF16823A),
                    fontSize = 12.sp)
            }
            item {
                Card(colors = CardDefaults.cardColors(Color.White),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Text("Sobre este produto",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp)
                        Text(p.descricao,
                            color = Color(0xFF555555),
                            modifier = Modifier.padding(top = 7.dp))
                        Text("✓ ${p.estoque} unidades disponíveis",
                            color = Color(0xFF16823A),
                            modifier = Modifier.padding(top = 9.dp))
                    }
                }
            }
            item {
                FreightBox(cep,
                    {
                        cep = it
                        error = null
                        address = null
                        frete = null
                    },
                    {
                        calc()
                    },
                    address,
                    frete,
                    error,
                    p.freteGratis)
            }
            item {
                Button( {
                        CartManager.adicionar(p)
                        onCart()
                    },
                    Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Outlined.AddShoppingCart,
                        null)
                    Text("  Adicionar ao carrinho")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Avaliações",
                        style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    TextButton( {
                            show = true
                    }) {
                        Text("Avaliar produto")
                    }
                }
            }
            items(reviews) {
                r -> Card(Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(Color.White)) {
                    Column(Modifier.padding(13.dp)) {
                        Row {
                            Text(r.nome,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${r.nota}/5 ★",
                                color = Color(0xFFFFA000))
                        }
                        Text(r.comentario,
                            color = Color(0xFF555555),
                            modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}
