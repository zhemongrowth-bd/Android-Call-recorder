package com.winstone.connect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winstone.connect.data.AgentSession
import com.winstone.connect.data.sync.PendingReportWorker
import com.winstone.connect.telephony.CallPhase
import com.winstone.connect.telephony.LiveCallLauncher
import com.winstone.connect.ui.DeskScreen
import com.winstone.connect.ui.LiveCallScreen
import com.winstone.connect.ui.MaintenanceScreen
import com.winstone.connect.ui.DeskViewModel
import com.winstone.connect.ui.RoleSelectionScreen
import com.winstone.connect.ui.SignInScreen
import com.winstone.connect.ui.collectAsStateSafe
import com.winstone.connect.ui.theme.WinstoneTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.winstone.connect.data.remote.MaintenanceControl

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.winstone.connect.data.AppVersion.init(this)
        requestCallPermissions()

        setContent {
            WinstoneTheme {
                val vm: DeskViewModel = viewModel { DeskViewModel(application) }
                val state by vm.state.collectAsStateSafe()
                val phase by LiveCallLauncher.phase.collectAsStateSafe()
                var maintenance by remember { mutableStateOf(MaintenanceControl.cached(this@MainActivity)) }
                LaunchedEffect(Unit) {
                    while (true) {
                        maintenance = MaintenanceControl.refresh(this@MainActivity)
                        delay(20_000)
                    }
                }

                // Mandatory post-call report gate: even if the app was closed
                // after the call, the report opens itself on the next launch
                // and no new call is possible before it is submitted.
                // `reportTick` is bumped on every resume and whenever the report
                // notification reopens the app, so a call made from the phone's
                // own dialer is re-checked instead of only on first composition.
                val tick by reportTick.collectAsStateSafe()
                var pendingReport by remember { mutableStateOf(false) }
                LaunchedEffect(phase, state.employeeId, tick) {
                    if (phase == CallPhase.Idle && !state.employeeId.isNullOrBlank()) {
                        val body = runCatching {
                            com.winstone.connect.data.remote.WinstoneApi.pendingReport()
                        }.getOrNull()
                        pendingReport = body?.optJSONObject("pending") != null
                        if (!pendingReport) PendingReportWorker.clear(this@MainActivity)
                    }
                }

                if (maintenance.active) {
                    if (com.winstone.connect.telephony.WinstoneInCallService.controls.collectAsStateSafe().value.available) {
                        com.winstone.connect.ui.SystemSimCallScreen()
                    } else {
                    MaintenanceScreen(maintenance)
                    }
                } else if (phase != CallPhase.Idle) {
                    LiveCallScreen(phase = phase)
                } else if (com.winstone.connect.telephony.WinstoneInCallService.controls.collectAsStateSafe().value.available) {
                    com.winstone.connect.ui.SystemSimCallScreen()
                } else if (state.employeeId.isNullOrBlank()) {
                    SignInScreen(
                        error = state.error,
                        loading = state.loading,
                        onSignIn = { id, pass -> vm.signIn(id, pass) },
                    )
                } else if (
                    AgentSession.selectedRoleNow(this@MainActivity).isNullOrBlank() &&
                    AgentSession.allowedRolesNow(this@MainActivity).size > 1
                ) {
                    RoleSelectionScreen(
                        agentName = state.agentName ?: "",
                        allowedRoles = AgentSession.allowedRolesNow(this@MainActivity),
                        onSelectRole = { role ->
                            lifecycleScope.launch {
                                AgentSession.saveSelectedRole(this@MainActivity, role)
                            }
                        },
                    )
                } else {
                    DeskScreen(
                        activity = this,
                        vm = vm,
                        reportPending = pendingReport,
                        onReportSubmitted = {
                            pendingReport = false
                            PendingReportWorker.clear(this@MainActivity)
                            // Another call may have rung meanwhile: re-check so its
                            // own report opens straight away.
                            reportTick.value = reportTick.value + 1
                        },
                    )
                }
            }
        }
    }

    /**
     * Every time the agent opens the app, reconcile the phone's call log: a call
     * made or received through any other app still becomes a CRM entry with its
     * mandatory report.
     */
    override fun onResume() {
        super.onResume()
        reportTick.value = reportTick.value + 1
        Thread { runCatching { com.winstone.connect.telephony.CallLogSweeper.sweep(this) } }.start()
    }

    /** The report notification reopens the running app: re-check immediately. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reportTick.value = reportTick.value + 1
    }

    private companion object {
        /** Bumped to force a fresh pending-report check. */
        val reportTick = MutableStateFlow(0)
    }

    private fun requestCallPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALL_LOG,
        )
        if (Build.VERSION.SDK_INT >= 28) wanted += Manifest.permission.ANSWER_PHONE_CALLS
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        // Read the phone's own call-recorder files so they reach the CRM.
        wanted += if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        ActivityCompat.requestPermissions(this, wanted.toTypedArray(), 9001)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LiveCallLauncher.REQ_CALL && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            LiveCallLauncher.retryPendingCall(this)
        }
    }
}
