package com.davidlevi.shabbatwatch

import android.Manifest
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/** One watch setting the install script found by calibration, and the value that turns it off. */
data class Tweak(val label: String, val table: String, val key: String, val offValue: String)

/**
 * System changes for Shabbat: total-silence Do Not Disturb (no notifications, no alarms,
 * no timers, no vibration) and the calibrated watch settings (raise-to-wake, tap-to-wake,
 * palm cover...). Everything is saved first and put back by [restore].
 */
object SystemTweaks {

    // install.ps1 sends ASCII ids (Windows PowerShell 5.1 mangles Hebrew in scripts).
    // Labels the user typed for extra settings are shown as-is.
    private val LABELS = mapOf(
        "wrist" to "הרמת יד", "tap" to "נגיעה להערה", "palm" to "כיסוי בכף יד",
        "heart" to "דופק", "stress" to "מתח", "bp" to "לחץ דם", "spo2" to "חמצן בדם",
        "skin" to "טמפרטורת עור", "ages" to "מדד AGEs",
    )

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun canWriteSecure(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun canDnd(ctx: Context) = nm(ctx).isNotificationPolicyAccessGranted

    fun canRelaunch(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun apply(ctx: Context) {
        val cr = ctx.contentResolver
        val saved = JSONArray()
        for (t in Store.tweaks(ctx)) {
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
        for (i in 0 until saved.length()) {
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
        val tweaks = Store.tweaks(ctx)
        val lines = mutableListOf<String>()
        lines += mark(canDnd(ctx)) + " השתקה מלאה (גם שעון מעורר)"
        lines += mark(canRelaunch(ctx)) + " החזרת מסך השבת אוטומטית"
        lines += mark(canWriteSecure(ctx) && tweaks.isNotEmpty()) + " כיבוי הגדרות שעון: " +
            if (tweaks.isEmpty()) "לא בוצע כיול" else tweaks.map { LABELS[it.label] ?: it.label }.distinct().joinToString(", ")
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
