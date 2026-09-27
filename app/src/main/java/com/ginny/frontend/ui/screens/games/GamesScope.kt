package com.ginny.frontend.ui.screens.games

import com.ginny.frontend.data.model.SystemId

/** What [GamesViewModel]/[GamesScreen] list: either one real system's ROMs, or the cross-system
 * Favoritos pseudo-system (see [com.ginny.frontend.ui.screens.systems.SystemTileItem]). */
sealed interface GamesScope {
    data class Single(val systemId: SystemId) : GamesScope
    data object Favorites : GamesScope
}
