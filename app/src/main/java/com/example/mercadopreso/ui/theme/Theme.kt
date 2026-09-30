package com.example.mercadopreso.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Colors =
    lightColorScheme(
        primary = Preto,
        onPrimary = Branco,
        secondary = PretoSuave,
        onSecondary = Branco,
        background = Fundo,
        onBackground = Preto,
        surface = Branco,
        onSurface = Preto,
        surfaceVariant = CinzaCard,
        onSurfaceVariant = CinzaTexto,
        outline = CinzaClaro,
        error = Vermelho,
        onError = Branco
    )
private val Typography =
    androidx.compose.material3.Typography(
        displayLarge =
            TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineLarge =
            TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 15.sp),
        bodyMedium = TextStyle(fontSize = 13.sp),
        bodySmall = TextStyle(fontSize = 11.sp, color = CinzaTexto),
        labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    )

@Composable
fun MercadoPresoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography, content = content)
}
