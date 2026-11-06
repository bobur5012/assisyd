package uz.yoldosh.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Offline speech recognition with Vosk. Models live in assets/model-ru and assets/model-uz
 * (downloaded by the CI build) and are unpacked to storage on first use.
 */
class Voice(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onState: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val models = HashMap<String, Model>()
    private val loading = HashSet<String>()
    private var service: SpeechService? = null
    private var delivered = false
    private var wantLang: String? = null

    fun preload(lang: String) {
        if (models.containsKey(lang) || loading.contains(lang)) return
        loading.add(lang)
        StorageService.unpack(ctx, "model-$lang", "model-$lang",
            { model ->
                loading.remove(lang)
                models[lang] = model
                if (wantLang == lang) begin(model)
            },
            { e ->
                loading.remove(lang)
                if (wantLang == lang) {
                    wantLang = null
                    onState("error:Голосовая модель не найдена (${e.message})")
                }
            })
    }

    fun start(lang: String) {
        stop()
        wantLang = lang
        val m = models[lang]
        if (m == null) {
            onState("loading")
            preload(lang)
            return
        }
        begin(m)
    }

    private fun begin(model: Model) {
        wantLang = null
        try {
            delivered = false
            val rec = Recognizer(model, 16000.0f)
            val s = SpeechService(rec, 16000.0f)
            service = s
            s.startListening(listener, 9000)
            onState("listening")
        } catch (e: Exception) {
            onState("error:" + (e.message ?: "микрофон недоступен"))
        }
    }

    private fun text(hyp: String?, key: String): String {
        if (hyp.isNullOrBlank()) return ""
        return try { JSONObject(hyp).optString(key, "").trim() } catch (e: Exception) { "" }
    }

    private fun deliver(t: String) {
        if (delivered || t.isBlank()) return
        delivered = true
        main.post { stop() }
        onFinal(t)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val t = text(hypothesis, "partial")
            if (t.isNotBlank() && !delivered) onPartial(t)
        }

        override fun onResult(hypothesis: String?) = deliver(text(hypothesis, "text"))

        override fun onFinalResult(hypothesis: String?) = deliver(text(hypothesis, "text"))

        override fun onError(exception: Exception?) {
            main.post { stop() }
            onState("error:" + (exception?.message ?: ""))
        }

        override fun onTimeout() {
            main.post { stop() }
            if (!delivered) onState("timeout")
        }
    }

    fun stop() {
        wantLang = null
        val s = service ?: return
        service = null
        try { s.stop() } catch (_: Exception) {}
        try { s.shutdown() } catch (_: Exception) {}
        onState("idle")
    }

    fun release() {
        stop()
        models.values.forEach { try { it.close() } catch (_: Exception) {} }
        models.clear()
    }
}
