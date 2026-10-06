package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ginny.frontend.ui.theme.GinnyFont
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ginny.frontend.R
import com.ginny.frontend.ui.theme.GinnyThemes

@Composable
fun SystemLabel(
    systemName: String? = null
) {
    Container() {
        GinnyIcon()
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Ginny",
                fontFamily = GinnyFont,   // la familia
                fontWeight = FontWeight.ExtraBold,        // el peso (grosor)
                fontSize = 18.sp,                    // el tamaño
                color = GinnyThemes.current.ink,
            )
            if (systemName != null) {
                Text(
                    text = "/",
                    fontFamily = GinnyFont,   // la familia
                    fontWeight = FontWeight.Bold,        // el peso (grosor)
                    fontSize = 16.sp,                    // el tamaño
                    color = GinnyThemes.current.ink,
                )
                Text(
                    text = systemName,
                    fontFamily = GinnyFont,   // la familia
                    fontWeight = FontWeight.Bold,        // el peso (grosor)
                    fontSize = 14.sp,                    // el tamaño
                    color = Color.Black,
                )
            }
        }
    }
}
