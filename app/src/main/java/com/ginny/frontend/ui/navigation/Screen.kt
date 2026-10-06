package com.ginny.frontend.ui.navigation

import com.ginny.frontend.ui.screens.games.GamesScope

/** La pantalla que se muestra en el panel central. */
sealed interface Screen {
    /** Inicio: la grilla de sistemas. */
    data object Systems : Screen

    /** Los juegos de [scope] (un sistema o Favoritos); [title] va en el header. */
    data class Games(val scope: GamesScope, val title: String) : Screen
}
