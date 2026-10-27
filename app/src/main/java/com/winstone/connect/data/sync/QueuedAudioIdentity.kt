package com.winstone.connect.data.sync

import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Identity is generated once at enqueue and persisted; filenames never identify a call. */
object QueuedAudioIdentity {
    /** Only a matching server acknowledgement permits removing the durable original. */
    fun uploadConfirmed(recordingId: String?, uploadStatus: String?, returnedChecksum: String?, expectedChecksum: String): Boolean =
        !recordingId.isNullOrBlank() && uploadStatus == "uploaded" &&
            expectedChecksum.isNotBlank() && returnedChecksum == expectedChecksum

    fun uploadId(callUid: String?, fallbackId: String = UUID.randomUUID().toString()): String =
        callUid?.takeIf { it.isNotBlank() }?.let { "audio_$it" } ?: fallbackId

    fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count > 0) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}