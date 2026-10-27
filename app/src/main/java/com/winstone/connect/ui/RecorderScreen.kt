package com.winstone.connect.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.winstone.connect.data.AgentSession
import com.winstone.connect.data.remote.WinstoneApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** One saved voice note, exactly as the CRM returned it (nothing invented here). */
data class VoiceNote(
    val id: String,
    val createdAt: String,
    val durationSeconds: Int,
    val title: String,
    val transcript: String?,
    val summary: String?,
    val takeaways: List<String>,
    val actionItems: List<String>,
    val error: String?,
    /** ready | transcript_failed | summary_failed — decided by the CRM. */
    val stage: String,
)

private object RecorderApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val url get() = WinstoneApi.BASE_URL + "/api/public/agent/recorder"

    private fun call(req: Request): JSONObject = client.newCall(req).execute().use { res ->
        val body = runCatching { JSONObject(res.body?.string().orEmpty()) }.getOrElse { JSONObject() }
        if (!res.isSuccessful) error(body.optString("error", "HTTP ${res.code}"))
        body
    }

    private fun builder(u: String) = Request.Builder().url(u).header("x-device-token", AgentSession.deviceToken.orEmpty())

    suspend fun list(): List<VoiceNote> = withContext(Dispatchers.IO) {
        val arr = call(builder(url).get().build()).optJSONArray("notes")
        (0 until (arr?.length() ?: 0)).map { i ->
            val o = arr!!.getJSONObject(i)
            fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            VoiceNote(
                id = o.getString("id"),
                createdAt = o.optString("created_at"),
                durationSeconds = o.optInt("duration_seconds"),
                title = o.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: "ভয়েস নোট",
                transcript = o.optString("transcript").takeIf { it.isNotBlank() && it != "null" },
                summary = o.optString("summary").takeIf { it.isNotBlank() && it != "null" },
                takeaways = strs("takeaways"),
                actionItems = strs("action_items"),
                error = o.optString("error_message").takeIf { it.isNotBlank() && it != "null" },
                stage = o.optString("stage", "ready"),
            )
        }
    }

    suspend fun save(file: File, seconds: Int): Unit = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("audio_base64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            put("mime_type", "audio/mp4")
            put("duration_seconds", seconds.coerceAtLeast(1))
        }
        call(builder(url).post(payload.toString().toRequestBody("application/json".toMediaType())).build())
    }

    /** Retries transcript/summary from the audio already saved in the CRM. Returns the error text, or null on success. */
    suspend fun retry(id: String): String? = withContext(Dispatchers.IO) {
        val body = call(builder(url).post(JSONObject().put("retry_id", id).toString().toRequestBody("application/json".toMediaType())).build())
        if (body.optBoolean("ok")) null else body.optString("error").ifBlank { "আবার ব্যর্থ হয়েছে" }
    }

    suspend fun playUrl(id: String): String = withContext(Dispatchers.IO) {
        call(builder("$url?play=$id").get().build()).getString("url")
    }
}

/** Full-screen Recorder inside the app: record a voice note, then review its transcript and AI summary. */
@Composable
fun RecorderScreen(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { RecorderBody(onClose) }
    }
}

