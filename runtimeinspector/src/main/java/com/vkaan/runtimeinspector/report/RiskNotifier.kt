package com.vkaan.runtimeinspector.report

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vkaan.runtimeinspector.rules.Risk

internal class RiskNotifier (
    private val context: Context,
) {
    private companion object {
        const val CHANNEL_ID = "runtimeinspector_risks"
        const val CHANNEL_NAME = "Runtime risks"
    }

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun notify(risk: Risk) {
        val manager = manager ?: return

        val title = "[${risk.severity.name}] ${risk.ruleId}" +
            if (risk.occurrences > 1) " ×${risk.occurrences}" else ""
        val text = risk.subject?.let { "$it — ${risk.message}" } ?: risk.message

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        // Same dedup key -> same notification ID, so a repeat updates the existing
        // notification with its new count instead of stacking another one.
        manager.notify(risk.dedupKey.hashCode(), notification)
    }
}
