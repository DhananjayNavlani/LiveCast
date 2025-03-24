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

package com.dhananjay.livecast.webrtc.connection

import android.os.Build
import android.util.Log
import com.dhananjay.livecast.cast.model.DeviceOnline
import com.dhananjay.livecast.cast.model.Ice
import com.dhananjay.livecast.cast.model.OfferAnswer
import com.dhananjay.livecast.webrtc.peer.StreamPeerType
import com.dhananjay.livecast.webrtc.session.ICE_SEPARATOR
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.coroutineContext

//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.WebSocket
//import okhttp3.WebSocketListener

class SignalingClient(
    private val firestore: FirebaseFirestore
) {
    private val TAG = javaClass.simpleName
    private val signalingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

//  private val client = OkHttpClient()
//  private val request = Request
//    .Builder()
//    .url(BuildConfig.SIGNALING_SERVER_IP_ADDRESS)
//    .build()

    // opening web socket with signaling server
//  private val ws = client.newWebSocket(request, SignalingWebSocketListener())

    private var callDoc: DocumentReference? = null
    private val offerCandidates get() = callDoc?.collection("offerCandidates")
    private val answerCandidates get() = callDoc?.collection("answerCandidates")
    private var callId: String? = null

    private val deviceId by lazy {
        "${Build.FINGERPRINT}_${Build.DEVICE}_${Build.MANUFACTURER}".hashCode()
    }
    private val deviceName by lazy {
        "${Build.DEVICE}_${Build.MANUFACTURER}_${Build.MODEL}"
    }


    // signaling commands to send commands to value pairs to the subscribers
    private val _signalingCommandFlow = MutableSharedFlow<Pair<SignalingCommand, String>>(replay = 10, extraBufferCapacity = 20)
    val signalingCommandFlow: SharedFlow<Pair<SignalingCommand, String>> = _signalingCommandFlow

    init {
        signalingScope.launch {
            firestore.collection("calls")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e(TAG,"Error fetching offer: $error" )
                        return@addSnapshotListener
                    }

                    snapshot.documentChanges.forEach { change ->
                        Log.d(TAG, "The snapshot changes are ${change.document.id} type = ${change.type}, isOffer ${change.document.get("offer")} ")
                        if (change.type == DocumentChange.Type.ADDED) {
                            callId = change.document.id
                        }
                    }
                    callId?.let {
                        callDoc = firestore.collection("calls").document(it)

                        callDoc!!.get().addOnSuccessListener {
                            it.toObject(OfferAnswer::class.java)?.let {
                                if (it.isOffer) {
                                    Log.d(TAG, "Got offer with $callId: & ${it.timestamp}")
                                     _signalingCommandFlow.tryEmit(SignalingCommand.OFFER to it.sdp)
                                }
                            }
                        }
                    }
                }


        }
    }

    val devicesOnline = callbackFlow<DeviceOnline> {
        val listener = firestore.collection("rooms").document("online")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e(TAG,"Error fetching offer: $error" )
                    return@addSnapshotListener
                }

                snapshot.toObject(DeviceOnline::class.java)?.let {
                    trySend(it)
                }
            }
        awaitClose { listener.remove() }
    }


    suspend fun addDeviceOnline(): Boolean {
        return try {
            firestore.collection("rooms").document("online").let {
                val deviceOnline = it.get().await().takeIf { it.exists() }?.toObject<DeviceOnline>()
                val hasDevice = deviceOnline?.devices?.contains(deviceId) ?: false

                Log.d(TAG, "addDeviceOnline: The collection has device ? $deviceOnline")
                if(!hasDevice){
                    var count = deviceOnline?.count?.coerceAtLeast(0) ?: 0
                    it.set(
                        hashMapOf(
                            "count" to ++count,
                            "names" to FieldValue.arrayUnion(deviceName),
                            "devices" to FieldValue.arrayUnion(deviceId)
                        ),
                        SetOptions.merge()
                    ).await()
                }

            }

            true
        }catch (e: Exception){
            coroutineContext.ensureActive()
            Log.e(TAG,"Error adding device online: $e" )
            false
        }

    }
    suspend fun removeDeviceOnline(): Boolean {
        return try {
            firestore.collection("rooms").document("online").let {
                val deviceOnline = it.get().await().takeIf { it.exists() }?.toObject<DeviceOnline>()
                val hasDevice = deviceOnline?.devices?.contains(deviceId) ?: false

                Log.d(TAG, "removeDeviceOnline: The collection has device ? $deviceOnline  && hasDevice $hasDevice")
                if (hasDevice) {
                    var count = deviceOnline?.count?.coerceAtLeast(1) ?: 1
                    it.set(
                        hashMapOf(
                            "count" to --count,
                            "names" to FieldValue.arrayRemove(deviceName),
                            "devices" to FieldValue.arrayRemove(deviceId)
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }
            true
        }catch (e: Exception){
            coroutineContext.ensureActive()
            Log.e(TAG,"Error removing device online: $e" )
            false
        }

    }

    fun sendCommand(
        signalingCommand: SignalingCommand,
        message: String,
        type: StreamPeerType = StreamPeerType.PUBLISHER
    ) {
        Log.d(TAG,"[sendCommand] $signalingCommand $type" )
//    ws.send("$signalingCommand $message")
        when (signalingCommand) {
            SignalingCommand.STATE -> {

            }

            SignalingCommand.OFFER -> {
                callDoc = firestore.collection("calls").document()

                callDoc!!.set(
                    OfferAnswer(
                        sdp = message,
                        isOffer = true
                    )
                )

                //listen for remote answer
                callDoc!!.addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e(TAG,"Error fetching offer: $error" )
                        return@addSnapshotListener
                    }

                    snapshot.toObject(OfferAnswer::class.java)?.let {
                        if (!it.isOffer) {
                            _signalingCommandFlow.tryEmit(SignalingCommand.ANSWER to it.sdp)
                        }
                    }
                }

                //listen for remote ice candidates
                answerCandidates!!.addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e(TAG,"Error fetching offer: $error" )
                        return@addSnapshotListener
                    }

                    snapshot.documentChanges.forEach { change ->
                        if (change.type == DocumentChange.Type.ADDED) {
                            change.document.toObject(Ice::class.java).let {
                                _signalingCommandFlow.tryEmit(SignalingCommand.ICE to it.toString())
                            }
                        }
                    }
                }


            }

            SignalingCommand.ANSWER -> {
                signalingScope.launch {

                    callId?.let {

                        callDoc!!.set(
                            OfferAnswer(
                                sdp = message,
                                isOffer = false
                            ),
                            SetOptions.merge()
                        ).await()


                        offerCandidates?.addSnapshotListener { snapshot, error ->

                            if (error != null || snapshot == null) {
                                Log.e(TAG,"Error fetching offer: $error" )
                                return@addSnapshotListener
                            }

                            snapshot.documentChanges.forEach { change ->
                                if (change.type == DocumentChange.Type.ADDED) {
                                    change.document.toObject(Ice::class.java).let {
                                        _signalingCommandFlow.tryEmit(SignalingCommand.ICE to it.toString())
                                    }
                                }
                            }
                        }


                    }
                }

            }

            SignalingCommand.ICE -> {
                val (mid, index, sdp) = message.split(ICE_SEPARATOR)
                val ice = Ice(
                    sdpMid = mid,
                    sdpMLineIndex = index.toInt(),
                    candidate = sdp
                )
                signalingScope.launch {
                    when (type) {
                        StreamPeerType.PUBLISHER -> {
                            answerCandidates?.add(ice)?.await()
                        }

                        StreamPeerType.SUBSCRIBER -> {
                            offerCandidates?.add(ice)?.await()
                        }
                    }

                }
            }
        }
    }

