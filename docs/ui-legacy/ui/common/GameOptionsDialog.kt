package com.ginny.frontend.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.ui.components.ButtonBadge
import com.ginny.frontend.ui.components.BadgeStyle
import com.ginny.frontend.ui.components.PillDropdown
import com.ginny.frontend.ui.components.TileStripedPlaceholder
import com.ginny.frontend.ui.theme.LocalGinnyPalette
import com.ginny.frontend.ui.theme.diagonalGradient
import com.ginny.frontend.ui.theme.oklch
import com.ginny.frontend.ui.theme.tileHue

private val CoverSize = 112.dp

/**
 * "Opciones de juego" — opens on Y from the games grid. Consolidates what used to be three
 * separate things (the overflow menu's "Seleccionar emulador"/"Reescanear este sistema" and the
 * standalone [DeleteGameDialog]) into one modal, matching docs/diseño. Delete is a two-tap inline
 * confirm ([deleteArmed], local to this composable) instead of a second dialog.
 */
@Composable
fun GameOptionsDialog(
    game: GameEntry,
    system: SystemDefinition,
    romFilePath: String?,
    isFavorite: Boolean,
    currentEmulator: EmulatorTarget,
    currentCoreId: String?,
    onSetEmulator: (EmulatorTarget) -> Unit,
    onSetCore: (String) -> Unit,
    onSetPackageOverride: (String) -> Unit,
    packageOverride: String?,
    onToggleFavorite: () -> Unit,
    onRescanSystem: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalGinnyPalette.current
    var deleteArmed by remember { mutableStateOf(false) }
    val hue = system.id.tileHue()
    val showsCoreDropdown = currentEmulator in EmulatorTarget.RETROARCH_VARIANTS && system.retroArchCores.isNotEmpty()

    GinnyModalSurface(
        onDismissRequest = onDismiss,
        cardModifier = Modifier.width(720.dp),
    ) {
        Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(
                    modifier = Modifier
                        .size(CoverSize)
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(5.dp),
                ) {
                    if (game.iconUri != null) {
                        coil.compose.AsyncImage(
                            model = game.iconUri,
                            contentDescription = game.displayName,
                            modifier = Modifier.fillMaxWidth().height(CoverSize - 10.dp),
                        )
                    } else {
                        TileStripedPlaceholder(hue = hue, modifier = Modifier.height(CoverSize - 10.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(game.displayName, style = MaterialTheme.typography.titleLarge, color = palette.ink)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(palette.chip, RoundedCornerShape(16.dp))
                            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(20.dp).background(
                                diagonalGradient(oklch(0.78f, 0.13f, hue), oklch(0.83f, 0.11f, hue + 50f)),
                                RoundedCornerShape(7.dp),
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(system.displayName, style = MaterialTheme.typography.labelLarge, color = palette.muted)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(palette.chip, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", style = MaterialTheme.typography.titleMedium, color = palette.ink)
                }
            }

            ModalSection(title = "Archivo") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(22.dp))
                        .border(1.dp, palette.line, RoundedCornerShape(22.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        game.romFileName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (romFilePath != null) {
                        Text(
                            romFilePath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            ModalSection(title = "Emulador") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Aplicación", style = MaterialTheme.typography.labelMedium, color = palette.muted)
                        PillDropdown(
                            selectedLabel = currentEmulator.displayName,
                            options = system.availableEmulators.map { it.displayName to it },
                            onSelect = onSetEmulator,
                            background = palette.chip,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Core", style = MaterialTheme.typography.labelMedium, color = palette.muted)
                        if (showsCoreDropdown) {
                            val selectedCore = system.retroArchCores.firstOrNull { it.id == currentCoreId }
                                ?: system.retroArchCores.first()
                            PillDropdown(
                                selectedLabel = selectedCore.label,
                                options = system.retroArchCores.map { it.label to it.id },
                                onSelect = onSetCore,
                                background = Color.White,
                                bordered = true,
                            )
                        } else if (system.supportsPackageOverride) {
                            PackageOverrideField(
                                value = packageOverride.orEmpty(),
                                onValueChange = onSetPackageOverride,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .background(Color.Transparent, RoundedCornerShape(23.dp))
                                    .border(1.dp, palette.line, RoundedCornerShape(23.dp)),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    "App independiente",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.muted,
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "Reescanear este sistema",
                style = MaterialTheme.typography.labelMedium,
                color = palette.accentInk,
                modifier = Modifier.clickable(onClick = onRescanSystem),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (isFavorite) oklch(0.95f, 0.05f, palette.hue) else palette.chip,
                            RoundedCornerShape(26.dp),
                        )
                        .clickable(onClick = onToggleFavorite)
                        .padding(start = 16.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
                ) {
                    Text(if (isFavorite) "★" else "☆", color = palette.accentInk, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isFavorite) "En favoritos" else "Favorito",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.accentInk,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (deleteArmed) oklch(0.6f, 0.19f, 25f) else palette.chip,
                            RoundedCornerShape(26.dp),
                        )
                        .clickable {
                            if (deleteArmed) onDelete() else deleteArmed = true
                        }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                ) {
                    Text(
                        if (deleteArmed) "¿Eliminar de verdad?" else "Eliminar",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (deleteArmed) Color.White else oklch(0.52f, 0.17f, 25f),
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(palette.grad, RoundedCornerShape(28.dp))
                        .clickable(onClick = onPlay)
                        .padding(start = 8.dp, end = 26.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    ButtonBadge(letter = "A", style = BadgeStyle.SOLID, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Jugar", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ModalSection(title: String, content: @Composable () -> Unit) {
    val palette = LocalGinnyPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = palette.accentInk,
            modifier = Modifier.padding(start = 6.dp),
        )
        content()
    }
}


@Composable
private fun PackageOverrideField(value: String, onValueChange: (String) -> Unit) {
    val palette = LocalGinnyPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color.White, RoundedCornerShape(23.dp))
            .border(1.dp, palette.line, RoundedCornerShape(23.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.labelLarge.copy(color = palette.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text("Package…", style = MaterialTheme.typography.labelLarge, color = palette.muted)
                }
                inner()
            },
        )
    }
}
