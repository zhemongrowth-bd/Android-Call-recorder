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
}
