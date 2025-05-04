package com.bramestorm.voiceecho

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


// In VoiceSetupActivity.kt
class VoiceSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_setup)

        // Read the raw secure settings
        val assist = Settings.Secure.getString(contentResolver, "voice_interaction_service").orEmpty()
        val recog  = Settings.Secure.getString(contentResolver, "voice_recognition_service").orEmpty()

        findViewById<TextView>(R.id.txtDefaultAssist)
            .text = "Default assistant: $assist"
        findViewById<TextView>(R.id.txtDefaultRecognizer)
            .text = "Default recognizer: $recog"

        // Button to Bixby App Info
        findViewById<Button>(R.id.btnBixbySettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .apply {
                    data = Uri.parse("package:com.samsung.android.bixby.agent")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
        }

        // Button to Voice‑Input Settings
        findViewById<Button>(R.id.btnVoiceInputSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }

        // Button to Assistant Settings (Google)
        findViewById<Button>(R.id.btnAssistantSettings).setOnClickListener {
            startActivity(Intent("com.google.android.apps.gsa.settings.ASSISTANT")
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        }
    }
}
