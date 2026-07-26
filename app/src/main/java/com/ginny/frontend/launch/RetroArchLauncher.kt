package com.ginny.frontend.launch

import android.content.Context
import android.content.Intent
import com.ginny.frontend.data.model.EmulatorTarget

/**
 * Launches RetroArch via its documented explicit-activity + extras scheme (the same approach used
 * by other third-party Android frontends): ROM path/Uri + LIBRETRO core .so name as intent extras
 * targeting RetroActivityFuture directly. One instance per [EmulatorTarget.RETROARCH_VARIANTS]
 * entry, since the universal/aarch64/ra32 builds are separate installable packages that otherwise
 * behave identically.
 */
class RetroArchLauncher(override val emulator: EmulatorTarget = EmulatorTarget.RETROARCH) : LauncherStrategy {

    override fun buildIntent(context: Context, request: LaunchRequest, packageNameOverride: String?): Intent {
        val corePath = request.retroArchCoreSoFileName
            ?: error("RetroArch requiere un core seleccionado para ${request.game.systemId}")

        return Intent(Intent.ACTION_VIEW).apply {
            setClassName(
                packageNameOverride ?: emulator.packageName,
                "com.retroarch.browser.retroactivity.RetroActivityFuture",
            )
            setDataAndType(request.game.romUri, "application/octet-stream")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("ROM", request.game.romUri.toString())
            putExtra("LIBRETRO", "/data/data/${emulator.packageName}/cores/$corePath")
            putExtra("CONFIGFILE", "")
            putExtra("QUITFOCUS", false)
            // NOTE: RetroArch's Android intent scheme does not have a confirmed, documented extra
            // for forcing a specific save-state slot on boot. STATE_SLOT is included as a best-effort
            // attempt and may be ignored depending on the installed RetroArch build — verify manually
            // (see README, "Estados guardados").
            request.saveStateSlot?.let { putExtra("STATE_SLOT", it) }
        }
    }

    override fun storeUrl(): String? = "https://www.retroarch.com/index.php?page=platforms"
}
