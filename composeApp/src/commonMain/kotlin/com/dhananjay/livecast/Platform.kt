package com.dhananjay.livecast

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform