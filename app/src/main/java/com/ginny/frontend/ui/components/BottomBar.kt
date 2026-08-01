package com.ginny.frontend.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyColors

/** Bar is sized to match the height of the pill-shaped buttons that sit next to it. */
private val BottomBarHeight = ButtonPillHeight

/**
 * Bottom bar: the glass pill wraps only the currently-selected item name. The action pills
 * ("Y Recargar", "B Retroceder", "A ...") live outside the pill as separate elements, not inside
 * the bar. The "B Retroceder" pill only appears inside a games/save-states list (never on the
 * systems screen) and is touch/physical-button only — it deliberately never receives D-Pad focus.
 * The "Y Recargar" pill is opt-in via [onReloadClick] since it only makes sense on the systems and
 * games lists, not on the save-states screen.
 */
@Composable
fun GinnyBottomBar(
    selectedName: String,
    showBack: Boolean,
    onBackClick: () -> Unit,
    onOpenClick: () -> Unit,
    openLabel: String = "Abrir",
    onReloadClick: (() -> Unit)? = null,
    /** Optional second, smaller line under [selectedName] — e.g. the real ROM file name behind a
     * [com.ginny.frontend.data.model.GameEntry.displayName] taken from the names.json dictionary. */
    selectedSecondaryText: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = BottomBarHeight)
                .glassShadow(999.dp)
                .then(glassBackgroundModifier(999.dp))
                .padding(horizontal = 22.dp, vertical = 6.dp),
        ) {
            Text(
                text = selectedName,
                style = MaterialTheme.typography.titleSmall,
                color = GinnyColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!selectedSecondaryText.isNullOrBlank()) {
                Text(
                    text = selectedSecondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = GinnyColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        onReloadClick?.let { reload ->
            ActionPill(letter = "Y", label = "Recargar", style = BadgeStyle.Y, onClick = reload)
            Spacer(Modifier.width(10.dp))
        }

        AnimatedVisibility(visible = showBack) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionPill(letter = "B", label = "Retroceder", style = BadgeStyle.B, onClick = onBackClick)
                Spacer(Modifier.width(10.dp))
            }
        }

        ActionPill(letter = "A", label = openLabel, style = BadgeStyle.A, onClick = onOpenClick)
    }
}
