package com.davidlevi.shabbatwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives extra watch settings (beyond SystemTweaks' built-in Galaxy list) that
 * scripts/install.ps1 -Calibrate discovered, e.g.:
 *   am broadcast -a com.davidlevi.shabbatwatch.ADD_TWEAK -n com.davidlevi.shabbatwatch/.CalibrationReceiver
 *      --es label wrist --es table secure --es key some_key --es off 0
 * Protected by android.permission.DUMP, so only adb shell can send it.
 */
class CalibrationReceiver : BroadcastReceiver() {

    companion object {
        const val ADD = "com.davidlevi.shabbatwatch.ADD_TWEAK"
        const val CLEAR = "com.davidlevi.shabbatwatch.CLEAR_TWEAKS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CLEAR -> Store.clearTweaks(context)
            ADD -> {
                val label = intent.getStringExtra("label") ?: return
                val table = intent.getStringExtra("table") ?: return
                val key = intent.getStringExtra("key") ?: return
                val off = intent.getStringExtra("off") ?: return
                if (table !in setOf("system", "secure", "global")) return
                Store.addTweak(context, Tweak(label, table, key, off))
            }
        }
        resultData = Store.tweaks(context).joinToString(";") { "${it.table}/${it.key}=${it.offValue}" }
    }
}
