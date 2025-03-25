package com.dhananjay.livecast.cast.ui.navigation

import kotlinx.serialization.Serializable

sealed class Routes {
    @Serializable
    object StageScreen

    @Serializable
    object LoginScreen

}