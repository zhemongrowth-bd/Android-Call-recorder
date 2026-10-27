package com.winstone.connect.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winstone.connect.data.CallRow
import com.winstone.connect.data.Lead
import com.winstone.connect.data.shortTime
import com.winstone.connect.data.statusLabel
import com.winstone.connect.data.remote.WinstoneApi
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.winstone.connect.data.AgentSession
import com.winstone.connect.telephony.LiveCallLauncher
import com.winstone.connect.data.remote.UpdateChecker
import com.winstone.connect.data.remote.VersionHandoff
import com.winstone.connect.location.NearbyLeadLocator
import com.winstone.connect.location.asDistanceLabel
import com.winstone.connect.telephony.RecordingCapabilityCheck
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.winstone.connect.ui.theme.WinAmber
import com.winstone.connect.ui.theme.WinBorder
import com.winstone.connect.ui.theme.WinCreamDeep
import com.winstone.connect.ui.theme.WinGreen
import com.winstone.connect.ui.theme.WinGreenSoft
import com.winstone.connect.ui.theme.WinInkMuted
import com.winstone.connect.ui.theme.WinLive
import com.winstone.connect.ui.theme.WinRed

private const val TAB_HOME = 0
private const val TAB_LEADS = 1
private const val TAB_CALLS = 2
private const val TAB_AI_COACH = 3
private const val TAB_MORE = 4

/**
 * এজেন্ট অ্যাপের প্রধান পর্দা — উপরে ব্র্যান্ড বার, নিচে পাঁচটি ট্যাবের নিচের
 * নেভিগেশন: ড্যাশবোর্ড, লিড, ফলো-আপ, পারফরম্যান্স, প্রোফাইল। ডেটা, কল গেট ও
 * রিপোর্টের নিয়ম আগের মতোই — শুধু সাজানোটা বদলেছে।
 */
@Composable
fun DeskScreen(
    activity: Activity,
    vm: DeskViewModel,
    reportPending: Boolean = false,
    onReportSubmitted: () -> Unit = {},
) {
    val state by vm.state.collectAsStateSafe()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(TAB_HOME) }
    var showRecorder by remember { mutableStateOf(false) }
    val callScope = rememberCoroutineScope()
    var callBlocked by remember { mutableStateOf<String?>(null) }
    var briefing by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var briefingError by remember { mutableStateOf<String?>(null) }
    var briefingSaving by remember { mutableStateOf(false) }
    var showPendingReport by remember { mutableStateOf(false) }
    var showNewLead by remember { mutableStateOf(false) }
    // ফলো-আপ ট্যাব — নিজের সব কল রিপোর্ট, ফিল্টার সহ
    var reports by remember { mutableStateOf<List<ReportRow>>(emptyList()) }
    var reportsLoading by remember { mutableStateOf(false) }
    var reportCategory by remember { mutableStateOf("") }
    var reportState by remember { mutableStateOf("") }
    var openReportLead by remember { mutableStateOf<Lead?>(null) }
    var leadDetail by remember { mutableStateOf<Lead?>(null) }
    var whatsappLead by remember { mutableStateOf<Lead?>(null) }
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateChecker.Available?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    var nearbyLeads by remember { mutableStateOf<List<Lead>?>(null) }
    var locatingLeads by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) {
            locatingLeads = true
            callScope.launch {
                nearbyLeads = NearbyLeadLocator.locate(context, state.data?.leads.orEmpty())
                locatingLeads = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(0)
        update = UpdateChecker.check(installed)
    }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { WinstoneApi.briefingStatus() }
                .onSuccess { briefing = it; briefingError = null }
                .onFailure { briefingError = "ব্রিফিং যাচাই করতে ইন্টারনেট সংযোগ প্রয়োজন" }
            delay(60_000)
        }
    }

    // First launch on a new version: pull back anything earlier versions of the
    // app produced for this agent, so no lead or report is stranded on a version
    // that no longer runs.
    LaunchedEffect(Unit) {
        val claimed = VersionHandoff.claim()
        if (claimed != null && claimed.total > 0) {
            snackbar.showSnackbar(
                "পুরনো সংস্করণের ${claimed.leads}টি লিড ও ${claimed.reports}টি রিপোর্ট এই সংস্করণে বুঝিয়ে দেওয়া হয়েছে",
            )
            vm.refresh(silent = true)
        }
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }
    LaunchedEffect(tab) { if (tab == TAB_AI_COACH && state.coach == null) vm.loadCoach() }

    LaunchedEffect(tab, reportCategory, reportState) {
        if (tab != TAB_CALLS) return@LaunchedEffect
        reportsLoading = true
        reports = runCatching {
            WinstoneApi.listReports(
                reportCategory.takeIf { it.isNotBlank() },
                reportState.takeIf { it.isNotBlank() },
            )
        }.getOrNull()?.let { body ->
            val array = body.optJSONArray("reports")
            (0 until (array?.length() ?: 0)).map { index ->
                val row = array!!.getJSONObject(index)
                val lead = row.optJSONObject("lead")
                ReportRow(
                    id = row.optString("id"),
                    leadId = row.optString("lead_id").takeIf { it.isNotBlank() && it != "null" },
                    leadName = lead?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                    phone = (lead?.optString("phone_number") ?: row.optString("phone_number"))
                        .takeIf { it.isNotBlank() && it != "null" },
                    category = row.optString("category").takeIf { it.isNotBlank() && it != "null" },
                    summary = row.optString("summary").takeIf { it.isNotBlank() && it != "null" },
                    connected = row.optBoolean("connected", false),
                    durationSeconds = row.optInt("duration_seconds", 0),
                    status = row.optString("status"),
                    endedAt = row.optString("call_ended_at").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        } ?: emptyList()
        reportsLoading = false
    }

    val data = state.data
    val leads = data?.leads.orEmpty()
    val displayedLeads = nearbyLeads ?: leads
    val calls = data?.calls.orEmpty()
    val pendingLeads = leads.count { it.status == "pending" }
    val followUpLeads = leads.filter { it.status == "follow_up" }

    /** একটাই কল-পথ: সার্ভার গেট পাস করলে লাইভ কল, নইলে রিপোর্টের বার্তা। */
    fun startCall(lead: Lead) {
        callScope.launch {
            val result = runCatching { WinstoneApi.startCall(lead.id) }
            val gate = result.getOrNull()
            if (result.isFailure || gate == null || gate.optInt("http_status", 500) !in 200..299 || gate.optBoolean("blocked", true)) {
                callBlocked = gate?.optString("reason")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: "CRM যাচাই ছাড়া কল শুরু করা যাবে না — ইন্টারনেট সংযোগ পরীক্ষা করুন"
                if (gate?.optJSONObject("briefing") != null) briefing = gate.optJSONObject("briefing")
            } else {
                LiveCallLauncher.call(activity, lead.id, lead.phone, data?.agentId, lead.name)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            DeskBottomBar(
                tab = tab,
                onTab = { tab = it },
                leadBadge = pendingLeads,
                followUpBadge = followUpLeads.size,
            )
        },
        floatingActionButton = {
            if (tab == TAB_HOME || tab == TAB_LEADS) {
                ExtendedFloatingActionButton(
                    onClick = { showNewLead = true },
                    containerColor = WinGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text("নতুন লিড") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrandBar(
                agentName = data?.agentName ?: "…",
                online = state.error == null,
            )

            update?.takeIf { !updateDismissed || it.mandatory }?.let { available ->
                UpdateBanner(
                    available = available,
                    onInstall = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(available.downloadUrl),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    onDismiss = { updateDismissed = true },
                )
            }

            state.error?.let {
                Text(
                    "সমস্যা: $it",
                    color = WinRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // মূল ব্যানার: আগের কলের রিপোর্ট বাকি — এক ট্যাপে শিট খোলে
            if (reportPending || callBlocked != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        callBlocked ?: "আগের কলের রিপোর্ট জমা বাকি",
                        color = WinRed,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showPendingReport = true }) {
                        Text("এখনই জমা দিন", color = WinRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            if (state.loading && data == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (tab) {
                        TAB_HOME -> {
                            item {
                                AgentDashboardScreen(
                                    activity = activity,
                                    agentName = data?.agentName ?: "…",
                                    employeeId = state.employeeId.orEmpty(),
                                    leads = displayedLeads,
                                    onStartCall = { startCall(it) },
                                    onLeadDetail = { leadDetail = it },
                                    onViewAllLeads = { tab = TAB_LEADS },
                                    onViewAllFollowUps = { tab = TAB_CALLS },
                                )
                            }
                        }
                        TAB_LEADS -> {
                            if (leads.isEmpty()) item { EmptyNote("এখনও কোনো লিড অ্যাসাইন হয়নি।") }
                            if (leads.isNotEmpty()) {
                                item {
                                    OutlinedButton(
                                        enabled = !locatingLeads,
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                locatingLeads = true
                                                callScope.launch {
                                                    nearbyLeads = NearbyLeadLocator.locate(context, leads)
                                                    locatingLeads = false
                                                }
                                            } else {
                                                locationPermission.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                                    ),
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Filled.Place, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (locatingLeads) "দূরত্ব বের হচ্ছে…" else "কাছের লিড আগে দেখান")
                                    }
                                }
                            }
                            items(displayedLeads, key = { it.id }) { lead ->
                                LeadCard(
                                    lead = lead,
                                    recordingMode = state.recordingMode,
                                    recordingReason = state.recordingReason,
                                    onWhatsApp = { whatsappLead = lead },
                                    onCall = { startCall(lead) },
                                )
                            }
                        }
                        TAB_CALLS -> {
                            if (followUpLeads.isNotEmpty()) {
                                item { SectionTitle("আজকের ফলো-আপ", null) {} }
                                items(followUpLeads) { lead ->
                                    ScheduledCallRow(lead = lead, onCall = { startCall(lead) })
                                }
                            }
                            item { SectionTitle("কল রিপোর্ট", null) {} }
                            if (reportsLoading) item { EmptyNote("রিপোর্ট আনা হচ্ছে…") }
                            item {
                                ReportFilterRow(
                                    category = reportCategory,
                                    state = reportState,
                                    onCategory = { reportCategory = it },
                                    onState = { reportState = it },
                                )
                            }
                            if (!reportsLoading && reports.isEmpty()) {
                                item { EmptyNote("এই ফিল্টারে কোনো রিপোর্ট নেই।") }
                            }
                            items(reports) { row ->
                                ReportRowCard(row) { leadId ->
                                    leads.firstOrNull { it.id == leadId }?.let { openReportLead = it }
                                }
                            }
                        }
                        TAB_AI_COACH -> {
                            item { KpiGrid(leads = leads, calls = calls) }
                            item { SectionTitle("AI কোচ", null) {} }
                            if (state.coachLoading) item { EmptyNote("AI কোচ তৈরি হচ্ছে…") }
                            state.coach?.let { coach ->
                                item { CoachCard("সারসংক্ষেপ", listOf(coach.summary)) }
                                if (coach.strengths.isNotEmpty()) item { CoachCard("শক্তি", coach.strengths) }
                                if (coach.risks.isNotEmpty()) item { CoachCard("ঝুঁকি", coach.risks) }
                                if (coach.nextSteps.isNotEmpty()) item { CoachCard("পরবর্তী ধাপ", coach.nextSteps) }
                            }
                            item { PhoneRecordingsSection() }
                            item { SectionTitle("আজকের কল লগ", null) {} }
                            if (calls.isEmpty()) item { EmptyNote("আজ কোনো কল রেকর্ড নেই।") }
                            items(calls) { CallCard(it, leads) }
                        }
                        else -> {
                            item {
                                Button(onClick = { showRecorder = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("রেকর্ডার — ভয়েস নোট")
                                }
                            }
                            item { StatusCards(state) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { vm.refresh() }) { Text("রিফ্রেশ") }
                                    TextButton(onClick = { vm.signOut() }) { Text("সাইন আউট", color = WinRed) }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showRecorder) RecorderScreen(onClose = { showRecorder = false })

    if (showNewLead) {
        NewLeadDialog(
            onDismiss = { showNewLead = false },
            onSubmit = { name, phone, company, notes ->
                vm.submitLead(name, phone, company, notes)
                showNewLead = false
            },
        )
    }

    leadDetail?.let { lead ->
        AlertDialog(
            onDismissRequest = { leadDetail = null },
            title = { Text(lead.name) },
            text = {
                Column {
                    Text(lead.phone, fontSize = 14.sp)
                    lead.company?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp) }
                    lead.address?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = WinInkMuted) }
                    lead.distanceKm?.let { Text(it.asDistanceLabel(), fontSize = 12.sp, color = WinGreen) }
                    lead.notes?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = leadDetail
                    leadDetail = null
                    target?.let {
                        startCall(it)
                    }
                }) { Text("কল করুন") }
            },
            dismissButton = { TextButton(onClick = { leadDetail = null }) { Text("বন্ধ") } },
        )
    }

    openReportLead?.let { lead ->
        AlertDialog(
            onDismissRequest = { openReportLead = null },
            title = { Text(lead.name) },
            text = {
                Column {
                    Text(lead.phone, fontSize = 14.sp)
                    lead.company?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp) }
                    lead.notes?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = openReportLead
                    openReportLead = null
                    target?.let {
                        startCall(it)
                    }
                }) { Text("কল করুন") }
            },
            dismissButton = { TextButton(onClick = { openReportLead = null }) { Text("বন্ধ") } },
        )
    }

    whatsappLead?.let { lead ->
        WhatsAppDialog(
            lead = lead,
            onDismiss = { whatsappLead = null },
            onSend = { text ->
                vm.sendWhatsApp(lead.id, lead.phone, text)
                whatsappLead = null
            },
        )
    }

    // অ্যাপ চালু হওয়ার পরই পেন্ডিং রিপোর্ট থাকলে নিজে খুলে যায়; জমা না দিলে বন্ধ হয় না।
    if (reportPending || showPendingReport) {
        ReportSheet(
            notes = "",
            onSubmitted = {
                showPendingReport = false
                callBlocked = null
                onReportSubmitted()
                vm.refresh()
            },
        )
    }

    val briefingRequired = briefingError != null ||
        (briefing?.optBoolean("required", false) == true && briefing?.optBoolean("acknowledged", false) != true)
    if (briefingRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(briefing?.optString("title") ?: "সকাল ৯:০৫-এর দৈনিক ব্রিফিং") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(briefing?.optString("detail")?.takeIf { it.isNotBlank() }
                        ?: "আজকের ব্রিফিং যাচাই করে তারপর কল শুরু করুন।")
                    Text("এই নোটিশ স্বীকার না করা পর্যন্ত কোনো কল শুরু হবে না।", fontWeight = FontWeight.SemiBold)
                    briefingError?.let { Text(it, color = WinRed, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !briefingSaving,
                    onClick = {
                        briefingSaving = true
                        callScope.launch {
                            val action = if (briefingError != null) WinstoneApi::briefingStatus
                                else WinstoneApi::acknowledgeBriefing
                            runCatching { action() }
                                .onSuccess { briefing = it; briefingError = null }
                                .onFailure { briefingError = "সংযোগ পাওয়া যায়নি — আবার চেষ্টা করুন" }
                            briefingSaving = false
                        }
                    },
                ) {
                    Text(
                        if (briefingSaving) "যাচাই হচ্ছে…"
                        else if (briefingError != null) "আবার যাচাই করুন"
                        else "বুঝেছি — কল শুরু করব",
                    )
                }
            },
        )
    }
}

/** উপরের ব্র্যান্ড বার — লোগো, নাম, ফেজ ব্যাজ, অনলাইন বিন্দু ও এজেন্টের আদ্যক্ষর। */
@Composable
private fun BrandBar(agentName: String, online: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(WinGreen, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "W",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("উইনস্টোন এজেন্ট", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Pill("অন-ডিউটি")
            }
            Text(
                "Winstone Properties Ltd. · অভ্যন্তরীণ সেলস",
                color = WinInkMuted,
                fontSize = 11.sp,
            )
        }
        Box(
            Modifier
                .size(8.dp)
                .background(if (online) WinLive else WinRed, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Avatar(agentName, 34.dp)
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = WinGreen,
        modifier = Modifier
            .background(WinGreenSoft, RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val initials = name.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercase() }
        .take(2).joinToString("").ifBlank { "W" }
    Box(
        Modifier
            .size(size)
            .background(WinGreenSoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = WinGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** স্বাগত কার্ড — নাম, আইডি, আজকের তারিখ, শিফট ও লক্ষ্য। */
@Composable
private fun WelcomeCard(
    agentName: String,
    employeeId: String,
    online: Boolean,
    targetLine: String,
) {
    val today = remember {
        java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", java.util.Locale("bn", "BD"))
            .format(java.util.Date())
    }
    WinCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Winstone Properties Ltd.", color = WinGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("স্বাগতম, $agentName", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(today, color = WinInkMuted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(employeeId.ifBlank { "—" }, color = WinInkMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Avatar(agentName, 44.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (online) WinLive else WinRed, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (online) "শিফট: সক্রিয় · CRM সংযুক্ত" else "শিফট: সক্রিয় · অফলাইন",
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(targetLine, fontSize = 12.sp, color = WinInkMuted)
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = WinInkMuted,
            modifier = Modifier.weight(1f),
        )
        action?.let {
            Text(
                "$it ›",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WinGreen,
                modifier = Modifier.clickable { onAction() },
            )
        }
    }
}

/** ছয়টি টাইলে আজকের অবস্থা — স্ক্রিনশটের KPI গ্রিডের মতো। */
@Composable
private fun KpiGrid(leads: List<Lead>, calls: List<CallRow>) {
    val connected = calls.count { it.durationSeconds > 0 }
    val talkMinutes = calls.sumOf { it.durationSeconds } / 60
    val avgSeconds = if (calls.isNotEmpty()) calls.sumOf { it.durationSeconds } / calls.size else 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("নতুন লিড", leads.count { it.status == "pending" }.toString(), "অপেক্ষমাণ", Modifier.weight(1f))
            KpiTile("আজকের কল", calls.size.toString(), "$connected কথা", Modifier.weight(1f))
            KpiTile("ফলো-আপ", leads.count { it.status == "follow_up" }.toString(), "নির্ধারিত", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiTile("সম্পন্ন", leads.count { it.status == "closed" }.toString(), "লিড", Modifier.weight(1f))
            KpiTile("কথা", "$talkMinutes মি.", "মোট", Modifier.weight(1f))
            KpiTile("গড় সময়", "$avgSeconds সে.", "প্রতি কলে", Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(WinCreamDeep, RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Column {
            Text(hint, fontSize = 10.sp, color = WinGreen, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(label, fontSize = 11.sp, color = WinInkMuted)
        }
    }
}

/** নির্ধারিত কলের সারি — নাম, প্রতিষ্ঠান/নোট এবং গোল কল বাটন। */
@Composable
private fun ScheduledCallRow(lead: Lead, onCall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, WinBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lead.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        statusLabel(lead.status),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lead.status == "pending") WinAmber else WinGreen,
                        modifier = Modifier
                            .background(WinGreenSoft, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    listOfNotNull(lead.phone, lead.company?.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    color = WinInkMuted,
                    fontSize = 12.sp,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .background(WinGreenSoft, CircleShape)
                    .clickable { onCall() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Phone, contentDescription = "কল করুন", tint = WinGreen)
            }
        }
    }
}

@Composable
private fun InfoBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = WinGreenSoft),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("উইনস্টোন এজেন্ট সিস্টেম", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WinGreen)
            Text(
                "কল, রেকর্ডিং ও রিপোর্ট ইন্টারনেট ছাড়াও ফোনে জমা থাকে এবং নেটওয়ার্ক ফিরলে নিজে থেকেই CRM-এ চলে যায়।",
                fontSize = 12.sp,
                color = WinInkMuted,
            )
        }
    }
}

/** নিচের নেভিগেশন — পাঁচটি ট্যাব, লিড ও ফলো-আপে সংখ্যার ব্যাজ। */
@Composable
private fun DeskBottomBar(
    tab: Int,
    onTab: (Int) -> Unit,
    leadBadge: Int,
    followUpBadge: Int,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf(
            Triple("Home", Icons.Filled.GridView, 0),
            Triple("Leads", Icons.Filled.Groups, leadBadge),
            Triple("Calls", Icons.Filled.CalendarMonth, followUpBadge),
            Triple("AI Coach", Icons.Filled.BarChart, 0),
            Triple("More", Icons.Filled.Person, 0),
        )
        items.forEachIndexed { index, (label, icon, badge) ->
            NavigationBarItem(
                selected = tab == index,
                onClick = { onTab(index) },
                icon = {
                    if (badge > 0) {
                        BadgedBox(badge = {
                            Badge(containerColor = WinGreen, contentColor = MaterialTheme.colorScheme.onPrimary) {
                                Text(badge.toString(), fontSize = 9.sp)
                            }
                        }) { Icon(icon, contentDescription = label) }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = WinGreen,
                    selectedTextColor = WinGreen,
                    indicatorColor = WinGreenSoft,
                    unselectedIconColor = WinInkMuted,
                    unselectedTextColor = WinInkMuted,
                ),
            )
        }
    }
}

/**
 * Message composer for the lead's WhatsApp. Sending opens WhatsApp on this phone
 * and syncs the same text to the CRM lead timeline.
 */
@Composable
private fun WhatsAppDialog(lead: Lead, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("হোয়াটসঅ্যাপ · ${lead.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lead.phone, fontSize = 12.sp, color = WinInkMuted)
                OutlinedTextField(text, { text = it }, label = { Text("মেসেজ") })
                Text(
                    "পাঠালে হোয়াটসঅ্যাপ খুলবে এবং একই লেখা লিডের টাইমলাইনে জমা হবে।",
                    fontSize = 11.sp,
                    color = WinInkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSend(text.trim()) }) {
                Text("পাঠান")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    )
}

@Composable
private fun WinCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, WinBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun LeadCard(
    lead: Lead,
    recordingMode: String?,
    recordingReason: String?,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
) {
    // Honest per-lead recording state, straight from this phone's own probe.
    val blocked = recordingMode == "unavailable"
    val recordingLine = when (recordingMode) {
        "two_sided" -> "রেকর্ডিং: দুই পাশের কথা জমা হবে"
        "mic_only" -> "রেকর্ডিং: শুধু এজেন্টের পাশ জমা হবে"
        "unavailable" -> "এই ফোনে রেকর্ডিং সম্ভব নয় — রেকর্ড কল বন্ধ"
        else -> "রেকর্ডিং ক্ষমতা যাচাই হচ্ছে…"
    }
    val recordingColor = when (recordingMode) {
        "two_sided" -> WinGreen
        "mic_only" -> WinAmber
        "unavailable" -> WinAmber
        else -> WinInkMuted
    }
    WinCard {
        Text(lead.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            listOfNotNull(lead.phone, lead.company).joinToString(" · "),
            color = WinInkMuted,
            fontSize = 13.sp,
        )
        lead.address?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = WinInkMuted) }
        lead.distanceKm?.let { Text(it.asDistanceLabel(), fontSize = 12.sp, color = WinGreen, fontWeight = FontWeight.SemiBold) }
        Text(
            "${statusLabel(lead.status)} · চেষ্টা ${lead.attempts}",
            color = if (lead.status == "pending") WinAmber else WinGreen,
            fontSize = 12.sp,
        )
        lead.notes?.let { Text(it, fontSize = 12.sp, color = WinInkMuted) }
        Spacer(Modifier.height(6.dp))
        Text(recordingLine, fontSize = 12.sp, color = recordingColor)
        if (blocked) {
            recordingReason?.let { Text(it, fontSize = 10.sp, color = WinInkMuted) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(
                onClick = onCall,
                modifier = Modifier.weight(1f),
            ) { Text(if (blocked) "কল করুন (রেকর্ডিং ছাড়া)" else "কল করুন") }
            OutlinedButton(
                onClick = onWhatsApp,
                modifier = Modifier.weight(1f),
            ) { Text("হোয়াটসঅ্যাপ") }
        }
    }
}

