package com.ginny.frontend.data

import com.ginny.frontend.data.model.ButtonLayout
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.util.SafUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

private const val CONFIG_FILE_NAME = "ginny.cfg"
// Deliberately not "text/plain": SAF providers (e.g. the on-device ExternalStorageProvider) look up
// the canonical extension for the given mime type via MimeTypeMap and force-append it to the display
// name when it doesn't already end with it — "text/plain" maps to ".txt", so a plain createFile with
// this name would silently become "ginny.cfg.txt". "application/octet-stream" has no canonical
// extension registered, so the name is kept exactly as given.
private const val CONFIG_MIME_TYPE = "application/octet-stream"
private const val CONFIG_VERSION = 1
private const val AUTO_SYNC_DEBOUNCE_MS = 500L

/**
 * Mirrors every app setting except the "Carpeta de Usuario" location itself (that Uri is what
 * locates this file, so it can never live inside it) to a plain-text JSON file, `ginny.cfg`, at
 * the root of the user-chosen folder — see [SettingsRepository] for the DataStore that remains the
 * live, per-device source of truth. This lets a fresh install (or a second device pointed at the
 * same folder) pick up emulators/cores per system, button mapping, grid rows and volumes without
 * reconfiguring by hand.
 *
 * [ensureConfigFile] is the single entry point: call it both at app start (if a user folder is
 * already configured) and right after the user (re)picks one. It reads and applies an existing
 * file, or creates one from the current settings if none exists yet. Afterwards, [requestSync]
 * (wired into every settings mutation in SettingsViewModel) keeps the file up to date; writes are
 * debounced so dragging a volume slider doesn't hammer the SAF provider. [flushNow] bypasses
 * that debounce — see its doc for why that matters.
 */
