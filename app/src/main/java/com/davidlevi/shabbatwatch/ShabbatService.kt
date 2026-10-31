package com.davidlevi.shabbatwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

/**
 * Keeps the app alive for the whole Shabbat and brings the Shabbat screen back if
 * anything hides it (Home pressed, palm cover, the system returning to the watch face).
 */
class ShabbatService : Service() {

    companion object {
        private const val CHANNEL = "shabbat"
        private const val CHECK_MS = 15_000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val check = object : Runnable {
        override fun run() {
            val ctx = this@ShabbatService
            if (!Store.isActive(ctx)) {
                stopSelf()
                return
            }
            SystemTweaks.reassertDnd(ctx)
            if (!ShabbatActivity.isVisible) {
                try {
                    startActivity(Intent(ctx, ShabbatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // No overlay permission: nothing more we can do from the background.
                }
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "מצב שבת", NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("מצב שבת פעיל")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(check)
        handler.postDelayed(check, CHECK_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
