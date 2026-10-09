package com.ginny.frontend.ui.components.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes
import com.ginny.frontend.ui.theme.adjust
import com.ginny.frontend.ui.theme.lighten

/*
 * Piezas de la pantalla de Configuración, según el diseño de referencia:
 *   SettingsGroup ─ etiqueta en mayúsculas + tarjeta con filas
 *   SettingsRow   ─ título (y subtítulo) a la izquierda, control a la derecha
 * y los controles: SegmentedPicker, GinnySwitch, GinnySlider, GinnyDropdown.
 */

/** Acento oscurecido: para textos en el color del tema (etiquetas de grupo, opción elegida). */
@Composable
private fun accentText(): Color = GinnyThemes.current.accent.adjust(lightness = 0.45f)

/** Color de las líneas finas (bordes de tarjeta, divisores). */
@Composable
private fun lineColor(): Color = GinnyThemes.current.ink.copy(alpha = 0.08f)

// ── Estructura ────────────────────────────────────────────────────────────────────────────

/** Un grupo de opciones: [label] en mayúsculas y, debajo, una tarjeta con las filas ([content]). */
@Composable
fun SettingsGroup(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontFamily = GinnyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = accentText(),
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.7f), shape)
                .border(1.dp, lineColor(), shape)
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
}

/** Línea fina entre dos filas de un mismo grupo. */
@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(lineColor()),
    )
}

/** Una opción: [title] (y [subtitle] opcional) a la izquierda; el control ([trailing]) a la derecha. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val palette = GinnyThemes.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GinnyFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = palette.ink,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = palette.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
}

// ── Controles ─────────────────────────────────────────────────────────────────────────────

/** Opciones en una píldora; la elegida, resaltada en blanco. Ej. filas visibles 1 · 2 · 3 · 4. */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String = { it.toString() },
) {
    val palette = GinnyThemes.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .background(palette.background, CircleShape)
            .padding(4.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(32.dp)
                    .widthInFor(label(option))
                    .then(if (active) Modifier.shadow(3.dp, CircleShape).background(Color.White, CircleShape) else Modifier)
                    .pointerInput(option) { detectTapGestures { onSelect(option) } },
            ) {
                Text(
                    text = label(option),
                    fontFamily = GinnyFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (active) accentText() else palette.muted,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

/** Opciones de una sola cifra: todas del mismo ancho, como en el diseño. */
private fun Modifier.widthInFor(text: String): Modifier =
    if (text.length <= 2) this.width(40.dp) else this

/** Interruptor encendido / apagado. */
@Composable
fun GinnySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = GinnyThemes.current
    val trackColor by animateColorAsState(
        if (checked) palette.accent else palette.ink.copy(alpha = 0.15f),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switchThumb")

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .background(trackColor, CircleShape)
            .pointerInput(checked) { detectTapGestures { onCheckedChange(!checked) } },
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** Deslizador 0…1: tocar o arrastrar sobre la barra cambia el valor. */
@Composable
fun GinnySlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val palette = GinnyThemes.current
    val thumbSize = 22.dp
    val currentOnChange by rememberUpdatedState(onValueChange)

    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { currentOnChange((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    currentOnChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val v = value.coerceIn(0f, 1f)
        val thumbX = ((maxWidth - thumbSize) * v)

        // Pista
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(palette.ink.copy(alpha = 0.08f), CircleShape),
        )
        // Parte llena, hasta el centro del círculo
        Box(
            modifier = Modifier
                .width(thumbX + thumbSize / 2)
                .height(6.dp)
                .background(Brush.horizontalGradient(listOf(palette.accent.lighten(0.08f), palette.accent)), CircleShape),
        )
        // Círculo
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .size(thumbSize)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
                .border(3.dp, palette.accent, CircleShape),
        )
    }
}

/**
 * Desplegable: muestra la opción [selected]; al tocarlo, lista [options] (texto → valor) con la
 * elegida resaltada (fondo del acento suave, texto en el color del tema y ✓).
 */
@Composable
fun <T> GinnyDropdown(
    selected: T,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 240.dp,
) {
    val palette = GinnyThemes.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.second == selected }?.first.orEmpty()

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(width)
                .height(40.dp)
                .background(palette.background, CircleShape)
                .border(1.dp, lineColor(), CircleShape)
                .pointerInput(Unit) { detectTapGestures { expanded = true } }
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = selectedLabel,
                fontFamily = GinnyFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = "▾", fontSize = 12.sp, color = palette.muted)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(width)
                .background(Color.White), // sin esto, Material usa un fondo lila por defecto
        ) {
            options.forEach { (label, value) ->
                val active = value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            fontFamily = GinnyFont,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (active) accentText() else palette.ink,
                        )
                    },
                    trailingIcon = if (active) {
                        { Text("✓", fontWeight = FontWeight.ExtraBold, color = accentText()) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                    // La elegida: fondo del acento suave, como una píldora dentro del menú.
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .then(
                            if (active) {
                                Modifier.background(palette.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

/**
 * Botón píldora. [primary]: degradado del acento con texto blanco (la acción principal, ej.
 * "Escanear"); si no, fondo tenue con texto del tema (ej. "Elegir"). Deshabilitado: apagado y
 * sin responder al toque.
 */
@Composable
fun GinnyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = GinnyThemes.current
    val currentOnClick by rememberUpdatedState(onClick)
    val background = when {
        !enabled -> Brush.horizontalGradient(listOf(lineColor(), lineColor()))
        primary -> Brush.horizontalGradient(listOf(palette.accent.lighten(0.08f), palette.accent))
        else -> Brush.horizontalGradient(listOf(palette.background, palette.background))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .then(if (primary && enabled) Modifier.shadow(4.dp, CircleShape, spotColor = palette.accent) else Modifier)
            .background(background, CircleShape)
            .then(if (!primary) Modifier.border(1.dp, lineColor(), CircleShape) else Modifier)
            .pointerInput(enabled) { if (enabled) detectTapGestures { currentOnClick() } }
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = text,
            fontFamily = GinnyFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            color = when {
                !enabled -> palette.muted
                primary -> Color.White
                else -> accentText()
            },
            maxLines = 1,
        )
    }
}

/**
 * Campo de texto de una línea, con la misma forma que [GinnyDropdown]. Guarda su propio texto
 * mientras se escribe (así el cursor no salta esperando a que se guarde) y avisa cada cambio con
 * [onValueChange]. [placeholder] se ve mientras está vacío.
 *
 * [secret]: es una credencial; el teclado no sugiere ni autocorrige. [masked]: se muestra como
 * puntos (por defecto, igual que [secret]; el que lo usa puede mostrarlo con `masked = false`).
 */
@Composable
fun GinnyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    width: Dp = 240.dp,
    secret: Boolean = false,
    masked: Boolean = secret,
) {
    val palette = GinnyThemes.current
    // remember(value) no: el valor guardado llega un instante después de cada tecla y pisaría lo
    // escrito. Se toma el valor inicial una sola vez.
    var text by remember { mutableStateOf(value) }
    val style = TextStyle(fontFamily = GinnyFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.ink)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .width(width)
            .height(40.dp)
            .background(palette.background, CircleShape)
            .border(1.dp, lineColor(), CircleShape)
            .padding(horizontal = 16.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            singleLine = true,
            textStyle = style,
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (secret) {
                KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Password)
            } else {
                KeyboardOptions.Default
            },
            cursorBrush = SolidColor(palette.accent),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(placeholder, style = style.copy(color = palette.muted))
                inner()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
