package com.winstone.connect.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.winstone.connect.MainActivity
import com.winstone.connect.data.AgentSession
import com.winstone.connect.data.remote.WinstoneApi
import java.util.concurrent.TimeUnit

/**
 * Reminder notifications on the phone: an unfinished post-call report, or a
 * follow-up whose time has arrived. The list comes from the same server feed the
 * web CRM bell uses, so both surfaces remind about exactly the same items.
 *
 * These are local notifications, so reminders work even when push is not
 * configured; the server push (when live) covers the app being force-stopped.
 */
class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AgentSession.employeeIdNow(applicationContext) ?: return Result.success()

        val body = runCatching { WinstoneApi.reminders() }.getOrNull() ?: return Result.retry()
        val items = body.optJSONArray("reminders") ?: return Result.success()

        ensureChannel(applicationContext)
        val manager = NotificationManagerCompat.from(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val kind = item.optString("kind")
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(item.optString("title"))
                .setContentText(item.optString("detail"))
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.optString("detail")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            // Stable id per reminder: an item still due only updates its own card.
            runCatching { manager.notify("$kind:$id".hashCode(), notification) }
        }
        val notices = runCatching { WinstoneApi.notices().optJSONArray("notices") }.getOrNull()
        val noticePrefs = applicationContext.getSharedPreferences("it_notice_delivery", Context.MODE_PRIVATE)
        if (notices != null) for (index in 0 until notices.length()) {
            val item = notices.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isBlank() || noticePrefs.getBoolean(id, false)) continue
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("IT Council · ${item.optString("title")}")
                .setContentText(item.optString("body"))
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.optString("body")))
                .setPriority(if (item.optString("severity") == "urgent") NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            runCatching { manager.notify("it_notice:$id".hashCode(), notification) }
                .onSuccess { noticePrefs.edit().putBoolean(id, true).apply() }
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "winstone_reminders"
        private const val UNIQUE = "reminder_sweep"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "রিপোর্ট ও ফলো-আপ রিমাইন্ডার",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
