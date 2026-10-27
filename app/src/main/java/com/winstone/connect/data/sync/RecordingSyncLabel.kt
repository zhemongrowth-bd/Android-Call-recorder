package com.winstone.connect.data.sync

/**
 * One honest label for the phone's recording sync, derived from SyncStatus
 * counters. Priority: Failed > Syncing > Pending Sync > Synced > Available.
 * "Synced" needs a server-confirmed audio upload; nothing is assumed.
 */
enum class RecordingSyncLabel(val english: String, val bangla: String) {
    AVAILABLE("Available", "ফোনে আছে"),
    PENDING_SYNC("Pending Sync", "পাঠানোর অপেক্ষায়"),
    SYNCING("Syncing", "পাঠানো হচ্ছে"),
    SYNCED("Synced", "সার্ভারে নিশ্চিত"),
    FAILED("Failed", "ব্যর্থ — আবার চেষ্টা করুন");

    companion object {
        /** Label for ONE recording from its own upload job. Synced only after the worker succeeded (server confirmed). */
        fun ofWork(state: androidx.work.WorkInfo.State?, audioOnPhone: Boolean): RecordingSyncLabel = when (state) {
            androidx.work.WorkInfo.State.RUNNING -> SYNCING
            androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.BLOCKED -> PENDING_SYNC
            androidx.work.WorkInfo.State.SUCCEEDED -> SYNCED
            androidx.work.WorkInfo.State.FAILED, androidx.work.WorkInfo.State.CANCELLED -> FAILED
            null -> if (audioOnPhone) AVAILABLE else FAILED
        }

        fun of(s: SyncStatus.Snapshot): RecordingSyncLabel = when {
            s.failed > 0 -> FAILED
            s.uploading > 0 -> SYNCING
            s.pending > 0 -> PENDING_SYNC
            s.lastAudioUploadedAtMillis > 0 -> SYNCED
            else -> AVAILABLE
        }
    }
}

/** Details of one queued recording, read from its upload job's tags (pure, unit-tested). */
data class RecordingRowInfo(val phone: String, val durationSeconds: Int?, val queuedAtMillis: Long?, val uploadId: String?) {
    companion object {
        fun fromTags(tags: Set<String>): RecordingRowInfo {
            fun v(prefix: String) = tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
            return RecordingRowInfo(
                phone = v(CallSyncQueue.TAG_PHONE_PREFIX).orEmpty().ifBlank { "Unknown number" },
                durationSeconds = v(CallSyncQueue.TAG_DURATION_PREFIX)?.toIntOrNull()?.takeIf { it >= 0 },
                queuedAtMillis = v(CallSyncQueue.TAG_QUEUED_AT_PREFIX)?.toLongOrNull()?.takeIf { it > 0 },
                uploadId = v(CallSyncQueue.TAG_UPLOAD_PREFIX),
            )
        }
    }
}

/** Filter chips for the Phone recordings list; null = All. */
object RecordingFilter {
    val choices: List<RecordingSyncLabel?> = listOf(null, RecordingSyncLabel.PENDING_SYNC, RecordingSyncLabel.SYNCING, RecordingSyncLabel.SYNCED, RecordingSyncLabel.FAILED)
    fun matches(filter: RecordingSyncLabel?, label: RecordingSyncLabel) = filter == null || filter == label
}
