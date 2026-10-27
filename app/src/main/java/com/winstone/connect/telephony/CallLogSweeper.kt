package com.winstone.connect.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.winstone.connect.data.sync.CallSyncQueue
import com.winstone.connect.data.sync.PendingReportWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The safety net that makes "every call becomes a report" true in practice.
 *
 * The live receivers catch calls while the app process is alive. They cannot
 * catch a call placed from WhatsApp or another dialer app while the app was
 * force-stopped, or a broadcast an OEM swallowed. Android still writes all of
 * those into the system call log, so on every app start (and every 15 minutes)
 * we read the log forward from a saved cursor and push anything unseen to the
 * CRM, which resolves the number to its existing lead — even a lead last called
 * fifty days ago — or creates one, and opens the mandatory report.
 *
 * Idempotency is layered: one stable `call_uid` per call-log row, one unique
 * WorkManager name per uid, and a server-side "same number, same minute" check
 * that folds a late log entry into the call the receiver already reported.
 */
object CallLogSweeper {

    private const val PREFS = "winstone_call_log"
    private const val KEY_CURSOR = "cursor_ms"
    private const val KEY_CURSOR_ID = "cursor_id"
    private const val MAX_PER_SWEEP = 40

    /** Never look further back than this on a fresh install / first grant. */
    private const val FIRST_RUN_WINDOW_MS = 3L * 24 * 60 * 60 * 1000

    fun sweep(context: Context): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) return 0

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cursor = prefs.getLong(KEY_CURSOR, now - FIRST_RUN_WINDOW_MS)
        val cursorId = prefs.getLong(KEY_CURSOR_ID, 0L)
        var newest = cursor
        var newestId = cursorId
        var queued = 0

        val rows = runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                ),
                "${CallLog.Calls.DATE} > ? OR (${CallLog.Calls.DATE} = ? AND ${CallLog.Calls._ID} > ?)",
                arrayOf(cursor.toString(), cursor.toString(), cursorId.toString()),
                "${CallLog.Calls.DATE} ASC, ${CallLog.Calls._ID} ASC LIMIT $MAX_PER_SWEEP",
            )
        }.getOrNull() ?: return 0

        rows.use { c ->
            val idCol = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numCol = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeCol = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateCol = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durCol = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                val at = c.getLong(dateCol)
                val rowId = c.getLong(idCol)
                // A call still in progress is left to the live receiver, and the
                // cursor is NOT advanced past it: if the receiver misses it, the
                // next sweep must still find the row.
                if (at > now - 20_000) continue
                if (CallLogCursor(at, rowId) > CallLogCursor(newest, newestId)) {
                    newest = at
                    newestId = rowId
                }
                val number = c.getString(numCol)?.trim().orEmpty()
                if (number.length < 5 || number.any { !it.isDigit() && it != '+' }) continue
                val duration = c.getInt(durCol).coerceAtLeast(0)
                val classified = CallLogClassifier.classify(c.getInt(typeCol), duration)
                val direction = classified.direction
                val state = classified.state
                CallSyncQueue.queueIncomingCall(
                    context = context,
                    callUid = "log-" + c.getLong(idCol) + "-" + number.filter(Char::isDigit).takeLast(11),
                    phoneNumber = number,
                    state = state,
                    durationSeconds = duration,
                    direction = direction,
                    source = "call_log",
                    atIso = iso(at),
                )
                queued++
            }
        }

        if (newest > cursor || (newest == cursor && newestId > cursorId)) {
            prefs.edit().putLong(KEY_CURSOR, newest).putLong(KEY_CURSOR_ID, newestId).apply()
        }
        // A call found afterwards (app was closed, call placed from WhatsApp or
        // the phone dialer) also owes a report: prompt for it on the phone.
        if (queued > 0) PendingReportWorker.prompt(context, delaySeconds = 10)
        return queued
    }

    private fun iso(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs))
    }
}
