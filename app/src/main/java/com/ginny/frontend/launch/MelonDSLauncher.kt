package com.ginny.frontend.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.ginny.frontend.data.model.EmulatorTarget

/**
 * Nintendo DS, via melonDS DS. Same story as [DolphinLauncher]: verified against a real install
 * (`adb shell dumpsys package me.magnum.melonds`) that the app registers no ACTION_VIEW
 * intent-filter at all — only its internal `me.magnum.melonds.LAUNCH_ROM`/`LAUNCH_FIRMWARE`
 * actions (used by its own shortcut flow) show up — so [GenericViewLauncher]'s "ACTION_VIEW +
 * setPackage" approach can't resolve an activity and throws ActivityNotFoundException.
 *
 * The documented third-party frontend contract instead (melonDS-android's own README, "Frontend
 * integration" section) is an explicit launch of `me.magnum.melonds.ui.emulator.EmulatorActivity`
 * with the ROM Uri set as the Intent's `data` — the "preferred" of the three methods it lists
 * (the other two, a `PATH` absolute-path extra and a deprecated `uri` string extra, both exist
 * only for backwards compat). Since the target activity has no manifest intent-filter to match,
 * resolution only works via an explicit [ComponentName] — action doesn't need to satisfy any
 * filter, so ACTION_VIEW here is just for clarity, not a requirement. `data` being set (rather
 * than an extra) is also what makes FLAG_GRANT_READ_URI_PERMISSION actually take effect for the
 * content:// Uri.
 */
class MelonDSLauncher : LauncherStrategy {
    override val emulator: EmulatorTarget = EmulatorTarget.MELONDS

    override fun buildIntent(context: Context, request: LaunchRequest, packageNameOverride: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(
                packageNameOverride ?: emulator.packageName,
                "me.magnum.melonds.ui.emulator.EmulatorActivity",
            )
            data = request.game.romUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    override fun storeUrl(): String? = "https://github.com/rafaelvcaetano/melonDS-android/releases"
}
