package com.winstone.connect.data.sync

import java.security.MessageDigest

/**
 * Privacy-safe sync diagnostics (mirrors src/lib/sync-diagnostics.ts).
 * Only fingerprints and technical fields; never phone numbers, names, audio, transcripts or tokens.
 */
object SyncDiagnostics {
    fun fingerprint(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val d = MessageDigest.getInstance("SHA-256").digest("wsdiag:$value".toByteArray())
        return d.take(6).joinToString("") { "%02x".format(it) }
    }

    fun reason(httpStatus: Int?, errorCode: String?, permissionGranted: Boolean?): String {
        val code = errorCode?.uppercase().orEmpty()
        return when {
            permissionGranted == false -> "permission_denied"
            code == "CALL_IDENTITY_CONFLICT" || code == "AUDIO_CONTENT_ALREADY_LINKED" -> "identity_conflict"
            code == "AUDIO_CHECKSUM_CONFLICT" -> "checksum_conflict"
            code == "FILE_MISSING" -> "file_missing"
            code == "NO_MATCHING_RECORDING" -> "no_matching_recording"
            code == "UNCONFIRMED_ACK" -> "unconfirmed_ack"
            code == "OFFLINE" || httpStatus == 0 -> "offline"
            httpStatus == 401 || httpStatus == 403 -> "auth_expired"
            httpStatus == 409 -> "lifecycle_not_synced"
            httpStatus == 429 -> "rate_limited"
            httpStatus != null && httpStatus >= 500 -> "server_error"
            httpStatus != null && httpStatus in 200..299 && code.isEmpty() -> "ok"
            else -> "unknown"
        }
    }

    /** One redacted line for the local sync log; safe to share with IT. */
    fun line(stage: String, callUid: String?, checksum: String?, httpStatus: Int?, errorCode: String?, attempt: Int, permissionGranted: Boolean?): String =
        "stage=$stage call=${fingerprint(callUid) ?: "-"} sum=${fingerprint(checksum) ?: "-"} " +
            "http=${httpStatus ?: "-"} reason=${reason(httpStatus, errorCode, permissionGranted)} attempt=${attempt.coerceIn(0, 99)}"
}
