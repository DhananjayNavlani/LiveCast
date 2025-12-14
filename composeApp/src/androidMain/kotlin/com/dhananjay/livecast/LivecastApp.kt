package com.dhananjay.livecast

import android.app.Application
import android.app.NotificationManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.dhananjay.livecast.auth.authModule
import com.dhananjay.livecast.cast.data.RemoteDataSource
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.cast.utils.NotificationHelper
import com.dhananjay.livecast.di.appModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class LivecastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(appModule, authModule)
            androidContext(this@LivecastApp)
            androidLogger()
            workManagerFactory()
        }

        getKoin().get<NotificationHelper>().createNotificationChannel(
            channelId = Constants.CHANNEL_ID_APP,
            importance = NotificationManager.IMPORTANCE_HIGH,
            channelName = R.string.notification_channel_name,
            channelDesc = R.string.notification_channel_desc
        )

    }


}