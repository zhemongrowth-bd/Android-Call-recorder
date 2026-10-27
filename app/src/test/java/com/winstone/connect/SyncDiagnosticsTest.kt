package com.winstone.connect

import com.winstone.connect.data.sync.SyncDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncDiagnosticsTest {
    @Test fun lineHasNoRawIdentifiers() {
        val line = SyncDiagnostics.line("audio_upload", "call-123", "abcdef", 409, null, 3, true)
        assertFalse(line.contains("call-123"))
        assertFalse(line.contains("abcdef"))
        assertEquals(true, line.contains("reason=lifecycle_not_synced"))
    }
    @Test fun permissionDeniedWins() {
        assertEquals("permission_denied", SyncDiagnostics.reason(200, null, false))
    }
}