class GinnyConfigRepository(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Starts the debounced background writer. Call once, with an app-lifetime [scope]. */
    fun startAutoSync(scope: CoroutineScope) {
        scope.launch {
            syncRequests.debounce(AUTO_SYNC_DEBOUNCE_MS).collect { writeCurrentConfig() }
        }
    }

    /** Queues a debounced rewrite of `ginny.cfg` reflecting the current settings. No-ops quietly
     * if no user folder is configured yet or the write fails (e.g. permission revoked). */
    fun requestSync() {
        syncRequests.tryEmit(Unit)
    }

    /** Writes `ginny.cfg` immediately, bypassing [AUTO_SYNC_DEBOUNCE_MS]. Call this whenever the
     * app leaves the foreground (e.g. `ProcessLifecycleOwner` ON_STOP), since a device shutdown
     * kills the process with no guarantee the debounced writer from [requestSync] got to run —
     * without this, a setting changed right before power-off can revert to whatever `ginny.cfg`
     * last had once [ensureConfigFile] re-applies it on the next launch. */
    fun flushNow(scope: CoroutineScope) {
        scope.launch { writeCurrentConfig() }
    }

    /**
     * Reads `ginny.cfg` from [userTreeUri] and applies it to [settings] if present, or creates it
     * from the current settings if this folder doesn't have one yet. Call at app start (when a
     * user folder is already saved) and right after the folder is (re)selected.
     */
    suspend fun ensureConfigFile(userTreeUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = SafUtils.treeDocument(context, userTreeUri)
                ?: throw IOException("No se pudo abrir la carpeta de Usuario")
            val existing = findConfigFile(root)
            if (existing != null) {
                readAndApply(existing)
            } else {
                writeConfigTo(root)
            }
        }
    }

    private suspend fun writeCurrentConfig(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userUri = settings.userFolderUri.first()?.let(Uri::parse) ?: return@runCatching
            val root = SafUtils.treeDocument(context, userUri)
                ?: throw IOException("No se pudo abrir la carpeta de Usuario")
            writeConfigTo(root)
        }
    }

    private suspend fun writeConfigTo(root: DocumentFile) {
        val json = buildConfigJson()
        val file = findConfigFile(root)
            ?: root.createFile(CONFIG_MIME_TYPE, CONFIG_FILE_NAME)
            ?: throw IOException("No se pudo crear $CONFIG_FILE_NAME")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
            out.write(json.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IOException("No se pudo escribir $CONFIG_FILE_NAME")
    }

    /**
     * Finds the single `ginny.cfg` file at the root of the user folder, exact name match only.
     * Also sweeps away any stray "ginny.cfg*" siblings — e.g. "ginny.cfg.txt" or "ginny.cfg (1).txt"
     * — left behind by a previous run that used a mime type ("text/plain") the SAF provider force-
     * appended an extension onto, which stopped the exact-name lookup from ever finding them again
     * and made every sync create a brand new copy instead of overwriting one.
     */
    private fun findConfigFile(root: DocumentFile): DocumentFile? {
        val candidates = root.listFiles().filter { it.isFile && it.name?.startsWith(CONFIG_FILE_NAME, ignoreCase = true) == true }
        val exact = candidates.firstOrNull { it.name.equals(CONFIG_FILE_NAME, ignoreCase = true) }
        candidates.filter { it.uri != exact?.uri }.forEach { it.delete() }
        return exact
    }

    private suspend fun buildConfigJson(): JSONObject {
        val buttonMapping = settings.buttonMapping.first()
        val buttonsJson = JSONObject().apply {
            LogicalButton.values().forEach { button -> put(button.name, buttonMapping.keyCodeFor(button)) }
        }

        val systemsJson = JSONObject().apply {
            for (system in SystemCatalog.all) {
                val systemJson = JSONObject().apply {
                    put("emulator", settings.emulatorFor(system.id).first().name)
                    settings.retroArchCoreFor(system.id).first()?.let { put("core", it) }
                    settings.packageOverrideFor(system.id).first()?.let { put("packageOverride", it) }
                    settings.romFolderOverrideFor(system.id).first()?.let { put("romFolderUri", it) }
                }
                put(system.id.name, systemJson)
            }
        }

        return JSONObject().apply {
            put("version", CONFIG_VERSION)
            put("gridRows", settings.gridRows.first())
            put("buttonLayout", settings.buttonLayout.first().name)
            put("swapDisplays", settings.swapDisplays.first())
            put("musicVolume", settings.musicVolume.first().toDouble())
            put("uiSoundVolume", settings.uiSoundVolume.first().toDouble())
            put("videoVolume", settings.videoVolume.first().toDouble())
            put("backgroundMusicTrack", settings.backgroundMusicTrack.first() ?: JSONObject.NULL)
            put("romFolderUri", settings.romFolderUri.first() ?: JSONObject.NULL)
            put("retroArchSavesUri", settings.retroArchSavesUri.first() ?: JSONObject.NULL)
            put("onboardingDone", settings.onboardingDone.first())
            put("lastScanAt", settings.lastScanAt.first() ?: JSONObject.NULL)
            put("buttonMapping", buttonsJson)
            put("systems", systemsJson)
        }
    }

    private suspend fun readAndApply(configFile: DocumentFile) {
        val text = context.contentResolver.openInputStream(configFile.uri)?.use { it.reader(Charsets.UTF_8).readText() }
            ?: throw IOException("No se pudo leer $CONFIG_FILE_NAME")
        val json = JSONObject(text)

        json.optInt("gridRows", -1).takeIf { it in 1..4 }?.let { settings.setGridRows(it) }
        json.optStringOrNull("buttonLayout")
            ?.let { name -> runCatching { ButtonLayout.valueOf(name) }.getOrNull() }
            ?.let { settings.setButtonLayout(it) }
        if (json.has("swapDisplays")) settings.setSwapDisplays(json.optBoolean("swapDisplays", false))
        if (json.has("musicVolume")) settings.setMusicVolume(json.optDouble("musicVolume", 0.5).toFloat())
        if (json.has("uiSoundVolume")) settings.setUiSoundVolume(json.optDouble("uiSoundVolume", 0.7).toFloat())
        if (json.has("videoVolume")) settings.setVideoVolume(json.optDouble("videoVolume", 0.5).toFloat())
        json.optStringOrNull("backgroundMusicTrack")?.let { settings.setBackgroundMusicTrack(it) }
        json.optStringOrNull("romFolderUri")?.let { settings.setRomFolderUri(it) }
        json.optStringOrNull("retroArchSavesUri")?.let { settings.setRetroArchSavesUri(it) }
        if (json.has("onboardingDone")) settings.setOnboardingDone(json.optBoolean("onboardingDone", false))
        if (json.has("lastScanAt") && !json.isNull("lastScanAt")) settings.setLastScanAt(json.optLong("lastScanAt"))

        json.optJSONObject("buttonMapping")?.let { buttonsJson ->
            LogicalButton.values().forEach { button ->
                if (buttonsJson.has(button.name)) settings.setButtonKeyCode(button, buttonsJson.optInt(button.name, button.defaultKeyCode))
            }
        }

        json.optJSONObject("systems")?.let { systemsJson ->
            for (system in SystemCatalog.all) {
                val systemJson = systemsJson.optJSONObject(system.id.name) ?: continue
                systemJson.optStringOrNull("emulator")
                    ?.let { name -> runCatching { EmulatorTarget.valueOf(name) }.getOrNull() }
                    ?.let { settings.setEmulatorFor(system.id, it) }
                systemJson.optStringOrNull("core")?.let { settings.setRetroArchCoreFor(system.id, it) }
                systemJson.optStringOrNull("packageOverride")?.let { settings.setPackageOverride(system.id, it) }
                systemJson.optStringOrNull("romFolderUri")?.let { settings.setRomFolderOverride(system.id, it) }
                systemJson.optStringOrNull("romFolderUri")?.let { settings.setRomFolderOverride(system.id, it) }
            }
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
}
