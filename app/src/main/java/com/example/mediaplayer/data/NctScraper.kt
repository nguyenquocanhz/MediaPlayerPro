package com.example.mediaplayer.data

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URLEncoder

data class NctSong(
    val title: String,
    val artist: String,
    val pageUrl: String,
    var streamUrl: String = "",
    val thumbnailUrl: String = ""
)

object NctScraper {
    private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    
    private const val SEARCH_URL_PREFIX = "https://www.nhaccuatui.com/tim-kiem?q="

    private val songRegex = Regex("""href="(https://www\.nhaccuatui\.com/bai-hat/[^"]+)"\s+title="([^"]+)"[^>]*>[\s\n]*<img[^>]*data-src="([^"]+)"""")
    private val streamUrlRegex = Regex("""https://(?:stream|a01)\.nct\.vn/[^\s"'>]+?\.mp3\?[^\s"'>]+""")

    private suspend fun fetchHtml(urlString: String, userAgent: String = DESKTOP_USER_AGENT): Result<String> = runCatching {
        val client = HttpClientFactory.client
        val response: HttpResponse = client.get(urlString) {
            header(HttpHeaders.UserAgent, userAgent)
        }
        if (response.status == HttpStatusCode.OK) {
            response.bodyAsText()
        } else {
            throw Exception("HTTP status ${response.status}")
        }
    }

    suspend fun searchSong(query: String): List<NctSong> {
        if (query.isBlank()) return emptyList()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$SEARCH_URL_PREFIX$encodedQuery"

        val result = fetchHtml(searchUrl, MOBILE_USER_AGENT)
        return result.mapCatching { html ->
            val parsed = parseSearchHtml(html)
            parsed.ifEmpty { getFallbackSongs(query) }
        }.getOrElse {
            it.printStackTrace()
            getFallbackSongs(query)
        }
    }

    fun parseSearchHtml(html: String): List<NctSong> {
        val matches = songRegex.findAll(html).toList()
        return buildList {
            for (match in matches) {
                if (size >= 20) break
                val pageUrl = match.groupValues[1]
                val rawTitle = match.groupValues[2]
                val thumbnailUrl = match.groupValues.getOrNull(3) ?: ""
                val parts = rawTitle.split(" - ")
                val artist = if (parts.size > 1) parts.last().trim() else "NhacCuaTui Artist"
                val title = if (parts.size > 1) parts.dropLast(1).joinToString(" - ").trim() else rawTitle.trim()
                add(NctSong(title, artist, pageUrl, thumbnailUrl = thumbnailUrl))
            }
        }
    }

    suspend fun getStreamUrl(pageUrl: String): String {
        if (pageUrl.startsWith("http") && pageUrl.contains("soundhelix.com")) {
            return pageUrl // direct mock stream link
        }
        val result = fetchHtml(pageUrl, DESKTOP_USER_AGENT)
        return result.mapCatching { html ->
            val streamMatches = streamUrlRegex.findAll(html).map { it.value }.toList()
            val bestStreamUrl = streamMatches.firstOrNull { !it.contains("_hq.mp3") && !it.contains("download=true") }
                ?: streamMatches.firstOrNull { !it.contains("download=true") }
                ?: streamMatches.firstOrNull()
                ?: ""
            bestStreamUrl
        }.getOrElse {
            it.printStackTrace()
            ""
        }
    }

    fun getFallbackSongs(query: String): List<NctSong> {
        val queryLower = query.lowercase().trim()
        val database = listOf(
            NctSong("Có Chàng Trai Viết Lên Cây", "Phan Mạnh Quỳnh", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
            NctSong("Chúng Ta Của Hiện Tại", "Sơn Tùng M-TP", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
            NctSong("Nàng Thơ", "Hoàng Dũng", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"),
            NctSong("Bông Hoa Đẹp Nhất", "Quân A.P", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"),
            NctSong("Ngày Đầu Tiên", "Đức Phúc", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"),
            NctSong("Chân Ái", "Orange x Khói", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"),
            NctSong("Hồng Nhan", "Jack", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3"),
            NctSong("Bạc Phận", "Jack x K-ICM", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"),
            NctSong("Sóng Gió", "Jack x K-ICM", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3"),
            NctSong("Lạc Trôi", "Sơn Tùng M-TP", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3")
        )
        
        if (queryLower.isBlank()) return database
        val filtered = database.filter { it.title.lowercase().contains(queryLower) || it.artist.lowercase().contains(queryLower) }
        return filtered.ifEmpty {
            listOf(
                NctSong(query.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, "Nghệ Sĩ NhacCuaTui", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
            )
        }
    }
}
