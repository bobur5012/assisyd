package uz.yoldosh.app

import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Tiny uploader for the diagnostic report. Tries two no-auth paste services. */
object Net {

    fun paste(text: String): String? = pasteRs(text) ?: zeroXzero(text)

    private fun pasteRs(text: String): String? {
        return try {
            val c = (URL("https://paste.rs/").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 15000; readTimeout = 15000
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }
            c.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)
                .bufferedReader().readText().trim()
            if (code in 200..299 && body.startsWith("http")) body else null
        } catch (_: Exception) { null }
    }

    private fun zeroXzero(text: String): String? {
        return try {
            val boundary = "----yoldosh" + System.currentTimeMillis()
            val c = (URL("https://0x0.st").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 15000; readTimeout = 15000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", "yoldosh-app")
            }
            DataOutputStream(c.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"diag.txt\"\r\n")
                out.writeBytes("Content-Type: text/plain\r\n\r\n")
                out.write(text.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)
                .bufferedReader().readText().trim()
            if (code in 200..299 && body.startsWith("http")) body else null
        } catch (_: Exception) { null }
    }
}