@Composable
private fun CallCard(call: CallRow, leads: List<Lead>) {
    val leadName = leads.firstOrNull { it.id == call.leadId }?.name ?: call.phone
    WinCard {
        Text(leadName, fontWeight = FontWeight.SemiBold)
        Text(
            "${call.durationSeconds} সেকেন্ড · ${shortTime(call.createdAt)}",
            fontSize = 12.sp,
            color = WinInkMuted,
        )
        Text(
            when (call.syncStatus) {
                "failed" -> "সার্ভারে ওঠেনি"
                "verified" -> "যাচাই হয়েছে"
                else -> "আপলোড হয়েছে"
            },
            fontSize = 12.sp,
            color = if (call.syncStatus == "failed") WinRed else WinGreen,
        )
        call.summary?.let { Text(it, fontSize = 13.sp) }
        call.sentiment?.let { Text("AI মনোভাব: $it", fontSize = 12.sp, color = WinInkMuted) }
    }
}

@Composable
private fun CoachCard(title: String, lines: List<String>) {
    WinCard {
        Text(title, fontWeight = FontWeight.Bold)
        lines.forEach { Text("• $it", fontSize = 13.sp) }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = WinInkMuted, fontSize = 13.sp)
}

@Composable
private fun NewLeadDialog(onDismiss: () -> Unit, onSubmit: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন লিড জমা দিন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("নাম") })
                OutlinedTextField(phone, { phone = it }, label = { Text("মোবাইল (01XXXXXXXXX)") })
                OutlinedTextField(company, { company = it }, label = { Text("প্রতিষ্ঠান (ঐচ্ছিক)") })
                OutlinedTextField(notes, { notes = it }, label = { Text("নোট (ঐচ্ছিক)") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phone.length >= 11,
                onClick = { onSubmit(name.trim(), phone.trim(), company.trim(), notes.trim()) },
            ) { Text("জমা দিন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    )
}

/**
 * Device + sync status, so an agent can see at a glance whether this phone is
 * bound, whether recording works on it, and whether anything is still waiting
 * to reach the CRM.
 */
@Composable
private fun StatusCards(state: DeskUiState) {
    val context = LocalContext.current
    var capability by remember { mutableStateOf(RecordingCapabilityCheck.cachedOrNull()) }
    LaunchedEffect(Unit) {
        if (capability == null) capability = RecordingCapabilityCheck.check(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WinCard {
            Text("ইন-অ্যাপ SIM কল নিয়ন্ত্রণ", fontWeight = FontWeight.Bold)
            Text("মিউট, স্পিকার ও কল কাটার জন্য ডিফল্ট ফোন অ্যাপের অনুমতি প্রয়োজন। রেকর্ডিং সক্ষমতা আলাদা।", fontSize = 12.sp)
            OutlinedButton(onClick = { (context as? Activity)?.let { requestSimDialerRole(it) } }) {
                Text("ফোন অ্যাপের অনুমতি দিন")
            }
        }
        WinCard {
            Text("ডিভাইস", fontWeight = FontWeight.Bold)
            Text("এজেন্ট: ${state.data?.agentName ?: "—"}", fontSize = 13.sp)
            Text("Employee ID: ${state.employeeId ?: "—"}", fontSize = 13.sp, color = WinInkMuted)
            Text(
                "ডিভাইস আইডি: ${AgentSession.deviceId ?: "—"}",
                fontSize = 12.sp,
                color = WinInkMuted,
            )
            Text(
                if (AgentSession.deviceToken != null) "এই ফোনটি CRM-এ যুক্ত আছে"
                else "এই ফোনটি এখনও যুক্ত হয়নি — আবার সাইন ইন করুন",
                fontSize = 12.sp,
                color = if (AgentSession.deviceToken != null) WinGreen else WinRed,
            )
        }

        WinCard {
            Text("ফোনের তথ্য", fontWeight = FontWeight.Bold)
            Text(
                "অ্যাপ ভার্সন: ${com.winstone.connect.BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = WinInkMuted,
            )
            Text(
                "ফোন: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                fontSize = 12.sp,
                color = WinInkMuted,
            )
            Text(
                "অ্যান্ড্রয়েড: ${android.os.Build.VERSION.RELEASE}",
                fontSize = 12.sp,
                color = WinInkMuted,
            )
            Text(
                state.recordingStatus?.let { "CRM-এ জানানো হয়েছে: $it" }
                    ?: "রেকর্ডিং ক্ষমতা CRM-এ পাঠানোর অপেক্ষায়",
                fontSize = 12.sp,
                color = if (state.recordingStatus != null) WinGreen else WinInkMuted,
            )
        }

        WinCard {
            Text("সিঙ্ক", fontWeight = FontWeight.Bold)
            Text(
                if (state.error == null && state.lastSyncedAt != null) "সর্বশেষ পড়া সফল হয়েছে; অডিও আপলোড আলাদা"
                else if (state.error == null) "সার্ভারের নিশ্চিত তথ্যের অপেক্ষায়"
                else "সংযোগে সমস্যা: ${state.error}",
                fontSize = 13.sp,
                color = if (state.error == null) WinGreen else WinRed,
            )
            Text(
                "শেষ আপডেট: ${state.lastSyncedAt ?: "—"}",
                fontSize = 12.sp,
                color = WinInkMuted,
            )
            val recLabel = com.winstone.connect.data.sync.RecordingSyncLabel.of(state.sync)
            Text(
                "Recording: ${recLabel.english} · ${recLabel.bangla}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (recLabel) {
                    com.winstone.connect.data.sync.RecordingSyncLabel.FAILED -> WinRed
                    com.winstone.connect.data.sync.RecordingSyncLabel.SYNCED -> WinGreen
                    com.winstone.connect.data.sync.RecordingSyncLabel.SYNCING,
                    com.winstone.connect.data.sync.RecordingSyncLabel.PENDING_SYNC -> WinAmber
                    else -> WinInkMuted
                },
            )
            if (recLabel == com.winstone.connect.data.sync.RecordingSyncLabel.FAILED) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var retryNote by remember { mutableStateOf<String?>(null) }
                androidx.compose.material3.OutlinedButton(onClick = {
                    val n = com.winstone.connect.data.sync.CallSyncQueue.retryFailedRecordings(ctx)
                    retryNote = if (n > 0) "Resending $n recording(s) · $n টি রেকর্ডিং আবার পাঠানো হচ্ছে"
                    else "No recording can be resent — the audio file is no longer on this phone · ফোনে অডিও ফাইল নেই"
                }) { Text("Retry failed uploads · আবার পাঠান") }
                retryNote?.let { Text(it, fontSize = 11.sp, color = WinInkMuted) }
            }
            Text(
                "পাঠানোর অপেক্ষায়: ${state.sync.pending} · ব্যর্থ: ${state.sync.failed}",
                fontSize = 12.sp,
                color = if (state.sync.failed > 0) WinRed else WinInkMuted,
            )
            val syncTime = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Dhaka") }
            Text(
                "অডিও সার্ভারে নিশ্চিত: ${if (state.sync.lastAudioUploadedAtMillis > 0) syncTime.format(java.util.Date(state.sync.lastAudioUploadedAtMillis)) else "যাচাই হয়নি"}",
                fontSize = 12.sp, color = WinInkMuted,
            )
            Text(
                "রিপোর্ট সার্ভারে নিশ্চিত: ${if (state.sync.lastReportSyncedAtMillis > 0) syncTime.format(java.util.Date(state.sync.lastReportSyncedAtMillis)) else "যাচাই হয়নি"} · Asia/Dhaka",
                fontSize = 12.sp, color = WinInkMuted,
            )
            state.sync.lastError?.let {
                Text("শেষ সমস্যা: $it", fontSize = 11.sp, color = WinRed)
            }
            Text(
                "ইন্টারনেট না থাকলে কল, রেকর্ডিং ও রিপোর্ট ফোনে জমা থাকে এবং নেটওয়ার্ক ফিরলে নিজে থেকেই পাঠানো হয়।",
                fontSize = 12.sp,
                color = WinInkMuted,
            )
        }

        WinCard {
            Text("কল রেকর্ডিং", fontWeight = FontWeight.Bold)
            Text(
                capability?.label ?: "পরীক্ষা করা হচ্ছে…",
                fontSize = 13.sp,
                color = if (capability?.available == false) WinRed else WinGreen,
            )
            capability?.reason?.let {
                Text(it, fontSize = 11.sp, color = WinInkMuted)
            }
            Text(
                "রেকর্ডিং সম্ভব না হলেও কলের তথ্য ও বাধ্যতামূলক রিপোর্ট আগের মতোই CRM-এ যাবে।",
                fontSize = 12.sp,
                color = WinInkMuted,
            )
        }
    }
}


/**
 * New-version banner. A mandatory release cannot be dismissed, but installing is
 * still an explicit agent action followed by Android's own install confirmation.
 */
@Composable
private fun UpdateBanner(
    available: UpdateChecker.Available,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = WinGreenSoft),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (available.mandatory) "নতুন ভার্সন বাধ্যতামূলক: ${available.versionName}"
                else "নতুন ভার্সন এসেছে: ${available.versionName}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            available.notes?.let {
                Text(it, fontSize = 12.sp, color = WinInkMuted, modifier = Modifier.padding(top = 2.dp))
            }
            Text(
                "ডাউনলোড শেষে ইনস্টল করার অনুমতি ফোন নিজেই চাইবে।",
                fontSize = 11.sp,
                color = WinInkMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onInstall) { Text("ডাউনলোড ও ইনস্টল") }
                if (!available.mandatory) {
                    TextButton(onClick = onDismiss) { Text("পরে") }
                }
            }
        }
    }
}

/** One row of the agent's report history. */
data class ReportRow(
    val id: String,
    val leadId: String?,
    val leadName: String?,
    val phone: String?,
    val category: String?,
    val summary: String?,
    val connected: Boolean,
    val durationSeconds: Int,
    val status: String,
    val endedAt: String?,
)

@Composable
private fun ReportFilterRow(
    category: String,
    state: String,
    onCategory: (String) -> Unit,
    onState: (String) -> Unit,
) {
    Column {
        Text("ফিল্টার", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("" to "সব", "connected" to "কথা হয়েছে", "not_connected" to "কথা হয়নি")
                .forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { onState(value) },
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                    ) { Text(if (state == value) "✓ $label" else label, fontSize = 11.sp) }
                }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("" to "সব ক্যাটাগরি", "hot_lead" to "HOT", "follow_up" to "FOLLOW UP")
                .forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { onCategory(value) },
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                    ) { Text(if (category == value) "✓ $label" else label, fontSize = 11.sp) }
                }
        }
    }
}

@Composable
private fun ReportRowCard(row: ReportRow, onOpenLead: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, WinBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                row.leadName ?: row.phone ?: "অজানা নম্বর",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            row.phone?.let { Text(it, fontSize = 12.sp, color = WinInkMuted) }
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    if (row.connected) "কথা হয়েছে" else "কথা হয়নি",
                    "${row.durationSeconds} সেকেন্ড",
                    row.category,
                    if (row.status == "pending") "জমা হয়নি" else null,
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = if (row.status == "pending") WinRed else Color.Unspecified,
            )
            row.summary?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 12.sp)
            }
            row.leadId?.let { leadId ->
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { onOpenLead(leadId) }) { Text("লিড খুলুন", fontSize = 12.sp) }
            }
        }
    }
}

