package com.winstone.connect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winstone.connect.ui.theme.WinGreen
import com.winstone.connect.ui.theme.WinGreenSoft
import com.winstone.connect.ui.theme.WinInk
import com.winstone.connect.ui.theme.WinInkMuted
import com.winstone.connect.ui.theme.WinSurface

/**
 * Role selection shown after sign-in when the agent can wear more than one hat.
 * For most agents only Sales Agent is available and this screen is skipped.
 */
@Composable
fun RoleSelectionScreen(
    agentName: String,
    allowedRoles: List<String>,
    onSelectRole: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Text(
            "Choose Your Role",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = WinInk,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            "ভূমিকা নির্বাচন করুন — $agentName",
            color = WinInkMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        val roles = listOf(
            RoleCard(
                key = "agent",
                title = "Sales Agent",
                subtitle = "Your own lead queue, call log, AI coaching.",
                icon = Icons.Filled.Person,
            ),
            RoleCard(
                key = "coordinator",
                title = "Coordinator",
                subtitle = "Assign and balance leads, import lists.",
                icon = Icons.Filled.Groups,
            ),
            RoleCard(
                key = "executive",
                title = "Executive HQ",
                subtitle = "Live performance & reports.",
                icon = Icons.Filled.ShowChart,
            ),
            RoleCard(
                key = "it_console",
                title = "IT Console",
                subtitle = "System configuration, data health, access control.",
                icon = Icons.Filled.Build,
            ),
        )

        // Only the desks the server allows for this account are shown. A sales
        // agent sees just their own desk — never Executive HQ or IT Console.
        val visible = roles.filter { allowedRoles.contains(it.key) }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            visible.forEach { role ->
                RoleCardView(role) { onSelectRole(role.key) }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Building A Brighter Bangladesh",
            color = WinGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

private data class RoleCard(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

@Composable
private fun RoleCardView(role: RoleCard, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WinSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(WinGreenSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(role.icon, contentDescription = role.title, tint = WinGreen, modifier = Modifier.size(26.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(role.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = WinInk)
                Text(role.subtitle, color = WinInkMuted, fontSize = 13.sp)
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = WinGreen,
            )
        }
    }
}
