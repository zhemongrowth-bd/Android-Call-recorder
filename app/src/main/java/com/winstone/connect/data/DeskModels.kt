package com.winstone.connect.data

import org.json.JSONArray
import org.json.JSONObject

data class Lead(
    val id: String,
    val name: String,
    val phone: String,
    val company: String?,
    val address: String?,
    val status: String,
    val attempts: Int,
    val notes: String?,
    val distanceKm: Double? = null,
)

data class CallRow(
    val id: String,
    val leadId: String?,
    val phone: String,
    val durationSeconds: Int,
    val direction: String,
    val syncStatus: String,
    val summary: String?,
    val sentiment: String?,
    val createdAt: String,
)

data class MessageRow(
    val id: String,
    val leadId: String?,
    val senderType: String,
    val content: String,
    val createdAt: String,
)

data class DeskData(
    val agentId: String,
    val agentName: String,
    val leads: List<Lead>,
    val calls: List<CallRow>,
    val messages: List<MessageRow>,
)

/** Dashboard metrics returned by /api/public/agent/dashboard-metrics. */
data class DashboardMetrics(
    val progressPercent: Int,
    val assigned: Int,
    val called: Int,
    val connected: Int,
    val interested: Int,
    val followUp: Int,
    val siteVisit: Int,
    val hotLeads: Int,
    val followUpsDue: Int,
    val newAssigned: Int,
    val missedRetry: Int,
) {
    companion object {
        fun fromJson(obj: org.json.JSONObject): DashboardMetrics = DashboardMetrics(
            progressPercent = obj.optInt("progress_percent", 0),
            assigned = obj.optInt("assigned", 0),
            called = obj.optInt("called", 0),
            connected = obj.optInt("connected", 0),
            interested = obj.optInt("interested", 0),
            followUp = obj.optInt("follow_up", 0),
            siteVisit = obj.optInt("site_visit", 0),
            hotLeads = obj.optInt("hot_leads", 0),
            followUpsDue = obj.optInt("follow_ups_due", 0),
            newAssigned = obj.optInt("new_assigned", 0),
            missedRetry = obj.optInt("missed_retry", 0),
        )
    }
}

private fun JSONObject.str(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }

fun parseLeads(arr: JSONArray): List<Lead> = (0 until arr.length()).map { i ->
    val o = arr.getJSONObject(i)
    Lead(
        id = o.optString("id"),
        name = o.optString("name"),
        phone = o.optString("phone_number"),
        company = o.str("company"),
        address = o.str("address"),
        status = o.optString("status", "pending"),
        attempts = o.optInt("call_attempts"),
        notes = o.str("notes"),
    )
}

fun parseCalls(arr: JSONArray): List<CallRow> = (0 until arr.length()).map { i ->
    val o = arr.getJSONObject(i)
    CallRow(
        id = o.optString("id"),
        leadId = o.str("lead_id"),
        phone = o.optString("phone_number"),
        durationSeconds = o.optInt("duration_seconds"),
        direction = o.optString("call_direction", "outgoing"),
        syncStatus = o.optString("sync_status", "uploaded"),
        summary = o.str("ai_summary"),
        sentiment = o.str("sentiment"),
        createdAt = o.optString("created_at"),
    )
}

fun parseMessages(arr: JSONArray): List<MessageRow> = (0 until arr.length()).map { i ->
    val o = arr.getJSONObject(i)
    MessageRow(
        id = o.optString("id"),
        leadId = o.str("lead_id"),
        senderType = o.optString("sender_type", "agent"),
        content = o.str("message_content") ?: "(মিডিয়া)",
        createdAt = o.optString("created_at"),
    )
}

fun statusLabel(status: String): String = when (status) {
    "pending" -> "অপেক্ষমাণ"
    "contacted" -> "যোগাযোগ হয়েছে"
    "follow_up" -> "ফলো-আপ"
    "closed" -> "সম্পন্ন"
    else -> status
}

fun shortTime(iso: String): String =
    iso.replace("T", " ").take(16)
