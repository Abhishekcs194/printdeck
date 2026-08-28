package io.github.abhishekcs194.printdeck.core.design.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing

/**
 * Full-page empty state.
 *
 * Always carries an action. An empty screen that only explains itself leaves the
 * user to go looking for the way forward, which is the moment most people give
 * up on an unfamiliar app.
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = PrintDeckTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Box(
            modifier = Modifier
                .size(ICON_TILE)
                .clip(RoundedCornerShape(Radius.lg))
                .background(colors.primaryMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(ICON_IN_TILE),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.foreground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mutedForeground,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

/** Tinted status chip. Sized to sit inline with 12sp text without shifting it. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    val colors = PrintDeckTheme.colors
    val accent = tone ?: colors.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.lg))
            .background(accent.copy(alpha = TINT_ALPHA))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

/** Small ringed dot for per-row status, where a pill would be too loud. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(DOT_RING)
            .clip(CircleShape)
            .background(color.copy(alpha = RING_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(DOT_CORE).clip(CircleShape).background(color))
    }
}

/**
 * An inline notice, used for the discovery diagnosis.
 *
 * Deliberately not a dialog: the explanation for a failed printer search has to
 * stay on screen while the user acts on it, and a dialog they dismiss to go
 * looking takes the instructions with it.
 */
@Composable
fun Notice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    @DrawableRes icon: Int? = null,
    bullets: List<String> = emptyList(),
) {
    val colors = PrintDeckTheme.colors
    val accent = tone ?: colors.warning

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(accent.copy(alpha = TINT_ALPHA))
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(NOTICE_ICON),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.foreground)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = colors.mutedForeground)
            bullets.forEach { bullet ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = colors.mutedForeground)
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedForeground,
                    )
                }
            }
        }
    }
}

private val ICON_TILE = 64.dp
private val ICON_IN_TILE = 28.dp
private val NOTICE_ICON = 20.dp
private val DOT_RING = 12.dp
private val DOT_CORE = 6.dp
private const val TINT_ALPHA = 0.12f
private const val RING_ALPHA = 0.24f
