package co.obra.controlconcreto

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta del Manual de Identidad Corporativa de Construcciones El Cóndor S.A.
val AzulOscuro = Color(0xFF002F5C)   // Pantone 289C – color principal
val AzulClaro = Color(0xFFA0BEE7)    // Pantone 283C
val AzulMedio = Color(0xFF38628E)    // complementario
val Cian = Color(0xFF0090AE)         // complementario
val Fondo = Color(0xFFF1F4F8)
val Aviso = Color(0xFF8A5A00)
val AvisoFondo = Color(0xFFFFF3CC)

@Composable
fun TemaCondor(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AzulOscuro,
            onPrimary = Color.White,
            primaryContainer = AzulClaro,
            onPrimaryContainer = AzulOscuro,
            secondary = Cian,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDCE7F6),
            onSecondaryContainer = AzulOscuro,
            background = Fondo,
            surface = Color.White,
            surfaceVariant = Color(0xFFE6ECF4),
            outline = Color(0xFF8A99AB)
        ),
        content = content
    )
}
