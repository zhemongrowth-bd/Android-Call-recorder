package com.winstone.connect.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.winstone.connect.data.AgentSession
import com.winstone.connect.data.remote.WinstoneApi
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Never lose a call log to a dead network.
 *
 * Everything the phone must push to the CRM (recording upload, post-call
 * report and outcome) is queued through WorkManager, so it survives a lost
 * signal, an app kill and a reboot, and retries with backoff until the CRM
 * confirms it. This is what makes calls and messages *actually* land in the
 * database rather than only when the agent happens to have 4G.
 */
object CallSyncQueue {

    fun queueRecording(
        context: Context,
        leadId: String?,
        phoneNumber: String,
        file: File,
        durationSeconds: Int,
        twoSided: Boolean,
        incoming: Boolean = false,
        recorderSource: String = "unknown",
        callUid: String? = null,
        callStartedAt: String? = null,
    ) {
        val uploadId = QueuedAudioIdentity.uploadId(callUid)
        // Cache storage can be evicted while offline. Keep queued originals in app files.
        val dir = File(context.filesDir, "pending_call_audio").apply { mkdirs() }
        val durable = File(dir, "$uploadId.${file.extension.ifBlank { "m4a" }}")
        if (!durable.exists()) file.copyTo(durable, overwrite = false)
        enqueue(
            context,
            unique = "rec_$uploadId",
            data = Data.Builder()
                .putString(KEY_KIND, KIND_RECORDING)
                .putString(KEY_LEAD, leadId)
                .putString(KEY_PHONE, phoneNumber)
                .putString(KEY_FILE, durable.absolutePath)
                .putInt(KEY_DURATION, durationSeconds)
                .putBoolean(KEY_TWO_SIDED, twoSided)
                .putBoolean(KEY_INCOMING, incoming)
                .putString(KEY_SOURCE, recorderSource)
                .putString(KEY_UPLOAD_ID, uploadId)
                .putString(KEY_CALL_UID, callUid)
                .putString(KEY_AT, callStartedAt)
                .build(),
        )
    }

    /** Opens the mandatory post-call report for a finished call. */
    fun queueReportOpen(
        context: Context,
        leadId: String,
        phoneNumber: String,
        durationSeconds: Int,
        connected: Boolean,
        callUid: String? = null,
    ) {
        enqueue(
            context,
            unique = "report_${callUid ?: java.util.UUID.randomUUID().toString()}",
            data = Data.Builder()
                .putString(KEY_KIND, KIND_REPORT_OPEN)
                .putString(KEY_CALL_UID, callUid)
                .putString(KEY_LEAD, leadId)
                .putString(KEY_PHONE, phoneNumber)
                .putInt(KEY_DURATION, durationSeconds)
                .putBoolean(KEY_CONNECTED, connected)
                .build(),
        )
    }

    /**
     * Parks a finished post-call report on the phone when there is no network.
     * The payload is already validated by the sheet, so the queue only has to
     * replay it; the unique name is the report id, so one report is sent once.
     */
    fun queueReportSubmit(context: Context, reportId: String, payloadJson: String) {
        // Mark the parked copy as captured offline, so the CRM's version ledger
        // knows this report was typed without a connection.
        val tagged = runCatching {
            org.json.JSONObject(payloadJson).put("captured_offline", true).toString()
        }.getOrDefault(payloadJson)
        enqueue(
            context,
            unique = "report_submit_$reportId",
            data = Data.Builder()
                .putString(KEY_KIND, KIND_REPORT_SUBMIT)
                .putString(KEY_TEXT, tagged)
                .build(),
        )
    }

    /**
     * Parks a lead typed with no signal. The unique name is the client ref, so
     * the lead is sent exactly once no matter how often the queue retries.
     */
    fun queueLeadSubmit(context: Context, clientRef: String, payloadJson: String) {
        enqueue(
            context,
            unique = "lead_submit_$clientRef",
            data = Data.Builder()
                .putString(KEY_KIND, KIND_LEAD_SUBMIT)
                .putString(KEY_TEXT, payloadJson)
                .build(),
        )
    }


    fun queueOutcome(context: Context, leadId: String, outcome: String, notes: String, connected: Boolean) {
        enqueue(
            context,
            unique = "out_${leadId}_${System.currentTimeMillis()}",
            data = Data.Builder()
                .putString(KEY_KIND, KIND_OUTCOME)
                .putString(KEY_LEAD, leadId)
                .putString(KEY_TEXT, notes)
                .putString(KEY_OUTCOME, outcome)
                .putBoolean(KEY_CONNECTED, connected)
                .build(),
        )
    }

