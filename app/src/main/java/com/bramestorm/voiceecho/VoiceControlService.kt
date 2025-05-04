package com.bramestorm.voiceecho

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat


class VoiceControlService : Service() {
    companion object {
        private const val CHANNEL_ID = "vc_channel"
        private const val NOTIF_ID = 1
        const val EXTRA_START_FROM_HEADSET = "START_FROM_HEADSET"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private val handler = Handler(Looper.getMainLooper())
    private val tapTimestamps = ArrayDeque<Long>()

    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            -> mediaSession.isActive = false

            AudioManager.AUDIOFOCUS_GAIN -> mediaSession.isActive = true
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1) Request audio focus for assistant playback
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(afChangeListener, handler)
            .build()
        audioManager.requestAudioFocus(focusRequest)

        // 2) Post persistent notification
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        // 3) Initialize MediaSession
        mediaSession = MediaSessionCompat(this, "VoiceCtrl").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                    val key = intent
                        .getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return false

                    if (key.action == KeyEvent.ACTION_DOWN &&
                        key.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT  // double-tap → NEXT
                    ) {
                        Log.d("VoiceCtrlSvc", "Double-tap detected (MEDIA_NEXT), calling onWake()")
                        onWake()
                        return true
                    }
                    return super.onMediaButtonEvent(intent)
                }
            })

            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }


        // Receive media-button intents
        val mediaIntent = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName)
        val pi = PendingIntent.getBroadcast(
            this, 0, mediaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession.setMediaButtonReceiver(pi)

    }//=== END onCreate ======================

    private fun recordTap(code: Int) {
        val now = SystemClock.elapsedRealtime()
        tapTimestamps.addLast(now)
        while (tapTimestamps.size > 2) tapTimestamps.removeFirst()
        Log.d("VoiceCtrlSvc", "recordTap(code=$code) taps=$tapTimestamps")

        // Was previously <=800L
        if (tapTimestamps.size == 2 && now - tapTimestamps.first() <= 1200L) {
            tapTimestamps.clear()
            Log.d("VoiceCtrlSvc", ">>> Double-tap detected, calling onWake()")
            onWake()
        }
    }


    private fun onWake() {
        Log.d(TAG, "onWake(): launching UI")
        mediaSession.isActive = false

        // Bring up your MainActivity
        val ui = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_FROM_HEADSET, true)
        }
        startActivity(ui)

        // re-enable after delay
        handler.postDelayed({ mediaSession.isActive = true }, 2000)
    }

    private fun buildNotification(): Notification {
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_FROM_HEADSET, true)
        }
        val pi = PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoiceControl Active")
            .setContentText("Double‑tap headset to talk")
            .setSmallIcon(R.drawable.icon_speak)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Voice Control",
                NotificationManager.IMPORTANCE_LOW
            ).also {
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(it)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        mediaSession.release()
        audioManager.abandonAudioFocusRequest(focusRequest)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

}//=========== END ============
