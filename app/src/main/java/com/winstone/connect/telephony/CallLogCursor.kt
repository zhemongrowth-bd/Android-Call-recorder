package com.winstone.connect.telephony

/** Stable paging cursor for CallLog rows, including multiple rows with the same timestamp. */
data class CallLogCursor(val dateMillis: Long, val rowId: Long) : Comparable<CallLogCursor> {
    override fun compareTo(other: CallLogCursor): Int {
        val byDate = dateMillis.compareTo(other.dateMillis)
        return if (byDate != 0) byDate else rowId.compareTo(other.rowId)
    }
}
