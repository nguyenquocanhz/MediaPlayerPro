package com.example.mediaplayer.ui.settings

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediaplayer.data.SecurityDatabaseHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToSetupPin: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToEula: () -> Unit
) {
    val context = LocalContext.current
    val dbHelper = remember { SecurityDatabaseHelper(context) }
    
    var isPinSet by remember { mutableStateOf(dbHelper.isPasswordSet()) }
    var isBiometricEnabled by remember { mutableStateOf(dbHelper.isBiometricEnabled()) }
    
    var showDeletePinDialog by remember { mutableStateOf(false) }
    var deletePinInput by remember { mutableStateOf("") }
    
    // Check if biometric is supported on hardware level
    val biometricManager = remember { BiometricManager.from(context) }
    val isBiometricHardwareAvailable = remember {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Bảo mật",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column {
                    // PIN row
                    SettingsRow(
                        icon = Icons.Default.Lock,
                        title = "Mã PIN bảo mật",
                        subtitle = if (isPinSet) "Đã thiết lập" else "Chưa thiết lập",
                        onClick = {
                            if (isPinSet) {
                                showDeletePinDialog = true
                            } else {
                                onNavigateToSetupPin()
                            }
                        },
                        trailing = {
                            if (isPinSet) {
                                Button(
                                    onClick = { showDeletePinDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Xóa PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = onNavigateToSetupPin,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Thiết lập", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Biometric Row
                    SettingsRow(
                        icon = Icons.Default.Fingerprint,
                        title = "Khóa bằng vân tay",
                        subtitle = if (!isBiometricHardwareAvailable) {
                            "Thiết bị không hỗ trợ vân tay"
                        } else if (!isPinSet) {
                            "Cần thiết lập mã PIN trước"
                        } else {
                            "Yêu cầu vân tay khi mở ứng dụng"
                        },
                        onClick = {
                            if (isPinSet && isBiometricHardwareAvailable) {
                                val newState = !isBiometricEnabled
                                dbHelper.setBiometricEnabled(newState)
                                isBiometricEnabled = newState
                            }
                        },
                        trailing = {
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { checked ->
                                    dbHelper.setBiometricEnabled(checked)
                                    isBiometricEnabled = checked
                                },
                                enabled = isPinSet && isBiometricHardwareAvailable
                            )
                        }
                    )
                }
            }

            Text(
                text = "Thông tin & Pháp lý",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "Thông tin ứng dụng",
                        subtitle = "Nhà phát triển & Phiên bản",
                        onClick = onNavigateToAbout
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = Icons.Default.PrivacyTip,
                        title = "Điều khoản & Quyền riêng tư",
                        subtitle = "Chính sách bảo mật dữ liệu",
                        onClick = onNavigateToTerms
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = Icons.Default.Description,
                        title = "Thỏa thuận EULA",
                        subtitle = "Hợp đồng cấp phép sử dụng",
                        onClick = onNavigateToEula
                    )
                }
            }
        }
    }

    // Passcode Verification Dialog to delete PIN
    if (showDeletePinDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeletePinDialog = false
                deletePinInput = ""
            },
            title = { Text("Xác nhận xóa PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vui lòng nhập mã PIN hiện tại của bạn để vô hiệu hóa bảo mật:")
                    OutlinedTextField(
                        value = deletePinInput,
                        onValueChange = { if (it.length <= 4) deletePinInput = it },
                        label = { Text("Mã PIN (4 chữ số)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dbHelper.verifyPassword(deletePinInput)) {
                            dbHelper.clearPassword()
                            isPinSet = false
                            isBiometricEnabled = false
                            showDeletePinDialog = false
                            deletePinInput = ""
                            Toast.makeText(context, "Đã xóa mã PIN bảo mật!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Mã PIN không chính xác!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Xác nhận xóa", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeletePinDialog = false
                        deletePinInput = ""
                    }
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
