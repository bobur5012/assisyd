package uz.yoldosh.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.KeyEvent
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Follows whatever app is playing (Yandex Music first) through Android media sessions.
 * Needs notification access, which the onboarding screen asks for.
 */
object MediaHub {
    const val YANDEX_MUSIC = "ru.yandex.music"

    var listener: ((JSONObject) -> Unit)? = null
    private lateinit var app: Context
    private var manager: MediaSessionManager? = null
    private var ctrl: MediaController? = null
    private var hasAccess = false
    private val main = Handler(Looper.getMainLooper())
    private var playAfterOpen = 0L
    private var artKey: String? = null
    private var artData: String = ""

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = push()
        override fun onPlaybackStateChanged(state: PlaybackState?) = push()
        override fun onSessionDestroyed() {
            ctrl = null
            attach()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        attach()
    }

    fun attach() {
        if (!::app.isInitialized) return
        val cn = ComponentName(app, MediaListener::class.java)
        try {
            val m = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            if (manager == null) {
                m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
                manager = m
            }
            hasAccess = true
            pick(m.getActiveSessions(cn))
        } catch (e: SecurityException) {
            hasAccess = false
            push()
        }
    }

    private fun pick(list: List<MediaController>?) {
        val c = list?.firstOrNull { it.packageName == YANDEX_MUSIC }
            ?: list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (c?.sessionToken != ctrl?.sessionToken) {
            try { ctrl?.unregisterCallback(callback) } catch (_: Exception) {}
            ctrl = c
            c?.registerCallback(callback, main)
        }
        // "Включи музыку" opened Yandex Music: start playback as soon as its session appears.
        if (c != null && playAfterOpen > 0 && SystemClock.elapsedRealtime() - playAfterOpen < 20000) {
            playAfterOpen = 0
            main.postDelayed({ try { c.transportControls.play() } catch (_: Exception) {} }, 800)
        }
        push()
    }

    fun state(): JSONObject {
        val o = JSONObject()
        o.put("access", hasAccess)
        val c = ctrl
        if (c == null) {
            o.put("active", false)
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            o.put("playing", am.isMusicActive)
            return o
        }
        o.put("active", true)
        o.put("app", c.packageName)
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        o.put("title", title)
        o.put("artist", artist)
        o.put("duration", md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)

        val key = "$title|$artist"
        if (key != artKey) {
            artKey = key
            val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            artData = if (bmp != null) encode(bmp) else ""
        }
        o.put("art", artData)

        val ps = c.playbackState
        val playing = ps?.state == PlaybackState.STATE_PLAYING
        o.put("playing", playing)
        var pos = ps?.position ?: 0L
        if (playing && ps != null) {
            pos += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
        }
        o.put("position", pos)
        return o
    }

    private fun encode(src: Bitmap): String {
        return try {
            val side = 360
            val scaled = if (src.width > side || src.height > side) {
                val k = side.toFloat() / maxOf(src.width, src.height)
                Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true)
            } else src
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }

    fun push() {
        val l = listener ?: return
        main.post { l(state()) }
    }

    fun command(ctx: Context, cmd: String) {
        val t = ctrl?.transportControls
        if (t != null) {
            when (cmd) {
                "play" -> t.play()
                "pause" -> t.pause()
                "toggle" -> if (ctrl?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
                "next" -> t.skipToNext()
                "prev" -> t.skipToPrevious()
                "open" -> openMusic(ctx)
            }
            return
        }
        if (cmd == "open") { openMusic(ctx); return }
        // No media-session access (notification access not granted): use media buttons, like the steering wheel does.
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val wantPlay = cmd == "play" || (cmd == "toggle" && !am.isMusicActive)
        val key = when {
            cmd == "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            cmd == "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            wantPlay -> KeyEvent.KEYCODE_MEDIA_PLAY
            else -> KeyEvent.KEYCODE_MEDIA_PAUSE
        }
        sendKey(am, key)
        if (wantPlay) {
            // Nothing started (Yandex Music not running yet): start it, press play, come back to Yo'ldosh.
            main.postDelayed({
                if (!am.isMusicActive && openMusic(ctx)) {
                    playAfterOpen = SystemClock.elapsedRealtime()
                    main.postDelayed({ sendKey(am, KeyEvent.KEYCODE_MEDIA_PLAY); attach() }, 3500)
                    main.postDelayed({
                        try {
                            ctx.startActivity(Intent(ctx, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                        } catch (_: Exception) {}
                    }, 4500)
                }
                push()
            }, 1200)
        }
        main.postDelayed({ push() }, 700)
    }

    private fun sendKey(am: AudioManager, code: Int) {
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    fun openMusic(ctx: Context): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(YANDEX_MUSIC) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
    }
}
