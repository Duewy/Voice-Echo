// src/main/java/com/bramestorm/voiceecho/TtsManager.kt
package com.bramestorm.voiceecho

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"
    }

    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var ready = false
    private var onReadyCallback: ((Boolean) -> Unit)? = null

    /**
     * Call this from your Activity to get notified when TTS is ready.
     */
    fun init(callback: (Boolean) -> Unit) {
        onReadyCallback = callback
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.getDefault()
            Log.d(TAG, "TTS initialized (locale=${Locale.getDefault()})")
        } else {
            Log.w(TAG, "TTS initialization failed (status=$status)")
        }
        onReadyCallback?.invoke(ready)
    }

    /**
     * Speak out the given text.  Will only work once `ready` is true.
     */
    fun speakResponse(text: String) {
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w(TAG, "TTS not yet ready; dropped “$text”")
        }
    }

    /** Call this from onDestroy() of your Activity to clean up. */
    fun shutdown() {
        tts.shutdown()
    }
}
