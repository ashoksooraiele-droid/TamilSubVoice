package com.ashok.tamilsubvoice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var service: SpeakService? = null
    private var cues: List<Cue> = emptyList()
    private val translator = Translator()
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var tvStatus: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvLine: TextView
    private lateinit var etJump: EditText

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as SpeakService.LocalBinder).get()
            service?.onStatus = { msg -> runOnUiThread { tvLine.text = msg } }
            if (cues.isNotEmpty()) service?.loadCues(cues)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val positionUpdater = object : Runnable {
        override fun run() {
            val ms = service?.positionMs() ?: 0L
            tvPosition.text = format(ms)
            ui.postDelayed(this, 250)
        }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        cues = SrtParser.parse(text)
        tvStatus.text = "${cues.size} வரிகள் ஏற்றிட்டேன். இப்ப மொழிபெயர் பட்டன் அழுத்துங்க"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvPosition = findViewById(R.id.tvPosition)
        tvLine = findViewById(R.id.tvLine)
        etJump = findViewById(R.id.etJump)

        val svc = Intent(this, SpeakService::class.java)
        startService(svc)
        bindService(svc, connection, Context.BIND_AUTO_CREATE)

        findViewById<Button>(R.id.btnPick).setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btnTranslate).setOnClickListener {
            if (cues.isEmpty()) {
                Toast.makeText(this, "முதல்ல SRT தேர்ந்தெடுங்க", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    tvStatus.text = "தமிழ் மாடல் டவுன்லோட் ஆகுது (இணையம் வேணும், ஒரு தடவை மட்டும்)..."
                    translator.prepare()
                    translator.translateAll(cues) { done, total ->
                        tvStatus.text = "மொழிபெயர்க்குது: $done / $total"
                    }
                    service?.loadCues(cues)
                    tvStatus.text = "மொழிபெயர்ப்பு முடிஞ்சு. Play அழுத்தலாம்"
                } catch (e: Exception) {
                    tvStatus.text = "பிழை: ${e.message}"
                }
            }
        }

        findViewById<Button>(R.id.btnPlay).setOnClickListener { service?.play() }
        findViewById<Button>(R.id.btnPause).setOnClickListener { service?.pause() }
        findViewById<Button>(R.id.btnBack5).setOnClickListener { service?.shiftBy(-5000) }
        findViewById<Button>(R.id.btnFwd5).setOnClickListener { service?.shiftBy(5000) }
        findViewById<Button>(R.id.btnBack1).setOnClickListener { service?.shiftBy(-500) }
        findViewById<Button>(R.id.btnFwd1).setOnClickListener { service?.shiftBy(500) }

        findViewById<Button>(R.id.btnJump).setOnClickListener {
            val ms = parseTime(etJump.text.toString())
            if (ms == null) {
                Toast.makeText(this, "mm:ss இப்படி எழுதுங்க, உதா: 12:30", Toast.LENGTH_SHORT).show()
            } else {
                service?.seekTo(ms)
            }
        }

        ui.post(positionUpdater)
    }

    private fun parseTime(s: String): Long? {
        val p = s.trim().split(":").map { it.toLongOrNull() ?: return null }
        return when (p.size) {
            2 -> (p[0] * 60 + p[1]) * 1000
            3 -> (p[0] * 3600 + p[1] * 60 + p[2]) * 1000
            else -> null
        }
    }

    private fun format(ms: Long): String {
        val s = ms / 1000
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    override fun onDestroy() {
        ui.removeCallbacks(positionUpdater)
        unbindService(connection)
        translator.close()
        super.onDestroy()
    }
}
