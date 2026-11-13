package com.dhananjay.livecast.client

import android.app.Application
import com.dhananjay.livecast.auth.authModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LiveCastClientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LiveCastClientApp)
            modules(authModule)
        }
    }
}
