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

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.dhananjay.livecast.cast.data.services.AccessibilityService
import com.dhananjay.livecast.cast.data.services.ScreenSharingService
import com.dhananjay.livecast.cast.data.model.DeviceOnline
import com.dhananjay.livecast.cast.data.model.Ice
import com.dhananjay.livecast.cast.data.model.OfferAnswer
import com.dhananjay.livecast.cast.utils.Constants
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.coroutineContext

class SignalingClient(
    private val context: Context,
    private val firestore: FirebaseFirestore
) {
    private val TAG = javaClass.simpleName
    private val signalingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)


    private var callDoc: DocumentReference? = null
    private val offerCandidates get() = callDoc?.collection("offerCandidates")
    private val answerCandidates get() = callDoc?.collection("answerCandidates")
    private var callId: String? = null

    // signaling commands to send commands to value pairs to the subscribers
    private val _signalingCommandFlow =
        MutableSharedFlow<Pair<SignalingCommand, String>>(replay = 10, extraBufferCapacity = 100)
    val signalingCommandFlow: SharedFlow<Pair<SignalingCommand, String>> = _signalingCommandFlow

    init {
        signalingScope.launch {
            firestore.collection("calls")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Error fetching offer: $error")
                        return@addSnapshotListener
                    }

                    snapshot.documentChanges.forEach { change ->
                        Log.d(TAG, "The snapshot changes are ${change.type} && ${change.document.id} ")
                        val doc = change.document.toObject<OfferAnswer>()
                        when(change.type){
                            DocumentChange.Type.ADDED -> {
                                callId = doc.id
                            }
                            DocumentChange.Type.MODIFIED -> {
                                if(!doc.isCallActive){
                                    Intent(context, ScreenSharingService::class.java).apply {
                                        action = Constants.ACTION_STOP_SCREEN_SHARING
                                    }.also {
                                        context.startService(it)
                                    }
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                            }
                        }

                    }
                    callId?.let {
                        callDoc = firestore.collection("calls").document(it)

                        callDoc!!.get().addOnSuccessListener {
                            it.toObject(OfferAnswer::class.java)?.let {
                                if (it.isOffer) {
                                    _signalingCommandFlow.tryEmit(SignalingCommand.OFFER to it.sdp)
                                }
                            }
                        }
                    }
                }


        }
    }



    fun sendCommand(
        signalingCommand: SignalingCommand,
        message: String,
        type: StreamPeerType = StreamPeerType.PUBLISHER
    ) {
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
                        Log.e(TAG, "Error fetching offer: $error")
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
                        Log.e(TAG, "Error fetching offer: $error")
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
                                isOffer = false,
                                isCallActive = true
                            ),
                            SetOptions.merge()
                        ).await()


                        offerCandidates?.addSnapshotListener { snapshot, error ->

                            if (error != null || snapshot == null) {
                                Log.e(TAG, "Error fetching offer: $error")
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

    fun dispose() {

    }

    fun disconnectCall(){
        signalingScope.launch {
            callDoc?.set(
                OfferAnswer(
                    sdp = "",
                    isOffer = false,
                    isCallActive = false
                ),
                SetOptions.merge()
            )?.await()
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
