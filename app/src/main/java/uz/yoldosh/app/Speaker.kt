package uz.yoldosh.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Speaks answers with the head unit's text-to-speech engine, if it has one. */
class Speaker(ctx: Context, private val onSpeaking: (Boolean) -> Unit) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    var ready = false
        private set

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onSpeaking(true)
            override fun onDone(utteranceId: String?) = onSpeaking(false)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onSpeaking(false)
        })
    }

    /** Returns false when no voice is available for the language. */
    fun speak(text: String, lang: String): Boolean {
        if (!ready) return false
        var r = tts.setLanguage(if (lang == "uz") Locale("uz", "UZ") else Locale("ru", "RU"))
        if (r < 0 && lang == "uz") r = tts.setLanguage(Locale("ru", "RU"))
        if (r < 0) return false
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "y" + System.currentTimeMillis())
        return true
    }

    fun stop() {
        if (ready) tts.stop()
        onSpeaking(false)
    }

    fun release() = tts.shutdown()
}
