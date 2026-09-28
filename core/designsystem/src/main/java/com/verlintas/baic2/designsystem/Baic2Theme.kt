package com.verlintas.baic2.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * BAIC2 theme. Dark-first: dark is the primary surface language, light is a
 * fully supported counterpart built from the same tokens.
 *
 * Note: material3 1.4.0 keeps the M3 Expressive theme/motion APIs internal,
 * so motion comes from [Baic2Motion] instead of MotionScheme. Revisit when
 * material3 1.5 goes stable.
 */
@Composable
fun Baic2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Baic2DarkColorScheme else Baic2LightColorScheme,
        shapes = Baic2Shapes,
        typography = Baic2Typography,
        content = content,
    )
}
