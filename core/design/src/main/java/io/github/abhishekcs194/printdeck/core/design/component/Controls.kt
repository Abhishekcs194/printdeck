package io.github.abhishekcs194.printdeck.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.abhishekcs194.printdeck.core.design.theme.PrintDeckTheme
import io.github.abhishekcs194.printdeck.core.design.theme.Radius
import io.github.abhishekcs194.printdeck.core.design.theme.Spacing

/**
 * Segmented control: a small set of mutually exclusive options, all visible.
 *
 * Preferred over a dropdown when there are two to four choices, because the
 * options themselves explain what the control does — which matters here, where
 * "booklet" and "poster" are the feature, not a setting buried behind a menu.
 */
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = PrintDeckTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(colors.muted)
            .padding(TRACK_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TRACK_PADDING),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) colors.background else Color.Transparent,
                label = "segmentBackground",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) colors.foreground else colors.mutedForeground,
                label = "segmentContent",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SEGMENT_HEIGHT)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(background)
                    .clickable(role = Role.Tab) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    textAlign = TextAlign.Center,
                    // A wrapped label gets clipped by the fixed segment height,
                    // which reads as a rendering fault rather than a long word.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}

/** Horizontally scrolling choices, for sets too long for a segmented control. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = PrintDeckTheme.colors
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(options.size) { index ->
            val option = options[index]
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.md))
                    .background(if (isSelected) colors.primaryMuted else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) colors.primary else colors.border,
                        shape = RoundedCornerShape(Radius.md),
                    )
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.primary else colors.mutedForeground,
                )
            }
        }
    }
}

/**
 * One setting: a label, optional explanation, and its control on the right.
 *
 * The explanation slot is used a lot. Imposition has genuinely obscure controls —
 * creep, gutter, signature size — and a label alone leaves people guessing.
 */
@Composable
fun OptionRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    control: @Composable () -> Unit,
) {
    val colors = PrintDeckTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = colors.foreground)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedForeground,
                )
            }
        }
        control()
    }
}

/** Numeric stepper for small integer settings. */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = DEFAULT_RANGE,
    suffix: String = "",
) {
    val colors = PrintDeckTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .border(1.dp, colors.inputBorder, RoundedCornerShape(Radius.md)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("−", enabled = value > range.first) { onChange(value - 1) }
        Text(
            text = "$value$suffix",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(VALUE_WIDTH),
        )
        StepperButton("+", enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = PrintDeckTheme.colors
    Box(
        modifier = Modifier
            .width(STEPPER_BUTTON)
            .height(STEPPER_BUTTON)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) colors.foreground else colors.mutedForeground.copy(alpha = DISABLED_ALPHA),
        )
    }
}

private val SEGMENT_HEIGHT = 32.dp
private val TRACK_PADDING = 3.dp
private val STEPPER_BUTTON = 36.dp
private val VALUE_WIDTH = 44.dp
private const val DISABLED_ALPHA = 0.4f

/** Two digits keeps the value box a fixed width. */
private val DEFAULT_RANGE = 1..99
