package com.adgamepriority.ad_fast_dismiss.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.adgamepriority.ad_fast_dismiss.util.NotificationHelper

/**
 * PersistentForegroundService
 * ============================
 * Keeps the app process in the foreground priority bucket so Android
 * does not kill GamePriorityService under memory pressure.
 *
 * FIX BUG-14: startForeground() called WITHOUT a service type.
 * Using FOREGROUND_SERVICE_TYPE_DATA_SYNC required the extra permission
 * FOREGROUND_SERVICE_DATA_SYNC on Android 14+, causing a crash.
 * Plain startForeground() (no type) works on all APIs 26–34+ with just
 * the FOREGROUND_SERVICE permission already declared in the manifest.
 */
class PersistentForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        // No type argument — compatible with all API levels 26+
        startForeground(
            NotificationHelper.SERVICE_NOTIF_ID,
            NotificationHelper.buildServiceNotification(this)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
