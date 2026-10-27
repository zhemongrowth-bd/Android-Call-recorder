package com.winstone.connect.telephony

import android.provider.CallLog

/**
 * Pure mapping from one Android call-log row to what the CRM is told.
 *
 * `state` keeps the existing CRM contract (completed / no_answer), so no server
 * change is needed. `kind` is the finer phone-side label (missed, rejected,
 * blocked…) shown on the device; it is only as accurate as the OEM call log —
 * some phones record a rejected call as MISSED.
 */
object CallLogClassifier {
    data class Result(val direction: String, val state: String, val kind: String)

    fun classify(type: Int, durationSeconds: Int): Result {
        val duration = durationSeconds.coerceAtLeast(0)
        val direction = if (type == CallLog.Calls.OUTGOING_TYPE) "outgoing" else "incoming"
        val kind = when (type) {
            CallLog.Calls.OUTGOING_TYPE -> if (duration > 0) "outgoing_connected" else "outgoing_unanswered"
            CallLog.Calls.INCOMING_TYPE -> if (duration > 0) "incoming_answered" else "incoming_unanswered"
            CallLog.Calls.MISSED_TYPE -> "missed"
            CallLog.Calls.REJECTED_TYPE -> "rejected"
            CallLog.Calls.BLOCKED_TYPE -> "blocked"
            CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
            7 /* ANSWERED_EXTERNALLY_TYPE, API 25; minSdk is 24 */ -> "answered_elsewhere"
            else -> "unknown"
        }
        val state = when (kind) {
            "outgoing_connected", "incoming_answered" -> "completed"
            else -> "no_answer"
        }
        return Result(direction, state, kind)
    }
}
