package com.winstone.connect.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.winstone.connect.data.sync.PendingReportWorker
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.winstone.connect.data.remote.WinstoneApi
import com.winstone.connect.data.LeadEvaluation
import com.winstone.connect.data.sync.CallSyncQueue
import com.winstone.connect.telephony.CallPhase
import com.winstone.connect.telephony.LiveCallLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * কলের সময়ের পর্দা — লাইভ টাইমার, রেকর্ডিং চিহ্ন, নোট, মিউট/স্পিকার,
 * কল কাটা এবং কল শেষে ফলাফল (outcome) পাঠানো।
 */
@Composable
fun LiveCallScreen(phase: CallPhase) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf("") }
    var elapsed by remember { mutableLongStateOf(0L) }


    LaunchedEffect(phase) {
        while (phase == CallPhase.Connected || phase == CallPhase.Dialing) {
            val start = LiveCallLauncher.connectedAt
            elapsed = if (start > 0L) (System.currentTimeMillis() - start) / 1000 else 0L
            delay(1000)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = LiveCallLauncher.activeLeadName ?: "কল চলছে",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = LiveCallLauncher.activePhone.orEmpty(),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when (phase) {
                    CallPhase.Dialing -> "ডায়াল করা হচ্ছে…"
                    CallPhase.Connected -> formatDuration(elapsed)
                    CallPhase.Ended -> "কল শেষ"
                    CallPhase.Idle -> ""
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (LiveCallLauncher.recording && phase == CallPhase.Connected) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFB3261E), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("● REC", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            LiveCallLauncher.recordingIssue?.let { issue ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "$issue — কলের তথ্য ও রিপোর্ট ঠিকই CRM-এ যাবে",
                    fontSize = 12.sp,
                    color = Color(0xFFB3261E),
                )
            }

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("লাইভ নোট") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Spacer(Modifier.height(20.dp))
            if (phase != CallPhase.Ended) SimCallControls()

        }
    }

    if (phase == CallPhase.Ended) {
        ReportSheet(notes = notes)
    }
}

