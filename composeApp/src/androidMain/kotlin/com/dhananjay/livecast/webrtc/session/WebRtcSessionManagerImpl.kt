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

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.getSystemService
import com.dhananjay.livecast.cast.data.services.ScreenSharingService
import com.dhananjay.livecast.cast.model.OfferAnswer
import com.dhananjay.livecast.cast.utils.Constants
import com.dhananjay.livecast.webrtc.connection.SignalingClient
import com.dhananjay.livecast.webrtc.connection.SignalingCommand
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnection
import com.dhananjay.livecast.webrtc.peer.StreamPeerConnectionFactory
import com.dhananjay.livecast.webrtc.peer.StreamPeerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.UUID

const val ICE_SEPARATOR = '$'

val LocalWebRtcSessionManager: ProvidableCompositionLocal<WebRtcSessionManager> =
    compositionLocalOf { error("WebRtcSessionManager was not initialized!") }

enum class RTCSessionState {
    Offline,
    Impossible,
    Ready,
    Creating,
    Active
}

class WebRtcSessionManagerImpl(
    private val context: Context,
    override val signalingClient: SignalingClient,
    override val peerConnectionFactory: StreamPeerConnectionFactory,
) : WebRtcSessionManager {
    private val TAG = javaClass.simpleName
    private val sessionManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // used to send local video track to the fragment
    private val _localVideoTrackFlow = MutableSharedFlow<VideoTrack>()
    override val localVideoTrackFlow: SharedFlow<VideoTrack> = _localVideoTrackFlow

    // used to send remote video track to the sender
    private val _remoteVideoTrackFlow = MutableSharedFlow<VideoTrack>()
    override val remoteVideoTrackFlow: SharedFlow<VideoTrack> = _remoteVideoTrackFlow

    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var pendingAnswer: SessionDescription? = null

    // declaring video constraints and setting OfferToReceiveVideo to true
    // this step is mandatory to create valid offer and answer
    private val mediaConstraints = MediaConstraints().apply {
        mandatory.addAll(
            listOf(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"),
                MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
            )
        )
    }

    // getting front camera
    /*    private val videoCapturer: VideoCapturer by lazy {
            buildCameraCapturer()
        }*/
    private val cameraManager by lazy { context.getSystemService<CameraManager>() }
    private val cameraEnumerator: Camera2Enumerator by lazy {
        Camera2Enumerator(context)
    }

    private val resolution: CameraEnumerationAndroid.CaptureFormat
        get() {
            val frontCamera = cameraEnumerator.deviceNames.first { cameraName ->
                cameraEnumerator.isFrontFacing(cameraName)
            }
            val supportedFormats = cameraEnumerator.getSupportedFormats(frontCamera) ?: emptyList()
            return supportedFormats.firstOrNull {
                (it.width == 720 || it.width == 480 || it.width == 360)
            } ?: error("There is no matched resolution!")
        }

    // we need it to initialize video capturer
    private val surfaceTextureHelper = SurfaceTextureHelper.create(
        "SurfaceTextureHelperThread",
        peerConnectionFactory.eglBaseContext
    )

    private lateinit var videoCapturer: VideoCapturer
    private lateinit var videoSource: VideoSource

    private lateinit var localVideoTrack: VideoTrack

    private fun createVideoTrack(): VideoTrack {
        return peerConnectionFactory.makeVideoTrack(
            source = videoSource,
            trackId = "Video${UUID.randomUUID()}"
        )
    }
    private fun createVideoSource(): VideoSource {
        return peerConnectionFactory.makeVideoSource(videoCapturer.isScreencast).apply {
            videoCapturer.initialize(surfaceTextureHelper, context, this.capturerObserver)
            val displayMetrics = context.resources.displayMetrics
            videoCapturer.startCapture(displayMetrics.widthPixels, displayMetrics.heightPixels, 30)
        }
    }

    /** Audio properties */

//  private val audioHandler: AudioHandler by lazy {
//    AudioSwitchHandler(context)
//  }

    private val audioManager by lazy {
        context.getSystemService<AudioManager>()
    }

    private val audioConstraints: MediaConstraints by lazy {
        buildAudioConstraints()
    }

    private val audioSource by lazy {
        peerConnectionFactory.makeAudioSource(audioConstraints)
    }

    private val localAudioTrack: AudioTrack by lazy {
        peerConnectionFactory.makeAudioTrack(
            source = audioSource,
            trackId = "Audio${UUID.randomUUID()}"
        )
    }

    private var offer: String? = null

    private lateinit var peerConnection: StreamPeerConnection
    init {
        sessionManagerScope.launch {
            signalingClient.signalingCommandFlow
                .collect { commandToValue ->
                    when (commandToValue.first) {
                        SignalingCommand.OFFER -> handleOffer(commandToValue.second)
                        SignalingCommand.ANSWER -> handleAnswer(commandToValue.second)
                        SignalingCommand.ICE -> handleIce(commandToValue.second)
                        else -> Unit
                    }
                }
        }

    }

    private fun createPeerConnection() = peerConnectionFactory.makePeerConnection(
        coroutineScope = sessionManagerScope,
        configuration = peerConnectionFactory.rtcConfig,
        type = StreamPeerType.SUBSCRIBER,
        mediaConstraints = mediaConstraints,
        onIceCandidateRequest = { iceCandidate, type ->
            Log.d(TAG, "The iceCandidate type: $type is ${iceCandidate.serverUrl}")
            signalingClient.sendCommand(
                SignalingCommand.ICE,
                "${iceCandidate.sdpMid}$ICE_SEPARATOR${iceCandidate.sdpMLineIndex}$ICE_SEPARATOR${iceCandidate.sdp}",
                type
            )
        },
        onVideoTrack = { rtpTransceiver ->
            val track = rtpTransceiver?.receiver?.track() ?: return@makePeerConnection
            if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                val videoTrack = track as VideoTrack
                sessionManagerScope.launch {
                    Log.d(TAG, "Getting video track : ${videoTrack.id()}")
                    _remoteVideoTrackFlow.emit(videoTrack)
                }
            }
        }
    )

    override fun onSessionScreenReady(isSubscriber: Boolean) {
//    setupAudio()
//    peerConnection.connection.addTrack(localAudioTrack)
        peerConnection = createPeerConnection()
        sessionManagerScope.launch {
            pendingAnswer?.let {
                peerConnection.setRemoteDescription(it)
                pendingAnswer = null
            }
            pendingIceCandidates.takeUnless { it.isEmpty() }?.forEach { iceCandidate ->
                peerConnection.addIceCandidate(iceCandidate)
            }.also {
                pendingIceCandidates.clear()
            }

        }
        sessionManagerScope.launch {
            // sending local video track to show local video from start

            Log.d(
                TAG,
                "onSessionScreenReady: The isSubscriber is $isSubscriber && has offer ? ${!offer.isNullOrBlank()}"
            )
            if (isSubscriber) {
                sendOffer()
            } else {
                if (!::localVideoTrack.isInitialized || localVideoTrack.isDisposed) {
                    localVideoTrack = createVideoTrack()
                }
                peerConnection.connection.addTrack(localVideoTrack)
                _localVideoTrackFlow.emit(localVideoTrack)
                sendAnswer()
            }
        }
    }

    override fun flipCamera() {
        (videoCapturer as? Camera2Capturer)?.switchCamera(null)
    }

    override fun enableMicrophone(enabled: Boolean) {
        audioManager?.isMicrophoneMute = !enabled
    }

    override fun enableCamera(enabled: Boolean) {
        if (enabled) {
            videoCapturer.startCapture(resolution.width, resolution.height, 30)
        } else {
            videoCapturer.stopCapture()
        }
    }

    override fun disconnect(isSubscriber: Boolean) {
        // dispose audio & video tracks.
        remoteVideoTrackFlow.replayCache.forEach { videoTrack ->
            videoTrack.dispose()
        }
        localVideoTrackFlow.replayCache.forEach { videoTrack ->
            videoTrack.dispose()
        }
//        localAudioTrack.dispose()

        try {
            if (!isSubscriber) {
                Intent(context, ScreenSharingService::class.java).apply {
                    action = Constants.ACTION_STOP_SCREEN_SHARING
                }.also {
                    context.startForegroundService(it)
                }
                peerConnection.connection.senders.forEach {
                    if (it.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                        peerConnection.connection.removeTrack(it)
                    }
                }
                videoCapturer.stopCapture()
                videoCapturer.dispose()
                localVideoTrack.dispose()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while stopping video capture: ${e.message}")
        }

        // dispose audio handler and video capturer.
//    audioHandler.stop()

        // dispose signaling clients and socket.

        signalingClient.dispose()
    }

    private suspend fun sendOffer() {
        val offer = peerConnection.createOffer().getOrThrow()
        val result = peerConnection.setLocalDescription(offer)
        Log.d(TAG, "[SDP] send offer: ${offer.type}")
        result.onSuccess {
            signalingClient.sendCommand(SignalingCommand.OFFER, offer.description)
        }
    }

    private suspend fun sendAnswer() {
        peerConnection.setRemoteDescription(
            SessionDescription(SessionDescription.Type.OFFER, offer)
        )
        val answer = peerConnection.createAnswer().getOrThrow()
        val result = peerConnection.setLocalDescription(answer)
        Log.d(TAG, "[SDP] send answer ${answer.type}")
        result.onSuccess {
            signalingClient.sendCommand(SignalingCommand.ANSWER, answer.description)
        }
    }

    private fun handleOffer(sdp: String) {
        Log.d(TAG, "[SDP] handle offer: from signaling server")
        offer = sdp
    }

    private suspend fun handleAnswer(sdp: String) {
        Log.d(TAG, "[SDP] handle answer: from signaling server")
        if(!::peerConnection.isInitialized){
            pendingAnswer = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            return
        }
        peerConnection.setRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    private suspend fun handleIce(iceMessage: String) {
        Log.d(TAG, "handleIce: with message: ${iceMessage.length}")
        val iceArray = iceMessage.split(ICE_SEPARATOR)
        if (!::peerConnection.isInitialized) {
            pendingIceCandidates.add(
                IceCandidate(
                    iceArray[0],
                    iceArray[1].toInt(),
                    iceArray[2]
                )
            )
            return
        }
        peerConnection.addIceCandidate(
            IceCandidate(
                iceArray[0],
                iceArray[1].toInt(),
                iceArray[2]
            )
        )
    }

    override fun handleScreenSharing(data: Intent) {

        videoCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
            }
        })
        Log.d(TAG, "handleScreenSharing: The videoCapturer is : ${videoCapturer}")

        videoSource = createVideoSource()

        /*      .apply {
                    val source = peerConnectionFactory.makeVideoSource(true)
                    initialize(surfaceTextureHelper, context, source.capturerObserver)
                }


                // Get screen dimensions
                val displayMetrics = context.resources.displayMetrics
                videoCapturer.startCapture(displayMetrics.widthPixels, displayMetrics.heightPixels, 30)

                // Create and add video track
                val screenVideoTrack = peerConnectionFactory.makeVideoTrack(
                    source = videoSource,
                    trackId = "ScreenVideo${UUID.randomUUID()}"
                )

                // Update the local video track
                sessionManagerScope.launch {
                    _localVideoTrackFlow.emit(screenVideoTrack)
                }
                peerConnection.connection.senders.forEach { sender ->
            if (sender.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                peerConnection.connection.removeTrack(sender)
            }
        }
        peerConnection.connection.addTrack(localVideoTrack)

        */

    }

    private fun buildCameraCapturer(): VideoCapturer {
        val manager = cameraManager ?: throw RuntimeException("CameraManager was not initialized!")

        val ids = manager.cameraIdList
        var foundCamera = false
        var cameraId = ""

        for (id in ids) {
            val characteristics = manager.getCameraCharacteristics(id)
            val cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            if (cameraLensFacing == CameraMetadata.LENS_FACING_FRONT) {
                foundCamera = true
                cameraId = id
            }
        }

        if (!foundCamera && ids.isNotEmpty()) {
            cameraId = ids.first()
        }

        val camera2Capturer = Camera2Capturer(context, cameraId, null)
        return camera2Capturer
    }

    private fun buildAudioConstraints(): MediaConstraints {
        val mediaConstraints = MediaConstraints()
        val items = listOf(
            MediaConstraints.KeyValuePair(
                "googEchoCancellation",
                true.toString()
            ),
            MediaConstraints.KeyValuePair(
                "googAutoGainControl",
                true.toString()
            ),
            MediaConstraints.KeyValuePair(
                "googHighpassFilter",
                true.toString()
            ),
            MediaConstraints.KeyValuePair(
                "googNoiseSuppression",
                true.toString()
            ),
            MediaConstraints.KeyValuePair(
                "googTypingNoiseDetection",
                true.toString()
            )
        )

        return mediaConstraints.apply {
            with(optional) {
                add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
                addAll(items)
            }
        }
    }

    private fun setupAudio() {
        Log.d(TAG, "[setupAudio] #sfu; no args")
//    audioHandler.start()
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager?.availableCommunicationDevices ?: return
            val deviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

            val device = devices.firstOrNull { it.type == deviceType } ?: return

            val isCommunicationDeviceSet = audioManager?.setCommunicationDevice(device)
            Log.d(TAG, "[setupAudio] #sfu; isCommunicationDeviceSet: $isCommunicationDeviceSet")
        }
    }
}
