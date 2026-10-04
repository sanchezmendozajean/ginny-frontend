package com.ginny.frontend.data.model

/** Posición de un botón en el rombo de botones frontales del mando. */
enum class FacePosition { Left, Top, Right, Bottom }

/**
 * Distribución física de los botones frontales, elegida en Configuración > Botones. Solo cambia
 * qué botón señalan los íconos de la leyenda; no toca el mapeo de teclas ([com.ginny.frontend.data.ButtonMapping]).
 *
 * [label] se lee como el rombo: izquierda y arriba / abajo y derecha.
 */
enum class ButtonLayout(
    val label: String,
    val a: FacePosition,
    val b: FacePosition,
    val x: FacePosition,
    val y: FacePosition,
) {
    /** Tipo Nintendo: X arriba, Y izquierda, A derecha, B abajo. */
    YX_BA("YX/BA", a = FacePosition.Right, b = FacePosition.Bottom, x = FacePosition.Top, y = FacePosition.Left),

    /** Tipo Xbox: Y arriba, X izquierda, B derecha, A abajo. */
    XY_AB("XY/AB", a = FacePosition.Bottom, b = FacePosition.Right, x = FacePosition.Left, y = FacePosition.Top),
}
