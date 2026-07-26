package com.ginny.frontend.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/**
 * Bottom bar: currently-selected item name on the left, action pills on the right. The "B
 * Retroceder" pill only appears inside a games/save-states list (never on the systems screen) and
 * is touch/physical-button only — it deliberately never receives D-Pad focus. The "Y Recargar"
 * pill is opt-in via [onReloadClick] since it only makes sense on the systems and games lists,
 * not on the save-states screen.
 */
@Composable
fun GinnyBottomBar(
    selectedName: String,
    showBack: Boolean,
    onBackClick: () -> Unit,
    onOpenClick: () -> Unit,
    openLabel: String = "Abrir",
    onReloadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .glassGlow(GinnyColors.GlowBottomBar, 999.dp)
            .then(glassBackgroundModifier(999.dp))
            .padding(start = 22.dp, end = 10.dp),
    ) {
        Text(
            text = selectedName,
            style = MaterialTheme.typography.titleSmall,
            color = GinnyColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

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
