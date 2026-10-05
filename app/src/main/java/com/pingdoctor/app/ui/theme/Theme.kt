package com.pingdoctor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkBg,
    primaryContainer = CyanDark,
    onPrimaryContainer = TextPrimary,
    secondary = EmeraldFast,
    onSecondary = DarkBg,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = CardBg,
    onSurface = TextPrimary,
    surfaceVariant = CardBorder,
    onSurfaceVariant = TextSecondary,
    error = RoseDead,
    onError = TextPrimary
)

@Composable
fun PingDoctorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
