package com.ginny.frontend.data.scrap

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Decide si ahora se puede descargar, según la red y la opción "Solo con Wi-Fi". */
class ScrapNetwork(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /**
     * [ScrapResult.Ok] si se puede descargar; si no, [ScrapResult.NetworkBlocked] con el motivo.
     * Con [onlyOnWifi] solo se acepta una red **sin tarifa** (Wi-Fi, Ethernet...): es más fiable que
     * mirar el tipo de transporte, porque una red de datos compartida por VPN sigue siendo de datos.
     */
    fun check(onlyOnWifi: Boolean): ScrapResult<Unit> {
        val network = connectivity?.activeNetwork
        val caps = network?.let { connectivity.getNetworkCapabilities(it) }
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return ScrapResult.NetworkBlocked("No hay conexión a internet.")
        }
        if (onlyOnWifi && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            return ScrapResult.NetworkBlocked("Conéctate a una red Wi-Fi, o desactiva \"Solo con Wi-Fi\" en Configuración > Scrapper.")
        }
        return ScrapResult.Ok(Unit)
    }
}
