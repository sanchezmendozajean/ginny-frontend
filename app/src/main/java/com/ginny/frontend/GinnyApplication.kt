package com.ginny.frontend

import android.app.Application
import com.ginny.frontend.di.AppContainer

class GinnyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
