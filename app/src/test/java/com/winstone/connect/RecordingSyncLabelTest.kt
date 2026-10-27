package com.winstone.connect

import com.winstone.connect.data.sync.RecordingSyncLabel
import com.winstone.connect.data.sync.SyncStatus.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingSyncLabelTest {
    @Test fun nothingQueuedOrUploadedIsAvailable() =
        assertEquals(RecordingSyncLabel.AVAILABLE, RecordingSyncLabel.of(Snapshot()))

    @Test fun queuedIsPendingSync() =
        assertEquals(RecordingSyncLabel.PENDING_SYNC, RecordingSyncLabel.of(Snapshot(pending = 2)))

    @Test fun uploadingIsSyncing() =
        assertEquals(RecordingSyncLabel.SYNCING, RecordingSyncLabel.of(Snapshot(pending = 1, uploading = 1)))

    @Test fun confirmedUploadIsSynced() =
        assertEquals(RecordingSyncLabel.SYNCED, RecordingSyncLabel.of(Snapshot(lastAudioUploadedAtMillis = 1L)))

    @Test fun anyFailureWins() =
        assertEquals(RecordingSyncLabel.FAILED, RecordingSyncLabel.of(Snapshot(failed = 1, uploading = 1, lastAudioUploadedAtMillis = 1L)))

    @org.junit.Test
    fun perRecordingStatusFollowsItsOwnUploadJob() {
        val S = androidx.work.WorkInfo.State.values().associateBy { it.name }
        org.junit.Assert.assertEquals(RecordingSyncLabel.SYNCING, RecordingSyncLabel.ofWork(S["RUNNING"], true))
        org.junit.Assert.assertEquals(RecordingSyncLabel.PENDING_SYNC, RecordingSyncLabel.ofWork(S["ENQUEUED"], true))
        org.junit.Assert.assertEquals(RecordingSyncLabel.PENDING_SYNC, RecordingSyncLabel.ofWork(S["BLOCKED"], true))
        org.junit.Assert.assertEquals(RecordingSyncLabel.SYNCED, RecordingSyncLabel.ofWork(S["SUCCEEDED"], true))
        org.junit.Assert.assertEquals(RecordingSyncLabel.FAILED, RecordingSyncLabel.ofWork(S["FAILED"], true))
        org.junit.Assert.assertEquals(RecordingSyncLabel.AVAILABLE, RecordingSyncLabel.ofWork(null, true))
        org.junit.Assert.assertEquals(RecordingSyncLabel.FAILED, RecordingSyncLabel.ofWork(null, false))
    }
}
