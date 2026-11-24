package com.sarotech.lthisab.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val BurgundyPrimary = Color(0xFF4B1028)
val BurgundySecondary = Color(0xFF7A344F)
val LightBg = Color(0xFFF5F1F2)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF211B1E)

val YellowPrimary = Color(0xFFFFD600)
val YellowSecondary = Color(0xFFFFE780)
val OledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF141414)
val DarkSurfaceVariant = Color(0xFF222222)

val CashInGreenLight = Color(0xFF1E7E43)
val CashInGreenDark = Color(0xFF4ADE80)
val CashOutRedLight = Color(0xFFC93434)
val CashOutRedDark = Color(0xFFF87171)

private val LightColors = lightColorScheme(
    primary = BurgundyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7D8E2),
    onPrimaryContainer = BurgundyPrimary,
    secondary = BurgundySecondary,
    onSecondary = Color.White,
    background = LightBg,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFEDE5E8),
    onSurfaceVariant = Color(0xFF5D4C53)
)

private val DarkColors = darkColorScheme(
    primary = YellowPrimary,
    onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF3A3200),
    onPrimaryContainer = YellowPrimary,
    secondary = YellowSecondary,
    onSecondary = Color(0xFF171717),
    background = OledBlack,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBDBDBD)
)

@Composable
fun LTHisabTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = if (darkTheme) OledBlack.toArgb() else BurgundyPrimary.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = if (darkTheme) OledBlack.toArgb() else LightBg.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
