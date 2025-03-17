package com.dhananjay.livecast

import android.app.Application
import androidx.work.Configuration
import com.dhananjay.livecast.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class LivecastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(appModule)
            androidContext(this@LivecastApp)
            androidLogger()
            workManagerFactory()
        }
    }

}