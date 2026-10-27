package com.winstone.connect.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Server-controlled CRM closure, cached so an offline phone cannot bypass an active lock. */
object MaintenanceControl {
    private const val PREFS = "winstone_maintenance"
    private const val KEY_ACTIVE = "active"
    private const val KEY_KNOWN = "known"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class State(val active: Boolean, val title: String, val detail: String)

    private val defaultState = State(
        active = false,
        title = "জরুরি নোটিশ — CRM সাময়িকভাবে বন্ধ",
        detail = "CRM-এর ongoing development ও system update চলছে। প্রতিটি Agent-এর individual report আলাদাভাবে PDF download করার সুবিধা যোগ করা হচ্ছে। System পুনরায় active করার confirmation না পাওয়া পর্যন্ত CRM ব্যবহার করবেন না।",
    )

    fun cached(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = if (prefs.getBoolean(KEY_KNOWN, false)) {
            prefs.getBoolean(KEY_ACTIVE, defaultState.active)
        } else {
            defaultState.active
        }
        return defaultState.copy(active = active)
    }

    suspend fun refresh(context: Context): State = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${WinstoneApi.BASE_URL}/api/public/config/maintenance")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                State(
                    active = json.optBoolean("active", defaultState.active),
                    title = json.optString("title", defaultState.title).ifBlank { defaultState.title },
                    detail = json.optString("detail", defaultState.detail).ifBlank { defaultState.detail },
                ).also { state ->
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean(KEY_KNOWN, true)
                        .putBoolean(KEY_ACTIVE, state.active)
                        .apply()
                }
            }
        }.getOrElse { cached(context) }
    }
}