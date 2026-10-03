package com.ginny.frontend.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ginny.frontend.GinnyApplication
import com.ginny.frontend.di.AppContainer

/**
 * El [AppContainer] de la app (repositorios, lanzadores…) desde cualquier composable. Se usa para
 * crear ViewModels: `viewModel(factory = viewModelFactory { initializer { XViewModel(appContainer()) } })`.
 */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as GinnyApplication).container
