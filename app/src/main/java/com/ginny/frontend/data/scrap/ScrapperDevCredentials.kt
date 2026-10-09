package com.ginny.frontend.data.scrap

import com.ginny.frontend.BuildConfig

/**
 * Credenciales de **desarrollador** de Ginny ante ScreenScraper (`devid` / `devpassword`): identifican
 * al programa, no al usuario, así que el usuario final no las escribe. Se incrustan en el APK al
 * compilar desde `scrapper.properties` (ignorado por git; ver `scrapper.properties.example`).
 *
 * No son secretas de verdad: cualquiera puede extraerlas de un APK. Es lo habitual con esta API (los
 * demás frontends hacen lo mismo) y ScreenScraper puede revocarlas y emitir otras.
 */
object ScrapperDevCredentials {
    val screenScraperDevId: String = BuildConfig.SCREENSCRAPER_DEV_ID
    val screenScraperDevPassword: String = BuildConfig.SCREENSCRAPER_DEV_PASSWORD

    /** false si se compiló sin `scrapper.properties`: ScreenScraper no puede usarse. */
    val screenScraperAvailable: Boolean
        get() = screenScraperDevId.isNotBlank() && screenScraperDevPassword.isNotBlank()
}
