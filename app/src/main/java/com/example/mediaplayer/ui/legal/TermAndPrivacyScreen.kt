package com.example.mediaplayer.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermAndPrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Điều khoản & Quyền riêng tư", fontWeight = FontWeight.Bold) },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ĐIỀU KHOẢN DỊCH VỤ & CHÍNH SÁCH QUYỀN RIÊNG TƯ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Cập nhật lần cuối: 23 tháng 5, 2026",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cam kết bảo mật của chúng tôi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "1. Dữ liệu thu thập\n" +
                                "Chúng tôi tôn trọng quyền riêng tư của bạn tối đa. Ứng dụng Premium Media Player hoạt động hoàn toàn ngoại tuyến cho các tệp tin cục bộ. Chúng tôi KHÔNG thu thập, lưu trữ, truyền tải hoặc chia sẻ bất kỳ thông tin cá nhân, video hoặc lịch sử xem nào của bạn lên bất kỳ máy chủ nào.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "2. Quyền truy cập thiết bị\n" +
                                "Để phát các video của bạn, ứng dụng yêu cầu quyền đọc bộ nhớ lưu trữ chứa các tệp video (READ_MEDIA_VIDEO / READ_EXTERNAL_STORAGE). Quyền này chỉ được sử dụng tại chỗ để quét và tạo danh sách video trong ứng dụng. Chúng tôi không tải dữ liệu của bạn lên mạng.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "3. Xác thực sinh trắc học và SQLite cục bộ\n" +
                                "Nếu bạn thiết lập mật khẩu bảo vệ hoặc bật vân tay, dữ liệu mật khẩu được mã hóa SHA-256 và lưu trữ trực tiếp trên thiết bị của bạn thông qua SQLite nội bộ của ứng dụng. Dữ liệu vân tay được xử lý trực tiếp bởi hệ thống bảo mật Android KeyStore; ứng dụng không truy cập hoặc lưu trữ vân tay thô của bạn.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "4. Liên kết luồng HLS trực tuyến\n" +
                                "Khi bạn thêm và phát luồng trực tuyến HLS (.m3u8), ứng dụng sẽ kết nối trực tiếp với máy chủ lưu trữ của luồng đó. Các kết nối này tuân theo chính sách riêng tư của nhà cung cấp luồng. Chúng tôi không lưu trữ hoặc quản lý lịch sử phát trực tuyến này ngoài thiết bị.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "5. Thay đổi chính sách\n" +
                                "Chúng tôi có thể cập nhật chính sách riêng tư này theo thời gian. Mọi thay đổi sẽ được công bố trên phiên bản mới của ứng dụng.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
