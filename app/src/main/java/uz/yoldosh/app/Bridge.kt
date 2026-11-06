package uz.yoldosh.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Everything the web interface can ask the head unit to do (window.Android in JavaScript). */
class Bridge(private val act: MainActivity) {
    private val ctx: Context = act.applicationContext
    private var pageReady = false
    private var pendingListen = false

    private val voice = Voice(ctx,
        onPartial = { act.emit("voice", JSONObject().put("state", "partial").put("text", it)) },
        onFinal = { act.emit("voice", JSONObject().put("state", "final").put("text", it)) },
        onState = { act.emit("voice", JSONObject().put("state", it)) })

    private val speaker = Speaker(ctx) { talking -> act.emit("speaking", talking) }

    init {
        MediaHub.listener = { act.emit("media", it) }
        MediaHub.init(ctx)
        voice.onWake = { act.emit("listen", true) }
    }

    // ---- lifecycle -------------------------------------------------------------------------

    fun onResume() {
        MediaHub.attach()
        if (pageReady) {
            pushPerms()
            act.emit("resumed", true)
        }
    }

    fun onStop() {
        voice.stopWake()
        voice.stop()
    }

    fun requestListenFromOutside() {
        if (pageReady) act.emit("listen", true) else pendingListen = true
    }

    fun pushPerms() = act.emit("perms", Perms.status(ctx))

    fun release() {
        voice.release()
        speaker.release()
        MediaHub.listener = null
    }

    // ---- called from JavaScript -------------------------------------------------------------

    @JavascriptInterface
    fun ready() {
        pageReady = true
        pushPerms()
        MediaHub.push()
        if (Perms.status(ctx).optBoolean("mic")) {
            act.runOnUiThread { voice.preload(Prefs.str(ctx, "lang", "uz")) }
        }
        if (pendingListen) {
            pendingListen = false
            act.emit("listen", true)
        }
    }

    @JavascriptInterface
    fun settings(): String = JSONObject()
        .put("lang", Prefs.str(ctx, "lang", "uz"))
        .put("speak", Prefs.bool(ctx, "speak", true))
        .put("bubble", Prefs.bool(ctx, "bubble", true))
        .put("autostart", Prefs.bool(ctx, "autostart", false))
        .put("wake", Prefs.bool(ctx, "wake", false))
        .put("home", Prefs.str(ctx, "home_lat", "").isNotEmpty())
        .put("work", Prefs.str(ctx, "work_lat", "").isNotEmpty())
        .put("onboarded", Prefs.bool(ctx, "onboarded", false))
        .put("lastKey", Prefs.int(ctx, "lastKey", -1))
        .put("wheelKey", Prefs.int(ctx, "wheelKey", -1))
        .put("tts", speaker.ready)
        .put("version", appVersion())
        .toString()

    @JavascriptInterface
    fun setSetting(key: String, value: String) {
        when (key) {
            "lang" -> {
                Prefs.put(ctx, "lang", value)
                if (Perms.status(ctx).optBoolean("mic")) act.runOnUiThread { voice.preload(value) }
            }
            "speak", "bubble", "autostart", "onboarded", "wake" -> Prefs.put(ctx, key, value == "true")
            "wheelKey" -> Prefs.put(ctx, "wheelKey", value.toIntOrNull() ?: -1)
        }
    }

    @JavascriptInterface
    fun requestPerm(key: String) = act.runOnUiThread {
        Perms.request(act, key)
        pushPerms()
    }

    @JavascriptInterface
    fun listen(lang: String) = act.runOnUiThread {
        speaker.stop()
        if (!Perms.status(ctx).optBoolean("mic")) {
            act.emit("voice", JSONObject().put("state", "nomic"))
            return@runOnUiThread
        }
        voice.start(if (lang == "ru") "ru" else "uz")
    }

    @JavascriptInterface
    fun stopListen() = act.runOnUiThread { voice.stop() }

    /** Listen for "Salom, Yo'ldosh" in the background while the app is on screen. */
    @JavascriptInterface
    fun startWake(lang: String) = act.runOnUiThread {
        if (Prefs.bool(ctx, "wake", false) && Perms.status(ctx).optBoolean("mic")) {
            voice.startWake(if (lang == "ru") "ru" else "uz")
        }
    }

