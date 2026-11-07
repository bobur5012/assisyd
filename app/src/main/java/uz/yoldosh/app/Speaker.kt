package uz.yoldosh.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Speaks answers. First choice: the voice built into the app (sherpa-onnx + Piper, works offline on any
 * head unit). Fallback: the head unit's own text-to-speech engine, if it has one.
 */
class Speaker(ctx: Context, private val onSpeaking: (Boolean) -> Unit) : TextToSpeech.OnInitListener {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var system: TextToSpeech? = null
    private var systemReady = false

    @Volatile private var offline: OfflineTts? = null
    @Volatile private var offlineState = "loading"
    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    @Volatile private var generation = 0

    val ready: Boolean get() = offline != null || systemReady
    val engine: String get() = when {
        offline != null -> "встроенный голос"
        systemReady -> "системный синтез"
        else -> "нет ($offlineState)"
    }

    init {
        try { system = TextToSpeech(app, this) } catch (_: Exception) {}
        worker.execute { loadOffline() }
    }

    override fun onInit(status: Int) {
        systemReady = status == TextToSpeech.SUCCESS
        if (!systemReady) return
        system?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onSpeaking(true)
            override fun onDone(utteranceId: String?) = finished()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
        })
    }

    private fun loadOffline() {
        try {
            val names = app.assets.list("tts") ?: emptyArray()
            if (!names.contains("model.onnx")) { offlineState = "голос не встроен"; return }
            val dataDir = copyAssetDir("tts/espeak-ng-data")
            val vits = OfflineTtsVitsModelConfig(
                model = "tts/model.onnx",
                lexicon = "",
                tokens = "tts/tokens.txt",
                dataDir = dataDir,
                dictDir = "",
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f,
            )
            val cfg = OfflineTtsConfig(
                model = OfflineTtsModelConfig(vits = vits, numThreads = 2, debug = false, provider = "cpu"),
            )
            offline = OfflineTts(app.assets, cfg)
            offlineState = "ok"
        } catch (t: Throwable) {
            offlineState = "ошибка: ${t.message}"
        }
    }

    /** espeak-ng-data must be a real folder on disk; copy it out of the APK once per app version. */
    private fun copyAssetDir(path: String): String {
        val target = File(app.filesDir, path)
        val marker = File(target, ".v" + (try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (_: Exception) { "0" }))
        if (marker.exists()) return target.absolutePath
        target.deleteRecursively()
        copyRecursive(path, File(app.filesDir, path))
        marker.createNewFile()
        return target.absolutePath
    }

    private fun copyRecursive(assetPath: String, out: File) {
        val children = app.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            out.parentFile?.mkdirs()
            app.assets.open(assetPath).use { input -> out.outputStream().use { input.copyTo(it) } }
        } else {
            out.mkdirs()
            for (c in children) copyRecursive("$assetPath/$c", File(out, c))
        }
    }

    /** Returns false when there is no voice at all. */
    fun speak(text: String, lang: String): Boolean {
        val say = if (lang == "uz") uzLatinToCyrillic(text) else text
        val tts = offline
        if (tts != null) {
            stopPlayback()
            val myGen = ++generation
            onSpeaking(true)
            worker.execute {
                try {
                    val audioOut = tts.generate(say, 0, 1.0f)
                    if (myGen == generation) main.post { if (myGen == generation) play(audioOut.samples, audioOut.sampleRate) }
                } catch (_: Throwable) {
                    main.post { finished() }
                }
            }
            return true
        }
        val s = system ?: return false
        if (!systemReady) return false
        var useText = text
        var r = s.setLanguage(if (lang == "uz") Locale("uz", "UZ") else Locale("ru", "RU"))
        if (r < 0 && lang == "uz") {
            r = s.setLanguage(Locale("ru", "RU"))
            useText = say
        }
        if (r < 0) return false
        s.speak(useText, TextToSpeech.QUEUE_FLUSH, null, "y" + System.currentTimeMillis())
        return true
    }

    private fun play(samples: FloatArray, rate: Int) {
        if (samples.isEmpty()) { finished(); return }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
        focus = req
        try { audio.requestAudioFocus(req) } catch (_: Exception) {}
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 4)
                .build()
            t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            t.notificationMarkerPosition = samples.size - 1
            t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) = finished()
                override fun onPeriodicNotification(track: AudioTrack?) {}
            }, main)
            track = t
            t.play()
            // Safety net in case the marker never fires on this head unit.
            val ms = samples.size * 1000L / rate + 600
            val gen = generation
            main.postDelayed({ if (gen == generation && track === t) finished() }, ms)
        } catch (_: Exception) {
            finished()
        }
    }

    private fun finished() {
        main.post {
            stopPlayback()
            onSpeaking(false)
        }
    }

    private fun stopPlayback() {
        track?.let { try { it.stop() } catch (_: Exception) {}; try { it.release() } catch (_: Exception) {} }
        track = null
        focus?.let { try { audio.abandonAudioFocusRequest(it) } catch (_: Exception) {} }
        focus = null
    }

    fun stop() {
        generation++
        try { system?.stop() } catch (_: Exception) {}
        main.post { stopPlayback() }
        onSpeaking(false)
    }

    fun release() {
        stop()
        try { system?.shutdown() } catch (_: Exception) {}
        worker.shutdown()
        try { offline?.release() } catch (_: Throwable) {}
    }

    companion object {
        private val PAIRS = listOf(
            "o'" to "у", "g'" to "г", "sh" to "ш", "ch" to "ч", "ng" to "нг",
            "ya" to "я", "yo" to "ё", "yu" to "ю", "ye" to "е",
        )
        private val SINGLE = mapOf(
            'a' to "а", 'b' to "б", 'd' to "д", 'e' to "е", 'f' to "ф", 'g' to "г", 'h' to "х", 'i' to "и",
            'j' to "дж", 'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н", 'o' to "о", 'p' to "п", 'q' to "к",
            'r' to "р", 's' to "с", 't' to "т", 'u' to "у", 'v' to "в", 'x' to "х", 'y' to "й", 'z' to "з",
        )

        /** Rough Uzbek Latin → Cyrillic so the Russian voice pronounces it acceptably. Cyrillic stays as is. */
        fun uzLatinToCyrillic(src: String): String {
            val s = src.lowercase().replace(Regex("[ʻʼ’‘`´]"), "'")
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val pair = PAIRS.firstOrNull { s.startsWith(it.first, i) }
                if (pair != null) {
                    out.append(pair.second); i += pair.first.length; continue
                }
                val c = s[i]
                val wordStart = i == 0 || !s[i - 1].isLetter()
                when {
                    c == 'e' && wordStart -> out.append("э")
                    c == '\'' -> {}
                    SINGLE.containsKey(c) -> out.append(SINGLE[c])
                    else -> out.append(c)
                }
                i++
            }
            return out.toString()
        }
    }
}
