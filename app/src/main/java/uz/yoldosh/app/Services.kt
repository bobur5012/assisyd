package uz.yoldosh.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import kotlin.math.abs

fun listenIntent(ctx: Context): Intent =
    Intent(ctx, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        .putExtra("listen", true)

/** Needed only so Android lets us read the media sessions of Yandex Music. */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaHub.attach()
    }
}

/** Catches the voice button on the steering wheel (if the head unit passes it on). */
class KeyService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) Prefs.put(this, "lastKey", event.keyCode)
        val custom = Prefs.int(this, "wheelKey", -1)
        val isVoiceKey = event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                event.keyCode == KeyEvent.KEYCODE_ASSIST ||
                event.keyCode == KeyEvent.KEYCODE_SEARCH ||
                (custom > 0 && event.keyCode == custom)
        if (!isVoiceKey) return false
        if (event.action == KeyEvent.ACTION_UP) startActivity(listenIntent(this))
        return true
    }
}

/** Floating mic button shown over other apps (navigator, music) while Yo'ldosh is in the background. */
class BubbleService : Service() {
    private var view: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("bubble", "Yo'ldosh", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "bubble")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Yo'ldosh рядом")
            .setContentText("Нажмите на круглую кнопку, чтобы позвать ассистента")
            .setOngoing(true)
            .build()
        startForeground(1, n)
        addBubble()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val size = (76 * d).toInt()
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            x = (24 * d).toInt()
            y = Prefs.int(this@BubbleService, "bubbleY", (220 * d).toInt())
        }
        val v = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            contentDescription = "Позвать ассистента"
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 12 * d || abs(dy) > 12 * d) moved = true
                    if (moved) {
                        lp.x = (startX - dx).toInt()
                        lp.y = (startY + dy).toInt()
                        wm.updateViewLayout(v, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) Prefs.put(this, "bubbleY", lp.y) else startActivity(listenIntent(this))
                }
            }
            true
        }
        wm.addView(v, lp)
        view = v
    }

    override fun onDestroy() {
        view?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        view = null
        super.onDestroy()
    }
}

/** Starts Yo'ldosh when the head unit boots, if the user turned autostart on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.bool(context, "autostart", false)) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            if (Settings.canDrawOverlays(context) && Prefs.bool(context, "bubble", true)) {
                try { context.startForegroundService(Intent(context, BubbleService::class.java)) } catch (_: Exception) {}
            }
        }
    }
}
