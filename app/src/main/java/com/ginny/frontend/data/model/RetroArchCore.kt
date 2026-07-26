package com.ginny.frontend.data.model

/**
 * A libretro core selectable for a RetroArch-backed system.
 * [soFileName] is passed to RetroArch as the LIBRETRO intent extra.
 */
data class RetroArchCore(
    val id: String,
    val label: String,
    val soFileName: String,
)
