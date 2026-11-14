package com.dhananjay.livecast.client.check

import io.ktor.client.HttpClient

expect fun platformHttpClient(): HttpClient
