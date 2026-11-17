package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = Color(0xFF0B2142),
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF2E1E06),
    secondaryContainer = CoralContainer,
    onSecondaryContainer = OnCoralContainer,
    tertiary = SlateTertiary,
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = SlateContainer,
    onTertiaryContainer = OnSlateContainer,
    background = DeskBackgroundDark,
    onBackground = InkTextDark,
    surface = DeskSurfaceDark,
    onSurface = InkTextDark,
    surfaceVariant = DeskSurfaceVariantDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerHigh = DeskSurfaceElevatedDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = DarkColorScheme

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ThreeDitDocTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun CamadaPdfTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) = ThreeDitDocTheme(darkTheme = darkTheme, content = content)
