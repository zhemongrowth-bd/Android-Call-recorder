package com.winstone.connect

import com.winstone.connect.telephony.PhoneRecordingMatcher
import com.winstone.connect.telephony.RecordingCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneRecordingMatcherTest {
    private val start = 1_000_000_000L
    private val end = start + 120_000L

    private fun c(id: Long, name: String, path: String, at: Long, dur: Long = 0) =
        RecordingCandidate(id, name, path, at, dur)

    @Test fun picksSamsungCallFolderFileJustAfterHangUp() {
        val hit = PhoneRecordingMatcher.pick(
            listOf(c(1, "Call recording 01711223344_241024.m4a", "Recordings/Call/", end + 5_000, 118_000)),
            start, end, "+8801711223344",
        )
        assertEquals(1L, hit?.id)
    }

    @Test fun ignoresMusicAndVoiceNotes() {
        assertNull(
            PhoneRecordingMatcher.pick(
                listOf(c(2, "song.mp3", "Music/", end + 5_000), c(3, "Voice 001.m4a", "Recordings/Voice Recorder/", end + 5_000)),
                start, end, "01711223344",
            ),
        )
    }

    @Test fun ignoresFilesOutsideTheCallWindow() {
        assertNull(
            PhoneRecordingMatcher.pick(
                listOf(c(4, "01711223344.amr", "MIUI/sound_recorder/call_rec/", end + 4 * 60_000)),
                start, end, "01711223344",
            ),
        )
    }

    @Test fun picksClosestToCallEnd() {
        val hit = PhoneRecordingMatcher.pick(
            listOf(
                c(5, "a.m4a", "Recordings/Call/", start - 10_000),
                c(6, "b.m4a", "Recordings/Call/", end + 3_000),
            ),
            start, end, null,
        )
        assertEquals(6L, hit?.id)
    }
}
