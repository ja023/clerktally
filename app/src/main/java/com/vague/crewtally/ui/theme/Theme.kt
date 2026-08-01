package com.vague.crewtally.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The root CrewTally theme. Wraps [MaterialTheme] with the CrewTally color schemes,
 * type scale, and shapes so every Material component inherits them, and exposes the
 * app's dp tokens through the [CrewTallyTheme] accessor object.
 *
 * Every screen and component must consume ONLY these tokens — no ad-hoc colors, sp, or
 * dp literals outside `ui/theme/`.
 *
 * Dynamic (wallpaper-based) color is intentionally NOT used: CrewTally ships one
 * deliberate high-contrast palette tuned for the primary user, and it must look the same
 * on every device.
 */
@Composable
fun CrewTallyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CrewTallyDarkScheme else CrewTallyLightScheme,
        typography = CrewTallyTypography,
        shapes = CrewTallyShapes,
        content = content,
    )
}

/**
 * Central accessor for CrewTally design tokens that don't live on [MaterialTheme].
 * Colors, typography, and shapes come from `MaterialTheme.*`; dimensions, radii, named
 * shapes, and money text styles come from here. Usage: `CrewTallyTheme.dimens.primaryTarget`.
 */
object CrewTallyTheme {
    val dimens: CrewTallyDimens get() = CrewTallyDimens
    val radius: CrewTallyRadius get() = CrewTallyRadius
    val shape: CrewTallyShape get() = CrewTallyShape
    val type: CrewTallyType get() = CrewTallyType
}
