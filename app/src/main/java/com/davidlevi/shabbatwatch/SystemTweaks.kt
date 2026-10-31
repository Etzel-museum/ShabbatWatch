package com.davidlevi.shabbatwatch

import android.Manifest
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/** One watch setting and the value it gets during Shabbat. */
data class Tweak(val label: String, val table: String, val key: String, val offValue: String)

/**
 * System changes for Shabbat: total-silence Do Not Disturb (no notifications, no alarms,
 * no timers, no vibration), the built-in Galaxy Watch settings below, and any extra
 * settings the install script calibrated. Everything is saved first and put back by [restore].
 */
object SystemTweaks {

    // Found on a Galaxy Watch8 (SM-L330, One UI 8 Watch). A key the watch doesn't have is skipped.
    // Power saving goes last: with "extended" saving (low_power_additional=1, the watch default)
    // it also stops Samsung Health background measuring - heart rate, HRV, stress, skin
    // temperature, SpO2 and workout detection - which no app can switch off directly.
    private val GALAXY_DEFAULTS = listOf(
        Tweak("wrist", "global", "ambient_tilt_to_wake", "0"),
        Tweak("wrist", "global", "psm_ambient_tilt_to_wake", "0"),
        Tweak("tap", "global", "ambient_touch_to_wake", "0"),
        Tweak("tap", "secure", "double_tap_to_wake", "0"),
        Tweak("bezel", "global", "setting_wake_by_turning_bezel", "0"),
        Tweak("ungaze", "global", "ungaze_sleep_enabled", "0"),
        Tweak("psm", "global", "low_power", "1"),
    )

    // install.ps1 sends ASCII ids (Windows PowerShell 5.1 mangles Hebrew in scripts).
    // Labels the user typed for extra settings are shown as-is.
    private val LABELS = mapOf(
        "wrist" to "הרמת יד", "tap" to "נגיעה להערה", "palm" to "כיסוי בכף יד",
        "bezel" to "הערה בבזל", "ungaze" to "כיבוי מסך כשלא מביטים",
        "psm" to "חיסכון בסוללה + מדדי Samsung Health",
        "heart" to "דופק", "stress" to "מתח", "bp" to "לחץ דם", "spo2" to "חמצן בדם",
        "skin" to "טמפרטורת עור", "ages" to "מדד AGEs",
    )

    /** Built-in settings this watch actually has, then calibrated extras; power saving always last. */
    private fun tweaks(ctx: Context): List<Tweak> {
        val cr = ctx.contentResolver
        val all = GALAXY_DEFAULTS.filter { read(cr, it.table, it.key) != null } + Store.tweaks(ctx)
        val unique = all.distinctBy { it.table + "/" + it.key }
        return unique.sortedBy { it.label == "psm" }
    }

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun canWriteSecure(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun canDnd(ctx: Context) = nm(ctx).isNotificationPolicyAccessGranted

    fun canRelaunch(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun apply(ctx: Context) {
        val cr = ctx.contentResolver
        val saved = JSONArray()
        for (t in tweaks(ctx)) {
            val original = read(cr, t.table, t.key)
            if (write(cr, t.table, t.key, t.offValue)) {
                saved.put(JSONObject().put("table", t.table).put("key", t.key).put("value", original ?: JSONObject.NULL))
            }
        }
        Store.setSaved(ctx, saved.toString())

        if (canDnd(ctx)) {
            Store.setSavedDnd(ctx, nm(ctx).currentInterruptionFilter)
            nm(ctx).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
    }

    fun restore(ctx: Context) {
        val cr = ctx.contentResolver
        val saved = JSONArray(Store.saved(ctx))
        // Reverse order: power saving goes off first, so it can't overwrite what we restore after it.
        for (i in saved.length() - 1 downTo 0) {
            val o = saved.getJSONObject(i)
            write(cr, o.getString("table"), o.getString("key"), if (o.isNull("value")) null else o.getString("value"))
        }
        Store.setSaved(ctx, "[]")

        val dnd = Store.savedDnd(ctx)
        if (dnd != NotificationManager.INTERRUPTION_FILTER_UNKNOWN && canDnd(ctx)) {
            nm(ctx).setInterruptionFilter(dnd)
        }
        Store.setSavedDnd(ctx, NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
    }

    /** Called periodically during Shabbat in case something switched DND off. */
    fun reassertDnd(ctx: Context) {
        if (canDnd(ctx) && nm(ctx).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_NONE) {
            nm(ctx).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
    }

    /** Readiness lines for the setup screen. */
    fun status(ctx: Context): List<String> {
        val tweaks = tweaks(ctx)
        val lines = mutableListOf<String>()
        lines += mark(canDnd(ctx)) + " השתקה מלאה (גם שעון מעורר)"
        lines += mark(canRelaunch(ctx)) + " החזרת מסך השבת אוטומטית"
        lines += mark(canWriteSecure(ctx) && tweaks.isNotEmpty()) + " כיבוי בשבת: " +
            if (tweaks.isEmpty()) "לא נמצאו הגדרות" else tweaks.map { LABELS[it.label] ?: it.label }.distinct().joinToString(", ")
        if (!canDnd(ctx) || !canRelaunch(ctx) || !canWriteSecure(ctx)) {
            lines += "חסרות הרשאות - יש להריץ את install.ps1 מהמחשב."
        }
        return lines
    }

    private fun mark(ok: Boolean) = if (ok) "✓" else "✗"

    private fun read(cr: ContentResolver, table: String, key: String): String? = when (table) {
        "system" -> Settings.System.getString(cr, key)
        "secure" -> Settings.Secure.getString(cr, key)
        "global" -> Settings.Global.getString(cr, key)
        else -> null
    }

    // Returns false if this watch refuses the write (e.g. a private key in the system table).
    private fun write(cr: ContentResolver, table: String, key: String, value: String?): Boolean = try {
        when (table) {
            "system" -> Settings.System.putString(cr, key, value)
            "secure" -> Settings.Secure.putString(cr, key, value)
            "global" -> Settings.Global.putString(cr, key, value)
            else -> false
        }
    } catch (e: Exception) {
        false
    }
}
