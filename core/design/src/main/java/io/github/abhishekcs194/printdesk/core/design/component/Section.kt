package io.github.abhishekcs194.printdesk.core.design.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.abhishekcs194.printdesk.core.design.theme.PrintDeskTheme
import io.github.abhishekcs194.printdesk.core.design.theme.Radius
import io.github.abhishekcs194.printdesk.core.design.theme.Spacing

/**
 * The standard content grouping: a 1dp bordered card with an 8dp radius and
 * **no elevation**.
 *
 * Structure here is drawn with borders rather than shadows. That single choice
 * is most of what separates this from a default Material 3 layout — stacked
 * elevated cards are the house style of every generated Compose app.
 */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PrintDeskTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .border(1.dp, colors.border, RoundedCornerShape(Radius.md)),
    ) {
        if (title != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.foreground,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedForeground,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = colors.border)
        }
        Column(
            modifier = if (contentPadding) Modifier.padding(Spacing.lg) else Modifier,
            content = content,
        )
    }
}

/**
 * Page header. Repeated per screen rather than hidden behind a shared scaffold,
 * so a screen's title and subtitle stay visible where the screen is defined.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = PrintDeskTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.foreground,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedForeground,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
