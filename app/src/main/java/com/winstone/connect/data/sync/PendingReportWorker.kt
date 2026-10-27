package com.winstone.connect.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.winstone.connect.MainActivity
import com.winstone.connect.data.AgentSession
import com.winstone.connect.data.remote.WinstoneApi
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * "Any call, any number -> the report popup appears on the phone."
 *
 * The in-app live overlay only exists for calls dialled inside the app. A call
 * placed from the phone's own dialer / WhatsApp, an incoming call, or a call
 * found later by the call-log sweeper finishes with the app in the background —
 * nothing would bring the report in front of the agent.
 *
 * This worker runs right after such a call ends: it polls the CRM until the
 * mandatory report row exists (the server opens it from the number, creating the
 * lead when it is new), then forces the report gate to the front of the phone —
 * a full-screen-intent notification plus a direct activity start where Android
 * still allows one. It is idempotent: if the report was already submitted, or
 * none is pending, it does nothing.
 */
class PendingReportWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AgentSession.employeeIdNow(applicationContext) ?: return Result.success()

        // The report is opened server-side from the queued call event, which may
        // still be in flight: poll for a short while instead of giving up.
        repeat(ATTEMPTS) { attempt ->
            val body = runCatching { WinstoneApi.pendingReport() }.getOrNull()
            val pending = body?.optJSONObject("pending")
            if (pending != null) {
                prompt(
                    applicationContext,
                    pending.optString("phone_number").takeIf { it.isNotBlank() },
                )
                return Result.success()
            }
            if (attempt < ATTEMPTS - 1) delay(POLL_DELAY_MS)
        }
        // Nothing yet (offline queue still pending): retry with backoff so the
        // agent is still prompted once the call reaches the CRM.
        return Result.retry()
    }

    companion object {
        private const val CHANNEL_ID = "winstone_pending_report"
        private const val NOTIFICATION_ID = 77001
        private const val UNIQUE = "pending_report_prompt"
        private const val ATTEMPTS = 6
        private const val POLL_DELAY_MS = 5_000L

        /** Ask for the report gate after a call the app could not show live. */
        fun prompt(context: Context, delaySeconds: Long = 6) {
            val request = OneTimeWorkRequestBuilder<PendingReportWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
        }

        private fun reportIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_REPORT
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        /** Brings the mandatory report in front of the agent, right now. */
        private fun prompt(context: Context, phoneNumber: String?) {
            ensureChannel(context)
            val intent = reportIntent(context)
            val open = PendingIntent.getActivity(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val detail = if (phoneNumber.isNullOrBlank()) {
                "শেষ কলের রিপোর্ট এখনো জমা হয়নি। রিপোর্ট না দিলে পরের কল করা যাবে না।"
            } else {
                "$phoneNumber — কলের রিপোর্ট এখনো জমা হয়নি। রিপোর্ট না দিলে পরের কল করা যাবে না।"
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("কলের রিপোর্ট বাধ্যতামূলক")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            // Where the OS still allows a background activity start, the report
            // opens itself without the agent tapping anything.
            runCatching { context.startActivity(reportIntent(context)) }
        }

        /** Called once the report is submitted, so the reminder card disappears. */
        fun clear(context: Context) {
            runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "বাধ্যতামূলক কল রিপোর্ট",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        const val ACTION_OPEN_REPORT = "com.winstone.connect.OPEN_PENDING_REPORT"
    }
}
