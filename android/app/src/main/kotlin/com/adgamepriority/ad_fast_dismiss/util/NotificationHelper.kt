package com.adgamepriority.ad_fast_dismiss.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.adgamepriority.ad_fast_dismiss.MainActivity
import com.adgamepriority.ad_fast_dismiss.R

/**
 * NotificationHelper.kt
 * =====================
 * Manages two types of notifications:
 *
 * 1. PERSISTENT SERVICE NOTIFICATION
 *    Required for the Foreground Service to stay alive.
 *    Shows "AdFastDismiss Active" in the status bar.
 *    Low priority — does not make sound.
 *
 * 2. RETURN-TO-GAME NOTIFICATION
 *    Shows when a full-screen ad is detected and window shift fails.
 *    Has a "Return to Game" action button that re-launches the game.
 *    Automatically dismissed when the ad closes.
 */
object NotificationHelper {

    const val SERVICE_NOTIF_ID = 1001
    const val RETURN_NOTIF_ID = 1002

    private const val CHANNEL_SERVICE = "afd_service"
    private const val CHANNEL_RETURN  = "afd_return"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Service channel — silent, low importance
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "AdFastDismiss Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent service notification"
                setShowBadge(false)
            }

            // Return-to-game channel — slightly higher, single sound
            val returnChannel = NotificationChannel(
                CHANNEL_RETURN,
                "Return to Game",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alert when an ad is playing"
                setShowBadge(true)
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(returnChannel)
        }
    }

    /**
     * Builds the persistent foreground service notification.
     */
    fun buildServiceNotification(context: Context): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("🎮 PlayBlitz Active")
            .setContentText("Watching for ads — tap to open app")
            .setSmallIcon(R.drawable.ic_stat_afd)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Shows a "Return to Game" notification during a full-screen ad.
     * @param gamePkg Package name of the game to return to.
     */
    fun showReturnToGameNotification(context: Context, gamePkg: String) {
        val launchGameIntent = context.packageManager
            .getLaunchIntentForPackage(gamePkg)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

        if (launchGameIntent == null) return

        val pendingIntent = PendingIntent.getActivity(
            context,
            RETURN_NOTIF_ID,
            launchGameIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RETURN)
            .setContentTitle("📺 Ad is playing…")
            .setContentText("Tap to return to your game immediately")
            .setSmallIcon(R.drawable.ic_stat_afd)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_stat_afd,
                "▶ Return to Game",
                pendingIntent
            )
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RETURN_NOTIF_ID, notification)
    }

    /**
     * Dismisses the return-to-game notification once the ad closes.
     */
    fun dismissReturnNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(RETURN_NOTIF_ID)
    }
}
