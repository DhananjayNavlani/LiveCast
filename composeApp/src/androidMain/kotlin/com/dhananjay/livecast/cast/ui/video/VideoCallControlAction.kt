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

package com.dhananjay.livecast.cast.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.dhananjay.livecast.R
import com.dhananjay.livecast.cast.ui.style.Disabled
import com.dhananjay.livecast.cast.ui.style.Primary

sealed class CallAction {


  data object UnlockDevice : CallAction()

  data object GoToRecent : CallAction()
  data object Home : CallAction()
  data object GoBack : CallAction()

  data object LeaveCall : CallAction()
}

data class VideoCallControlAction(
  val icon: Painter,
  val iconTint: Color,
  val background: Color,
  val callAction: CallAction
)

@Composable
fun buildDefaultCallControlActions(
  callMediaState: CallMediaState
): List<VideoCallControlAction> {
  val microphoneIcon =
    painterResource(
      id = if (callMediaState.isMicrophoneEnabled) {
        R.drawable.ic_mic_on
      } else {
        R.drawable.ic_mic_off
      }
    )

  val cameraIcon = painterResource(
    id = if (callMediaState.isCameraEnabled) {
      R.drawable.ic_videocam_on
    } else {
      R.drawable.ic_videocam_off
    }
  )

  return listOf(

    VideoCallControlAction(
      icon = painterResource(id = R.drawable.ic_power),
      iconTint = Color.White,
      background = Primary,
      callAction = CallAction.UnlockDevice
    ),
    VideoCallControlAction(
      icon = painterResource(id = R.drawable.ic_back),
      iconTint = Color.White,
      background = Primary,
      callAction = CallAction.GoBack
    ),
    VideoCallControlAction(
      icon = painterResource(id = R.drawable.ic_home),
      iconTint = Color.White,
      background = Primary,
      callAction = CallAction.Home
    ),
    VideoCallControlAction(
      icon = painterResource(id = R.drawable.ic_recent),
      iconTint = Color.White,
      background = Primary,
      callAction = CallAction.GoToRecent),

    VideoCallControlAction(
      icon = painterResource(id = R.drawable.ic_call_end),
      iconTint = Color.White,
      background = Disabled,
      callAction = CallAction.LeaveCall
    )
  )
}
