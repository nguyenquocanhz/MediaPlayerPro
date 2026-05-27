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
fun EulaScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thỏa thuận EULA", fontWeight = FontWeight.Bold) },
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
                text = "THỎA THUẬN CẤP PHÉP NGƯỜI DÙNG CUỐI (EULA)",
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
                        text = "Vui lòng đọc kỹ thỏa thuận cấp phép này trước khi tải xuống, cài đặt hoặc sử dụng ứng dụng Premium Media Player.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "1. Cấp phép sử dụng\n" +
                                "Nhà phát triển cấp cho bạn quyền cá nhân, không độc quyền, không thể chuyển nhượng để tải xuống, cài đặt và sử dụng ứng dụng này trên thiết bị Android cá nhân của bạn, tuân theo các điều khoản trong EULA này.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "2. Hạn chế sử dụng\n" +
                                "Bạn không được:\n" +
                                "- Sửa đổi, đảo ngược kỹ thuật (reverse engineer), biên dịch ngược hoặc tháo rời ứng dụng.\n" +
                                "- Cho thuê, cho mượn hoặc phân phối lại ứng dụng.\n" +
                                "- Sử dụng ứng dụng cho bất kỳ mục đích bất hợp pháp nào hoặc vi phạm bản quyền nội dung đa phương tiện.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "3. Nội dung đa phương tiện\n" +
                                "Premium Media Player là một công cụ phát video. Bạn tự chịu trách nhiệm về tính hợp pháp của tất cả các video và luồng trực tuyến mà bạn phát qua ứng dụng. Chúng tôi không lưu trữ, cung cấp hoặc chịu trách nhiệm về nội dung bạn phát.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "4. Chấm dứt\n" +
                                "Quyền cấp phép này sẽ tự động chấm dứt nếu bạn không tuân thủ bất kỳ điều khoản nào của EULA này. Khi chấm dứt, bạn phải xóa tất cả các bản sao của ứng dụng.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Text(
                        text = "5. Giới hạn trách nhiệm\n" +
                                "Ứng dụng được cung cấp \"như hiện tại\" mà không có bất kỳ hình thức bảo đảm nào. Trong mọi trường hợp, nhà phát triển sẽ không chịu trách nhiệm cho bất kỳ thiệt hại nào phát sinh từ việc sử dụng ứng dụng này.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
