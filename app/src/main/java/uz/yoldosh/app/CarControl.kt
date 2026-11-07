package uz.yoldosh.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Talks to the BYD head unit's climate API (android.hardware.bydauto.ac.BYDAutoAcDevice), confirmed
 * present on this car. The device is obtained once on the main thread (binder services need a Looper).
 *
 * Real methods on this firmware (from the probe):
 *   getInstance(Context):BYDAutoAcDevice
 *   setAcTemperature(int,int,int,int):int   setAcWindLevel(int,int):int   setAcWarmState(int):int
 *   setAcMaxCoolingState(int):int           getTemprature(int):int        getAcStartState():int
 *   getSetPermission():String               getGetPermission():String
 *
 * Safety: climate/comfort only — never driver-assistance or braking.
 */
object CarControl {

    private const val AC_CLASS = "android.hardware.bydauto.ac.BYDAutoAcDevice"
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var acCls: Class<*>? = null
    @Volatile private var ac: Any? = null
    @Volatile private var initError: String = "not-initialized"
    @Volatile private var initialized = false

    data class Result(val ok: Boolean, val detail: String)

    /** Call once, early, from the main thread. Safe to call again (no-op after success). */
    fun init(ctx: Context) {
        if (ac != null) return
        runOnMain {
            try {
                val cls = Class.forName(AC_CLASS)
                acCls = cls
                val m = cls.methods.firstOrNull { it.name == "getInstance" && it.parameterTypes.size == 1 }
                if (m == null) { initError = "no-getInstance"; return@runOnMain }
                val inst = m.invoke(null, ctx.applicationContext)
                if (inst == null) { initError = "getInstance-returned-null"; return@runOnMain }
                ac = inst
                initError = "ok"
            } catch (t: Throwable) {
                val c = t.cause ?: t
                initError = "${c.javaClass.simpleName}: ${c.message}"
            } finally {
                initialized = true
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) { block(); return }
        val latch = CountDownLatch(1)
        main.post { try { block() } finally { latch.countDown() } }
        try { latch.await(4, TimeUnit.SECONDS) } catch (_: InterruptedException) {}
    }

    private fun ensure(ctx: Context): Any? {
        if (ac == null) init(ctx)
        return ac
    }

    private fun method(obj: Any, name: String, argc: Int): Method? =
        obj.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == argc }

    private fun invoke(obj: Any, m: Method, vararg args: Any?): Result {
        var r: Result? = null
        runOnMain {
            r = try {
                m.isAccessible = true
                val out = m.invoke(obj, *args)
                // Most BYD setters return int 0 on success.
                val ok = out !is Int || out == 0
                Result(ok, "${m.name}(${args.joinToString(",")}) -> $out")
            } catch (t: Throwable) {
                val c = t.cause ?: t
                Result(false, "${m.name}: ${c.javaClass.simpleName}: ${c.message}")
            }
        }
        return r ?: Result(false, "main-timeout")
    }

    // ---------------------------------------------------------------- climate commands

    /** Sets cabin temperature in °C (16..33). setAcTemperature(area, temp, ?, ?). */
    fun setTemperature(ctx: Context, celsius: Int): Result {
        val obj = ensure(ctx) ?: return Result(false, "no-instance ($initError)")
        val t = celsius.coerceIn(16, 33)
        val m = method(obj, "setAcTemperature", 4) ?: return Result(false, "no-setAcTemperature")
        // area 1 is the driver zone on most BYD builds; value is the raw °C.
        return invoke(obj, m, 1, t, 0, 0)
    }

    fun setAc(ctx: Context, on: Boolean): Result {
        val obj = ensure(ctx) ?: return Result(false, "no-instance ($initError)")
        // "AC on/off" on these units maps to the compressor / control mode.
        val m = method(obj, "setAcControlMode", 2) ?: return Result(false, "no-setAcControlMode")
        return invoke(obj, m, if (on) 1 else 0, 0)
    }

    fun setFan(ctx: Context, level: Int): Result {
        val obj = ensure(ctx) ?: return Result(false, "no-instance ($initError)")
        val m = method(obj, "setAcWindLevel", 2) ?: return Result(false, "no-setAcWindLevel")
        return invoke(obj, m, level.coerceIn(0, 7), 0)
    }

    fun setWarm(ctx: Context, on: Boolean): Result {
        val obj = ensure(ctx) ?: return Result(false, "no-instance ($initError)")
        val m = method(obj, "setAcWarmState", 1) ?: return Result(false, "no-setAcWarmState")
        return invoke(obj, m, if (on) 1 else 0)
    }

    /** Windows aren't in the AC device; needs a separate device not present on this firmware yet. */
    fun window(ctx: Context, which: Int, open: Boolean): Result =
        Result(false, "windows-not-available-on-this-firmware")

    // ---------------------------------------------------------------- reads (safe)

    private fun readInt(obj: Any, name: String, vararg args: Any?): Int? {
        var v: Int? = null
        runOnMain {
            try {
                val m = obj.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == args.size }
                v = m?.invoke(obj, *args) as? Int
            } catch (_: Throwable) {}
        }
        return v
    }

    private fun readString(obj: Any?, cls: Class<*>?, name: String): String? {
        var v: String? = null
        runOnMain {
            try {
                val m = (obj?.javaClass ?: cls)?.methods?.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
                    ?: return@runOnMain
                val target = if (Modifier.isStatic(m.modifiers)) null else obj ?: return@runOnMain
                v = m.invoke(target) as? String
            } catch (_: Throwable) {}
        }
        return v
    }

    // ---------------------------------------------------------------- diagnostics

    /** Read-only discovery; actuates nothing. */
    fun probe(ctx: Context): JSONObject {
        ensure(ctx)
        val o = JSONObject()
        o.put("available", acCls != null)
        o.put("acInstance", ac != null)
        o.put("initError", initError)

        val obj = ac
        val cls = acCls
        o.put("setPermission", readString(obj, cls, "getSetPermission") ?: "")
        o.put("getPermission", readString(obj, cls, "getGetPermission") ?: "")

        if (obj != null) {
            val reads = JSONObject()
            readInt(obj, "getTemprature", 1)?.let { reads.put("temp_area1", it) }
            readInt(obj, "getTemprature", 0)?.let { reads.put("temp_area0", it) }
            readInt(obj, "getAcStartState")?.let { reads.put("acStart", it) }
            readInt(obj, "getAcOnlineState")?.let { reads.put("acOnline", it) }
            readInt(obj, "getAcWindLevel")?.let { reads.put("windLevel", it) }
            readInt(obj, "getTemperatureUnit")?.let { reads.put("tempUnit", it) }
            o.put("reads", reads)
        }

        val methods = JSONArray()
        val src = obj?.javaClass ?: cls
        src?.methods?.filter { it.declaringClass != Any::class.java }?.sortedBy { it.name }?.forEach {
            methods.put(it.name + "(" + it.parameterTypes.joinToString(",") { p -> p.simpleName } + "):" + it.returnType.simpleName)
        }
        o.put("acMethods", methods)
        return o
    }
}
