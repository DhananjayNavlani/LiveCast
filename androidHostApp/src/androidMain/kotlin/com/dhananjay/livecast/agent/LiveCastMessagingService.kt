package com.dhananjay.livecast.agent

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

class LiveCastMessagingService : FirebaseMessagingService(), KoinComponent {

    private val tokenRegistry by inject<PushTokenRegistry>()
    private val checkResponder by inject<CheckResponder>()
    private val appScope by inject<CoroutineScope>(named("appScope"))

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        appScope.launch {
            tokenRegistry.register(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val checkId = CheckMessageParser.checkIdOf(message.data) ?: return
        runBlocking {
            checkResponder.respond(checkId)
        }
    }
}
