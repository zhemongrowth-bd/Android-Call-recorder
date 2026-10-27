package com.winstone.connect

import com.winstone.connect.data.sync.QueuedAudioIdentity
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QueuedAudioIdentityTest {
    @Test fun onlyMatchingServerConfirmationReleasesOriginal() {
        assertTrue(QueuedAudioIdentity.uploadConfirmed("r", "uploaded", "hash", "hash"))
        assertFalse(QueuedAudioIdentity.uploadConfirmed(null, "uploaded", "hash", "hash"))
        assertFalse(QueuedAudioIdentity.uploadConfirmed(" ", "uploaded", "hash", "hash"))
        assertFalse(QueuedAudioIdentity.uploadConfirmed("r", "pending", "hash", "hash"))
        assertFalse(QueuedAudioIdentity.uploadConfirmed("r", "verified", "hash", "hash"))
        assertFalse(QueuedAudioIdentity.uploadConfirmed("r", "uploaded", "other", "hash"))
        assertFalse(QueuedAudioIdentity.uploadConfirmed("r", "uploaded", "", ""))
    }
    @Test fun sameCallReusesIdentityAcrossRetries() {
        assertEquals("audio_call-1", QueuedAudioIdentity.uploadId("call-1", "ignored"))
        assertEquals(QueuedAudioIdentity.uploadId("call-1"), QueuedAudioIdentity.uploadId("call-1"))
    }
    @Test fun differentCallsNeverShareFilenameIdentity() {
        assertNotEquals(QueuedAudioIdentity.uploadId("call-1"), QueuedAudioIdentity.uploadId("call-2"))
        assertNotEquals(QueuedAudioIdentity.uploadId(null, "id-1"), QueuedAudioIdentity.uploadId(null, "id-2"))
    }
    @Test fun changedQueuedBytesHaveDifferentChecksum() {
        val file = File.createTempFile("queued-audio-test", ".mp3")
        try {
            file.writeText("abc")
            val original = QueuedAudioIdentity.checksum(file)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", original)
            assertEquals(original, QueuedAudioIdentity.checksum(file))
            file.writeText("abcd")
            assertNotEquals(original, QueuedAudioIdentity.checksum(file))
        } finally { file.delete() }
    }
}