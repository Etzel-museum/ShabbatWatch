package com.davidlevi.shabbatwatch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** All persisted state. Writes that matter across a reboot use commit(), not apply(). */
object Store {

    val BRIGHTNESS_NAMES = listOf("נמוכה", "בינונית", "גבוהה")
    private val BRIGHTNESS_VALUES = listOf(0.02f, 0.12f, 0.35f)

    private fun p(ctx: Context) = ctx.getSharedPreferences("shabbat", Context.MODE_PRIVATE)

    fun isActive(ctx: Context) = p(ctx).getBoolean("active", false)
    fun setActive(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("active", v).commit()
    }

    fun cityIndex(ctx: Context) = p(ctx).getInt("city", 0).coerceIn(0, Cities.ALL.lastIndex)
    fun setCity(ctx: Context, i: Int) {
        // Picking a city resets candle lighting to that city's custom.
        p(ctx).edit().putInt("city", i).putInt("candle", Cities.ALL[i].candleMinutes).apply()
    }

    fun candleMinutes(ctx: Context) = p(ctx).getInt("candle", Cities.ALL[cityIndex(ctx)].candleMinutes)
    fun setCandleMinutes(ctx: Context, m: Int) {
        p(ctx).edit().putInt("candle", m).apply()
    }

    fun brightness(ctx: Context) = p(ctx).getInt("brightness", 0).coerceIn(0, BRIGHTNESS_NAMES.lastIndex)
    fun brightnessValue(ctx: Context) = BRIGHTNESS_VALUES[brightness(ctx)]
    fun setBrightness(ctx: Context, i: Int) {
        p(ctx).edit().putInt("brightness", i).apply()
    }

    // Settings found by the install script's calibration (see CalibrationReceiver).
    fun tweaks(ctx: Context): List<Tweak> {
        val arr = JSONArray(p(ctx).getString("tweaks", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Tweak(o.getString("label"), o.getString("table"), o.getString("key"), o.getString("off"))
        }
    }

    fun addTweak(ctx: Context, t: Tweak) {
        val kept = tweaks(ctx).filterNot { it.table == t.table && it.key == t.key } + t
        val arr = JSONArray()
        kept.forEach {
            arr.put(JSONObject().put("label", it.label).put("table", it.table).put("key", it.key).put("off", it.offValue))
        }
        p(ctx).edit().putString("tweaks", arr.toString()).commit()
    }

    fun clearTweaks(ctx: Context) {
        p(ctx).edit().putString("tweaks", "[]").commit()
    }

    // Original values saved when Shabbat mode starts, restored when it ends.
    fun saved(ctx: Context): String = p(ctx).getString("saved", "[]") ?: "[]"
    fun setSaved(ctx: Context, json: String) {
        p(ctx).edit().putString("saved", json).commit()
    }

    fun savedDnd(ctx: Context) = p(ctx).getInt("savedDnd", 0)
    fun setSavedDnd(ctx: Context, filter: Int) {
        p(ctx).edit().putInt("savedDnd", filter).commit()
    }
}
