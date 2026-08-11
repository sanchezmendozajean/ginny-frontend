package com.ginny.frontend.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.ginny.frontend.data.model.EmulatorTarget

/**
 * GameCube/Wii, via Dolphin. Verified against a real install (`adb shell dumpsys package
 * org.dolphinemu.dolphinemu`): MainActivity declares no data intent-filter at all, and the only
 * ACTION_VIEW target in the app is `dolphinemu://app` (AppLinkActivity, an internal deep link
 * unrelated to opening game files) — so [GenericViewLauncher]'s "ACTION_VIEW + setPackage"
 * approach that every other emulator here accepts throws ActivityNotFoundException for Dolphin.
 *
 * The working contract instead (also used by third-party frontends like Daijishou/Pegasus, since
 * Dolphin itself documents nothing here) is an explicit launch of MainActivity with ACTION_MAIN
 * and the ROM Uri passed as the "AutoStartFile" string extra — MainActivity reads that extra
 * directly in code, it isn't matched via intent-filter resolution, so action/data don't need to
 * satisfy any filter. `data` is still set to the same Uri (Dolphin itself ignores it) purely so
 * FLAG_GRANT_READ_URI_PERMISSION actually grants Dolphin read access to the content:// Uri — that
 * flag only auto-grants permission for a Uri in Intent.data/ClipData, not one packed inside a
 * plain string extra.
 */
class DolphinLauncher : LauncherStrategy {
    override val emulator: EmulatorTarget = EmulatorTarget.DOLPHIN

    override fun buildIntent(context: Context, request: LaunchRequest, packageNameOverride: String?): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                packageNameOverride ?: emulator.packageName,
                "org.dolphinemu.dolphinemu.ui.main.MainActivity",
            )
            data = request.game.romUri
            putExtra("AutoStartFile", request.game.romUri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    override fun storeUrl(): String? = "https://dolphin-emu.org/download/"
}