@Composable
private fun RecorderBody(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<VoiceNote>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var saveFailed by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var retrying by remember { mutableStateOf<String?>(null) }
    /** null = fine; "denied", "blocked" (never ask again) or "busy" (mic in use / failed to start). */
    var micProblem by remember { mutableStateOf<String?>(null) }
    val player = remember { MediaPlayer() }
    val file = remember { File(context.cacheDir, "voice_note.m4a") }

    fun reload() = scope.launch {
        loading = true
        runCatching { RecorderApi.list() }
            .onSuccess { notes = it; message = null }
            .onFailure { message = "তালিকা আনা যায়নি: ${it.message}" }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }
    DisposableEffect(Unit) { onDispose { runCatching { recorder?.release() }; player.release() } }
    LaunchedEffect(recorder) { while (recorder != null) { delay(1000); seconds++ } }

    fun start() {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare(); r.start()
        }.onSuccess { seconds = 0; recorder = r; message = null; micProblem = null }
            .onFailure { r.release(); micProblem = "busy" }
    }

    fun upload(secs: Int) {
        saving = true
        saveFailed = null
        message = "অডিও সেভ হচ্ছে → লেখা তৈরি → সারাংশ তৈরি…"
        scope.launch {
            runCatching { RecorderApi.save(file, secs) }
                .onSuccess { message = "ভয়েস নোট সেভ হয়েছে"; file.delete(); reload() }
                // Keep the audio file on the phone so the agent can retry without re-recording.
                .onFailure { message = null; saveFailed = secs to (it.message ?: "নেটওয়ার্ক সমস্যা") }
            saving = false
        }
    }

    fun stopAndSave() {
        val r = recorder ?: return
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || seconds < 2) { message = "রেকর্ডিং খুব ছোট — আবার রেকর্ড করুন"; return }
        upload(seconds)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { micProblem = null; start() } else {
            val activity = context as? Activity
            // After "Don't ask again" Android stops showing the prompt; only Settings can fix it.
            val canAsk = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            micProblem = if (canAsk) "denied" else "blocked"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("রেকর্ডার", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("বন্ধ করুন") }
        }
        Text(
            "ভয়েস নোট রেকর্ড করুন। সেভ হলে CRM নিজে লেখা, সারসংক্ষেপ, মূল বিষয় ও করণীয় তৈরি করে।",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = !saving && saveFailed == null,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = if (recorder != null) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            onClick = {
                when {
                    recorder != null -> stopAndSave()
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> start()
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        ) {
            Text(
                if (recorder != null) "থামান ও সেভ করুন  ${seconds / 60}:${"%02d".format(seconds % 60)}" else "রেকর্ড শুরু করুন",
                fontSize = 16.sp,
            )
        }
        if (micProblem == null && recorder == null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Text("রেকর্ড শুরু করলে ফোন মাইকের অনুমতি চাইবে — \"Allow\" / \"অনুমতি দিন\" চাপুন।",
                fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        micProblem?.let { p ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (title, steps) = when (p) {
                        "denied" -> "মাইকের অনুমতি দেওয়া হয়নি" to listOf("নিচের \"আবার অনুমতি চান\" চাপুন।", "ফোন জিজ্ঞেস করলে \"Allow\" / \"While using the app\" বেছে নিন।")
                        "blocked" -> "মাইকের অনুমতি বন্ধ করা আছে" to listOf("\"সেটিংস খুলুন\" চাপুন।", "Permissions → Microphone → Allow বেছে নিন।", "অ্যাপে ফিরে এসে \"আবার চেষ্টা করুন\" চাপুন।")
                        else -> "মাইক চালু করা যায়নি" to listOf("চলমান ফোন কল, WhatsApp/ভিডিও কল বা অন্য রেকর্ডার অ্যাপ বন্ধ করুন।", "তারপর \"আবার চেষ্টা করুন\" চাপুন।", "তাও না হলে ফোন রিস্টার্ট করুন বা IT-কে জানান।")
                    }
                    Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    steps.forEachIndexed { i, s -> Text("${i + 1}. $s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p == "blocked") {
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }) { Text("সেটিংস খুলুন") }
                        }
                        OutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { micProblem = null; start() }
                            else permission.launch(Manifest.permission.RECORD_AUDIO)
                        }) { Text(if (p == "denied") "আবার অনুমতি চান" else "আবার চেষ্টা করুন") }
                    }
                }
            }
        }
        message?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        saveFailed?.let { (secs, reason) ->
            Text("অডিও সেভ হয়নি: $reason", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving, onClick = { upload(secs) }) { Text("আবার চেষ্টা করুন") }
                TextButton(onClick = { saveFailed = null; file.delete() }) { Text("বাদ দিন") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("আমার ভয়েস নোট", fontWeight = FontWeight.SemiBold)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        if (!loading && notes.isEmpty()) Text("এখনও কোনো ভয়েস নোট নেই।", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(notes, key = { it.id }) { n ->
                Card(Modifier.fillMaxWidth().clickable { expanded = if (expanded == n.id) null else n.id }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(n.title, fontWeight = FontWeight.SemiBold)
                        Text("${n.createdAt.take(16).replace('T', ' ')} · ${n.durationSeconds} সেকেন্ড", fontSize = 11.sp)
                        val (statusText, failed) = when (n.stage) {
                            "transcript_failed" -> "অডিও সেভ ✓ · লেখা তৈরি হয়নি" to true
                            "summary_failed" -> "অডিও সেভ ✓ · লেখা ✓ · সারাংশ তৈরি হয়নি" to true
                            else -> "অডিও সেভ ✓ · লেখা ✓ · সারাংশ ✓" to false
                        }
                        Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        n.summary?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                        if (failed && n.error != null) Text(n.error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        if (failed) {
                            OutlinedButton(enabled = retrying == null, modifier = Modifier.padding(top = 6.dp), onClick = {
                                retrying = n.id
                                scope.launch {
                                    runCatching { RecorderApi.retry(n.id) }
                                        .onSuccess { err -> message = err?.let { "আবার ব্যর্থ: $it" } ?: "লেখা ও সারাংশ তৈরি হয়েছে" }
                                        .onFailure { message = "আবার চেষ্টা করা যায়নি: ${it.message}" }
                                    retrying = null
                                    reload()
                                }
                            }) {
                                Text(when {
                                    retrying == n.id -> "আবার চেষ্টা হচ্ছে…"
                                    n.stage == "transcript_failed" -> "লেখা ও সারাংশ আবার চেষ্টা করুন"
                                    else -> "সারাংশ আবার চেষ্টা করুন"
                                })
                            }
                        }
                        if (expanded == n.id) {
                            if (n.takeaways.isNotEmpty()) {
                                Text("মূল বিষয়", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                                n.takeaways.forEach { Text("• $it", fontSize = 13.sp) }
                            }
                            if (n.actionItems.isNotEmpty()) {
                                Text("করণীয়", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                                n.actionItems.forEach { Text("• $it", fontSize = 13.sp) }
                            }
                            n.transcript?.let {
                                Text("লেখা", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                                Text(it, fontSize = 12.sp)
                            }
                            OutlinedButton(modifier = Modifier.padding(top = 8.dp), onClick = {
                                scope.launch {
                                    runCatching {
                                        val u = RecorderApi.playUrl(n.id)
                                        player.reset(); player.setDataSource(u); player.prepareAsync()
                                        player.setOnPreparedListener { it.start() }
                                    }.onFailure { message = "শোনা যায়নি: ${it.message}" }
                                }
                            }) { Text("শুনুন") }
                        }
                    }
                }
            }
        }
    }
}
