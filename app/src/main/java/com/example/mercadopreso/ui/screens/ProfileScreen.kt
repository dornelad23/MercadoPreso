package com.example.mercadopreso.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation

data class UserSession(val nome: String,
    val email: String)

@Composable fun ProfileScreen(session: UserSession?,
    onLogin: (String,
        String) -> Unit,
    onLogout: () -> Unit) {
    var login by remember {
        mutableStateOf(false)
    }
    var edit by remember {
        mutableStateOf(false)
    }
    var email by remember(session) {
        mutableStateOf(session?.email ?: "")
    }
    var senha by remember {
        mutableStateOf("")
    }
    var nome by remember(session) {
        mutableStateOf(session?.nome ?: "")
    }
    var error by remember {
        mutableStateOf("")
    }
    if(login)AlertDialog(onDismissRequest = {
            login = false
        },
        title = {
            Text("Entrar na conta")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(email,
                    {
                        email = it
                    },
                    label = {
                        Text("E-mail")
                    },
                    singleLine = true)
                OutlinedTextField(senha,
                    {
                        senha = it
                    },
                    label = {
                        Text("Senha")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                if(error.isNotBlank())Text(error,
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp)
                Text("Login demonstrativo local para o projeto.",
                    color = Color.Gray,
                    fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton( {
                    if(email.contains("@") && senha.length >= 4) {
                        onLogin(email.substringBefore("@").replaceFirstChar {
                                it.uppercase()
                            },
                            email)
                        senha = ""
                        login = false
                    } else error = "E-mail válido e senha com 4+ caracteres."
            }) {
                Text("Entrar")
            }
        },
        dismissButton = {
            TextButton( {
                    login = false
            }) {
                Text("Cancelar")
            }
    })
    if(edit && session != null)AlertDialog(onDismissRequest = {
            edit = false
        },
        title = {
            Text("Editar perfil")
        },
        text = {
            OutlinedTextField(nome,
                {
                    nome = it
                },
                label = {
                    Text("Nome")
                },
                singleLine = true)
        },
        confirmButton = {
            TextButton( {
                    if(nome.isNotBlank()) {
                        onLogin(nome,
                            session.email)
                        edit = false
                    }
            }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton( {
                    edit = false
            }) {
                Text("Cancelar")
            }
    })
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(18.dp)) {
        Text("Meu perfil",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth().padding(top = 18.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(Color(0xFF101010))) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp).background(Color.White,
                            CircleShape),
                        Alignment.Center) {
                        Text(if(session == null) "👤" else session.nome.take(1).uppercase(),
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.padding(start = 13.dp)) {
                        Text(session?.nome ?: "Visitante",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold)
                        Text(session?.email ?: "Entre para acessar sua conta",
                            color = Color(0xFFBDBDBD),
                            fontSize = 11.sp)
                    }
                }
                Button( {
                        if(session == null)login = true else edit = true
                    },
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(Color.White,
                        Color.Black)) {
                    Text(if(session == null) "Entrar na conta" else "Editar perfil")
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        ProfileOption("Meus pedidos",
            Icons.Outlined.Inventory2)
        ProfileOption("Favoritos",
            Icons.Outlined.FavoriteBorder)
        ProfileOption("Endereços",
            Icons.Outlined.LocationOn)
        ProfileOption("Configurações",
            Icons.Outlined.Settings)
        if(session != null)TextButton( {
                onLogout()
            },
            Modifier.padding(top = 5.dp)) {
            Text("Sair da conta",
                color = Color(0xFFD32F2F))
        }
    }
}

@Composable private fun ProfileOption(title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(Color.White)) {
        Row(Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon,
                null)
            Text(title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 13.dp).weight(1f))
            Icon(Icons.Outlined.ChevronRight,
                null,
                tint = Color.Gray)
        }
    }
}