/** Each recording queued on this phone with its own sync status, read from its upload job. */
@Composable
private fun PhoneRecordingsSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val flow = remember { androidx.work.WorkManager.getInstance(ctx).getWorkInfosByTagFlow(com.winstone.connect.data.sync.CallSyncQueue.TAG_RECORDING) }
    val infos by flow.collectAsState(initial = emptyList())
    if (infos.isEmpty()) return
    WinCard {
        Text("Phone recordings · ফোনের রেকর্ডিং", fontWeight = FontWeight.SemiBold)
        infos.forEach { info ->
            val phone = info.tags.firstOrNull { it.startsWith(com.winstone.connect.data.sync.CallSyncQueue.TAG_PHONE_PREFIX) }
                ?.removePrefix(com.winstone.connect.data.sync.CallSyncQueue.TAG_PHONE_PREFIX).orEmpty().ifBlank { "Unknown number" }
            val label = com.winstone.connect.data.sync.RecordingSyncLabel.ofWork(info.state, audioOnPhone = true)
            val color = when (label) {
                com.winstone.connect.data.sync.RecordingSyncLabel.FAILED -> WinRed
                com.winstone.connect.data.sync.RecordingSyncLabel.SYNCED -> WinGreen
                else -> WinInkMuted
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(phone, fontSize = 13.sp)
                Text("${label.english} · ${label.bangla}", fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
            }
        }
    }
}
