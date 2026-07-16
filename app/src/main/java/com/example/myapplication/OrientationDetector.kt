package com.example.myapplication

import android.content.Context
import android.view.OrientationEventListener

class OrientationDetector(context: Context, onOrientationChanged: (Int) -> Unit) {
    private var lastOrientation = -1
    private val callback = onOrientationChanged
    
    private val orientationEventListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return

            val newOrientation = when {
                orientation <= 45 || orientation > 315 -> 0
                orientation in 46..135 -> 270 // Landscape Right
                orientation in 136..225 -> 180
                orientation in 226..315 -> 90 // Landscape Left
                else -> 0
            }

            if (newOrientation != lastOrientation) {
                lastOrientation = newOrientation
                callback(newOrientation)
            }
        }
    }

    fun startListening() {
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
    }

    fun stopListening() {
        orientationEventListener.disable()
    }
}
