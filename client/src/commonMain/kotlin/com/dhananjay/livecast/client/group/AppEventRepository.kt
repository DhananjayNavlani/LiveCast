package com.dhananjay.livecast.client.group

import com.dhananjay.livecast.protocol.AppEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AppEventRepository {
    fun observeRecent(deviceId: String, groupId: String, limit: Int = 50): Flow<List<AppEvent>>
}

class UnsupportedAppEventRepository(
    private val message: String = "App events are not available on this platform yet"
) : AppEventRepository {
    override fun observeRecent(deviceId: String, groupId: String, limit: Int): Flow<List<AppEvent>> = flow {
        throw UnsupportedOperationException(message)
    }
}
