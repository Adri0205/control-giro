package com.udb.controlgiro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CustomLightColorScheme = lightColorScheme(
    primary = Lavanda,
    onPrimary = TextoOscuro,
    secondary = Menta,
    onSecondary = TextoOscuro,
    tertiary = Durazno,
    onTertiary = TextoOscuro,
    background = FondoCrema,
    onBackground = TextoOscuro,
    surface = FondoCrema,
    onSurface = TextoOscuro,
    surfaceVariant = AzulCielo,
    onSurfaceVariant = TextoOscuro,
    error = RojoError,
    onError = FondoCrema
)

@Composable
fun ControlGiroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CustomLightColorScheme,
        typography = Typography,
        content = content
    )
}
