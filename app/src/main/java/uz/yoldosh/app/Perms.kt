package uz.yoldosh.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject

/** Checks and requests everything the onboarding screen lists. */
object Perms {

    fun status(ctx: Context): JSONObject {
        val pkg = ctx.packageName
        val o = JSONObject()
        o.put("mic", ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        o.put("location", ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        o.put("overlay", Settings.canDrawOverlays(ctx))
        val listeners = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        o.put("notif", listeners.contains(pkg))
        val a11y = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        o.put("a11y", a11y.contains(pkg))
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        o.put("battery", pm.isIgnoringBatteryOptimizations(pkg))
        o.put("autostart", Prefs.bool(ctx, "autostart", false))
        return o
    }

    fun request(act: Activity, key: String) {
        val pkg = act.packageName
        when (key) {
            "mic" -> act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            "location" -> act.requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2
            )
            "overlay" -> open(act, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")))
            "notif" -> open(act, Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            "a11y" -> open(act, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "battery" -> open(act, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
            "autostart" -> Prefs.put(act, "autostart", true)
        }
    }

    /** Opens a settings screen; head units often lack some of them, so fall back to the app's info page. */
    private fun open(act: Activity, intent: Intent) {
        try {
            act.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${act.packageName}")))
                Toast.makeText(act, "Этого раздела нет на магнитоле. Откройте его вручную в настройках.", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(act, "Не удалось открыть настройки", Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(act, "Магнитола запретила открыть этот раздел", Toast.LENGTH_LONG).show()
        }
    }
}
