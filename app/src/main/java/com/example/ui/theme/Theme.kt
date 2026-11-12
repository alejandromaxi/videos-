package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color(0xFF1E1000),
    primaryContainer = AmberTertiary,
    onPrimaryContainer = Color(0xFFFFECCF),
    secondary = CyanAccentLight,
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004D68),
    onSecondaryContainer = Color(0xFFC2E8FF),
    tertiary = Color(0xFFA7F3D0),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = DarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = AmberSecondary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFECCF),
    onPrimaryContainer = AmberTertiary,
    secondary = CyanAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2E8FF),
    onSecondaryContainer = Color(0xFF001E2B),
    tertiary = Color(0xFF059669),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightCard,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = LightCardBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
