package com.ginny.frontend.ui.screens.systems

import com.ginny.frontend.data.model.SystemDefinition

/** One tile in the Systems grid: either a real [SystemDefinition], or the synthetic Favoritos
 * tile (only shown when at least one game is favorited) — [SystemCatalog] itself never gains a
 * fake entry for this, it's injected at this UI layer only. */
sealed interface SystemTileItem {
    data class Real(val system: SystemDefinition) : SystemTileItem
    data object Favorites : SystemTileItem
}
