package com.winstone.connect.telephony

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records a short voice note with the microphone when the call itself could
 * not be recorded. This is deliberately a separate, agent-triggered capture —
 * the CRM stores it with recorder_source = "voice_note" so HQ can always tell
 * it apart from a real call recording.
 */
class VoiceNoteRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(tag: String): Boolean {
        stopQuietly()
        val dir = File(context.cacheDir, "notes").apply { mkdirs() }
        val file = File(dir, "note_${tag}_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(44_100)
            rec.setAudioEncodingBitRate(96_000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            output = file
            startedAt = System.currentTimeMillis()
            true
        } catch (error: Exception) {
            runCatching { rec.release() }
            recorder = null
            output = null
            false
        }
    }

    /** Stops and returns the file plus its length in seconds (null if too short). */
    fun stopAndGetFile(): Pair<File, Int>? {
        val rec = recorder ?: return null
        val file = output
        recorder = null
        output = null
        runCatching { rec.stop() }
        runCatching { rec.release() }
        if (file == null || !file.exists() || file.length() < 512) {
            file?.delete()
            return null
        }
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1)
        return file to seconds
    }

    fun stopQuietly() {
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null
        output = null
    }
}
