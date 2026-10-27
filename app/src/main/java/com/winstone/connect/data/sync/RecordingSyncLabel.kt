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
        fun of(s: SyncStatus.Snapshot): RecordingSyncLabel = when {
            s.failed > 0 -> FAILED
            s.uploading > 0 -> SYNCING
            s.pending > 0 -> PENDING_SYNC
            s.lastAudioUploadedAtMillis > 0 -> SYNCED
            else -> AVAILABLE
        }
    }
}
