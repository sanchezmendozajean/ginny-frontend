package com.ginny.frontend.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ginny.frontend.data.AssetRepository
import com.ginny.frontend.data.model.SystemDefinition
import com.ginny.frontend.ui.theme.shortCode

private sealed interface LogoState {
    data object Loading : LogoState
    data class Found(val uri: Uri) : LogoState
    data object NotFound : LogoState
}

/**
 * A system's custom logo (`Assets/<sistema>/system.png` in the Carpeta de Usuario) if configured,
 * with its short code ([com.ginny.frontend.ui.theme.shortCode]) as the fallback — used by the
 * Systems grid, the Emuladores tab in Configuración, and [com.ginny.frontend.ui.common.EmulatorConfigDialog].
 *
 * Checking whether a logo exists is a real (SAF) file lookup, not instant — this deliberately shows
 * NEITHER the image NOR the short-code fallback while that check is in flight, only ever revealing
 * the fallback once absence is *confirmed*. Showing the short code immediately (the old behavior)
 * meant every tile flashed its placeholder text first even when it did have a real logo, which
 * looked especially bad on a page a [PagedFocusGrid] had just scrolled into view.
 */
@Composable
fun SystemLogoBox(
    system: SystemDefinition,
    userFolderUri: String?,
    assetRepository: AssetRepository,
    modifier: Modifier = Modifier,
    imagePadding: Dp = 0.dp,
) {
    var state by remember(userFolderUri, system.id) { mutableStateOf<LogoState>(LogoState.Loading) }

    LaunchedEffect(userFolderUri, system.id) {
        state = LogoState.Loading
        val uri = userFolderUri?.let { uriString ->
            runCatching { assetRepository.systemLogoUri(Uri.parse(uriString), system.id) }.getOrNull()
        }
        state = if (uri != null) LogoState.Found(uri) else LogoState.NotFound
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            LogoState.Loading -> Unit
            is LogoState.Found -> AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(current.uri).crossfade(true).build(),
                contentDescription = system.displayName,
                modifier = Modifier.fillMaxSize().padding(imagePadding),
            )
            LogoState.NotFound -> TilePlaceholderLabel(text = system.id.shortCode)
        }
    }
}
