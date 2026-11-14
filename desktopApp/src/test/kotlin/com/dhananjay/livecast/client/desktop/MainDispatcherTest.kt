package com.dhananjay.livecast.client.desktop

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ViewModels use viewModelScope, which runs on Dispatchers.Main. On the desktop JVM that dispatcher
 * only exists when kotlinx-coroutines-swing is on the runtime classpath; without it Koin fails to
 * create the first ViewModel and the app dies at start-up.
 */
class MainDispatcherTest {
    @Test
    fun mainDispatcherIsAvailable() {
        // Throws IllegalStateException("Module with the Main dispatcher is missing") when absent.
        Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext)
        val impl = Dispatchers.Main.javaClass.name
        assertTrue("Swing" in impl, "Main dispatcher implementation: $impl")
    }
}
