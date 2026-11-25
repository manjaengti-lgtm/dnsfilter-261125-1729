package com.dnsfilter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B5FA5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF2E6B4F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2F1CB),
    onSecondaryContainer = Color(0xFF002114),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF9A),
    onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7FAFD),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7FAFD),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDFE3E8),
    onSurfaceVariant = Color(0xFF43474C),
    outline = Color(0xFF73777C),
    outlineVariant = Color(0xFFC3C7CC),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FCBFF),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFF96D5B0),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF14513A),
    onSecondaryContainer = Color(0xFFB2F1CB),
    tertiary = Color(0xFFEFC046),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDF9A),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474C),
    onSurfaceVariant = Color(0xFFC3C7CC),
    outline = Color(0xFF8D9196),
    outlineVariant = Color(0xFF43474C),
)

@Composable
fun DnsFilterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
