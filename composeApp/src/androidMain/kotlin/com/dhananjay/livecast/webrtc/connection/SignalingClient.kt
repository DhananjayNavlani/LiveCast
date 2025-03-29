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
import android.util.Log
import com.dhananjay.livecast.cast.data.model.Ice
import com.dhananjay.livecast.cast.data.model.LiveCastUser
import com.dhananjay.livecast.cast.data.model.OfferAnswer
import com.dhananjay.livecast.webrtc.peer.StreamPeerType
import com.dhananjay.livecast.webrtc.session.ICE_SEPARATOR
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
        MutableSharedFlow<Triple<SignalingCommand, String, LiveCastUser?>>(replay = 1)
    val signalingCommandFlow: SharedFlow<Triple<SignalingCommand, String, LiveCastUser?>> = _signalingCommandFlow

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
                        val doc = change.document.toObject<OfferAnswer>()
                        Log.d(TAG, "The snapshot changes are ${change.type} && ${doc.id} && ${doc.isCallActive} && ${doc.sdp.length} && ${doc.isOffer} && ${doc.timestamp} ")
                        when(change.type){
                            DocumentChange.Type.ADDED -> {
                                callId = if(Timestamp.now().seconds - doc.timestamp.seconds < 10) doc.id else null
                                callId?.let {
                                    callDoc = firestore.collection("calls").document(it)

                                    callDoc!!.get().addOnSuccessListener {
                                        it.toObject(OfferAnswer::class.java)?.let {
                                            if (it.isOffer) {
                                                signalingScope.launch {
                                                    val user = firestore.collection("users").document(doc.viewerUserId?:"").get().await().toObject<LiveCastUser>()
                                                    _signalingCommandFlow.emit(Triple(SignalingCommand.OFFER, it.sdp, user))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            DocumentChange.Type.MODIFIED -> {
                                if(!doc.isCallActive){
                                    signalingScope.launch {
                                        _signalingCommandFlow.emit(Triple(SignalingCommand.DISCONNECT, "", null))
                                    }
                                }
                            }
                            else -> {
                            }
                        }

                    }

                }


        }
    }



    fun sendCommand(
        signalingCommand: SignalingCommand,
        message: String,
        type: StreamPeerType = StreamPeerType.PUBLISHER,
        viewerUserId: String? = null
    ) {
        when (signalingCommand) {
            SignalingCommand.STATE -> {

            }

            SignalingCommand.OFFER -> {
                callDoc = firestore.collection("calls").document()

                callDoc!!.set(
                    OfferAnswer(
                        sdp = message,
                        isOffer = true,
                        viewerUserId = viewerUserId,
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
                            signalingScope.launch {
                                _signalingCommandFlow.emit(Triple(SignalingCommand.ANSWER, it.sdp, null))
                            }
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
                                signalingScope. launch {
                                    _signalingCommandFlow.emit(Triple(SignalingCommand.ICE, it.toString(), null))
                                }
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
                                        signalingScope.launch {
                                            _signalingCommandFlow.emit(Triple(SignalingCommand.ICE, it.toString(), null))
                                        }
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

            SignalingCommand.DISCONNECT -> {

            }
        }
    }

    fun dispose() {

    }

    fun disconnectCall(){
        signalingScope.launch {
            callDoc?.set(
                mapOf("is_call_active" to false),
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
    ICE, // to send and receive ice candidates
    DISCONNECT, // to disconnect the call
}
