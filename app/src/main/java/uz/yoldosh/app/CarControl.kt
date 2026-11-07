package uz.yoldosh.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method

/**
 * Talks to the BYD head unit's own vehicle API (android.hardware.bydauto.*).
 *
 * On a stock, unrooted DiLink the write permissions are signature-level, so a sideloaded app may be
 * refused. There is no single way that works on every firmware, so this module is DISCOVERY-FIRST:
 * `probe()` reports exactly what THIS car exposes (classes, methods, exported BYD services), and the
 * setters attempt the direct reflection path and report honestly whether it worked. We wire the exact
 * working calls for the Champion once we see a real probe from the car.
 *
 * Safety: this module never touches driver-assistance or braking systems — comfort functions only.
 */
object CarControl {

    // Class names that are public knowledge across DiLink research. Presence is firmware-dependent.
    private val AC_CLASSES = listOf(
        "android.hardware.bydauto.ac.BYDAutoAcDevice",
        "android.hardware.bydauto.ac.BydAutoAcDevice",
    )
    private val WINDOW_CLASSES = listOf(
        "android.hardware.bydauto.window.BYDAutoWindowDevice",
        "android.hardware.bydauto.door.BYDAutoDoorDevice",
    )
    private val MANAGER_CLASSES = listOf(
        "android.hardware.bydauto.BYDAutoDeviceManager",
        "android.hardware.bydauto.BYDAutoManager",
    )

    /** A context that answers "granted" to permission checks. Helps on permissive firmware only. */
    private class GrantContext(base: Context) : ContextWrapper(base) {
        override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int = PackageManager.PERMISSION_GRANTED
        override fun checkCallingPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
        override fun checkCallingOrSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
        override fun getApplicationContext(): Context = this
    }

    private fun firstClass(names: List<String>): Class<*>? {
        for (n in names) {
            try { return Class.forName(n) } catch (_: Throwable) {}
        }
        return null
    }

    /** getInstance(Context) / getInstance() on the device class. */
    private fun instance(ctx: Context, cls: Class<*>): Any? {
        for (m in cls.methods) {
            if (m.name != "getInstance" && m.name != "getDefault") continue
            try {
                return when (m.parameterTypes.size) {
                    0 -> m.invoke(null)
                    1 -> m.invoke(null, GrantContext(ctx.applicationContext))
                    else -> continue
                }
            } catch (_: Throwable) {}
        }
        // some builds expose a public constructor
        try { return cls.getConstructor(Context::class.java).newInstance(GrantContext(ctx.applicationContext)) } catch (_: Throwable) {}
        try { return cls.getConstructor().newInstance() } catch (_: Throwable) {}
        return null
    }

    /** Finds a method whose name contains any keyword and whose arg types match (by count+kind). */
    private fun pick(obj: Any, keywords: List<String>, argKinds: List<Class<*>>): Method? {
        val lowers = keywords.map { it.lowercase() }
        return obj.javaClass.methods.firstOrNull { m ->
            val n = m.name.lowercase()
            lowers.any { n.contains(it) } &&
                m.parameterTypes.size == argKinds.size &&
                m.parameterTypes.indices.all { matches(m.parameterTypes[it], argKinds[it]) }
        }
    }

    private fun matches(have: Class<*>, want: Class<*>): Boolean {
        if (want == Int::class.javaPrimitiveType) return have == Int::class.javaPrimitiveType || have == Integer::class.java
        if (want == Boolean::class.javaPrimitiveType) return have == Boolean::class.javaPrimitiveType || have == java.lang.Boolean::class.java
        if (want == Float::class.javaPrimitiveType) return have == Float::class.javaPrimitiveType || have == java.lang.Float::class.java
        return want.isAssignableFrom(have)
    }

    // ---------------------------------------------------------------- public result type

    data class Result(val ok: Boolean, val detail: String)

    private fun available(ctx: Context): Boolean = firstClass(AC_CLASSES) != null

    // ---------------------------------------------------------------- climate

