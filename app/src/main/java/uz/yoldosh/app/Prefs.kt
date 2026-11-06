package uz.yoldosh.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("yoldosh", Context.MODE_PRIVATE)

    fun str(ctx: Context, key: String, def: String): String = sp(ctx).getString(key, def) ?: def
    fun bool(ctx: Context, key: String, def: Boolean): Boolean = sp(ctx).getBoolean(key, def)
    fun int(ctx: Context, key: String, def: Int): Int = sp(ctx).getInt(key, def)

    fun put(ctx: Context, key: String, value: String) = sp(ctx).edit().putString(key, value).apply()
    fun put(ctx: Context, key: String, value: Boolean) = sp(ctx).edit().putBoolean(key, value).apply()
    fun put(ctx: Context, key: String, value: Int) = sp(ctx).edit().putInt(key, value).apply()
}