    @JavascriptInterface
    fun stopWake() = act.runOnUiThread { voice.stopWake() }

    /** Remembers the current GPS position as "home" or "work". */
    @JavascriptInterface
    fun saveHere(kind: String): Boolean {
        if (kind != "home" && kind != "work") return false
        val fine = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return false
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null } ?: continue
            if (best == null || l.time > best.time) best = l
        }
        val b = best ?: return false
        Prefs.put(ctx, "${kind}_lat", b.latitude.toString())
        Prefs.put(ctx, "${kind}_lon", b.longitude.toString())
        return true
    }

    /** Builds a route to a saved place in Yandex Navigator (or Maps). Returns the app used or "". */
    @JavascriptInterface
    fun routeTo(kind: String): String {
        val lat = Prefs.str(ctx, "${kind}_lat", "")
        val lon = Prefs.str(ctx, "${kind}_lon", "")
        if (lat.isEmpty() || lon.isEmpty()) return ""
        val options = listOf(
            "navigator" to "yandexnavi://build_route_on_map?lat_to=$lat&lon_to=$lon",
            "maps" to "yandexmaps://maps.yandex.ru/?rtext=~$lat,$lon&rtt=auto",
        )
        for ((name, uri) in options) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(ctx.packageManager) != null) {
                try { ctx.startActivity(i); return name } catch (_: Exception) {}
            }
        }
        return ""
    }

    @JavascriptInterface
    fun speak(text: String, lang: String): Boolean {
        if (!Prefs.bool(ctx, "speak", true)) return false
        return speaker.speak(text, lang)
    }

    @JavascriptInterface
    fun stopSpeaking() = speaker.stop()

    @JavascriptInterface
    fun media(cmd: String) = act.runOnUiThread { MediaHub.command(act, cmd) }

    @JavascriptInterface
    fun mediaState(): String = MediaHub.state().toString()

    /** dir: +1 louder, -1 quieter. Returns the new music volume in percent. */
    @JavascriptInterface
    fun volume(dir: Int): Int {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val flag = AudioManager.FLAG_SHOW_UI
        repeat(2) {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                if (it == 0) flag else 0
            )
        }
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    /**
     * Opens Yandex Navigator (or Yandex Maps) with a search. Returns which app opened, or "" if none.
     */
    @JavascriptInterface
    fun navigate(query: String): String {
        val q = Uri.encode(query)
        val options = listOf(
            "navigator" to "yandexnavi://map_search?text=$q",
            "maps" to "yandexmaps://maps.yandex.ru/?text=$q",
        )
        for ((name, uri) in options) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(ctx.packageManager) != null) {
                try {
                    ctx.startActivity(i)
                    return name
                } catch (_: Exception) {}
            }
        }
        return ""
    }

    /** Launches an installed app whose name matches what the driver said. Returns its label or "". */
    @JavascriptInterface
    fun openApp(spoken: String): String {
        val want = norm(spoken)
        if (want.isBlank()) return ""
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        var best: Pair<String, String>? = null
        for (ri in apps) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName) continue
            val label = ri.loadLabel(pm).toString()
            val l = norm(label)
            if (l.isBlank()) continue
            if (l == want || l.contains(want) || want.contains(l)) {
                best = pkg to label
                if (l == want) break
            }
        }
        val (pkg, label) = best ?: return ""
        val i = pm.getLaunchIntentForPackage(pkg) ?: return ""
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            label
        } catch (_: Exception) { "" }
    }

    @JavascriptInterface
    fun openPackage(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (_: Exception) { false }
    }

    @JavascriptInterface
    fun installed(): String {
        val arr = JSONArray()
        for (p in listOf(MediaHub.YANDEX_MUSIC, "ru.yandex.yandexnavi", "ru.yandex.yandexmaps")) {
            if (isInstalled(p)) arr.put(p)
        }
        return arr.toString()
    }

    @JavascriptInterface
    fun toast(text: String) = act.runOnUiThread { Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show() }

    @JavascriptInterface
    fun appVersion(): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    // ---- helpers ------------------------------------------------------------------------------

    private fun isInstalled(pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun norm(s: String): String =
        s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
}
