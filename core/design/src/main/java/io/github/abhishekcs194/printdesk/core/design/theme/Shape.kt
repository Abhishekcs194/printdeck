package io.github.abhishekcs194.printdesk.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One radius, applied consistently.
 *
 * 8dp on essentially everything: buttons, inputs, cards, sheets, thumbnails.
 * 12dp is reserved for badges and large icon tiles. Fully round is only for
 * status dots and avatars. Mixed radii are the most common tell of an interface
 * assembled from unrelated snippets, so the scale is deliberately this short.
 */
object Radius {
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
}

val PrintDeskShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.md),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.md),
    extraLarge = RoundedCornerShape(Radius.lg),
)

/** Spacing scale. Multiples of 4, and only these values. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
