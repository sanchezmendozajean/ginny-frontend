package com.ginny.frontend.data.scrap

import com.ginny.frontend.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP compartido por los clientes de ScreenScraper y SteamGridDB.
 *
 * Sin interceptor de log, a propósito: las URLs de ScreenScraper llevan credenciales. Tiempos de
 * espera holgados: una consulta a ScreenScraper tarda ~4-5 s en el servidor y un video, bastante más.
 */
object ScrapHttp {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "GinnyFrontend/${BuildConfig.VERSION_NAME} (Android)")
                        .build(),
                )
            }
            .build()
    }

    /**
     * Ejecuta [request] y entrega la respuesta a [handle], que debe leer el cuerpo ahí dentro (se
     * cierra al terminar). Corre en `Dispatchers.IO` y se cancela con la corrutina: cancelar aborta
     * la petición, también a mitad de una descarga.
     *
     * Un fallo de red se devuelve como [ScrapResult.Unavailable] (sin credenciales en el texto);
     * la cancelación de la corrutina se propaga como siempre.
     */
    suspend fun <T> execute(
        provider: ScrapProvider,
        request: Request,
        handle: (Response) -> ScrapResult<T>,
    ): ScrapResult<T> = withContext(Dispatchers.IO) {
        val call = client.newCall(request)
        coroutineScope {
            // La lectura de OkHttp bloquea el hilo y no mira si la corrutina se canceló: este
            // observador, en otro hilo, aborta la llamada cuando eso pasa. Si la petición ya terminó
            // (caso normal), call.cancel() no hace nada.
            val watcher = launch {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                call.execute().use(handle)
            } catch (e: IOException) {
                // Si la causa fue la cancelación, esto lanza CancellationException en vez de
                // devolver un fallo de red.
                ensureActive()
                ScrapResult.Unavailable(provider, ScrapRedactor.redact(e.message ?: e.javaClass.simpleName))
            } finally {
                watcher.cancel()
            }
        }
    }
}