    /**
     * Reports one observed call state. The unique work name is
     * callUid + state, so the same transition can never be sent twice even if
     * the phone reboots mid-queue.
     */
    fun queueCallState(
        context: Context,
        callUid: String,
        leadId: String,
        state: String,
        durationSeconds: Int = 0,
        phoneNumber: String? = null,
        recordingSupported: Boolean? = null,
        recordingNote: String? = null,
    ) {
        enqueue(
            context,
            unique = "state_${callUid}_$state",
            data = Data.Builder()
                .putString(KEY_KIND, KIND_CALL_STATE)
                .putString(KEY_CALL_UID, callUid)
                .putString(KEY_LEAD, leadId)
                .putString(KEY_STATE, state)
                .putInt(KEY_DURATION, durationSeconds)
                .putString(KEY_PHONE, phoneNumber)
                .putString(KEY_REC_NOTE, recordingNote)
                .apply { recordingSupported?.let { putBoolean(KEY_REC_SUPPORTED, it) } }
                .build(),
        )
    }

    /**
     * Reports one observed state of an *incoming* call. There is no lead id: the
     * CRM matches the caller's number to a lead, and creates one when the number
     * is new, so a customer's callback becomes a real lead.
     */
    fun queueIncomingCall(
        context: Context,
        callUid: String,
        phoneNumber: String,
        state: String,
        durationSeconds: Int = 0,
        recordingSupported: Boolean? = null,
        recordingNote: String? = null,
        /** "incoming" for a customer callback, "outgoing" for a dialer call. */
        direction: String = "incoming",
        /** "realtime" (observed live) or "call_log" (found later in the call log). */
        source: String = "realtime",
        /** When the call happened, for log entries that are reported late. */
        atIso: String? = null,
    ) {
        enqueue(
            context,
            unique = "${direction}_${callUid}_$state",
            data = Data.Builder()
                .putString(KEY_KIND, KIND_INCOMING_CALL)
                .putString(KEY_DIRECTION, direction)
                .putString(KEY_CALL_UID, callUid)
                .putString(KEY_PHONE, phoneNumber)
                .putString(KEY_STATE, state)
                .putInt(KEY_DURATION, durationSeconds)
                .putString(KEY_REC_NOTE, recordingNote)
                .putString(KEY_CALL_SOURCE, source)
                .putString(KEY_AT, atIso)
                .apply { recordingSupported?.let { putBoolean(KEY_REC_SUPPORTED, it) } }
                .build(),
        )
    }

    private const val FAILED_PREFS = "winstone_failed_recordings"

    /** Keeps a failed recording job so the agent can resend it later. No audio is stored here, only the job. */
    fun rememberFailedRecording(context: Context, data: Data) {
        val id = data.getString(KEY_UPLOAD_ID) ?: return
        context.getSharedPreferences(FAILED_PREFS, Context.MODE_PRIVATE).edit()
            .putString(id, android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.NO_WRAP))
            .apply()
    }

    /**
     * Re-queues every failed recording whose original audio is still on the phone.
     * Same upload id, so the CRM's idempotent ingest never creates a duplicate.
     * Returns how many were resent; jobs with a missing file stay failed.
     */
    fun retryFailedRecordings(context: Context): Int {
        val prefs = context.getSharedPreferences(FAILED_PREFS, Context.MODE_PRIVATE)
        var resent = 0
        for ((id, encoded) in prefs.all) {
            val data = runCatching { Data.fromByteArray(android.util.Base64.decode(encoded as String, android.util.Base64.NO_WRAP)) }.getOrNull()
            val path = data?.getString(KEY_FILE)
            if (data == null || path == null || !File(path).exists()) continue
            enqueue(context, "rec_$id", data, ExistingWorkPolicy.REPLACE)
            prefs.edit().remove(id).apply()
            resent++
        }
        if (resent > 0) SyncStatus.retried(context, resent)
        return resent
    }

    private fun enqueue(context: Context, unique: String, data: Data, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val request = OneTimeWorkRequestBuilder<CrmSyncWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        SyncStatus.queued(context)
        WorkManager.getInstance(context).enqueueUniqueWork(unique, policy, request)
    }

    const val KEY_CALL_UID = "call_uid"
    const val KEY_STATE = "state"
    const val KEY_REC_SUPPORTED = "recording_supported"
    const val KEY_REC_NOTE = "recording_note"
    const val KIND_CALL_STATE = "call_state"
    const val KEY_KIND = "kind"
    const val KEY_LEAD = "lead_id"
    const val KEY_PHONE = "phone"
    const val KEY_FILE = "file"
    const val KEY_DURATION = "duration"
    const val KEY_TWO_SIDED = "two_sided"
    const val KEY_INCOMING = "incoming"
    const val KEY_TEXT = "text"
    const val KEY_OUTCOME = "outcome"
    const val KEY_CONNECTED = "connected"
    const val KEY_SOURCE = "recorder_source"
    const val KEY_UPLOAD_ID = "client_upload_id"

    const val KIND_RECORDING = "recording"
    const val KIND_OUTCOME = "outcome"
    const val KIND_REPORT_OPEN = "report_open"
    const val KIND_REPORT_SUBMIT = "report_submit"
    const val KIND_LEAD_SUBMIT = "lead_submit"
    const val KIND_INCOMING_CALL = "incoming_call"
    const val KEY_DIRECTION = "direction"
    const val KEY_CALL_SOURCE = "call_source"
    const val KEY_AT = "at"
}

class CrmSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isRecording = inputData.getString(CallSyncQueue.KEY_KIND) == CallSyncQueue.KIND_RECORDING
        if (isRecording) SyncStatus.uploadStarted()
        return try { doSyncWork() } finally { if (isRecording) SyncStatus.uploadEnded() }
    }

    private suspend fun doSyncWork(): Result {
        val employeeId = AgentSession.employeeIdNow(applicationContext) ?: return Result.retry()
        val leadId = inputData.getString(CallSyncQueue.KEY_LEAD)
        val kind = inputData.getString(CallSyncQueue.KEY_KIND)

        return try {
            when (kind) {
                CallSyncQueue.KIND_RECORDING -> {
                    val path = inputData.getString(CallSyncQueue.KEY_FILE) ?: return Result.failure()
                    val file = File(path)
                    if (!file.exists()) {
                        SyncStatus.failed(applicationContext, "Original audio file missing — recover the phone recording; no audio uploaded")
                        return Result.failure()
                    }
                    WinstoneApi.uploadRecording(
                        leadId = leadId,
                        phoneNumber = inputData.getString(CallSyncQueue.KEY_PHONE).orEmpty(),
                        employeeId = employeeId,
                        agentId = AgentSession.agentIdNow(applicationContext),
                        file = file,
                        durationSeconds = inputData.getInt(CallSyncQueue.KEY_DURATION, 0),
                        twoSided = inputData.getBoolean(CallSyncQueue.KEY_TWO_SIDED, true),
                        incoming = inputData.getBoolean(CallSyncQueue.KEY_INCOMING, false),
                        clientUploadId = inputData.getString(CallSyncQueue.KEY_UPLOAD_ID),
                        recorderSource = inputData.getString(CallSyncQueue.KEY_SOURCE) ?: "unknown",
                        callUid = inputData.getString(CallSyncQueue.KEY_CALL_UID),
                        callStartedAt = inputData.getString(CallSyncQueue.KEY_AT),
                    )
                    file.delete()
                    Result.success()
                }

                CallSyncQueue.KIND_INCOMING_CALL -> {
                    val callUid = inputData.getString(CallSyncQueue.KEY_CALL_UID)
                    val phone = inputData.getString(CallSyncQueue.KEY_PHONE)
                    if (callUid == null || phone.isNullOrBlank()) return Result.failure()
                    WinstoneApi.postIncomingCall(
                        callUid = callUid,
                        phoneNumber = phone,
                        state = inputData.getString(CallSyncQueue.KEY_STATE).orEmpty(),
                        durationSeconds = inputData.getInt(CallSyncQueue.KEY_DURATION, 0),
                        recordingSupported =
                            if (inputData.keyValueMap.containsKey(CallSyncQueue.KEY_REC_SUPPORTED))
                                inputData.getBoolean(CallSyncQueue.KEY_REC_SUPPORTED, false)
                            else null,
                        recordingNote = inputData.getString(CallSyncQueue.KEY_REC_NOTE),
                        direction = inputData.getString(CallSyncQueue.KEY_DIRECTION) ?: "incoming",
                        source = inputData.getString(CallSyncQueue.KEY_CALL_SOURCE) ?: "realtime",
                        atIso = inputData.getString(CallSyncQueue.KEY_AT),
                    )
                    Result.success()
                }

                CallSyncQueue.KIND_OUTCOME -> {
                    if (leadId == null) return Result.failure()
                    WinstoneApi.postCallOutcome(
                        leadId = leadId,
                        agentId = AgentSession.agentIdNow(applicationContext),
                        outcome = inputData.getString(CallSyncQueue.KEY_OUTCOME).orEmpty(),
                        notes = inputData.getString(CallSyncQueue.KEY_TEXT).orEmpty(),
                        connected = inputData.getBoolean(CallSyncQueue.KEY_CONNECTED, false),
                    )
                    Result.success()
                }

                CallSyncQueue.KIND_REPORT_OPEN -> {
                    if (leadId == null) return Result.failure()
                    WinstoneApi.openReport(
                        leadId = leadId,
                        recordingId = null,
                        callUid = inputData.getString(CallSyncQueue.KEY_CALL_UID),
                        phoneNumber = inputData.getString(CallSyncQueue.KEY_PHONE),
                        durationSeconds = inputData.getInt(CallSyncQueue.KEY_DURATION, 0),
                        connected = inputData.getBoolean(CallSyncQueue.KEY_CONNECTED, false),
                    )
                    Result.success()
                }

                CallSyncQueue.KIND_REPORT_SUBMIT -> {
                    val raw = inputData.getString(CallSyncQueue.KEY_TEXT) ?: return Result.failure()
                    WinstoneApi.submitReportRaw(org.json.JSONObject(raw))
                    Result.success()
                }

                CallSyncQueue.KIND_LEAD_SUBMIT -> {
                    val raw = inputData.getString(CallSyncQueue.KEY_TEXT) ?: return Result.failure()
                    com.winstone.connect.data.remote.WinstoneAgentApi
                        .submitLeadRaw(org.json.JSONObject(raw))
                    Result.success()
                }

                CallSyncQueue.KIND_CALL_STATE -> {
                    val callUid = inputData.getString(CallSyncQueue.KEY_CALL_UID)
                    if (leadId == null || callUid == null) return Result.failure()
                    WinstoneApi.postCallState(
                        callUid = callUid,
                        leadId = leadId,
                        state = inputData.getString(CallSyncQueue.KEY_STATE).orEmpty(),
                        durationSeconds = inputData.getInt(CallSyncQueue.KEY_DURATION, 0),
                        agentPhone = inputData.getString(CallSyncQueue.KEY_PHONE),
                        recordingSupported =
                            if (inputData.keyValueMap.containsKey(CallSyncQueue.KEY_REC_SUPPORTED))
                                inputData.getBoolean(CallSyncQueue.KEY_REC_SUPPORTED, false)
                            else null,
                        recordingNote = inputData.getString(CallSyncQueue.KEY_REC_NOTE),
                    )
                    Result.success()
                }

                else -> Result.failure()
            }.also { if (it is Result.Success) SyncStatus.succeeded(applicationContext, kind == CallSyncQueue.KIND_RECORDING, kind == CallSyncQueue.KIND_REPORT_OPEN || kind == CallSyncQueue.KIND_REPORT_SUBMIT) }
        } catch (error: Exception) {
            // 4xx = bad payload, retrying will not help; anything else is transient.
            val message = error.message.orEmpty()
            val giveUp = message.contains("Invalid payload")
            // A call recording and its report are the audit trail of the call:
            // never give up on them while the failure could still be the network.
            val keepTrying = !giveUp && (
                kind == CallSyncQueue.KIND_RECORDING ||
                    kind == CallSyncQueue.KIND_REPORT_OPEN ||
                    kind == CallSyncQueue.KIND_REPORT_SUBMIT ||
                    kind == CallSyncQueue.KIND_LEAD_SUBMIT ||
                    kind == CallSyncQueue.KIND_CALL_STATE ||
                    runAttemptCount < 12
                )
            if (keepTrying) {
                SyncStatus.retrying(applicationContext, message)
                Result.retry()
            } else {
                if (kind == CallSyncQueue.KIND_RECORDING) CallSyncQueue.rememberFailedRecording(applicationContext, inputData)
                SyncStatus.failed(applicationContext, message)
                Result.failure()
            }
        }
    }
}
