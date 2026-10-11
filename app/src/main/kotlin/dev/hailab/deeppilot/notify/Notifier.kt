package dev.hailab.deeppilot.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.hailab.deeppilot.R
import dev.hailab.deeppilot.core.protocol.Notify
import dev.hailab.deeppilot.core.protocol.NotifyCategory
import dev.hailab.deeppilot.ui.MainActivity

/**
 * Local notifications. Phase 1 has no remote push: everything shown here
 * arrived over the bridge socket that the foreground service keeps open.
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_INTERACTIONS, context.getString(R.string.channel_interactions), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_TURNS, context.getString(R.string.channel_turns), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_ERRORS, context.getString(R.string.channel_errors), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_CONNECTION, context.getString(R.string.channel_connection), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun show(notify: Notify, sessionTitle: String?) {
        if (!canPost()) return
        val (channel, fallbackTitle) = when (notify.category) {
            NotifyCategory.APPROVAL_REQUIRED -> CHANNEL_INTERACTIONS to R.string.notify_approval
            NotifyCategory.QUESTION_ASKED -> CHANNEL_INTERACTIONS to R.string.notify_question
            NotifyCategory.SESSION_ERROR -> CHANNEL_ERRORS to R.string.notify_error
            else -> CHANNEL_TURNS to R.string.notify_turn_completed
        }
        val title = notify.title.ifBlank { sessionTitle?.takeIf { it.isNotBlank() } ?: context.getString(fallbackTitle) }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(notify.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notify.body))
            .setSubText(if (notify.title.isNotBlank()) sessionTitle else null)
            .setWhen(if (notify.ts > 0) notify.ts else System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            // Prompts and code can be sensitive: hide the body on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(
                if (channel == CHANNEL_INTERACTIONS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_MESSAGE,
            )
            .setContentIntent(openSession(notify.sessionId))
            .build()
        try {
            manager.notify(notify.sessionId, notify.category.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    /** Clears what was posted for [sessionId] once the user opens it. */
    fun cancelSession(sessionId: String) {
        CATEGORIES.forEach { manager.cancel(sessionId, it.hashCode()) }
    }

    fun cancelAll() {
        manager.activeNotifications
            .filter { it.id != CONNECTION_NOTIFICATION_ID }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /** The ongoing notification the foreground service must show. */
    fun connectionNotification(text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.connection_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openSession(null))
            .build()

    private fun openSession(sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (sessionId != null) putExtra(MainActivity.EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getActivity(
            context,
            sessionId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_INTERACTIONS = "interactions"
        const val CHANNEL_TURNS = "turns"
        const val CHANNEL_ERRORS = "errors"
        const val CHANNEL_CONNECTION = "connection"
        const val CONNECTION_NOTIFICATION_ID = 1

        private val CATEGORIES = listOf(
            NotifyCategory.TURN_COMPLETED,
            NotifyCategory.APPROVAL_REQUIRED,
            NotifyCategory.QUESTION_ASKED,
            NotifyCategory.SESSION_ERROR,
        )
    }
}