    /** Sets cabin temperature in °C. Returns ok=false with a reason if this firmware refuses. */
    fun setTemperature(ctx: Context, celsius: Int): Result {
        val cls = firstClass(AC_CLASSES) ?: return Result(false, "no-ac-class")
        val obj = instance(ctx, cls) ?: return Result(false, "no-instance")
        val t = celsius.coerceIn(16, 33)
        // Try an int setter first (most firmwares), then a float setter.
        val mi = pick(obj, listOf("temp"), listOf(Int::class.javaPrimitiveType!!))
        if (mi != null) return invoke(mi, obj, t)
        val mf = pick(obj, listOf("temp"), listOf(Float::class.javaPrimitiveType!!))
        if (mf != null) return invoke(mf, obj, t.toFloat())
        // Some APIs take (zone, value)
        val mz = pick(obj, listOf("temp"), listOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!))
        if (mz != null) return invoke(mz, obj, 0, t)
        return Result(false, "no-temp-setter")
    }

    fun setAc(ctx: Context, on: Boolean): Result {
        val cls = firstClass(AC_CLASSES) ?: return Result(false, "no-ac-class")
        val obj = instance(ctx, cls) ?: return Result(false, "no-instance")
        val mb = pick(obj, listOf("acstatus", "acpower", "setac", "power", "switch", "enable"), listOf(Boolean::class.javaPrimitiveType!!))
        if (mb != null) return invoke(mb, obj, on)
        val mi = pick(obj, listOf("acstatus", "acpower", "setac", "power"), listOf(Int::class.javaPrimitiveType!!))
        if (mi != null) return invoke(mi, obj, if (on) 1 else 0)
        return Result(false, "no-ac-setter")
    }

    fun setFan(ctx: Context, level: Int): Result {
        val cls = firstClass(AC_CLASSES) ?: return Result(false, "no-ac-class")
        val obj = instance(ctx, cls) ?: return Result(false, "no-instance")
        val m = pick(obj, listOf("wind", "fan", "blow"), listOf(Int::class.javaPrimitiveType!!))
            ?: return Result(false, "no-fan-setter")
        return invoke(m, obj, level.coerceIn(0, 7))
    }

    // ---------------------------------------------------------------- windows

    /** which: 0=all, 1..4 per window; open=true lowers. */
    fun window(ctx: Context, which: Int, open: Boolean): Result {
        val cls = firstClass(WINDOW_CLASSES) ?: return Result(false, "no-window-class")
        val obj = instance(ctx, cls) ?: return Result(false, "no-instance")
        val m2 = pick(obj, listOf("window", "pos"), listOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!))
        if (m2 != null) return invoke(m2, obj, which, if (open) 100 else 0)
        val m1 = pick(obj, listOf("window", "pos"), listOf(Int::class.javaPrimitiveType!!))
        if (m1 != null) return invoke(m1, obj, if (open) 100 else 0)
        return Result(false, "no-window-setter")
    }

    private fun invoke(m: Method, obj: Any, vararg args: Any?): Result {
        return try {
            m.isAccessible = true
            val r = m.invoke(obj, *args)
            Result(true, "${m.name}(${args.joinToString(",")}) -> $r")
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            Result(false, "${m.name}: ${cause.javaClass.simpleName}: ${cause.message}")
        }
    }

    // ---------------------------------------------------------------- diagnostics

    /** Read-only discovery of what the car exposes. Safe to run; changes nothing. */
    fun probe(ctx: Context): JSONObject {
        val o = JSONObject()
        o.put("available", available(ctx))

        val classes = JSONObject()
        for (name in AC_CLASSES + WINDOW_CLASSES + MANAGER_CLASSES) {
            classes.put(name, try { Class.forName(name); true } catch (_: Throwable) { false })
        }
        o.put("classes", classes)

        // Methods of the AC device, so we can see the real setter names on this firmware.
        val acCls = firstClass(AC_CLASSES)
        if (acCls != null) {
            val methods = JSONArray()
            val obj = try { instance(ctx, acCls) } catch (_: Throwable) { null }
            o.put("acInstance", obj != null)
            val src = obj?.javaClass ?: acCls
            for (m in src.methods.sortedBy { it.name }) {
                if (m.declaringClass == Any::class.java) continue
                val sig = m.name + "(" + m.parameterTypes.joinToString(",") { it.simpleName } + "):" + m.returnType.simpleName
                methods.put(sig)
            }
            o.put("acMethods", methods)
        }

        // Exported services of BYD / voice packages — the safe "assistant bridge" path lives here.
        val services = JSONArray()
        try {
            val pm = ctx.packageManager
            val pkgs = pm.getInstalledPackages(PackageManager.GET_SERVICES)
            for (pi in pkgs) {
                val pkg = pi.packageName
                if (!(pkg.startsWith("com.byd") || pkg.contains("voice") || pkg.contains("autovoice") || pkg.contains("dilink"))) continue
                pi.services?.forEach { s ->
                    if (s.exported) services.put("$pkg/${s.name}")
                }
            }
        } catch (t: Throwable) {
            o.put("servicesError", t.message)
        }
        o.put("exportedServices", services)
        return o
    }
}
