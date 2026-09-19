package com.nbarumble.game.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val CourtNight = Color(0xFF0E1116)
val SurfacePanel = Color(0xFF161C26)
val SurfaceRaised = Color(0xFF1E2735)
val Orange = Color(0xFFFF7A1F)
val OrangeDark = Color(0xFFB34A00)
val IceBlue = Color(0xFF4FC3F7)
val OffWhite = Color(0xFFE0E6ED)
val Danger = Color(0xFFFF5252)
val Success = Color(0xFF66D98A)

private val NbaColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color.Black,
    secondary = IceBlue,
    onSecondary = Color.Black,
    tertiary = OffWhite,
    onTertiary = CourtNight,
    background = CourtNight,
    onBackground = OffWhite,
    surface = SurfacePanel,
    onSurface = OffWhite,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = Color(0xFF9AA7B8),
    error = Danger,
    onError = Color.White
)

private val NbaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val NbaTypography = Typography()

@Composable
fun NbaRumbleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NbaColorScheme,
        shapes = NbaShapes,
        typography = NbaTypography,
        content = content
    )
}