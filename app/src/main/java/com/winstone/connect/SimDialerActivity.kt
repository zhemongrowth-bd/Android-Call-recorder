package com.winstone.connect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.winstone.connect.ui.theme.WinstoneTheme

/** ACTION_DIAL requires explicit confirmation, never auto-dials an external URI. */
class SimDialerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WinstoneTheme {
            var number by remember { mutableStateOf(intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()) }
            var error by remember { mutableStateOf<String?>(null) }
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Winstone Pulse · SIM", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("ফোন নম্বর") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = number.isNotBlank(), onClick = {
                        val telecom = getSystemService(TelecomManager::class.java)
                        val uri = Uri.fromParts("tel", number, null)
                        if (ContextCompat.checkSelfPermission(this@SimDialerActivity, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                            // Emergency and denied-permission calls use the preloaded system dialer.
                            val fallback = Intent(Intent.ACTION_DIAL, uri).setPackage(telecom.systemDialerPackage)
                            runCatching { startActivity(fallback) }.onFailure { error = "ফোন অ্যাপ খোলা যায়নি" }
                        } else {
                            runCatching { telecom.placeCall(uri, Bundle()) }.onFailure { error = "কল শুরু করা যায়নি" }
                        }
                    }) { Text("SIM দিয়ে কল করুন") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        } }
    }
}