package com.djpocket.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.djpocket.studio.audio.AudioEngine
import com.djpocket.studio.audio.Deck
import com.djpocket.studio.audio.Decoder
import com.djpocket.studio.ui.DjApp
import com.djpocket.studio.ui.L
import com.djpocket.studio.ui.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hot cues are stored per track (keyed by the file URI). */
object Store {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("dj", Context.MODE_PRIVATE) }
    private fun key(d: Deck) = "hc_" + (d.track?.uri ?: "")
    fun load(d: Deck) {
        val s = sp.getString(key(d), null) ?: return
        val parts = s.split(",")
        for (i in 0 until 8) d.hotCues[i] = parts.getOrNull(i)?.toDoubleOrNull() ?: -1.0
    }
    fun save(d: Deck) { if (d.track != null) sp.edit().putString(key(d), d.hotCues.joinToString(",")).apply() }
}

class MainActivity : ComponentActivity() {
    private lateinit var engine: AudioEngine
    private lateinit var am: AudioManager
    private var focusReq: AudioFocusRequest? = null
    private var pendingDeck = 0

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadTrack(pendingDeck, uri)
    }

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                engine.pauseAll()
                Status.msg = L.t("Output device disconnected - paused", "خروجی صدا قطع شد - پخش متوقف شد")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        engine = AudioEngine(applicationContext)
        engine.start()
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) engine.pauseAll()
            }.build()
        am.requestAudioFocus(focusReq!!)
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_EXPORTED)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            DjApp(engine,
                onLoad = { d -> pendingDeck = d; picker.launch(arrayOf("audio/*")) },
                onCues = { d -> Store.save(d) })
        }
    }

    private fun queryName(uri: Uri): String {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i).substringBeforeLast('.')
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment ?: "Track"
    }

    private fun loadTrack(deckId: Int, uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        val name = queryName(uri)
        Status.msg = L.t("Loading and analysing: ", "در حال بارگذاری و تحلیل: ") + name
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val t = Decoder.decode(applicationContext, uri, name)
                val d = engine.deck(deckId)
                d.loadTrack(t)
                Store.load(d)
                withContext(Dispatchers.Main) {
                    Status.msg = L.t("Loaded: ", "بارگذاری شد: ") + name + "  BPM " + "%.1f".format(t.bpm)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Status.msg = L.t("Could not load file: ", "خطا در بارگذاری: ") + (e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(noisy) } catch (_: Exception) {}
        focusReq?.let { am.abandonAudioFocusRequest(it) }
        engine.stop()
    }
}
