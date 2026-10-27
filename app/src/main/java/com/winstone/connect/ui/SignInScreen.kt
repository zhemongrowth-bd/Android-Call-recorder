package com.winstone.connect.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winstone.connect.ui.theme.WinGreen
import com.winstone.connect.ui.theme.WinGreenSoft
import com.winstone.connect.ui.theme.WinInk
import com.winstone.connect.ui.theme.WinInkMuted

/**
 * V2 Login Screen — Employee ID + password, black-gold branded.
 */
@Composable
fun SignInScreen(
    error: String?,
    loading: Boolean = false,
    onSignIn: (String, String) -> Unit,
) {
    var employeeId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        // Logo mark
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(WinGreenSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "W",
                color = WinGreen,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Winstone Pulse",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = WinInk,
        )
        Text(
            "The Premium Real Estate CRM\nFind. Build. Invest.",
            textAlign = TextAlign.Center,
            color = WinInkMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(48.dp))

        WinTextField(
            value = employeeId,
            onValueChange = { employeeId = it.uppercase() },
            label = "Agent Identifier / Employee ID",
            leadingIcon = Icons.Filled.Person,
            keyboardType = KeyboardType.Text,
        )

        Spacer(Modifier.height(14.dp))

        WinTextField(
            value = password,
            onValueChange = { password = it },
            label = "Account Password",
            leadingIcon = Icons.Filled.Lock,
            keyboardType = KeyboardType.Password,
            isPassword = true,
            passwordVisible = passwordVisible,
            onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
        )

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onSignIn(employeeId.trim(), password) },
            enabled = !loading && employeeId.trim().length >= 4 && password.length >= 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WinGreen, contentColor = MaterialTheme.colorScheme.onPrimary),
        ) {
            if (loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text("Authenticate & Launch Deck", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.size(width = 8.dp, height = 0.dp))
                Icon(Icons.Filled.ArrowForward, contentDescription = null)
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { /* forgot password flow */ }) {
            Text("Forgot Password?", color = WinInkMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Building A Brighter Bangladesh",
            color = WinGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "© 2026 TrendFlux Digital.\nDeveloped by Zahid Hasan Emon.",
            textAlign = TextAlign.Center,
            color = WinInkMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun WinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityChange: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = WinInkMuted) },
        trailingIcon = if (isPassword) {
            {
                TextButton(onClick = { onPasswordVisibilityChange?.invoke() }) {
                    Text(if (passwordVisible) "Hide" else "Show", color = WinGreen, fontSize = 12.sp)
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = WinGreenSoft,
            unfocusedContainerColor = WinGreenSoft,
            focusedBorderColor = WinGreen,
            unfocusedBorderColor = WinInkMuted.copy(alpha = 0.3f),
            focusedLabelColor = WinGreen,
            unfocusedLabelColor = WinInkMuted,
            focusedTextColor = WinInk,
            unfocusedTextColor = WinInk,
        ),
    )
}
