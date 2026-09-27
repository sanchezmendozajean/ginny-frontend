package com.ginny.frontend.ui.common

/** Shared "N juegos" / "1 juego" phrasing used next to system and game titles. */
fun gameCountLabel(count: Int): String = if (count == 1) "1 juego" else "$count juegos"
