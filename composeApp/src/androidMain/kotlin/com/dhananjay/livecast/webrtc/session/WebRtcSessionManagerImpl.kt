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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.core.content.getSystemService
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.data.repositories.PreferencesRepository
import com.dhananjay.livecast.cast.data.services.ScreenSharingService
import com.dhananjay.livecast.cast.ui.video.CallAction
import com.dhananjay.livecast.cast.ui.video.GestureType
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Capturer
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerationAndroid
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.util.UUID

const val ICE_SEPARATOR = '$'

val LocalWebRtcSessionManager: ProvidableCompositionLocal<WebRtcSessionManager> =
    staticCompositionLocalOf { error("WebRtcSessionManager was not initialized!") }

class WebRtcSessionManagerImpl(
    private val context: Context,
    override val signalingClient: SignalingClient,
    override val peerConnectionFactory: StreamPeerConnectionFactory,
    private val preferencesRepository: PreferencesRepository,
) : WebRtcSessionManager {

    companion object{
        private val _keyEventFlow = MutableSharedFlow<Triple<GestureType,Offset,Offset?>>()
        val keyEventFlow = _keyEventFlow.asSharedFlow()

        private val _callActionFlow = MutableSharedFlow<CallAction>()
        val callActionFlow = _callActionFlow.asSharedFlow()
    }
    private val TAG = javaClass.simpleName
    private val sessionManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // used to send local video track to the fragment
    private val _localVideoTrackFlow = MutableSharedFlow<VideoTrack>()
    override val localVideoTrackFlow: SharedFlow<VideoTrack> = _localVideoTrackFlow

    // used to send remote video track to the sender
    private val _remoteVideoTrackFlow = MutableSharedFlow<VideoTrack>()
    override val remoteVideoTrackFlow: SharedFlow<VideoTrack> = _remoteVideoTrackFlow

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

    override var isSubscriber: Boolean = false
    var remoteUser: LiveCastUser? = null
    private suspend fun getUser(): LiveCastUser? {
        return preferencesRepository.getUser().firstOrNull()
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

    private lateinit var dataChannel: DataChannel
    private lateinit var videoCapturer: VideoCapturer
    private val videoSource by lazy {
        if(!::videoCapturer.isInitialized) error("VideoCapturer was not initialized!")
        Log.d(TAG, "The videoCap is : ${videoCapturer.isScreencast}")
        peerConnectionFactory.makeVideoSource(videoCapturer.isScreencast).apply {
            videoCapturer.initialize(surfaceTextureHelper, context, this.capturerObserver)
            val displayMetrics = context.resources.displayMetrics
            videoCapturer.startCapture(displayMetrics.widthPixels, displayMetrics.heightPixels, 30)
        }
    }

    private val localVideoTrack: VideoTrack by lazy {
        peerConnectionFactory.makeVideoTrack(
            source = videoSource,
            trackId = "Video${UUID.randomUUID()}"
        )
    }

    private val widthPixels by lazy {
        context.resources.displayMetrics.widthPixels
    }
    private val heightPixels by lazy {
        context.resources.displayMetrics.heightPixels
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

    private val peerConnection: StreamPeerConnection by lazy {
        peerConnectionFactory.makePeerConnection(
            coroutineScope = sessionManagerScope,
            configuration = peerConnectionFactory.rtcConfig,
            type = StreamPeerType.SUBSCRIBER,
            mediaConstraints = mediaConstraints,
            onIceCandidateRequest = { iceCandidate, type ->
                Log.d(TAG, "The iceCandidate type: $type is offer null ? ${offer == null} ")
                signalingClient.sendCommand(
                    SignalingCommand.ICE,
                    "${iceCandidate.sdpMid}$ICE_SEPARATOR${iceCandidate.sdpMLineIndex}$ICE_SEPARATOR${iceCandidate.sdp}",
                    if(offer == null) {
                        StreamPeerType.SUBSCRIBER
                    } else {
                        StreamPeerType.PUBLISHER
                    }
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
            },
            onDataChannel = {
                Log.d(TAG, "onDataChannel: ${it} is subscriber ? $isSubscriber")
                if (!isSubscriber){
                    dataChannel = it
                    dataChannel.registerObserver(object : DataChannel.Observer {
                        override fun onBufferedAmountChange(p0: Long) {
                            Log.d(TAG, "onBufferedAmountChange: $p0")
                        }

                        override fun onStateChange() {
                            Log.d(TAG, "onStateChange: ${dataChannel.state()} => ${it.id()}")
                        }

                        override fun onMessage(buffer: DataChannel.Buffer) {
                            val data = buffer.data
                            val byteArray = ByteArray(data.remaining())
                            val scaleX =
                                remoteUser!!.widthPixels.toFloat() / widthPixels
                            val scaleY = remoteUser!!.heightPixels.toFloat() / heightPixels
                            data.get(byteArray)

                            val result = String(byteArray, Charsets.UTF_8).split(" ")
                            Log.d(TAG, "onMessage: The result is $result")
                                result
                                .takeIf { it.size == 5 }?.let {
                                Log.d(TAG, "onMessage: ${it} is ${it.get(3).isBlank()}")
                                sessionManagerScope.launch {
                                    _keyEventFlow.emit(
                                        Triple(
                                            GestureType.valueOf(it.last()),
                                            Offset(it[0].toFloat() * scaleX, it[1].toFloat() * scaleY),
                                            if(it[2].isNotBlank() && it[3].isNotBlank()) {
                                                Offset(it[2].toFloat() * scaleX, it[3].toFloat() * scaleY)
                                            } else null
                                        ))

                                }
                            }

                            result.takeIf { it.size == 1 }?.first().let {
                                sessionManagerScope.launch {
                                    when(it){
                                        CallAction.GoBack.toString() -> {
                                            _callActionFlow.emit(CallAction.GoBack)
                                        }
                                        CallAction.Home.toString() -> {
                                            _callActionFlow.emit(CallAction.Home)
                                        }
                                        CallAction.GoToRecent.toString() -> {
                                            _callActionFlow.emit(CallAction.GoToRecent)
                                        }
                                        CallAction.UnlockDevice.toString() ->{
                                            _callActionFlow.emit(CallAction.UnlockDevice)
                                        }
                                        else -> {
                                            Log.d(TAG, "onMessage: The call action is not valid")
                                        }
                                    }

                                }
                            }


                        }
                    })
                }

            }
        )
    }

    init {
        sessionManagerScope.launch {
            signalingClient.signalingCommandFlow
                .collect { commandToValue ->
                    when (commandToValue.first) {
                        SignalingCommand.OFFER -> handleOffer(commandToValue.second, commandToValue.third)
                        SignalingCommand.ANSWER -> handleAnswer(commandToValue.second)
                        SignalingCommand.ICE -> handleIce(commandToValue.second)
                        SignalingCommand.DISCONNECT -> {
                            if (!isSubscriber) handleDisconnect()
                        }
                        else -> Unit
                    }
                }
        }

/*        sessionManagerScope.launch {
            //capture the stats from peerConnection
            peerConnection.getStats().collectLatest {
                Log.d(TAG, "$it")
            }
        }*/
    }

    override fun onSessionScreenReady(isSubscriber: Boolean) {
//    setupAudio()
//    peerConnection.connection.addTrack(localAudioTrack)
        this.isSubscriber = isSubscriber
        dataChannel = peerConnection.connection.createDataChannel(Constants.DATA_CHANNEL_KEY, DataChannel.Init().apply {
        } )
        sessionManagerScope.launch {
            // sending local video track to show local video from start
            if (isSubscriber) {
                sendOffer(getUser()?.uid ?: "")
            } else {
                if(offer != null){
                    peerConnection.connection.addTrack(localVideoTrack)
                    _localVideoTrackFlow.emit(localVideoTrack)
                    sendAnswer()
                }
            }
        }
    }

    override fun sendEvent(start: Offset, gestureType: GestureType, end: Offset?) {
        if(!isSubscriber){
            Log.d(TAG, "sendEvent: not a subscriber")
            return
        }
        dataChannel.send(
            DataChannel.Buffer(
                ByteBuffer.wrap("${start.x} ${start.y} ${end?.x ?: ""} ${end?.y ?: ""} $gestureType".toByteArray(Charsets.UTF_8)),
                false
            )
        )
    }

    override fun sendEvent(callAction: CallAction) {
        if(!isSubscriber){
            Log.d(TAG, "sendEvent: not a subscriber")
            return
        }
        dataChannel.send(
            DataChannel.Buffer(
                ByteBuffer.wrap(callAction.toString().toByteArray(Charsets.UTF_8)),
                false
            )
        )
    }


    override fun unlockDevice() {
        sendEvent(CallAction.UnlockDevice)
    }

    override fun goBack() {
        sendEvent(CallAction.GoBack)
    }

    override fun goHome() {
        sendEvent(CallAction.Home)
    }

    override fun goToRecent() {
        sendEvent(CallAction.GoToRecent)
    }

    override fun disconnect() {
        // dispose audio & video tracks.
        remoteVideoTrackFlow.replayCache.forEach { videoTrack ->
            videoTrack.dispose()
        }
        localVideoTrackFlow.replayCache.forEach { videoTrack ->
            videoTrack.dispose()
        }
        Log.d(TAG, "disconnect: as Subscriber ? $isSubscriber && offer null? ${offer == null} ")
        if(!isSubscriber) {
            if(!localVideoTrack.isDisposed) {
                localVideoTrack.dispose()
                videoCapturer.stopCapture()
                videoCapturer.dispose()
            }
            Intent(context, ScreenSharingService::class.java).apply {
                action = Constants.ACTION_STOP_SCREEN_SHARING
            }.also {
                context.startService(it)
            }
        } else{
            signalingClient.disconnectCall()
        }


        signalingClient.dispose()
    }

    private suspend fun sendOffer(uid: String) {
        offer = null
        val offer = peerConnection.createOffer().getOrThrow()
        val result = peerConnection.setLocalDescription(offer)
        result.onSuccess {
            signalingClient.sendCommand(SignalingCommand.OFFER, offer.description, viewerUserId = uid)
        }
        Log.d(TAG,"[SDP] send offer: ${offer.type}" )
    }

    private suspend fun sendAnswer() {
        peerConnection.setRemoteDescription(
            SessionDescription(SessionDescription.Type.OFFER, offer)
        )
        val answer = peerConnection.createAnswer().getOrThrow()
        val result = peerConnection.setLocalDescription(answer)
        result.onSuccess {
            signalingClient.sendCommand(SignalingCommand.ANSWER, answer.description)
        }
        Log.d(TAG,"[SDP] send answer: ${answer.type}" )
    }

    private fun handleOffer(sdp: String, remoteUser: LiveCastUser?) {
        Log.d(TAG,"[SDP] handle offer: ${sdp.length}" )
        offer = sdp
        this.remoteUser = remoteUser
    }

    private suspend fun handleAnswer(sdp: String) {
        Log.d(TAG,"[SDP] handle answer: " )
        peerConnection.setRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    private suspend fun handleIce(iceMessage: String) {
        Log.d(TAG, "handleIce: with message: ${iceMessage.length}")
        val iceArray = iceMessage.split(ICE_SEPARATOR)
        peerConnection.addIceCandidate(
            IceCandidate(
                iceArray[0],
                iceArray[1].toInt(),
                iceArray[2]
            )
        )
    }

    private fun handleDisconnect(){
        disconnect()
    }

    override fun handleScreenSharing(data: Intent) {
        videoCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
            }
        })

        /*
            if (sender.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                peerConnection.connection.removeTrack(sender)
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
        Log.d(TAG,"[setupAudio] #sfu; no args" )
//    audioHandler.start()
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager?.availableCommunicationDevices ?: return
            val deviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

            val device = devices.firstOrNull { it.type == deviceType } ?: return

            val isCommunicationDeviceSet = audioManager?.setCommunicationDevice(device)
            Log.d(TAG,"[setupAudio] #sfu; isCommunicationDeviceSet: $isCommunicationDeviceSet" )
        }
    }
}
