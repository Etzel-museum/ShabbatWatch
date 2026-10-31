package com.davidlevi.shabbatwatch

import android.content.Context
import android.content.Intent

/** Turning Shabbat mode on and off: system settings, the keep-alive service and the screen. */
object ShabbatMode {

    fun start(ctx: Context) {
        // Save and change settings only once, so a second start can't overwrite the saved originals.
        if (!Store.isActive(ctx)) SystemTweaks.apply(ctx)
        Store.setActive(ctx, true)
        ctx.startForegroundService(Intent(ctx, ShabbatService::class.java))
        ctx.startActivity(Intent(ctx, ShabbatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun stop(ctx: Context) {
        if (Store.isActive(ctx)) {
            SystemTweaks.restore(ctx)
            Store.setActive(ctx, false)
        }
        ctx.stopService(Intent(ctx, ShabbatService::class.java))
    }
}
