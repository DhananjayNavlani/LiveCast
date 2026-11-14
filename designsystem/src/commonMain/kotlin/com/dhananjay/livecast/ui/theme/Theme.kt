
package com.dhananjay.livecast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

object LiveCastTheme {
    val colors: LiveCastColors
        @ReadOnlyComposable @Composable
        get() = LocalLiveCastColors.current

    val typography: LiveCastTypography
        @ReadOnlyComposable @Composable
        get() = LocalLiveCastTypography.current
}

@Composable
fun LiveCastTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkTheme) DarkColors else LightColors
    val selectionColors = rememberTextSelectionColors(colors)
    val typography = provideTypography()

    // Screens use Material 2 components (Text, TextField, Card, OutlinedButton, TopAppBar). Without a
    // MaterialTheme they fall back to Material's light palette (black content, white surfaces), which
    // turns into black-on-black / white-on-white in dark mode. Feed Material the same palette.
    MaterialTheme(colors = colors.toMaterialColors(isLight = !isDarkTheme)) {
        CompositionLocalProvider(
            LocalLiveCastColors provides colors,
            LocalLiveCastTypography provides typography,
            LocalTextSelectionColors provides selectionColors,
            LocalContentColor provides colors.contentColorFor(colors.background),
            LocalTextStyle provides typography.body1,
            content = content,
        )
    }
}

internal fun LiveCastColors.toMaterialColors(isLight: Boolean): Colors = Colors(
    primary = primary,
    primaryVariant = primary,
    secondary = tertiary,
    secondaryVariant = tertiary,
    background = background,
    surface = surface,
    error = error,
    onPrimary = onPrimary,
    onSecondary = onTertiary,
    onBackground = onBackground,
    onSurface = onSurface,
    onError = onError,
    isLight = isLight,
)

@Composable
fun contentColorFor(color: Color): Color {
    return LiveCastTheme.colors.contentColorFor(color)
}

@Composable
internal fun rememberTextSelectionColors(colorScheme: LiveCastColors): TextSelectionColors {
    val primaryColor = colorScheme.primary
    return remember(primaryColor) {
        TextSelectionColors(
            handleColor = primaryColor,
            backgroundColor = primaryColor.copy(alpha = TextSelectionBackgroundOpacity),
        )
    }
}

internal const val TextSelectionBackgroundOpacity = 0.4f
