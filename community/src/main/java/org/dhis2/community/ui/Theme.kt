package org.dhis2.community.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BluePrimary = Color(0xFF1976D2)
private val BluePrimaryDark = Color(0xFF1565C0)
private val BluePrimaryLight = Color(0xFF63A4FF)
private val BlueSecondary = Color(0xFF2196F3)
private val BlueSecondaryDark = Color(0xFF1769AA)
private val CardWhite = Color(0xFFFFFFFF)
private val BackgroundGrey = Color(0xFFF5F5F5)
private val SurfaceGrey = Color(0xFFF8F9FA)
private val OnPrimary = Color(0xFFFFFFFF)
private val OnSecondary = Color(0xFFFFFFFF)
private val OnSurface = Color(0xFF222B45)
private val Outline = Color(0xFFBDBDBD)

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = OnPrimary,
    primaryContainer = BluePrimaryLight,
    onPrimaryContainer = OnPrimary,
    secondary = BlueSecondary,
    onSecondary = OnSecondary,
    secondaryContainer = BlueSecondaryDark,
    onSecondaryContainer = OnSecondary,
    background = BackgroundGrey,
    onBackground = OnSurface,
    surface = CardWhite,
    onSurface = OnSurface,
    surfaceVariant = SurfaceGrey,
    onSurfaceVariant = OnSurface,
    outline = Outline,
)

private val DarkColors = darkColorScheme(
    primary = BluePrimary,
    onPrimary = OnPrimary,
    primaryContainer = BluePrimaryDark,
    onPrimaryContainer = OnPrimary,
    secondary = BlueSecondary,
    onSecondary = OnSecondary,
    secondaryContainer = BlueSecondaryDark,
    onSecondaryContainer = OnSecondary,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF23272B),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Outline,
)

@Composable
fun Dhis2CmtTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
