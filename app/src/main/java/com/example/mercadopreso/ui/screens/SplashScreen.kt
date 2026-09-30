package com.example.mercadopreso.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mercadopreso.R
import kotlinx.coroutines.delay

@Composable fun SplashScreen(onFinished: () -> Unit) {
    var show by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(Unit) {
        show = true
        delay(1800)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF101010)),
        Alignment.Center) {
        AnimatedVisibility(show,
            enter = fadeIn()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(118.dp).background(Color.White,
                        CircleShape),
                    Alignment.Center) {
                    Image(painterResource(R.drawable.mp_logo),
                        "Logo",
                        Modifier.size(92.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text("MERCADO PRESO",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold)
                Text("Preços que você não consegue escapar.",
                    color = Color(0xFFBDBDBD),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}
