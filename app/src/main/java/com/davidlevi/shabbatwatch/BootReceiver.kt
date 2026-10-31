package com.davidlevi.shabbatwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** If the watch restarts during Shabbat, go straight back into Shabbat mode. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Store.isActive(context)) {
            ShabbatMode.start(context)
        }
    }
}
