package com.winstone.connect.data.remote

import com.winstone.connect.data.AgentSession
import com.winstone.connect.data.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Carries an agent's own data across app versions.
 *
 * When a phone is upgraded (or an old version is dropped), the CRM still holds
 * every lead and report that version produced. On the first launch of the new
 * version this asks for anything that has not yet been handed over, and
 * confirms it, so nothing an agent typed offline is stranded on a version that
 * no longer runs. The CRM rows are the source of truth — nothing is recreated.
 */
object VersionHandoff {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class Result(
        val leads: Int,
        val reports: Int,
        val fromVersions: List<Int>,
    ) {
        val total: Int get() = leads + reports
    }

    /**
     * Pulls anything this agent's earlier versions produced and acknowledges it.
     * Returns null when there is nothing pending or the CRM is unreachable —
     * a failure is never reported as "everything handed over".
     */
    suspend fun claim(): Result? = withContext(Dispatchers.IO) {
        val token = AgentSession.deviceToken ?: return@withContext null
        val versionCode = AppVersion.code
        val pending = runCatching {
            val req = Request.Builder()
                .url("${WinstoneApi.BASE_URL}/api/public/agent/version-handoff?version_code=$versionCode")
                .header("x-device-token", token)
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                JSONObject(res.body?.string().orEmpty())
            }
        }.getOrNull() ?: return@withContext null

        val items = pending.optJSONArray("items") ?: JSONArray()
        if (items.length() == 0) return@withContext null

        val ledgerIds = JSONArray()
        var leads = 0
        var reports = 0
        val versions = linkedSetOf<Int>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            item.optString("ledger_id").takeIf { it.isNotBlank() }?.let { ledgerIds.put(it) }
            when (item.optString("entity_type")) {
                "lead" -> leads++
                "report" -> reports++
            }
            item.optInt("version_code", 0).takeIf { it > 0 }?.let { versions.add(it) }
        }

        val acked = runCatching {
            val body = JSONObject().apply {
                put("version_code", versionCode)
                put("ledger_ids", ledgerIds)
            }
            val req = Request.Builder()
                .url("${WinstoneApi.BASE_URL}/api/public/agent/version-handoff")
                .header("x-device-token", token)
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { res -> res.isSuccessful }
        }.getOrDefault(false)

        if (!acked) return@withContext null
        Result(leads = leads, reports = reports, fromVersions = versions.toList())
    }
}
