package com.winstone.connect.telephony

/**
 * Pure matching rule for the phone's own call recorder (Samsung, Xiaomi, Realme,
 * Oppo, Vivo, Google Dialer...). Kept free of Android types so it is JVM-tested.
 *
 * A candidate file belongs to a call only when ALL hold:
 *  - it is audio saved in a call-recording folder, or its name carries the
 *    called number's last 7 digits;
 *  - it was written between call start - 15 s and call end + 3 min;
 *  - its own length (when the OS reports it) is not wildly off the call length.
 * The closest file to the call end wins. No match = no upload; we never guess.
 */
data class RecordingCandidate(
    val id: Long,
    val displayName: String,
    /** RELATIVE_PATH (Android 10+) or full DATA path on older phones. */
    val path: String,
    /** File timestamp (date added / modified) in millis. */
    val writtenAtMs: Long,
    /** Audio length in millis, 0 when unknown. */
    val durationMs: Long,
)

object PhoneRecordingMatcher {

    const val BEFORE_START_MS = 15_000L
    const val AFTER_END_MS = 180_000L

    private val FOLDER_HINTS = listOf(
        "call", "record", "রেকর্ড", "sound_recorder", "callrec", "phonerecord",
    )

    fun digitsTail(phone: String?): String? =
        phone?.filter { it.isDigit() }?.takeLast(7)?.takeIf { it.length == 7 }

    fun looksLikeCallRecording(c: RecordingCandidate, phoneTail: String?): Boolean {
        val p = c.path.lowercase()
        val n = c.displayName.lowercase()
        val folderHit = FOLDER_HINTS.any { p.contains(it) } && (p.contains("call") || n.contains("call") || phoneTail != null && n.filter { it.isDigit() }.contains(phoneTail))
        val numberHit = phoneTail != null && n.filter { it.isDigit() }.contains(phoneTail)
        return folderHit || numberHit
    }

    fun pick(
        candidates: List<RecordingCandidate>,
        callStartMs: Long,
        callEndMs: Long,
        phone: String?,
    ): RecordingCandidate? {
        val tail = digitsTail(phone)
        val callMs = (callEndMs - callStartMs).coerceAtLeast(0)
        return candidates
            .filter { it.writtenAtMs in (callStartMs - BEFORE_START_MS)..(callEndMs + AFTER_END_MS) }
            .filter { looksLikeCallRecording(it, tail) }
            .filter { it.durationMs == 0L || callMs == 0L || it.durationMs <= callMs + 30_000L }
            .minByOrNull { kotlin.math.abs(it.writtenAtMs - callEndMs) }
    }
}
