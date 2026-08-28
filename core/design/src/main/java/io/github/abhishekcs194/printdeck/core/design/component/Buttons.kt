package io.github.abhishekcs194.printdeck.core.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing

enum class ButtonVariant { Primary, Outline, Ghost, Danger }

enum class ButtonSize(val height: androidx.compose.ui.unit.Dp, val horizontalPadding: androidx.compose.ui.unit.Dp) {
    /** Page-level actions. */
    Medium(44.dp, Spacing.lg),

    /** Toolbars and rows, where several actions sit side by side. */
    Small(32.dp, Spacing.md),
}

/**
 * The one button.
 *
 * Built from primitives rather than wrapping Material's `Button`, because the
 * design calls for a flat, bordered language: no elevation, no ripple-heavy
 * container, one 8dp radius everywhere. Fighting Material's defaults to get
 * there costs more than drawing it directly.
 */
@Composable
fun PrintDeckButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = PrintDeckTheme.colors
    val shape = RoundedCornerShape(Radius.md)

    val background = when (variant) {
        ButtonVariant.Primary -> colors.primary
        ButtonVariant.Danger -> colors.danger
        ButtonVariant.Outline, ButtonVariant.Ghost -> Color.Transparent
    }
    val content = when (variant) {
        ButtonVariant.Primary, ButtonVariant.Danger -> colors.onPrimary
        ButtonVariant.Outline -> colors.foreground
        ButtonVariant.Ghost -> colors.mutedForeground
    }
    val border = if (variant == ButtonVariant.Outline) {
        BorderStroke(1.dp, colors.inputBorder)
    } else {
        null
    }

    // Disabled state is carried by opacity rather than a separate palette, so a
    // token change cannot leave the disabled colours behind.
    val alpha = if (enabled) 1f else DISABLED_ALPHA

    Row(
        modifier = modifier
            .height(size.height)
            .clip(shape)
            .background(background.copy(alpha = background.alpha * alpha), shape)
            .then(border?.let { Modifier.border(it, shape) } ?: Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content.copy(alpha = alpha),
                modifier = Modifier.size(ICON_SIZE),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content.copy(alpha = alpha),
        )
    }
}

/** Icon-only action, for toolbars and page tiles. */
@Composable
fun PrintDeckIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val colors = PrintDeckTheme.colors
    val shape = RoundedCornerShape(Radius.md)
    val alpha = if (enabled) 1f else DISABLED_ALPHA

    Box(
        modifier = modifier
            .size(TOUCH_TARGET)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = (tint ?: colors.mutedForeground).copy(alpha = alpha),
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

private val ICON_SIZE = 18.dp

/** Kept at the platform minimum so icon-only actions stay comfortably tappable. */
private val TOUCH_TARGET = 40.dp

private const val DISABLED_ALPHA = 0.38f
