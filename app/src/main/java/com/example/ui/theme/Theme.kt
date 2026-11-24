package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
  DARK,
  LIGHT,
  SYSTEM,
}

private val DarkColorScheme = darkColorScheme(
  primary = AmberAccent,
  onPrimary = MidnightBackground,
  primaryContainer = DeepBlueContainer,
  onPrimaryContainer = IceWhiteText,
  secondary = SkyInfo,
  onSecondary = MidnightBackground,
  secondaryContainer = SlateSurfaceElevated,
  onSecondaryContainer = IceWhiteText,
  tertiary = EmeraldSuccess,
  onTertiary = MidnightBackground,
  tertiaryContainer = EmeraldContainerDark,
  onTertiaryContainer = IceWhiteText,
  background = MidnightBackground,
  onBackground = IceWhiteText,
  surface = SlateSurface,
  onSurface = IceWhiteText,
  surfaceVariant = SlateSurfaceElevated,
  onSurfaceVariant = MutedBlueGrey,
  surfaceContainerLowest = MidnightBackground,
  surfaceContainerLow = SlateSurface,
  surfaceContainer = SlateSurface,
  surfaceContainerHigh = SlateSurfaceElevated,
  surfaceContainerHighest = DeepBlueContainer,
  outline = SubtleBorderDark,
  outlineVariant = DeepBlueContainer,
  error = CoralDanger,
  onError = Color.White,
  errorContainer = CoralContainerDark,
  onErrorContainer = IceWhiteText,
)

private val LightColorScheme = lightColorScheme(
  primary = DeepNavyPrimaryLight,
  onPrimary = Color.White,
  primaryContainer = SoftBlueSurfaceVariant,
  onPrimaryContainer = InkNavyText,
  secondary = AmberAccentLight,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFFF3DC),
  onSecondaryContainer = InkNavyText,
  tertiary = Color(0xFF15803D),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFDCFCE7),
  onTertiaryContainer = Color(0xFF052E16),
  background = CrispLightBackground,
  onBackground = InkNavyText,
  surface = PureWhiteSurface,
  onSurface = InkNavyText,
  surfaceVariant = SoftBlueSurfaceVariant,
  onSurfaceVariant = MutedSlateText,
  surfaceContainerLowest = PureWhiteSurface,
  surfaceContainerLow = CrispLightBackground,
  surfaceContainer = PureWhiteSurface,
  surfaceContainerHigh = SoftBlueSurfaceVariant,
  surfaceContainerHighest = Color(0xFFDCE4F2),
  outline = SubtleBorderLight,
  outlineVariant = SoftBlueSurfaceVariant,
  error = Color(0xFFDC2626),
  onError = Color.White,
  errorContainer = Color(0xFFFEE2E2),
  onErrorContainer = Color(0xFF450A0A),
)

val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun GestorComercialTheme(
  themeMode: ThemeMode = ThemeMode.DARK,
  content: @Composable () -> Unit,
) {
  val useDark = when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
  }

  val colorScheme = if (useDark) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content,
  )
}
