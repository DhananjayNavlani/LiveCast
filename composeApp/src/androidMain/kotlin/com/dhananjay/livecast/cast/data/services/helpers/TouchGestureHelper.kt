package com.dhananjay.livecast.cast.data.services.helpers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Helper class to orchestrate touch gestures through AccessibilityService
 * Designed to be used as a static companion to an AccessibilityService
 */
class TouchGestureHelper {

    private val TAG = "TouchGestureHelper"

    companion object {
        // Default durations for different gestures
        private const val TAP_DURATION = 100L
        private const val DOUBLE_TAP_INTERVAL = 100L
        private const val LONG_PRESS_DURATION = 500L
        private const val SWIPE_DURATION = 300L

        /**
         * Performs a single tap at specified coordinates
         */
        fun tap(service: AccessibilityService, x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
            return performGesture(service, buildTapGesture(x, y, TAP_DURATION), callback)
        }

        /**
         * Performs a long press at specified coordinates
         */
        fun longPress(service: AccessibilityService, x: Float, y: Float, duration: Long = LONG_PRESS_DURATION, callback: ((Boolean) -> Unit)? = null): Boolean {
            return performGesture(service, buildTapGesture(x, y, duration), callback)
        }

        /**
         * Performs a double tap at specified coordinates
         */
        fun doubleTap(service: AccessibilityService, x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
            val success = AtomicBoolean(true)

            // First tap
            tap(service, x, y) { result ->
                if (!result) {
                    success.set(false)
                    callback?.invoke(false)
                    return@tap
                }

                // Wait a bit then perform second tap
                Handler(Looper.getMainLooper()).postDelayed({
                    val secondTapResult = tap(service, x, y) { secondResult ->
                        callback?.invoke(secondResult && success.get())
                    }
                    if (!secondTapResult) success.set(false)
                }, DOUBLE_TAP_INTERVAL)
            }

            return success.get()
        }

        /**
         * Performs a swipe from one point to another
         */
        fun swipe(
            service: AccessibilityService,
            startX: Float, startY: Float,
            endX: Float, endY: Float,
            duration: Long = SWIPE_DURATION,
            callback: ((Boolean) -> Unit)? = null
        ): Boolean {
            if(startX < 0 || startY < 0 || endX < 0 || endY < 0){
                Log.e("TouchGestureHelper", "Invalid coordinates for swipe")
                callback?.invoke(false)
                return false
            }
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            val gesture = gestureBuilder.build()

            return performGesture(service, gesture, callback)
        }

        /**
         * Performs a multi-point path movement
         * @param points List of Point(x,y) coordinates to follow in sequence
         */
        fun path(
            service: AccessibilityService,
            points: List<Pair<Float, Float>>,
            duration: Long = SWIPE_DURATION,
            callback: ((Boolean) -> Unit)? = null
        ): Boolean {
            if (points.size < 2) {
                Log.e("TouchGestureHelper", "Path requires at least 2 points")
                callback?.invoke(false)
                return false
            }

            val path = Path()
            path.moveTo(points[0].first, points[0].second)

            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }

            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            val gesture = gestureBuilder.build()

            return performGesture(service, gesture, callback)
        }

        /**
         * Performs a pinch gesture (zoom in/out)
         * @param centerX Center X coordinate of pinch
         * @param centerY Center Y coordinate of pinch
         * @param startSpread Starting distance between fingers
         * @param endSpread Ending distance between fingers
         * @param duration Duration of the gesture
         */
        fun pinch(
            service: AccessibilityService,
            centerX: Float, centerY: Float,
            startSpread: Float, endSpread: Float,
            duration: Long = SWIPE_DURATION,
            callback: ((Boolean) -> Unit)? = null
        ): Boolean {
            // Calculate the finger positions
            val startSpreadHalf = startSpread / 2
            val endSpreadHalf = endSpread / 2

            // First finger path
            val path1 = Path()
            path1.moveTo(centerX - startSpreadHalf, centerY)
            path1.lineTo(centerX - endSpreadHalf, centerY)

            // Second finger path
            val path2 = Path()
            path2.moveTo(centerX + startSpreadHalf, centerY)
            path2.lineTo(centerX + endSpreadHalf, centerY)

            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
            val gesture = gestureBuilder.build()

            return performGesture(service, gesture, callback)
        }

        /**
         * Execute a sequence of gestures one after another
         * @param gestures List of gesture functions to execute in order
         */
        fun sequence(
            service: AccessibilityService,
            gestures: List<((AccessibilityService) -> Boolean)>,
            callback: ((Boolean) -> Unit)? = null
        ) {
            if (gestures.isEmpty()) {
                callback?.invoke(true)
                return
            }

            executeGestureSequence(service, gestures, 0, callback)
        }

        private fun executeGestureSequence(
            service: AccessibilityService,
            gestures: List<((AccessibilityService) -> Boolean)>,
            index: Int,
            finalCallback: ((Boolean) -> Unit)?
        ) {
            if (index >= gestures.size) {
                finalCallback?.invoke(true)
                return
            }

            val currentGesture = gestures[index]
            val result = currentGesture(service)

            if (!result) {
                finalCallback?.invoke(false)
                return
            }

            // Wait for gesture to complete before executing next one
            Handler(Looper.getMainLooper()).postDelayed({
                executeGestureSequence(service, gestures, index + 1, finalCallback)
            }, 100)
        }

        // Helper method to build a tap gesture
        private fun buildTapGesture(x: Float, y: Float, duration: Long): GestureDescription {
            val path = Path()
            path.moveTo(x, y)

            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            return gestureBuilder.build()
        }

        // Helper method to perform a gesture with callback
        private fun performGesture(
            service: AccessibilityService,
            gesture: GestureDescription,
            callback: ((Boolean) -> Unit)? = null
        ): Boolean {
            if (callback == null) {
                // Synchronous call with no callback
                return service.dispatchGesture(gesture, null, null)
            }

            // For asynchronous call with callback
            val gestureCompleted = CountDownLatch(1)
            val gestureResult = AtomicBoolean(false)

            val dispatchResult = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    super.onCompleted(gestureDescription)
                    gestureResult.set(true)
                    gestureCompleted.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    super.onCancelled(gestureDescription)
                    gestureResult.set(false)
                    gestureCompleted.countDown()
                }
            }, null)

            if (!dispatchResult) {
                callback(false)
                return false
            }

            // Wait for async result in a background thread
            Thread {
                try {
                    if (gestureCompleted.await(2000, TimeUnit.MILLISECONDS)) {
                        callback(gestureResult.get())
                    } else {
                        Log.e("TouchGestureHelper", "Gesture timed out")
                        callback(false)
                    }
                } catch (e: InterruptedException) {
                    Log.e("TouchGestureHelper", "Gesture interrupted", e)
                    callback(false)
                }
            }.start()

            return true
        }
    }
}