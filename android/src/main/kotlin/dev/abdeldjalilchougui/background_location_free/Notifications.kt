package dev.abdeldjalilchougui.background_location_free

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat

/** Builds the foreground service notification from a [NotificationSpec]. */
internal object Notifications {
    const val ID = 0x6c6f63

    fun build(context: Context, spec: NotificationSpec): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(spec.channelId) == null) {
                manager?.createNotificationChannel(
                    NotificationChannel(spec.channelId, spec.channelName, NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
        val icon = spec.resolveIcon(
            { name, type -> context.resources.getIdentifier(name, type, context.packageName) },
            context.applicationInfo.icon,
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val builder = NotificationCompat.Builder(context, spec.channelId)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setSmallIcon(icon)
            .setOngoing(spec.ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        spec.color?.let { builder.setColor(it) }
        if (launch != null) {
            PendingIntentCompat.getActivity(context, 0, launch, 0, false)
                ?.let { builder.setContentIntent(it) }
        }
        return builder.build()
    }

    /** Replaces the shown notification. Silently hidden without POST_NOTIFICATIONS. */
    fun show(context: Context, spec: NotificationSpec) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ID, build(context, spec))
    }
}
