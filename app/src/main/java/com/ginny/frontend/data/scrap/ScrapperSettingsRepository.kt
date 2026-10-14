package com.ginny.frontend.data.scrap

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Nombre del DataStore. Si cambia, hay que actualizar también `res/xml/backup_rules.xml` y
 * `res/xml/data_extraction_rules.xml`: ahí se excluye el archivo `<nombre>.preferences_pb` de las
 * copias de seguridad.
 */
private const val SCRAPPER_DATASTORE_NAME = "ginny_scrapper"

private val Context.scrapperDataStore by preferencesDataStore(name = SCRAPPER_DATASTORE_NAME)

/**
 * Configuración del scrapper: credenciales de cada proveedor y qué se descarga.
 *
 * Vive en un DataStore **aparte** de [com.ginny.frontend.data.SettingsRepository] a propósito:
 * `GinnyConfigRepository` espeja la configuración a `ginny.cfg`, un texto plano en la Carpeta de
 * Usuario que se sincroniza entre dispositivos, y las credenciales no deben terminar ahí **salvo que el usuario
 * lo pida** ([saveCredentialsInConfig], desactivado por defecto, con aviso). El archivo de este DataStore está
 * excluido de las copias de seguridad.
 *
 * Los valores se guardan tal como se escriben (sin recortar espacios) para que el campo de
 * Configuración muestre exactamente lo que escribió el usuario; quien los use contra una API debe
 * hacer `trim()`.
 */
class ScrapperSettingsRepository(private val context: Context) {

    private object Keys {
        val SCREENSCRAPER_USER = stringPreferencesKey("screenscraper_user")
        val SCREENSCRAPER_PASSWORD = stringPreferencesKey("screenscraper_password")
        val STEAMGRIDDB_API_KEY = stringPreferencesKey("steamgriddb_api_key")
        val ENABLED_MEDIA = stringSetPreferencesKey("enabled_media")
        val ONLY_ON_WIFI = booleanPreferencesKey("only_on_wifi")
        val SAVE_CREDENTIALS_IN_CONFIG = booleanPreferencesKey("save_credentials_in_config")
    }

    // ── ScreenScraper ─────────────────────────────────────────────────────────────────────

    val screenScraperUser: Flow<String> = context.scrapperDataStore.data.map { it[Keys.SCREENSCRAPER_USER].orEmpty() }
    val screenScraperPassword: Flow<String> = context.scrapperDataStore.data.map { it[Keys.SCREENSCRAPER_PASSWORD].orEmpty() }

    // Las credenciales de desarrollador (devid/devpassword) no están aquí: van incrustadas en el APK,
    // ver [ScrapperDevCredentials].

    suspend fun setScreenScraperUser(value: String) = set(Keys.SCREENSCRAPER_USER, value)
    suspend fun setScreenScraperPassword(value: String) = set(Keys.SCREENSCRAPER_PASSWORD, value)

    // ── SteamGridDB ───────────────────────────────────────────────────────────────────────

    val steamGridDbApiKey: Flow<String> = context.scrapperDataStore.data.map { it[Keys.STEAMGRIDDB_API_KEY].orEmpty() }

    suspend fun setSteamGridDbApiKey(value: String) = set(Keys.STEAMGRIDDB_API_KEY, value)

    /**
     * Guardar las credenciales (ScreenScraper y SteamGridDB) también en `ginny.cfg`, en texto plano, para compartirlas con
     * Ginny Frontend Desktop. **Desactivado por defecto**: la Carpeta de Usuario se sincroniza y a veces se comparte.
     * La bandera misma sí viaja en `ginny.cfg`, para que otro dispositivo sepa que las credenciales están ahí.
     */
    val saveCredentialsInConfig: Flow<Boolean> = context.scrapperDataStore.data.map { it[Keys.SAVE_CREDENTIALS_IN_CONFIG] ?: false }

    suspend fun setSaveCredentialsInConfig(value: Boolean) {
        context.scrapperDataStore.edit { it[Keys.SAVE_CREDENTIALS_IN_CONFIG] = value }
    }

    // ── Qué se descarga ───────────────────────────────────────────────────────────────────

    /** Lo que el usuario eligió descargar; mientras no haya elegido nada, los valores por defecto. */
    val enabledMedia: Flow<Set<ScrapMedia>> = context.scrapperDataStore.data.map { prefs ->
        prefs[Keys.ENABLED_MEDIA]
            ?.mapNotNull { name -> runCatching { ScrapMedia.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: defaultMedia()
    }

    suspend fun setMediaEnabled(media: ScrapMedia, enabled: Boolean) {
        context.scrapperDataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_MEDIA]?.toSet() ?: defaultMedia().map { it.name }.toSet()
            prefs[Keys.ENABLED_MEDIA] = if (enabled) current + media.name else current - media.name
        }
    }

    /** Descargar solo con Wi-Fi (no con datos móviles): los videos y las cajas pesan. Activo por defecto. */
    val onlyOnWifi: Flow<Boolean> = context.scrapperDataStore.data.map { it[Keys.ONLY_ON_WIFI] ?: true }

    suspend fun setOnlyOnWifi(value: Boolean) {
        context.scrapperDataStore.edit { it[Keys.ONLY_ON_WIFI] = value }
    }

    private fun defaultMedia(): Set<ScrapMedia> = ScrapMedia.entries.filter { it.defaultEnabled }.toSet()

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        context.scrapperDataStore.edit { prefs ->
            if (value.isEmpty()) prefs.remove(key) else prefs[key] = value
        }
    }
}
