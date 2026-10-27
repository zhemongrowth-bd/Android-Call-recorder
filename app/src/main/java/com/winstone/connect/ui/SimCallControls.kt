package com.winstone.connect.ui

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winstone.connect.telephony.WinstoneInCallService

fun requestSimDialerRole(activity: Activity) {
    val intent = if (Build.VERSION.SDK_INT >= 29) {
        val role = activity.getSystemService(RoleManager::class.java)
        if (!role.isRoleAvailable(RoleManager.ROLE_DIALER)) return
        role.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    } else {
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
    }
    activity.startActivity(intent)
}

@Composable
fun SimCallControls() {
    val state by WinstoneInCallService.controls.collectAsState()
    val context = LocalContext.current
    fun checked(ok: Boolean) {
        if (!ok) Toast.makeText(context, "কল নিয়ন্ত্রণ পাওয়া যায়নি—ফোনের কল-পর্দা ব্যবহার করুন", Toast.LENGTH_LONG).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = state.available, onClick = { checked(WinstoneInCallService.mute(!state.muted)) }, modifier = Modifier.weight(1f)) {
                Text(if (state.muted) "মিউট বন্ধ" else "মিউট")
            }
            OutlinedButton(enabled = state.available && state.speakerSupported, onClick = { checked(WinstoneInCallService.speaker(!state.speaker)) }, modifier = Modifier.weight(1f)) {
                Text(if (state.speaker) "স্পিকার বন্ধ" else "স্পিকার")
            }
        }
        if (state.ringing) Button(onClick = { checked(WinstoneInCallService.answer()) }, modifier = Modifier.fillMaxWidth()) { Text("কল ধরুন") }
        Button(enabled = state.available, onClick = { checked(WinstoneInCallService.disconnect()) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text(if (state.ringing) "কল প্রত্যাখ্যান" else "কল কাটুন") }
        if (!state.available) Text("ইন-অ্যাপ SIM নিয়ন্ত্রণের জন্য Winstone Pulse-কে ডিফল্ট ফোন অ্যাপ করুন।", style = MaterialTheme.typography.bodySmall)
    }
}

/** System calls remain answerable even before CRM sign-in; no customer data read. */
@Composable
fun SystemSimCallScreen() {
    val state by WinstoneInCallService.controls.collectAsState()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(if (state.ringing) "ইনকামিং SIM কল" else "SIM কল", style = MaterialTheme.typography.headlineMedium)
            Text(state.phone, style = MaterialTheme.typography.titleLarge)
            SimCallControls()
        }
    }
}