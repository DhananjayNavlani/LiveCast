
package com.dhananjay.livecast.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation routes for the LiveCast app.
 * These routes are shared across all platforms.
 */
sealed class Routes {
    @Serializable
    data object LandingScreen : Routes()
    
    @Serializable
    data object FeaturesScreen : Routes()
    
    @Serializable
    data object LoginScreen : Routes()
    
    @Serializable
    data object StageScreen : Routes()
}
