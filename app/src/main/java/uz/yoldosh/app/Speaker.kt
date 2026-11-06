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
        var say = text
        var r = tts.setLanguage(if (lang == "uz") Locale("uz", "UZ") else Locale("ru", "RU"))
        if (r < 0 && lang == "uz") {
            // No Uzbek voice on the head unit: let the Russian voice read it in Cyrillic.
            r = tts.setLanguage(Locale("ru", "RU"))
            say = uzLatinToCyrillic(text)
        }
        if (r < 0) return false
        tts.speak(say, TextToSpeech.QUEUE_FLUSH, null, "y" + System.currentTimeMillis())
        return true
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

        /** Rough Uzbek Latin → Cyrillic so a Russian TTS voice pronounces it acceptably. */
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

    fun stop() {
        if (ready) tts.stop()
        onSpeaking(false)
    }

    fun release() = tts.shutdown()
}
