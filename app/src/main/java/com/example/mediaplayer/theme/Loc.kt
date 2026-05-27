package com.example.mediaplayer.theme

import androidx.compose.runtime.mutableStateOf

object Loc {
    var currentLanguage = mutableStateOf("vi") // "vi", "en", "zh"

    fun get(vi: String, en: String, zh: String): String {
        return when (currentLanguage.value) {
            "en" -> en
            "zh" -> zh
            else -> vi
        }
    }

    // Dynamic String mappings
    val videosTab get() = get("TẤT CẢ VIDEO", "ALL VIDEOS", "所有视频")
    val foldersTab get() = get("THƯ MỤC", "FOLDERS", "文件夹")
    val onlineMoviesTab get() = get("PHIM ONLINE", "ONLINE MOVIES", "在线电影")
    val addCustomHls get() = get("Thêm HLS", "Add HLS", "添加 HLS")
    val importPlaylist get() = get("Chọn File .TXT", "Select .TXT File", "选择 .TXT 轴")
    val clearImported get() = get("Xóa danh sách", "Clear Playlist", "清除列表")
    val videosBottomTab get() = get("Video", "Videos", "视频")
    val musicBottomTab get() = get("Nhạc NCT", "Music NCT", "音乐")
    val youtubeBottomTab get() = get("YouTube", "YouTube", "油管")
    val djMixerBottomTab get() = get("DJ Mixer", "DJ Mixer", "DJ 混音")
    val searchPlaceholder get() = get("Tìm kiếm...", "Search...", "搜索...")
    val dateAdded get() = get("Ngày quét", "Scanned date", "扫描时间")
    val size get() = get("Dung lượng", "Size", "大小")
    val format get() = get("Định dạng", "Format", "格式")
    val settings get() = get("Cài đặt", "Settings", "设置")
    val about get() = get("Giới thiệu", "About", "关于")
    val refreshSuccess get() = get("Đã cập nhật danh sách!", "List updated!", "列表已更新!")
    val hlsStream get() = get("Phát luồng trực tuyến HLS", "Play Online HLS Stream", "在线 HLS 播放")
    val hlsUrlPlaceholder get() = get("Đường dẫn (URL HLS/MP4/...)", "URL Path (HLS/MP4/...)", "URL 链接 (HLS/MP4/...)")
    val cancel get() = get("Hủy", "Cancel", "取消")
    val playNow get() = get("Phát ngay", "Play Now", "立即播放")
    val searchMusicPlaceholder get() = get("Tìm kiếm bài hát trên NhacCuaTui...", "Search songs on NhacCuaTui...", "搜索歌名...")
    val download get() = get("Tải xuống", "Download", "下载")
    val downloading get() = get("Đang tải...", "Downloading...", "下载中...")
    val downloadSuccess get() = get("Đã tải xong nhạc!", "Music download finished!", "音乐下载完成!")
    val downloadFailed get() = get("Tải nhạc thất bại!", "Music download failed!", "音乐下载失败!")
    val localMusicHeader get() = get("Nhạc đã tải / trên máy", "Downloaded / Local Music", "本地音乐")
    val nctSearchHeader get() = get("Kết quả tìm kiếm NhacCuaTui", "NhacCuaTui Search Results", "搜索结果")
    val developerInfo get() = get("Thông tin nhà phát triển", "Developer Info", "开发者信息")
    val subtitleSettings get() = get("Cấu hình Phụ đề & Dịch thuật", "Subtitle & Translation Settings", "字幕与翻译设置")
    val showSubtitlesToggle get() = get("Hiển thị phụ đề", "Show Subtitles", "显示字幕")
    val loadSrtFile get() = get("Nạp phụ đề (.srt) từ máy", "Load subtitle (.srt) from device", "从设备加载 (.srt)")
    val chooseSrtBtn get() = get("Chọn file SRT", "Select SRT File", "选择 SRT 轴")
    val demoSubtitlesToggle get() = get("Bật phụ đề mô phỏng (Demo)", "Enable simulated subtitles (Demo)", "启用模拟字幕 (Demo)")
    val autoTranslateToggle get() = get("Dịch tự động (Auto Translate)", "Auto Translate", "自动翻译")
    val selectLanguage get() = get("Dịch sang ngôn ngữ:", "Translate to:", "翻译为:")
    val confirm get() = get("Xác nhận", "Confirm", "确认")
    val removeSrt get() = get("Gỡ bỏ file", "Remove file", "卸载字幕")
    val usingCustomSrt get() = get("Đang sử dụng phụ đề ngoài", "Using external subtitles", "正在使用外部字幕")
    val lockScreen get() = get("Khóa màn hình", "Screen Locked", "锁屏")
    val unlockScreen get() = get("Mở khóa màn hình", "Unlock Screen", "解锁")
}