//  private inner class SignalingWebSocketListener : WebSocketListener() {
//    override fun onMessage(webSocket: WebSocket, text: String) {
//      when {
//        text.startsWith(SignalingCommand.STATE.toString(), true) ->
//          handleStateMessage(text)
//        text.startsWith(SignalingCommand.OFFER.toString(), true) ->
//          handleSignalingCommand(SignalingCommand.OFFER, text)
//        text.startsWith(SignalingCommand.ANSWER.toString(), true) ->
//          handleSignalingCommand(SignalingCommand.ANSWER, text)
//        text.startsWith(SignalingCommand.ICE.toString(), true) ->
//          handleSignalingCommand(SignalingCommand.ICE, text)
//      }
//    }
//  }

    private fun handleStateMessage(message: String) {
        val state = getSeparatedMessage(message)
//        _sessionStateFlow.value = WebRTCSessionState.valueOf(state)
    }

    private fun handleSignalingCommand(command: SignalingCommand, text: String) {
        val value = getSeparatedMessage(text)
        Log.d(TAG,"received signaling: $command $value" )
        signalingScope.launch {
            _signalingCommandFlow.emit(command to value)
        }
    }

    private fun getSeparatedMessage(text: String) = text.substringAfter(' ')

    fun dispose() {
//        _sessionStateFlow.value = WebRTCSessionState.Offline
//    ws.cancel()
    }

    suspend fun testRead() {
        firestore.collection("rooms").document("online")
            .let {
                it.get().addOnSuccessListener{
                    Log.d(TAG, "testRead: The success listener ${it.id}")
                }.addOnFailureListener{
                    Log.d(TAG, "testRead: The failure listener ${it.message}")
                }
                val id = it.get().await().id
                Log.d(TAG, "testRead: Getting await data -> $id")
            }
    }

    fun testWrite() {
        firestore.collection("rooms").document("test").let {
            it.set(
                hashMapOf(
                    "count" to 1,
                    "devices" to listOf(deviceId)
                )
            ).addOnSuccessListener {
                Log.d(TAG, "testWrite: The success listener ${it}")
            }.addOnFailureListener {
                Log.d(TAG, "testWrite: The failure listener ${it.message}")
            }

            Log.d(TAG, "testWrite: Before await()")
           it.set(
                hashMapOf(
                    "count" to 2,
                    "devices" to listOf(deviceId)
                ),
                SetOptions.merge()
            )
            Log.d(TAG, "testWrite: After await()")


        }

    }

}

enum class WebRTCSessionState {
    Active, // Offer and Answer messages has been sent
    Creating, // Creating session, offer has been sent
    Ready, // Both clients available and ready to initiate session
    Impossible, // We have less than two clients connected to the server
    Offline // unable to connect signaling server
}

enum class SignalingCommand {
    STATE, // Command for WebRTCSessionState
    OFFER, // to send or receive offer
    ANSWER, // to send or receive answer
    ICE // to send and receive ice candidates
}
