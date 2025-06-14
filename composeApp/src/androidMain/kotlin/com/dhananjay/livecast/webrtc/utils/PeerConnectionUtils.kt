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

package com.dhananjay.livecast.webrtc.utils

import org.webrtc.AddIceObserver
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun PeerConnection.addRtcIceCandidate(iceCandidate: IceCandidate): Result<Unit> {
  return suspendCoroutine { cont ->
    addIceCandidate(
      iceCandidate,
      object : AddIceObserver {
        override fun onAddSuccess() {
          cont.resume(Result.success(Unit))
        }

        override fun onAddFailure(error: String?) {
          cont.resume(Result.failure(RuntimeException(error)))
        }
      }
    )
  }
}
