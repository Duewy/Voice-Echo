package com.bramestorm.voiceecho

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), RecognitionListener {
    companion object {
        private const val TAG = "voiceEcho"
        private const val AUDIO_PERM_REQ = 1
        private const val BT_PERM_REQ = 1001
        const val EXTRA_START_FROM_HEADSET = "START_FROM_HEADSET"
    }

    private lateinit var btnMic: ImageButton
    private lateinit var txtSpoken: TextView
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var ttsManager: TtsManager
    private lateinit var mediaSession: MediaSessionCompat

    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == "com.example.voiceecho.ACTION_START_RECOGNIZER") {
                startListeningForCommand()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //$$$ If the cellphone is sleeping this wakes it up and puts the app onscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // 1) Start service to own media button & audio focus
        ensureAudioPermissionsAndStartService()

        // 2) Handle tap‑launch intent
        handleStartIntent(intent)

        // 3) Initialize speech & TTS
        ttsManager = TtsManager(this).apply { init {} }
        setupSpeechRecognizer()

        // 4) UI hookups …
        btnMic = findViewById(R.id.btnMic)
        txtSpoken = findViewById(R.id.txtSpoken)
        btnMic.setOnClickListener { startListeningForCommand() }
    }
//===END onCreate ======================

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartIntent(intent)
    }

    private fun checkBluetoothAndToast() {
        if (!isBtHeadsetConnected()) {
            Toast.makeText(
                this,
                "No Bluetooth headset detected. Use the on‑screen mic button instead.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleStartIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_FROM_HEADSET, false) == true) {
            ttsManager.speakResponse("I am ready")
            startListeningForCommand()
        }
    }


    override fun onResume() {
        super.onResume()
      }


    override fun onPause() {
        super.onPause()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            AUDIO_PERM_REQ -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startVoiceService()
                    checkBluetoothAndToast()
                } else {
                    Toast.makeText(
                        this,
                        "Audio permission required to use headset button.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            BT_PERM_REQ -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    checkBluetoothAndToast()
                    if (!isBtHeadsetConnected()) Toast.makeText(
                        this,
                        "No Bluetooth headset detected. Use the on‑screen mic button instead.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Permission denied. Use the on‑screen mic button only.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }//===== END onRequestPermissionResults ==================


    private fun startVoiceService() {
        Intent(this, VoiceControlService::class.java).also {
            ContextCompat.startForegroundService(this, it)
        }
    }

    private fun ensureAudioPermissionsAndStartService() {
        val perms = listOf(Manifest.permission.RECORD_AUDIO)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), AUDIO_PERM_REQ)
        } else {
            ContextCompat.startForegroundService(
                this, Intent(this, VoiceControlService::class.java)
            )
        }
    }

    private fun isBtHeadsetConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                BT_PERM_REQ
            )
            return false
        }
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        return btAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) ==
                BluetoothAdapter.STATE_CONNECTED
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
    }

    override fun onReadyForSpeech(params: Bundle) { Log.d(TAG, "onReadyForSpeech") }
    override fun onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech") }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buf: ByteArray) { Log.d(TAG, "bufferSize=${buf.size}") }
    override fun onEndOfSpeech() { Log.d(TAG, "onEndOfSpeech") }
    override fun onPartialResults(partial: Bundle) {}
    override fun onEvent(eventType: Int, params: Bundle) {}

    override fun onResults(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spoken = matches[0].trim()
            if (spoken.endsWith("over", ignoreCase = true)) {
                val command = spoken.removeSuffix("over").trim()
                txtSpoken.text = command
                ttsManager.speakResponse("  You just said: $command")
            } else {
                ttsManager.speakResponse("Please say Over when you’re done.")
            }
        }
        btnMic.isEnabled = true
    }

    override fun onError(error: Int) {
        Log.e(TAG, "onError code=$error")
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            speechRecognizer.destroy()
            setupSpeechRecognizer()
        }
        txtSpoken.text = getString(R.string.error_format, error)
        btnMic.isEnabled = true
    }

    private fun startListeningForCommand() {
        speechRecognizer.cancel()
        val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now… say ‘Over’ when done")
        }
        Handler(Looper.getMainLooper()).postDelayed({
            speechRecognizer.startListening(listenIntent)
        }, 100)
    }


    override fun onDestroy() {
        speechRecognizer.destroy()
        ttsManager.shutdown()
        super.onDestroy()
    }

}//==== END ==============
