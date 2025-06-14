/*
 * Copyright 2023 Stream.IO, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dhananjay.livecast.webrtc.session

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.ui.video.CallAction
import com.dhananjay.livecast.cast.ui.video.GestureType
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnectionFactory
import kotlinx.coroutines.flow.SharedFlow
import org.webrtc.VideoTrack

interface WebRtcSessionManager {

    var isSubscriber: Boolean

    val signalingClient: SignalingClient

    val peerConnectionFactory: StreamPeerConnectionFactory

    val localVideoTrackFlow: SharedFlow<VideoTrack>

    val remoteVideoTrackFlow: SharedFlow<VideoTrack>

    fun onSessionScreenReady(isSubscriber: Boolean)

    fun sendEvent(start: Offset, gestureType: GestureType, end: Offset? = null)
    fun sendEvent(callAction: CallAction)
    fun disconnect()
    fun handleScreenSharing(data: Intent)
    fun unlockDevice()
    fun goBack()
    fun goToRecent()
    fun goHome()
}
