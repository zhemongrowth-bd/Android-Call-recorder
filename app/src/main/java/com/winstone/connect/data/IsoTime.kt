package com.winstone.connect.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** UTC ISO-8601 timestamps that work on API 24+ without java.time. */
object IsoTime {
    // ThreadLocal.withInitial is API 26+, so override initialValue for API 24.
    private val fmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    fun fromMillis(ms: Long): String = fmt.get()!!.format(Date(ms))
    fun now(): String = fromMillis(System.currentTimeMillis())
}
