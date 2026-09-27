package com.ginny.frontend.ui.theme

import com.ginny.frontend.data.model.SystemId

/**
 * Deterministic per-system hue (golden-angle spacing gives good, even dispersion across the
 * whole catalog without hand-mapping ~30 systems the way the visual reference's small demo did).
 * Every system/game tile color, and the games/save-states top bar's system-color square, derive
 * from this so a given system always looks the same shade everywhere.
 */
fun SystemId.tileHue(): Float = (ordinal * 137.508f) % 360f

/** Games belonging to different systems (e.g. the Favoritos listing) get a hue variant derived
 * from the underlying system hue plus a per-tile offset, matching the reference's per-game hue
 * jitter (`SYS[it.s][2] + ((i * 37) % 90) - 45`) so a page of favorites isn't visually monotone. */
fun SystemId.gameTileHue(tileIndex: Int): Float = (tileHue() + (tileIndex * 37) % 90 - 45 + 360f) % 360f
