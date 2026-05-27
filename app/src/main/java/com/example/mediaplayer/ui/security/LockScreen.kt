package com.example.mediaplayer.ui.security

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.mediaplayer.data.SecurityDatabaseHelper

@Composable
fun LockScreen(
    isSettingUp: Boolean,
    onSuccess: () -> Unit,
    onCancelSetup: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val dbHelper = remember { SecurityDatabaseHelper(context) }
    var pinText by remember { mutableStateOf("") }
    var setupStep by remember { mutableIntStateOf(1) } // 1: Enter new, 2: Confirm new
    var tempPin by remember { mutableStateOf("") }

    val promptTitle = if (isSettingUp) {
        if (setupStep == 1) "Thiết lập mã PIN mới" else "Xác nhận mã PIN"
    } else {
        "Nhập mã PIN để mở khóa"
    }

    // Biometric authentication trigger
    val activity = context as? FragmentActivity
    val biometricEnabled = remember { dbHelper.isBiometricEnabled() }

    fun triggerBiometrics() {
        if (activity != null && biometricEnabled && !isSettingUp) {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        activity.runOnUiThread {
                            onSuccess()
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Xác thực vân tay")
                .setSubtitle("Quét vân tay để truy cập Media Player")
                .setNegativeButtonText("Nhập mã PIN")
                .build()

            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto-trigger biometric on start if enabled
    LaunchedEffect(Unit) {
        if (!isSettingUp && biometricEnabled) {
            triggerBiometrics()
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = promptTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Code circles display
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < pinText.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Num pad
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(280.dp)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Bio", "0", "Del")
                )

                for (row in keys) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (key in row) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (key == "Bio" || key == "Del") Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable(
                                        enabled = !(key == "Bio" && (!biometricEnabled || isSettingUp))
                                    ) {
                                        when (key) {
                                            "Del" -> {
                                                if (pinText.isNotEmpty()) {
                                                    pinText = pinText.dropLast(1)
                                                }
                                            }
                                            "Bio" -> {
                                                triggerBiometrics()
                                            }
                                            else -> {
                                                if (pinText.length < 4) {
                                                    pinText += key
                                                    if (pinText.length == 4) {
                                                        // Process completed PIN
                                                        if (isSettingUp) {
                                                            if (setupStep == 1) {
                                                                tempPin = pinText
                                                                pinText = ""
                                                                setupStep = 2
                                                            } else {
                                                                if (pinText == tempPin) {
                                                                    dbHelper.setPassword(pinText)
                                                                    Toast.makeText(context, "Thiết lập mã PIN thành công!", Toast.LENGTH_SHORT).show()
                                                                    onSuccess()
                                                                } else {
                                                                    Toast.makeText(context, "Mã PIN xác nhận không trùng khớp. Hãy thử lại!", Toast.LENGTH_SHORT).show()
                                                                    pinText = ""
                                                                    setupStep = 1
                                                                }
                                                            }
                                                        } else {
                                                            if (dbHelper.verifyPassword(pinText)) {
                                                                onSuccess()
                                                            } else {
                                                                Toast.makeText(context, "Sai mã PIN!", Toast.LENGTH_SHORT).show()
                                                                pinText = ""
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "Bio" -> {
                                        if (biometricEnabled && !isSettingUp) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Vân tay",
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    "Del" -> {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Xóa",
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = key,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isSettingUp && onCancelSetup != null) {
                TextButton(onClick = onCancelSetup) {
                    Text("Hủy thiết lập", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
