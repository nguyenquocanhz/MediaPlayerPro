# 🎬 Premium Media Player - Android Application

Một ứng dụng trình phát đa phương tiện (Video & Music) cao cấp dành cho nền tảng Android, được xây dựng bằng **Jetpack Compose**, **Kotlin**, và **Media3 (ExoPlayer)**. Ứng dụng sở hữu phong cách thiết kế hiện đại, cao cấp với gam màu xanh dương thời thượng và tích hợp nhiều tính năng thông minh.

---

## ✨ Tính Năng Nổi Bật

### 1. 📹 Trình Phát Video Chuyên Nghiệp
*   **Hỗ trợ HLS/M3U8:** Phát các luồng phát trực tuyến m3u8 độ trễ thấp cực kỳ ổn định.
*   **Điều khiển bằng cử chỉ trực quan (Gestures):**
    *   *Vuốt dọc bên trái:* Tăng/giảm độ sáng màn hình.
    *   *Vuốt dọc bên phải:* Tăng/giảm âm lượng hệ thống.
    *   *Vuốt ngang:* Tua nhanh/lùi thời gian video.
    *   *Chạm đúp (Double Tap):* Tua nhanh/lùi 10 giây (hiệu ứng bóng nước mượt mà).
    *   *Vuốt dọc vùng trung tâm:* Chuyển nhanh sang video tiếp theo/trước đó.
*   **Tỷ lệ khung hình đa dạng:** Tự động (Fit), Thu phóng (Zoom), Kéo giãn (Stretch), 16:9, Cinema 21:9.
*   **Chế độ Ảnh trong Ảnh (Picture-in-Picture - PiP):** Vừa xem video vừa làm việc khác trên điện thoại.
*   **Quản lý Phụ đề SRT:** Tự động quét phụ đề SRT nội bộ hoặc nạp file phụ đề tùy chọn từ thẻ nhớ.

### 2. ⏳ Video Xem Gần Đây (Lịch Sử Xem)
*   **Lịch sử tiện ích:** Lưu trữ tối đa 30 video đã xem gần đây nhất (áp dụng cho cả tệp cục bộ, luồng HLS, phim trực tuyến và video người lớn).
*   **Lưu trữ an toàn:** Tự động tuần tự hóa và lưu trữ cục bộ qua `SharedPreferences`.
*   **Quản lý dễ dàng:** Giao diện tab **GẦN ĐÂY** nằm trong thẻ Video cho phép xem lại nhanh chóng, kèm nút xóa toàn bộ lịch sử xem chỉ với 1 lượt chạm.

### 3. 🌐 Tích Hợp Stream Người Lớn (XXNAPI & SpankBang)
*   Tích hợp trực tiếp hai Tab trực tuyến **XXNAPI** (XNXX) và **SpankBang** ở thanh điều hướng dưới.
*   Hỗ trợ công cụ tìm kiếm từ khóa, hiển thị lưới video kèm ảnh thu nhỏ (Coil `AsyncImage`) và độ dài thời lượng video cụ thể.
*   Bộ giải mã liên kết luồng tự động trích xuất link HLS master (`.m3u8`) hoặc MP4 độ phân giải cao để phát trực tiếp mượt mà.
*   Chế độ dự phòng (Fallback) thông minh tự động tạo danh sách luồng demo mẫu khi thiết bị ngoại tuyến hoặc bị chặn bởi các nhà mạng ISP/DNS.

### 4. 🤖 Dịch Phụ Đề Thông Minh & Gemini AI
*   **Dịch thời gian thực (Real-time translation):** Hỗ trợ dịch phụ đề tiếng Anh sang Tiếng Việt, Tiếng Tây Ban Nha, Tiếng Pháp, Tiếng Đức, Tiếng Nhật, Tiếng Trung thông qua MyMemory API.
*   **Tích hợp Gemini AI:** Hỗ trợ nhập API Key cá nhân để dịch phụ đề chuẩn văn cảnh và giải thích chi tiết ngữ nghĩa, từ vựng hoặc thành ngữ có trong phụ đề chỉ bằng một cú click.

### 5. 🎵 Trình Phát Nhạc & Tải Nhạc Trực Tuyến
*   **Quét nhạc cục bộ (Local Music Scanner):** Quét toàn bộ thẻ nhớ và bộ nhớ trong để lập danh sách bài hát.
*   **Tìm kiếm & Tải nhạc từ NhạcCuaTui (NCT):** Tìm kiếm các bài hát trực tuyến, nghe thử trực tiếp và tải nhạc chất lượng cao về máy.
*   **Phát nhạc chạy nền (Background Playback):** Tích hợp dịch vụ chạy nền Android (`MusicService`) giúp phát nhạc ngay cả khi khóa màn hình, đi kèm thanh thông báo hiển thị Album Art và cụm phím điều khiển.

### 6. 🔄 Vuốt Để Làm Mới (Pull-To-Refresh)
*   Hỗ trợ cử chỉ vuốt xuống trực quan để cập nhật danh sách Video cục bộ, bài hát ngoại tuyến, và tải lại kết quả tìm kiếm trực tuyến (NCT, XXNAPI, SpankBang) bằng Material 3 `PullToRefreshBox`.

### 7. 🔒 Bảo Mật Vân Tay & Mã PIN
*   Bảo vệ sự riêng tư của ứng dụng bằng mã khóa PIN bảo mật.
*   Hỗ trợ sinh trắc học vân tay nhanh chóng để đăng nhập vào trình phát.

---

## 🛠️ Công Nghệ Sử Dụng

*   **UI Framework:** Jetpack Compose (Kotlin) với thiết kế gradient xanh dương sang trọng.
*   **Media Core:** AndroidX Media3 ExoPlayer, Media3 UI & Media3 HLS Extension.
*   **Networking:** Ktor HTTP Client (OkHttp engine, JSON serialization) để tải dữ liệu và cào HTML trực tuyến.
*   **Image & Video Thumbnail:** Coil Compose & Coil Video Extension.
*   **Security:** AndroidX Biometric API, SQLite (SecurityDatabaseHelper).
*   **Asynchronous:** Kotlin Coroutines & Flow.

---

## 🚀 Hướng Dẫn Cài Đặt & Chạy Dự Án

### Yêu Cầu Hệ Thống
*   Android SDK 24 (Android 7.0) trở lên.
*   Android Studio Ladybug trở lên.
*   Java Development Kit (JDK) 17.

### Các Bước Thực Hiện
1.  **Clone dự án:**
    ```bash
    git clone <repository_url>
    cd MediaPlayer
    ```
2.  **Đồng bộ Gradle:** Mở dự án trong Android Studio và tiến hành Sync Gradle.
3.  **Build & Run:** Chọn thiết bị ảo hoặc thiết bị thật kết nối qua ADB và chạy lệnh:
    ```bash
    ./gradlew installDebug
    ```
4.  **Tải file APK trực tiếp:** Tìm tệp tin debug được build sẵn tại thư mục:
    `app/build/outputs/apk/debug/app-debug.apk`

---

## 📝 Đóng Góp Phát Triển

Mọi ý kiến đóng góp, báo lỗi hoặc yêu cầu tính năng mới vui lòng tạo **Issue** hoặc gửi **Pull Request**. Xin cảm ơn!
