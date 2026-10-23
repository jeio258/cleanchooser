package com.eio258.cleanchooser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Fixed brand palette instead of the dynamic system palette: the mockups were drawn with
 * this indigo/teal pairing, and dynamic colors on MIUI produced washed-out lavender cards.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF4356D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF15205C),
    secondary = Color(0xFF2B9E82),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCEFE5),
    onSecondaryContainer = Color(0xFF063B2F),
    tertiary = Color(0xFFB46900),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF15171C),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFE6E9F2),
    onSurfaceVariant = Color(0xFF5B6273),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF0F7),
    surfaceContainerHigh = Color(0xFFE7EBF4),
    surfaceContainerHighest = Color(0xFFDFE4EF),
    outline = Color(0xFF9AA2B4),
    outlineVariant = Color(0xFFDCE0E9),
    error = Color(0xFFC62828)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9B8FF),
    onPrimary = Color(0xFF10184A),
    primaryContainer = Color(0xFF2A3A8F),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFF6FD9BC),
    onSecondary = Color(0xFF003829),
    tertiary = Color(0xFFFFB95C),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE7EAF2),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFE7EAF2),
    surfaceVariant = Color(0xFF232833),
    onSurfaceVariant = Color(0xFF9AA3B6),
    surfaceContainerLow = Color(0xFF151922),
    surfaceContainer = Color(0xFF1A1F2A),
    surfaceContainerHigh = Color(0xFF1F2530),
    surfaceContainerHighest = Color(0xFF252B38),
    outline = Color(0xFF5D6678),
    outlineVariant = Color(0xFF2A3140),
    error = Color(0xFFFFB4AB)
)

/** Tighter type scale than Material defaults so the lists read like the mockups. */
private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp)
)

@Composable
fun ListCleanerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
