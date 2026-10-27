package com.winstone.connect

import com.winstone.connect.telephony.CallLogCursor
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class CallLogCursorTest {
    @Test fun rowsWithSameTimestampArePagedById() {
        val cursor = CallLogCursor(1_000L, 10L)
        assertTrue(CallLogCursor(1_000L, 11L) > cursor)
        assertFalse(CallLogCursor(1_000L, 9L) > cursor)
    }

    @Test fun laterTimestampWinsRegardlessOfRowId() {
        assertTrue(CallLogCursor(1_001L, 1L) > CallLogCursor(1_000L, 999L))
    }
}
