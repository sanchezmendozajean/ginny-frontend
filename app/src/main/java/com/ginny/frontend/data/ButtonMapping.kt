package com.ginny.frontend.data

import android.view.KeyEvent

/** Logical buttons the app cares about, each remappable to a physical Android keycode in Settings. */
enum class LogicalButton(val defaultKeyCode: Int) {
    A(KeyEvent.KEYCODE_BUTTON_A),
    B(KeyEvent.KEYCODE_BUTTON_B),
    Y(KeyEvent.KEYCODE_BUTTON_Y),
    MENU(KeyEvent.KEYCODE_MENU),
    L(KeyEvent.KEYCODE_BUTTON_L1),
    R(KeyEvent.KEYCODE_BUTTON_R1),
    DPAD_UP(KeyEvent.KEYCODE_DPAD_UP),
    DPAD_DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
    DPAD_LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
    DPAD_RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
}

/** Resolves the current physical keycode assigned to each [LogicalButton], falling back to defaults. */
data class ButtonMapping(val keyCodes: Map<LogicalButton, Int>) {
    fun keyCodeFor(button: LogicalButton): Int = keyCodes[button] ?: button.defaultKeyCode

    fun buttonForKeyCode(keyCode: Int): LogicalButton? =
        LogicalButton.values().firstOrNull { keyCodeFor(it) == keyCode }

    companion object {
        val DEFAULT = ButtonMapping(LogicalButton.values().associateWith { it.defaultKeyCode })
    }
}