/**
 * বাধ্যতামূলক পোস্ট-কল রিপোর্ট।
 *
 * কল শেষ হলে এই শিটটি বন্ধ করা যায় না: ক্যাটাগরি ও তার আবশ্যক ঘর পূরণ করে
 * জমা না দিলে পরের কল শুরু করা যায় না — নিয়মটি সার্ভারেই বসানো, তাই অ্যাপ
 * বন্ধ করে দিলেও রিপোর্টটি বাকি থেকে যায়।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(notes: String, onSubmitted: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var reportId by remember { mutableStateOf<String?>(null) }
    var leadLabel by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(notes) }
    var reason by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aiSummary by remember { mutableStateOf<String?>(null) }
    var aiCategory by remember { mutableStateOf<String?>(null) }
    var talkSeconds by remember { mutableLongStateOf(0L) }
    var callTypeLabel by remember { mutableStateOf<String?>(null) }
    // শিটটি বন্ধ করা যায় — রিপোর্ট সার্ভারে বাকিই থাকে, আবার খোলার বাটন দেখায়।
    var dismissed by remember { mutableStateOf(false) }
    // কলের তথ্য — বাধ্যতামূলক
    var direction by remember { mutableStateOf("outgoing") }
    var callState by remember { mutableStateOf("connected") }
    var duration by remember { mutableStateOf("") }
    var noFollowUp by remember { mutableStateOf(false) }
    var noFollowUpNote by remember { mutableStateOf("") }
    var noFollowUpReason by remember { mutableStateOf("") }
    var evaluationAnswers by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var grade by remember { mutableStateOf<String?>(null) }

    // Wait for the server-side report (opened by the sync worker when the call ended).
    LaunchedEffect(Unit) {
        while (reportId == null) {
            runCatching { WinstoneApi.pendingReport() }.getOrNull()?.let { body ->
                body.optJSONObject("pending")?.let { pending ->
                    reportId = pending.optString("id").takeIf { it.isNotBlank() }
                    talkSeconds = pending.optLong("duration_seconds", 0L)
                    if (duration.isBlank()) duration = talkSeconds.toString()
                    if (!pending.optBoolean("connected", true)) callState = "no_answer"
                    pending.optJSONObject("lead")?.let { lead ->
                        leadLabel = listOfNotNull(
                            lead.optString("name").takeIf { it.isNotBlank() && it != "null" },
                            lead.optString("phone_number").takeIf { it.isNotBlank() && it != "null" },
                        ).joinToString(" · ").takeIf { it.isNotBlank() }
                    }
                    pending.optJSONObject("recording")?.let { rec ->
                        aiSummary = rec.optString("ai_summary").takeIf { it.isNotBlank() && it != "null" }
                        if (rec.optString("call_direction") == "incoming_callback") direction = "incoming"
                        callTypeLabel =
                            if (rec.optString("call_direction") == "incoming_callback") {
                                "ইনকামিং — ক্রেতা কল করেছেন"
                            } else {
                                "আউটগোয়িং — আমি কল করেছি"
                            }
                    }
                    pending.optJSONObject("ai_suggestion")?.let { suggestion ->
                        aiCategory = suggestion.optString("suggestedCategory").takeIf { it.isNotBlank() }
                    }
                }
            }
            if (reportId == null) delay(3_000)
        }
    }

    // প্রতিটি কলে: কলের দিক + অবস্থা + সময়কাল + ক্যাটাগরি + সারাংশ + নোট + ফলো-আপ — সবই বাধ্যতামূলক
    val needsReason = category == "not_interested" || category == "wrong_number"
    val seconds = duration.trim().toIntOrNull() ?: -1
    val received = callState == "connected"
    val evaluationComplete = LeadEvaluation.isComplete(evaluationAnswers)
    val evaluationScore = LeadEvaluation.score(evaluationAnswers)
    val temperature = if (evaluationComplete) LeadEvaluation.category(evaluationScore) else null
    val ready = category.isNotBlank() &&
        seconds >= 0 && (!received || seconds >= 1) &&
        summary.trim().length > 1 &&
        note.trim().length > 1 &&
        (if (noFollowUp) (if (received) ContactCloseReasons.isValid(noFollowUpReason, noFollowUpNote) else true) else (date.length == 10 && time.length == 5)) &&
        (!received || (evaluationComplete && grade != null)) &&
        (!needsReason || reason.trim().length > 1)

    if (dismissed) {
        Column(Modifier.padding(20.dp).fillMaxWidth()) {
            Text("একটি কলের রিপোর্ট বাকি আছে", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "জমা না দিলে পরের কল শুরু করা যাবে না — লিখে শেষ করতে আবার খুলুন।",
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { dismissed = false }, modifier = Modifier.fillMaxWidth()) {
                Text("রিপোর্ট খুলুন")
            }
        }
        return
    }

    ModalBottomSheet(onDismissRequest = { dismissed = true }, sheetState = sheet) {
        // লম্বা ফর্ম — ছোট ফোনেও পুরোটা স্ক্রল করে পাওয়া যায়, কীবোর্ড উঠলে
        // ইনপুট ঢাকা পড়ে না, আর নিচের নেভিগেশন বারে বাটন আটকে যায় না।
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("কল শেষ — রিপোর্ট পূরণ করুন", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            leadLabel?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (reportId == null) "রিপোর্ট তৈরি হচ্ছে…" else "ক্যাটাগরি বেছে নিয়ে জমা দিন — জমা না দিলে পরের কল হবে না",
                fontSize = 12.sp,
            )
            if (reportId != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "কলের ধরন: ${callTypeLabel ?: "—"} · সময়কাল: ${formatDuration(talkSeconds)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            aiSummary?.let {
                Spacer(Modifier.height(8.dp))
                Text("AI সারসংক্ষেপ: $it", fontSize = 12.sp)
            }
            aiCategory?.let {
                Spacer(Modifier.height(4.dp))
                Text("AI পরামর্শ: $it (চূড়ান্ত সিদ্ধান্ত আপনারই)", fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))
            Text("কলের দিক (বাধ্যতামূলক)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            DIRECTIONS.forEach { (value, label) ->
                OutlinedButton(
                    onClick = { direction = value },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(if (direction == value) "✓ $label" else label) }
            }
            Spacer(Modifier.height(8.dp))
            Text("কলের অবস্থা (বাধ্যতামূলক)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            CALL_STATES.forEach { (value, label) ->
                OutlinedButton(
                    onClick = { callState = value },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(if (callState == value) "✓ $label" else label) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter { ch -> ch.isDigit() } },
                label = { Text("সময়কাল — সেকেন্ড (বাধ্যতামূলক)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (received) {
                Spacer(Modifier.height(8.dp))
                Text("লিড মূল্যায়ন — সব প্রশ্নের হ্যাঁ / না দিন", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                LeadEvaluation.questions.forEach { question ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${question.label}  +${question.points}", fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(true to "হ্যাঁ", false to "না").forEach { (value, label) ->
                                OutlinedButton(
                                    onClick = { evaluationAnswers = evaluationAnswers + (question.key to value) },
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (evaluationAnswers[question.key] == value) "✓ $label" else label) }
                            }
                        }
                    }
                }
                Text(
                    "স্কোর: $evaluationScore/${LeadEvaluation.MAX_SCORE}" +
                        (temperature?.let { " · ${it.uppercase()} (স্বয়ংক্রিয়)" } ?: " · সব উত্তর দিন"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text("Grade (বাধ্যতামূলক)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth()) {
                    listOf("A", "B", "C", "D").forEach { value ->
                        OutlinedButton(
                            onClick = { grade = value },
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                        ) { Text(if (grade == value) "✓ $value" else value) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            CATEGORIES.forEach { (value, label) ->
                OutlinedButton(
                    onClick = { category = value },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) { Text(if (category == value) "✓ $label" else label) }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it },
                label = { Text("কলের সারাংশ (বাধ্যতামূলক)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            if (!noFollowUp) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("ফলো-আপ তারিখ (2026-05-20) — বাধ্যতামূলক") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("সময় (14:30) — বাধ্যতামূলক") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(
                onClick = { noFollowUp = !noFollowUp; if (noFollowUp) { date = ""; time = "" } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) { Text(if (noFollowUp) "✓ ফলো-আপ দরকার নেই" else "ফলো-আপ দরকার নেই") }
            if (noFollowUp) {
                Text("যোগাযোগ বন্ধের কারণ (বাধ্যতামূলক)", fontSize = 12.sp)
                ContactCloseReasons.all.forEach { (code, label) ->
                    OutlinedButton(
                        onClick = { noFollowUpReason = code },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    ) { Text(if (noFollowUpReason == code) "✓ $label" else label) }
                }
                OutlinedTextField(
                    value = noFollowUpNote,
                    onValueChange = { noFollowUpNote = it },
                    label = { Text(if (noFollowUpReason == "other") "\"Other\" কারণের বিবরণ (বাধ্যতামূলক)" else "অতিরিক্ত নোট (ঐচ্ছিক)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (needsReason) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("কারণ (বাধ্যতামূলক)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("নোট (বাধ্যতামূলক)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFB3261E), fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                enabled = ready && reportId != null && !busy,
                onClick = {
                    val id = reportId ?: return@Button
                    busy = true; error = null
                    scope.launch {
                        val followUp = if (noFollowUp) null else "${date}T${time}:00+06:00"
                        val payload = WinstoneApi.reportPayload(
                            reportId = id,
                            category = category,
                            summary = summary,
                            note = note,
                            reason = reason,
                            followUpAtIso = followUp,
                            aiDecision = if (aiCategory != null && aiCategory == category) "accepted" else "edited",
                            direction = direction,
                            callStatus = callState,
                            durationSeconds = seconds,
                            followUpNeeded = !noFollowUp,
                            followUpNote = if (noFollowUp) noFollowUpNote else null,
                            temperature = temperature,
                            grade = grade,
                            evaluationAnswers = if (received) evaluationAnswers else null,
                            noFollowUpReason = if (!noFollowUp) null else if (received) noFollowUpReason else "not_connected",
                        )
                        val result = runCatching { WinstoneApi.submitReportRaw(payload) }
                        busy = false
                        result.onSuccess { LiveCallLauncher.clear(); onSubmitted() }
                            .onFailure { failure ->
                                val message = failure.message.orEmpty()
                                // নেটওয়ার্কের সমস্যা হলে রিপোর্ট ফোনেই জমা থাকবে,
                                // নেট ফিরলে নিজে থেকেই CRM-এ চলে যাবে।
                                if (message.contains("Invalid payload") || message.contains("Unauthorized") ||
                                    message.contains("রিপোর্ট")
                                ) {
                                    error = message.ifBlank { "রিপোর্ট জমা হয়নি" }
                                } else {
                                    CallSyncQueue.queueReportSubmit(context, id, payload.toString())
                                    LiveCallLauncher.clear()
                                    PendingReportWorker.clear(context)
                                    Toast.makeText(
                                        context,
                                        "ইন্টারনেট নেই — রিপোর্ট ফোনে জমা থাকল, সংযোগ ফিরলেই নিজে থেকে CRM-এ চলে যাবে।",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    onSubmitted()
                                }
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "জমা হচ্ছে…" else "রিপোর্ট জমা দিন") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val DIRECTIONS = listOf(
    "outgoing" to "আউটগোয়িং — আমি কল করেছি",
    "incoming" to "ইনকামিং — ক্রেতা কল করেছেন",
)

private val CALL_STATES = listOf(
    "connected" to "কথা হয়েছে",
    "no_answer" to "ধরেনি",
    "busy" to "ব্যস্ত ছিল",
    "failed" to "কল যায়নি",
)

private val CATEGORIES = listOf(
    "hot_lead" to "HOT LEAD — খুব সম্ভাবনাময়",
    "follow_up" to "FOLLOW UP — পরে যোগাযোগ",
    "interested" to "INTERESTED — আগ্রহী",
    "not_interested" to "NOT INTERESTED — আগ্রহী নয়",
    "callback" to "CALLBACK — কলব্যাক চেয়েছেন",
    "no_answer" to "NO ANSWER — ধরেনি",
    "wrong_number" to "WRONG NUMBER — ভুল নম্বর",
    "closed_converted" to "CLOSED / CONVERTED — বিক্রি হয়েছে",
)

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

