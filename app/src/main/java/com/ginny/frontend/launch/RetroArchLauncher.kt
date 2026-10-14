package com.ginny.frontend.launch

import android.content.Context
import android.content.Intent
import android.os.Environment
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.util.SafUtils

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

        // RetroArch is granted broad/legacy filesystem access and reads a plain absolute path
        // instantly — but handing it our SAF content:// Uri instead forces it to resolve a
        // cross-process Uri-permission grant synchronously during its cold start, which can stall
        // for 5+ seconds and trip Android's "Input dispatching timed out" ANR (confirmed via
        // logcat: `ANR in .../RetroActivityFuture ... Waited 5001ms for KeyEvent` on first launch),
        // even though the exact same ROM opens instantly when browsed manually inside RetroArch.
        // Resolve the real path whenever the ROM sits on local/removable storage and use that
        // instead; only fall back to the content Uri for SAF providers with no real path (e.g. a
        // cloud-backed provider), which is the one case that genuinely needs the Uri grant.
        val realRomPath = SafUtils.realPathOrNull(request.game.romUri)
        val targetPackage = packageNameOverride ?: emulator.packageName

        return Intent(Intent.ACTION_VIEW).apply {
            setClassName(targetPackage, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            if (realRomPath != null) {
                setType("application/octet-stream")
            } else {
                setDataAndType(request.game.romUri, "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("ROM", realRomPath ?: request.game.romUri.toString())
            putExtra("LIBRETRO", "/data/data/$targetPackage/cores/$corePath")
            // Launching RetroActivityFuture directly (rather than through RetroArch's own
            // MainMenuActivity, which normally bootstraps its global config on cold start) means an
            // empty CONFIGFILE loads no config at all — confirmed on-device: RetroArch starts with
            // "Config directory not set, cannot save configuration" and the user's controller
            // autoconfig (hotkey, "Odin Controller" profile) silently unconfigured. Pointing this at
            // RetroArch's own default config path (Android's stable per-app external-files
            // convention: `getExternalFilesDir(null)`, which we can't call for another app's Context
            // but can compute the same way Android does) makes it load the user's real
            // retroarch.cfg — directories, hotkeys and autoconfig included — same as a normal launch.
            putExtra(
                "CONFIGFILE",
                "${Environment.getExternalStorageDirectory().path}/Android/data/$targetPackage/files/retroarch.cfg",
            )
            putExtra("QUITFOCUS", false)
        }
    }

    override fun storeUrl(): String? = "https://www.retroarch.com/index.php?page=platforms"
}
