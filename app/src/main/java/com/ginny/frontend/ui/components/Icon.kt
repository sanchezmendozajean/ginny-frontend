package com.ginny.frontend.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ginny.frontend.ui.theme.GinnyThemes

@Composable
fun Icon(
    id: Int,
    degrees: Float = 0f,
    tint: Color = GinnyThemes.current.ink,
    size: Dp = 24.dp,
) {
    Box(modifier = Modifier.size(size)) {
        Icon(
            painter = painterResource(id),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize().rotate(degrees),
        )
    }
}
