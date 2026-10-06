package com.ginny.frontend.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ginny.frontend.data.model.EmulatorTarget
import com.ginny.frontend.data.model.GameEntry
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.data.model.favoriteKey
import com.ginny.frontend.data.model.ButtonLayout
import com.ginny.frontend.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ginny_settings")

/** App-wide user preferences, persisted with Jetpack DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val USER_FOLDER_URI = stringPreferencesKey("user_folder_uri")
        val ROM_FOLDER_URI = stringPreferencesKey("rom_folder_uri")
        val RETROARCH_SAVES_URI = stringPreferencesKey("retroarch_saves_uri")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val MUSIC_TRACK = stringPreferencesKey("background_music_track")
        val UI_SOUND_VOLUME = floatPreferencesKey("ui_sound_volume")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FAVORITE_GAMES = stringSetPreferencesKey("favorite_games")
        val BUTTON_LAYOUT = stringPreferencesKey("button_layout")

        fun emulatorFor(systemId: SystemId) = stringPreferencesKey("emulator_${systemId.name}")
        fun coreFor(systemId: SystemId) = stringPreferencesKey("core_${systemId.name}")
        fun buttonFor(button: LogicalButton) = intPreferencesKey("button_${button.name}")
        fun packageOverrideFor(systemId: SystemId) = stringPreferencesKey("package_override_${systemId.name}")
        fun romFolderFor(systemId: SystemId) = stringPreferencesKey("rom_folder_${systemId.name}")
    }

    val gridRows: Flow<Int> = context.dataStore.data.map { it[Keys.GRID_ROWS]?.coerceIn(1, 4) ?: 2 }

    val userFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.USER_FOLDER_URI] }
    val romFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.ROM_FOLDER_URI] }
    val retroArchSavesUri: Flow<String?> = context.dataStore.data.map { it[Keys.RETROARCH_SAVES_URI] }

    /**
     * Carpeta de ROMs propia de un sistema (URI de SAF), elegida en Configuración > Sistemas.
     * null = la de siempre: `<carpeta de ROMs>/<SystemId.folderName>`.
     */
    fun romFolderOverrideFor(systemId: SystemId): Flow<String?> =
        context.dataStore.data.map { it[Keys.romFolderFor(systemId)] }

    /** Todas las carpetas propias, por sistema (solo los que tienen una). Para el escaneo completo. */
    val romFolderOverrides: Flow<Map<SystemId, String>> = context.dataStore.data.map { prefs ->
        SystemId.entries.mapNotNull { id -> prefs[Keys.romFolderFor(id)]?.let { id to it } }.toMap()
    }

    suspend fun setRomFolderOverride(systemId: SystemId, uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.romFolderFor(systemId)) else prefs[Keys.romFolderFor(systemId)] = uri
        }
    }

    /** Manual package-name override for systems with more than one real-world app/fork (see
     * [com.ginny.frontend.data.model.SystemDefinition.supportsPackageOverride]). */
    fun packageOverrideFor(systemId: SystemId): Flow<String?> =
        context.dataStore.data.map { it[Keys.packageOverrideFor(systemId)] }

    suspend fun setPackageOverride(systemId: SystemId, packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) {
                prefs.remove(Keys.packageOverrideFor(systemId))
            } else {
                prefs[Keys.packageOverrideFor(systemId)] = packageName
            }
        }
    }

    val musicVolume: Flow<Float> = context.dataStore.data.map { it[Keys.MUSIC_VOLUME] ?: 0.5f }
    val uiSoundVolume: Flow<Float> = context.dataStore.data.map { it[Keys.UI_SOUND_VOLUME] ?: 0.7f }

    /** Encoded [com.ginny.frontend.data.model.MusicSelection] — null means "use the default bundled track". */
    val backgroundMusicTrack: Flow<String?> = context.dataStore.data.map { it[Keys.MUSIC_TRACK] }

    suspend fun setBackgroundMusicTrack(encoded: String) {
        context.dataStore.edit { it[Keys.MUSIC_TRACK] = encoded }
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /** Timestamp (epoch millis) of the last completed "Escanear biblioteca" run, null if never run. */
    val lastScanAt: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_SCAN_AT] }

    suspend fun setLastScanAt(epochMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_SCAN_AT] = epochMillis }
    }

    /** Distribución de los botones frontales: solo afecta los íconos de la leyenda. */
    val buttonLayout: Flow<ButtonLayout> = context.dataStore.data.map { prefs ->
        prefs[Keys.BUTTON_LAYOUT]?.let { name -> runCatching { ButtonLayout.valueOf(name) }.getOrNull() }
            ?: ButtonLayout.YX_BA
    }

    suspend fun setButtonLayout(layout: ButtonLayout) {
        context.dataStore.edit { it[Keys.BUTTON_LAYOUT] = layout.name }
    }

    suspend fun setGridRows(rows: Int) {
        context.dataStore.edit { it[Keys.GRID_ROWS] = rows.coerceIn(1, 4) }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
            ?: ThemeMode.MENTA
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val favoriteGameKeys: Flow<Set<String>> = context.dataStore.data.map { it[Keys.FAVORITE_GAMES].orEmpty() }

    suspend fun setFavorite(game: GameEntry, favorite: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_GAMES].orEmpty()
            prefs[Keys.FAVORITE_GAMES] = if (favorite) current + game.favoriteKey() else current - game.favoriteKey()
        }
    }

    suspend fun setUserFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.USER_FOLDER_URI) else prefs[Keys.USER_FOLDER_URI] = uri
        }
    }

    suspend fun setRomFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.ROM_FOLDER_URI) else prefs[Keys.ROM_FOLDER_URI] = uri
        }
    }

    suspend fun setRetroArchSavesUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.RETROARCH_SAVES_URI) else prefs[Keys.RETROARCH_SAVES_URI] = uri
        }
    }

    suspend fun setMusicVolume(volume: Float) {
        context.dataStore.edit { it[Keys.MUSIC_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setUiSoundVolume(volume: Float) {
        context.dataStore.edit { it[Keys.UI_SOUND_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    fun emulatorFor(systemId: SystemId): Flow<EmulatorTarget> = context.dataStore.data.map { prefs ->
        val definition = SystemCatalog.byId.getValue(systemId)
        val stored = prefs[Keys.emulatorFor(systemId)]?.let { name ->
            runCatching { EmulatorTarget.valueOf(name) }.getOrNull()
        }
        stored?.takeIf { it in definition.availableEmulators } ?: definition.defaultEmulator
    }

    suspend fun setEmulatorFor(systemId: SystemId, emulator: EmulatorTarget) {
        context.dataStore.edit { it[Keys.emulatorFor(systemId)] = emulator.name }
    }

    fun retroArchCoreFor(systemId: SystemId): Flow<String?> = context.dataStore.data.map { prefs ->
        val definition = SystemCatalog.byId.getValue(systemId)
        val stored = prefs[Keys.coreFor(systemId)]
        val valid = definition.retroArchCores.any { it.id == stored }
        if (valid) stored else definition.defaultRetroArchCoreId
    }

    suspend fun setRetroArchCoreFor(systemId: SystemId, coreId: String) {
        context.dataStore.edit { it[Keys.coreFor(systemId)] = coreId }
    }

    val buttonMapping: Flow<ButtonMapping> = context.dataStore.data.map { prefs ->
        ButtonMapping(
            LogicalButton.values().associateWith { button ->
                prefs[Keys.buttonFor(button)] ?: button.defaultKeyCode
            }
        )
    }

    suspend fun setButtonKeyCode(button: LogicalButton, keyCode: Int) {
        context.dataStore.edit { it[Keys.buttonFor(button)] = keyCode }
    }

    suspend fun resetButtonMapping() {
        context.dataStore.edit { prefs ->
            LogicalButton.values().forEach { prefs.remove(Keys.buttonFor(it)) }
        }
    }

    suspend fun currentButtonMapping(): ButtonMapping = buttonMapping.first()
}
