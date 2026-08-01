package com.vague.crewtally.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii. Single source for all radii in the app. */
object CrewTallyRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 1000.dp
}

/** Material 3 Shapes mapped onto the CrewTally radius scale. */
val CrewTallyShapes = Shapes(
    extraSmall = RoundedCornerShape(CrewTallyRadius.sm),
    small = RoundedCornerShape(CrewTallyRadius.sm),
    medium = RoundedCornerShape(CrewTallyRadius.md),
    large = RoundedCornerShape(CrewTallyRadius.lg),
    extraLarge = RoundedCornerShape(CrewTallyRadius.xl),
)

/** Named component shapes so call sites reference intent, not a radius value. */
object CrewTallyShape {
    val card = RoundedCornerShape(CrewTallyRadius.lg)
    val button = RoundedCornerShape(CrewTallyRadius.md)
    val field = RoundedCornerShape(CrewTallyRadius.md)
    val row = RoundedCornerShape(CrewTallyRadius.md)
    val pill = RoundedCornerShape(CrewTallyRadius.pill)
}
