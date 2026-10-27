package com.winstone.connect.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the agent (and IT, when they ask) can see about syncing: how many items
 * are still waiting on the phone, when the last successful push happened, and
 * the last error message.
 *
 * Deliberately stores no phone numbers, no audio paths, no tokens — only
 * counters and a short error line, so debug output can never leak call data.
 */
object SyncStatus {
    private const val PREFS = "winstone_sync_status"
    private const val K_PENDING = "pending"
    private const val K_FAILED = "failed"
    private const val K_LAST_OK = "last_ok"
    private const val K_LAST_ERROR = "last_error"

    data class Snapshot(
        val pending: Int = 0,
        val failed: Int = 0,
        val lastSyncedAtMillis: Long = 0L,
        val lastError: String? = null,
        val lastAudioUploadedAtMillis: Long = 0L,
        val lastReportSyncedAtMillis: Long = 0L,
        /** Recordings uploading right now (in memory only, never persisted). */
        val uploading: Int = 0,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Snapshot {
        val p = prefs(context)
        val snap = Snapshot(
            pending = p.getInt(K_PENDING, 0).coerceAtLeast(0),
            failed = p.getInt(K_FAILED, 0).coerceAtLeast(0),
            lastSyncedAtMillis = p.getLong(K_LAST_OK, 0L),
            lastError = p.getString(K_LAST_ERROR, null),
            lastAudioUploadedAtMillis = p.getLong("last_audio", 0L),
            lastReportSyncedAtMillis = p.getLong("last_report", 0L),
        )
        _state.value = snap
        return snap
    }

    @Synchronized
    fun queued(context: Context) = update(context) { it.copy(pending = it.pending + 1) }

    @Synchronized
    fun succeeded(context: Context, audioUpload: Boolean = false, reportSync: Boolean = false) = update(context) {
        it.copy(
            pending = (it.pending - 1).coerceAtLeast(0),
            lastSyncedAtMillis = System.currentTimeMillis(),
            lastAudioUploadedAtMillis = if (audioUpload) System.currentTimeMillis() else it.lastAudioUploadedAtMillis,
            lastReportSyncedAtMillis = if (reportSync) System.currentTimeMillis() else it.lastReportSyncedAtMillis,
            lastError = null,
        )
    }

    /** Still queued, will be retried — keep it in the pending count. */
    @Synchronized
    fun retrying(context: Context, message: String?) = update(context) {
        it.copy(lastError = message?.take(160))
    }

    /** Given up on this item. */
    @Synchronized
    fun failed(context: Context, message: String?) = update(context) {
        it.copy(
            pending = (it.pending - 1).coerceAtLeast(0),
            failed = it.failed + 1,
            lastError = message?.take(160),
        )
    }

    @Synchronized
    fun uploadStarted() { _state.value = _state.value.copy(uploading = _state.value.uploading + 1) }

    @Synchronized
    fun uploadEnded() { _state.value = _state.value.copy(uploading = (_state.value.uploading - 1).coerceAtLeast(0)) }

    /** Failed items the agent resent: no longer failed (enqueue already counted them as pending). */
    @Synchronized
    fun retried(context: Context, count: Int) = update(context) {
        it.copy(failed = (it.failed - count).coerceAtLeast(0), lastError = null)
    }

    @Synchronized
    fun clearErrors(context: Context) = update(context) { it.copy(failed = 0, lastError = null) }

    private fun update(context: Context, transform: (Snapshot) -> Snapshot) {
        val next = transform(_state.value).copy(uploading = _state.value.uploading)
        _state.value = next
        prefs(context).edit()
            .putInt(K_PENDING, next.pending)
            .putInt(K_FAILED, next.failed)
            .putLong(K_LAST_OK, next.lastSyncedAtMillis)
            .putString(K_LAST_ERROR, next.lastError)
            .putLong("last_audio", next.lastAudioUploadedAtMillis)
            .putLong("last_report", next.lastReportSyncedAtMillis)
            .apply()
    }
}
