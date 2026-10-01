package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.ginny.frontend.R
import com.ginny.frontend.ui.theme.GinnyFont
import com.ginny.frontend.ui.theme.GinnyThemes

/**
 * Información del elemento enfocado en la grilla: título y subtítulo debajo, con el ícono del
 * mando. Va a la izquierda del footer.
 */
@Composable
fun SelectionInfo(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val palette = GinnyThemes.current

    Container(modifier = modifier) {
        Icon(id = R.drawable.ic_gamepad)
        Column {
            Text(
                text = title,
                fontFamily = GinnyFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = palette.ink,
                maxLines = 1,                       // nombres largos: una línea…
                overflow = TextOverflow.Ellipsis,   // …y "…" al final
            )
            Text(
                text = subtitle,
                fontFamily = GinnyFont,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
