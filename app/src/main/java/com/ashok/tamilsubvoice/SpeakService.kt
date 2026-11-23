package com.ashok.tamilsubvoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeakService : Service(), TextToSpeech.OnInitListener {

    inner class LocalBinder : Binder() {
        fun get(): SpeakService = this@SpeakService
    }

    private val binder = LocalBinder()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())

    private var cues: List<Cue> = emptyList()
    private var nextIndex = 0

    var isPlaying = false
        private set
    private var baseVideoMs = 0L
    private var baseClockMs = 0L
    var speechRate = 1.15f

    var onStatus: ((String) -> Unit)? = null

    private val ticker = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            val now = currentPositionMs()
            while (nextIndex < cues.size && cues[nextIndex].startMs <= now) {
                val cue = cues[nextIndex]
                if (now - cue.startMs < 3000) {
                    speak(cue.tamil.ifBlank { cue.text })
                    onStatus?.invoke(cue.tamil)
                }
                nextIndex++
            }
            if (nextIndex >= cues.size) {
                isPlaying = false
                onStatus?.invoke("முடிஞ்சு")
                return
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ta", "IN"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) onStatus?.invoke("தமிழ் TTS வாய்ஸ் இல்ல, Google TTS-ல Tamil டவுன்லோட் பண்ணுங்க")
            tts?.setSpeechRate(speechRate)
        }
    }

    fun loadCues(list: List<Cue>) {
        cues = list
        seekTo(0)
    }

    private fun currentPositionMs(): Long {
        return baseVideoMs + (SystemClock.elapsedRealtime() - baseClockMs)
    }

    fun play() {
        if (cues.isEmpty()) return
        startForegroundNotice()
        baseClockMs = SystemClock.elapsedRealtime()
        isPlaying = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    fun pause() {
        baseVideoMs = currentPositionMs()
        isPlaying = false
        handler.removeCallbacks(ticker)
        tts?.stop()
    }

    fun seekTo(ms: Long) {
        val wasPlaying = isPlaying
        handler.removeCallbacks(ticker)
        tts?.stop()
        baseVideoMs = ms.coerceAtLeast(0L)
        baseClockMs = SystemClock.elapsedRealtime()
        nextIndex = cues.indexOfFirst { it.startMs >= baseVideoMs }.let { if (it == -1) cues.size else it }
        if (wasPlaying) {
            isPlaying = true
            handler.post(ticker)
        }
    }

    fun shiftBy(deltaMs: Long) {
        seekTo(currentPositionMs() + deltaMs)
    }

    fun positionMs(): Long = currentPositionMs()

    private fun speak(text: String) {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue")
    }

    private fun startForegroundNotice() {
        val channelId = "tamilsub"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Tamil Sub Voice", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val n = Notification.Builder(this, channelId)
            .setContentTitle("Tamil Sub Voice")
            .setContentText("தமிழ் குரல் ஓடிக்கிட்டு இருக்கு")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(1, n)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
