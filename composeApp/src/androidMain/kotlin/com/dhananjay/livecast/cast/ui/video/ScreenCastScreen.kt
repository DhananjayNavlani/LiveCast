package com.dhananjay.livecast.cast.ui.video

import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.cast.ui.components.VideoRenderer
import com.dhananjay.livecast.webrtc.session.LocalWebRtcSessionManager

enum class GestureType {
    TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    PRESS,
    DRAG_START,
    DRAG_END,
    DRAG_CANCEL,
    ZOOM,
    ROTATE,
    PINCH,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT
}
@Composable
fun ScreenCastScreen(
    isSubscriber: Boolean,
    modifier: Modifier = Modifier
) {
    val sessionManager = LocalWebRtcSessionManager.current
    val isSub by rememberUpdatedState(isSubscriber)

    LaunchedEffect(key1 = Unit) {
        sessionManager.onSessionScreenReady(isSub)
    }

    //log info about each pointer input event
    Box(
        modifier = modifier
            .fillMaxSize()
            // Tap Gestures: single tap, double tap, long press, and press
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        Log.d("GestureDetection", "Tap: $offset")
                        sessionManager.sendEvent(offset, GestureType.TAP)
                    },
                    onDoubleTap = { offset ->
                        Log.d("GestureDetection", "Double Tap: $offset")
                        sessionManager.sendEvent(offset, GestureType.DOUBLE_TAP)
                    },
                    onLongPress = { offset ->
                        Log.d("GestureDetection", "Long Press: $offset")
                        sessionManager.sendEvent(offset, GestureType.LONG_PRESS)
                    },
                    onPress = { offset ->
                        Log.d("GestureDetection", "Press: $offset")
                        sessionManager.sendEvent(offset, GestureType.PRESS)
//                        // This is a suspend function, so you can use it to delay or cancel the press
//                        try {
//                            // Simulate a long press
//                            awaitRelease()
//                            Log.d("GestureDetection", "Press released: $offset")
//                            sessionManager.sendEvent(offset, GestureType.PRESS)
//                        } catch (e: Exception) {
//                            Log.d("GestureDetection", "Press cancelled: $offset")
//                            sessionManager.sendEvent(offset, GestureType.DRAG_CANCEL)
//                        }
                    }
                )
            }
            // Drag Gestures: detect drag start, dragging, and end/cancel events
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        Log.d("GestureDetection", "Drag Start: $offset")
                        sessionManager.sendEvent(offset, GestureType.DRAG_START)
                    },
                    onDragEnd = {
                        Log.d("GestureDetection", "Drag End")
                        sessionManager.sendEvent(Offset.Zero, GestureType.DRAG_END)
                    },
                    onDragCancel = {
                        Log.d("GestureDetection", "Drag Cancel")
                        sessionManager.sendEvent(Offset.Zero, GestureType.DRAG_CANCEL)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = Offset(
                            x = change.position.x + dragAmount.x,
                            y = change.position.y + dragAmount.y
                        )
                        Log.d("GestureDetection", "Dragging: $newOffset")
                        sessionManager.sendEvent(newOffset, GestureType.DRAG_START)
                    }
                )
            }
            // Transform Gestures: detect multi-touch transforms (pinch, zoom, and rotation)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    Log.d("GestureDetection", "Transform: centroid=$centroid, pan=$pan, zoom=$zoom, rotation=$rotation")
                    sessionManager.sendEvent(centroid, GestureType.PINCH)
                    sessionManager.sendEvent(centroid, GestureType.ZOOM)
                    sessionManager.sendEvent(centroid, GestureType.ROTATE)
                }
            }
            .pointerInput (Unit){
                var start = Offset.Zero
                detectVerticalDragGestures(
                    onDragStart = {
                        start = it
                    }
                ) { change, dragAmount ->
                    val end = change.position
                    if(dragAmount > 0){
                        //swipe down
                        Log.d("GestureDetection", "Swipe Down: $dragAmount")
                        sessionManager.sendEvent(start, GestureType.SWIPE_DOWN, end)
                    } else{
                        //swipe up
                        Log.d("GestureDetection", "Swipe Up: $dragAmount")
                        sessionManager.sendEvent(start, GestureType.SWIPE_UP, end)
                    }
                }
            }
            .pointerInput(Unit){
                var start = Offset.Zero
                detectHorizontalDragGestures(
                    onDragStart = {
                        start = it
                        // Handle drag start
                    }
                ) { change, dragAmount ->
                    val end = change.position
                    if(dragAmount > 0){
                        //swipe right
                        Log.d("GestureDetection", "Swipe Right: $dragAmount")
                        sessionManager.sendEvent(start, GestureType.SWIPE_RIGHT, end)
                    } else{
                        //swipe left
                        Log.d("GestureDetection", "Swipe Left: $dragAmount")
                        sessionManager.sendEvent(change.position, GestureType.SWIPE_LEFT, end)
                    }
                }
            }
    ) {
        var parentSize: IntSize by remember { mutableStateOf(IntSize(0, 0)) }

        val remoteVideoTrackState by sessionManager.remoteVideoTrackFlow.collectAsState(null)
        val remoteVideoTrack = remoteVideoTrackState

        val localVideoTrackState by sessionManager.localVideoTrackFlow.collectAsState(null)
        val localVideoTrack = localVideoTrackState

        var callMediaState by remember { mutableStateOf(CallMediaState()) }

        if (remoteVideoTrack != null) {
            VideoRenderer(
                videoTrack = remoteVideoTrack,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { parentSize = it }
            )
        }

        if (localVideoTrack != null && !isSub) {
            FloatingVideoRenderer(
                modifier = Modifier
                    .size(width = 150.dp, height = 210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .align(Alignment.TopEnd),
                videoTrack = localVideoTrack,
                parentBounds = parentSize,
                paddingValues = PaddingValues(0.dp)
            )
        }

        val activity = LocalActivity.current

        VideoCallControls(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            callMediaState = callMediaState,
            onCallAction = {
                when (it) {
                    is CallAction.ToggleMicroPhone -> {
                        val enabled = callMediaState.isMicrophoneEnabled.not()
                        callMediaState = callMediaState.copy(isMicrophoneEnabled = enabled)
//                        sessionManager.enableMicrophone(enabled)
                    }
                    is CallAction.ToggleCamera -> {
                        val enabled = callMediaState.isCameraEnabled.not()
                        callMediaState = callMediaState.copy(isCameraEnabled = enabled)
//                        sessionManager.enableCamera(enabled)
                    }
                    CallAction.FlipCamera -> {
//                        sessionManager.flipCamera()
                    }
                    CallAction.LeaveCall -> {
                        activity?.finish()
                    }
                }
            }
        )
    }
    
}