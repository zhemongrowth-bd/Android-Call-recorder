package com.winstone.connect.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winstone.connect.data.remote.MaintenanceControl
import com.winstone.connect.ui.theme.WinCreamDeep
import com.winstone.connect.ui.theme.WinRed

@Composable
fun MaintenanceScreen(state: MaintenanceControl.State) {
    BackHandler(enabled = true) { /* mandatory closure cannot be dismissed */ }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("IT / MANAGEMENT", color = WinRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(state.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text(state.detail, fontSize = 14.sp, lineHeight = 23.sp)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().background(WinCreamDeep, RoundedCornerShape(8.dp)).padding(12.dp),
                ) {
                    Text(
                        "CRM আপাতত বন্ধ আছে। সাময়িক অসুবিধার জন্য আমরা আন্তরিকভাবে দুঃখিত।",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("ধন্যবাদ সবাইকে সহযোগিতা করার জন্য।", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("— IT / Management", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
            }
        }
    }
}