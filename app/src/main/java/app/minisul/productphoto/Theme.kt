package app.minisul.productphoto

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Цвета бренда MiniSul
val Indigo = Color(0xFF3D5AFE)
val IndigoStrong = Color(0xFF2B44D9)
val IndigoTint = Color(0xFFE8ECFF)
val Accent = Color(0xFFFFC83D)
val Ink = Color(0xFF12142B)
val InkMuted = Color(0xFF5A5F7A)
val Surface = Color(0xFFF6F7FB)
val Line = Color(0xFFDDE0EE)
val Danger = Color(0xFFD92D3A)

@Composable
fun MiniSulTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Indigo,
            onPrimary = Color.White,
            primaryContainer = IndigoTint,
            onPrimaryContainer = Ink,
            secondary = Accent,
            onSecondary = Ink,
            background = Surface,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            onSurfaceVariant = InkMuted,
            outline = Line,
            error = Danger,
        ),
        content = content,
    )
}
