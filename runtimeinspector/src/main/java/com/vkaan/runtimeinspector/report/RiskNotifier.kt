package com.vkaan.runtimeinspector.report

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vkaan.runtimeinspector.RuntimeInspector
import com.vkaan.runtimeinspector.rules.Risk

internal class RiskNotifier (
    private val context: Context,
) {
    private companion object {
        // Android freezes a channel's settings once it exists on a device, so the id carries a
        // version suffix: changing importance means shipping a new channel, not editing this one.
        const val CHANNEL_ID = "runtimeinspector_risks_v2"
        const val CHANNEL_NAME = "Runtime risks"
    }

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun notify(risk: Risk) {
        val manager = manager ?: return

        val title = "[${risk.severity.name}] ${risk.ruleId}" +
            if (risk.occurrences > 1) " ×${risk.occurrences}" else ""
        val text = risk.label()?.let { "$it — ${risk.message}" } ?: risk.message

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            // Channels carry importance from API 26 on; below that the banner is driven by
            // priority, so both are set to keep behaviour identical across the fleet.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openFinding(risk))
            .build()

        // Same dedup key -> same notification ID, so a repeat updates the existing
        // notification with its new count instead of stacking another one.
        manager.notify(risk.dedupKey.hashCode(), notification)
    }

    /**
     * Opens whatever the inspector app's launcher Activity is, carrying which finding was tapped.
     * Resolved through the package manager so this module needs no reference to that Activity.
     */
    private fun openFinding(risk: Risk): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launch.putExtra(RuntimeInspector.EXTRA_RULE_ID, risk.ruleId)
        launch.putExtra(RuntimeInspector.EXTRA_SUBJECT, risk.subject)
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        // One PendingIntent per finding, or they would all carry the first one's extras.
        return PendingIntent.getActivity(
            context,
            risk.dedupKey.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
