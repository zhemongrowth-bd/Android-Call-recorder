package com.winstone.connect.telephony

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.winstone.connect.MainActivity

/**
 * Notification-led capture: when a call ends and no recording could be saved,
 * the phone itself asks the agent for the missing evidence.
 *
 * One tap opens the app straight into the voice-note recorder for that exact
 * call (lead id + number carried in the intent), so the call still lands in
 * the CRM with proof instead of silently becoming a "no recording" gap.
 * The notification is per-call (id = hash of phone+time) so two missed
 * captures never overwrite each other.
 */
object PostCallCaptureNotifier {

    const val ACTION_VOICE_NOTE = "com.winstone.connect.action.VOICE_NOTE"
    const val EXTRA_LEAD_ID = "voice_note_lead_id"
    const val EXTRA_PHONE = "voice_note_phone"

    private const val CHANNEL_ID = "winstone_capture_needed"

    fun prompt(context: Context, leadId: String?, phoneNumber: String) {
        val app = context.applicationContext
        ensureChannel(app)

        val open = Intent(app, MainActivity::class.java).apply {
            action = ACTION_VOICE_NOTE
            putExtra(EXTRA_LEAD_ID, leadId)
            putExtra(EXTRA_PHONE, phoneNumber)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val id = (phoneNumber + System.currentTimeMillis()).hashCode()
        val pending = PendingIntent.getActivity(
            app,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("কলের রেকর্ডিং পাওয়া যায়নি")
            .setContentText("$phoneNumber — এক ট্যাপে ভয়েস নোট রেকর্ড করুন")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            app.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "রেকর্ডিং বিকল্প", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "কল রেকর্ড না হলে ভয়েস নোট দেওয়ার অনুরোধ"
            },
        )
    }
}
