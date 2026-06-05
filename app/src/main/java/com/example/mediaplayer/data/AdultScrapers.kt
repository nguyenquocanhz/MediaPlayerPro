package com.example.mediaplayer.data

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AdultVideoItem(
    val title: String,
    val pageUrl: String,
    val thumbnailUrl: String,
    val duration: String,
    val source: String // "xnxx" or "spankbang"
)

@Serializable
data class XxvnCategory(
    val id: String = "",
    val name: String = "",
    val slug: String = ""
)

@Serializable
data class XxvnCountry(
    val id: String = "",
    val name: String = "",
    val slug: String = ""
)

@Serializable
data class XxvnEpisodeData(
    val name: String = "",
    val slug: String = "",
    val link: String = ""
)

@Serializable
data class XxvnEpisodeServer(
    val server_name: String = "",
    val server_data: List<XxvnEpisodeData> = emptyList()
)

@Serializable
data class XxvnMovieItem(
    val id: String,
    val name: String,
    val slug: String,
    val content: String? = "",
    val type: String? = "",
    val status: String? = "",
    val thumb_url: String? = "",
    val time: String? = "",
    val quality: String? = "",
    val lang: String? = null,
    val actors: List<String> = emptyList(),
    val categories: List<XxvnCategory> = emptyList(),
    val country: XxvnCountry? = null,
    val episodes: List<XxvnEpisodeServer> = emptyList()
)

@Serializable
data class XxvnPageInfo(
    val current_page: Int = 1,
    val from: Int? = null,
    val to: Int? = null,
    val total: Int = 0,
    val per_page: Int = 50,
    val last_page: Int = 1
)

@Serializable
data class XxvnMoviesResponse(
    val status: Boolean = false,
    val msg: String = "",
    val movies: List<XxvnMovieItem> = emptyList(),
    val page: XxvnPageInfo? = null
)

@Serializable
data class XxvnMovieDetailResponse(
    val status: Boolean = false,
    val msg: String = "",
    val movie: XxvnMovieItem? = null
)

object AdultScrapers {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val AGE_COOKIE = "age_pass=1; pg_interstitial_v5=1; player_quality=1080"
    
    private val json = Json { ignoreUnknownKeys = true }

    private val xnxxLinkRegex = Regex("""href="(/video-[^"]+)"""")
    private val xnxxImgRegex = Regex("""(?:data-src|src)="([^"]+)"""")
    private val xnxxTitleRegex = Regex("""title="([^"]+)"""")
    private val xnxxDurationRegex = Regex("""class="duration">([^<]+)<""")

    private val xnxxHlsRegex = Regex("""html5player\.setVideoHLS\(\s*'([^']+)'\s*\)""")
    private val xnxxHighRegex = Regex("""html5player\.setVideoUrlHigh\(\s*'([^']+)'\s*\)""")
    private val xnxxLowRegex = Regex("""html5player\.setVideoUrlLow\(\s*'([^']+)'\s*\)""")

    private val spankbangLinkRegex = Regex("""href="(/video/[^"]+)"""")
    private val spankbangImgRegex = Regex("""(?:data-src|src)="([^"]+)"""")
    private val spankbangTitleRegex = Regex("""alt="([^"]+)"""")
    private val spankbangDurationRegex = Regex("""class="l">([^<]+)<""")

    private val spankbangStreamDataRegex = Regex("""var\s+stream_data\s*=\s*(\{.*?});""", kotlin.text.RegexOption.DOT_MATCHES_ALL)
    private val urlRegex = Regex("""https?://[^\s"']+\.(?:m3u8|mp4)[^\s"']*""")

    private suspend fun fetchHtml(urlString: String): String {
        return try {
            val client = HttpClientFactory.client
            val response: HttpResponse = client.get(urlString) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Cookie, AGE_COOKIE)
            }
            if (response.status == HttpStatusCode.OK) {
                response.bodyAsText()
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    // --- XXVNAPI.COM Endpoints ---

    suspend fun getLatestXxvnMovies(page: Int): XxvnMoviesResponse {
        val url = "https://xxvnapi.com/api/phim-moi-cap-nhat?page=$page"
        val responseText = fetchHtml(url)
        if (responseText.isBlank()) {
            return XxvnMoviesResponse(status = false, msg = "Empty response")
        }
        return try {
            json.decodeFromString<XxvnMoviesResponse>(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            XxvnMoviesResponse(status = false, msg = e.message ?: "JSON parse error")
        }
    }

    suspend fun getXxvnMoviesByCategory(categorySlug: String, page: Int): XxvnMoviesResponse {
        val url = "https://xxvnapi.com/api/chuyen-muc/$categorySlug?page=$page"
        val responseText = fetchHtml(url)
        if (responseText.isBlank()) {
            return XxvnMoviesResponse(status = false, msg = "Empty response")
        }
        return try {
            json.decodeFromString<XxvnMoviesResponse>(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            XxvnMoviesResponse(status = false, msg = e.message ?: "JSON parse error")
        }
    }

    suspend fun getXxvnMovieDetail(slug: String): XxvnMovieDetailResponse {
        val url = "https://xxvnapi.com/api/phim/$slug"
        val responseText = fetchHtml(url)
        if (responseText.isBlank()) {
            return XxvnMovieDetailResponse(status = false, msg = "Empty response")
        }
        return try {
            json.decodeFromString<XxvnMovieDetailResponse>(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            XxvnMovieDetailResponse(status = false, msg = e.message ?: "JSON parse error")
        }
    }

    suspend fun searchXxvnMovies(query: String, page: Int): List<XxvnMovieItem> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://xxvnapi.com/tim-kiem?k=$encoded&page=$page"
        val html = fetchHtml(url)
        if (html.isBlank()) return emptyList()

        val results = mutableListOf<XxvnMovieItem>()
        try {
            val startSearchIndex = html.indexOf("videoContent")
            if (startSearchIndex != -1) {
                val endIndex = html.indexOf("</ul>", startSearchIndex)
                if (endIndex != -1) {
                    val content = html.substring(startSearchIndex, endIndex)
                    val items = content.split("<li>")
                    
                    val hrefRegex = Regex("""href="[^"]*/phim/([^"]+)"""")
                    val nameRegex = Regex("""<a[^>]*>([^<]+)</a>""")
                    val statusRegex = Regex("""class="status"[^>]*>([^<]+)</span>""")
                    val categoryRegex = Regex("""class="category type"[^>]*>([^<]+)</span>""")
                    val regionRegex = Regex("""class="region"[^>]*>([^<]+)</span>""")
                    val timeRegex = Regex("""<font[^>]*>([^<]+)</font>""")

                    for (item in items) {
                        if (item.isBlank() || !item.contains("/phim/")) continue
                        val slug = hrefRegex.find(item)?.groupValues?.get(1)?.trim() ?: continue
                        val name = nameRegex.find(item)?.groupValues?.get(1)?.trim() ?: "Phim"
                        val status = statusRegex.find(item)?.groupValues?.get(1)?.trim() ?: ""
                        val categoryName = categoryRegex.find(item)?.groupValues?.get(1)?.trim() ?: ""
                        val countryName = regionRegex.find(item)?.groupValues?.get(1)?.trim() ?: ""
                        val time = timeRegex.find(item)?.groupValues?.get(1)?.trim() ?: ""

                        results.add(
                            XxvnMovieItem(
                                id = slug,
                                name = name,
                                slug = slug,
                                status = status,
                                quality = status,
                                time = time,
                                categories = listOf(XxvnCategory(name = categoryName, slug = "")),
                                country = XxvnCountry(name = countryName, slug = "")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    fun parseDfPlayerUrl(dfPlayerUrl: String): String {
        if (dfPlayerUrl.isBlank()) return ""
        if (dfPlayerUrl.endsWith(".m3u8") || dfPlayerUrl.endsWith(".mp4")) {
            return dfPlayerUrl
        }
        try {
            val uri = android.net.Uri.parse(dfPlayerUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: "v.dfplayer.net"
            
            val id = uri.getQueryParameter("id")
            if (id != null) {
                return "$scheme://$host/v1/s/$id.m3u8"
            }
            val did = uri.getQueryParameter("did")
            if (did != null) {
                return "$scheme://$host/v2/s/$did.m3u8"
            }
            val urlParam = uri.getQueryParameter("url")
            if (urlParam != null) {
                return urlParam
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return dfPlayerUrl
    }

    // --- Legacy scraping methods ---

    suspend fun searchXnxx(query: String): List<AdultVideoItem> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.xnxx.com/?k=$encoded"
        val html = fetchHtml(url)
        if (html.isBlank()) return getMockVideos("xnxx", query)

        // Split by thumb-block to get separate items
        val blocks = html.split("class=\"thumb-block")
        if (blocks.size <= 1) return getMockVideos("xnxx", query)

        return buildList {
            // Skip the first split result as it is the HTML before any thumb-block
            for (block in blocks.drop(1)) {
                if (size >= 30) break
                val link = xnxxLinkRegex.find(block)?.groupValues?.get(1) ?: continue
                val img = xnxxImgRegex.find(block)?.groupValues?.get(1) ?: ""
                val title = xnxxTitleRegex.find(block)?.groupValues?.get(1) ?: "XNXX Video"
                val duration = xnxxDurationRegex.find(block)?.groupValues?.get(1) ?: "10 min"

                add(
                    AdultVideoItem(
                        title = title,
                        pageUrl = "https://www.xnxx.com$link",
                        thumbnailUrl = img,
                        duration = duration,
                        source = "xnxx"
                    )
                )
            }
        }.ifEmpty { getMockVideos("xnxx", query) }
    }

    suspend fun getXnxxStreamUrl(pageUrl: String): String {
        val html = fetchHtml(pageUrl)
        if (html.isBlank()) return ""

        val hlsMatch = xnxxHlsRegex.find(html)?.groupValues?.get(1)
        if (!hlsMatch.isNullOrBlank()) return hlsMatch

        val highMatch = xnxxHighRegex.find(html)?.groupValues?.get(1)
        if (!highMatch.isNullOrBlank()) return highMatch

        val lowMatch = xnxxLowRegex.find(html)?.groupValues?.get(1)
        if (!lowMatch.isNullOrBlank()) return lowMatch

        return ""
    }

    suspend fun searchSpankbang(query: String): List<AdultVideoItem> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://spankbang.com/s/$encoded/"
        val html = fetchHtml(url)
        if (html.isBlank()) return getMockVideos("spankbang", query)

        val blocks = html.split("class=\"js-video-item")
        if (blocks.size <= 1) return getMockVideos("spankbang", query)

        return buildList {
            for (block in blocks.drop(1)) {
                if (size >= 30) break
                val link = spankbangLinkRegex.find(block)?.groupValues?.get(1) ?: continue
                val img = spankbangImgRegex.find(block)?.groupValues?.get(1) ?: ""
                val title = spankbangTitleRegex.find(block)?.groupValues?.get(1) ?: "SpankBang Video"
                val duration = spankbangDurationRegex.find(block)?.groupValues?.get(1) ?: "12 min"

                add(
                    AdultVideoItem(
                        title = title,
                        pageUrl = "https://spankbang.com$link",
                        thumbnailUrl = img,
                        duration = duration,
                        source = "spankbang"
                    )
                )
            }
        }.ifEmpty { getMockVideos("spankbang", query) }
    }

    suspend fun getSpankbangStreamUrl(pageUrl: String): String {
        val html = fetchHtml(pageUrl)
        if (html.isBlank()) return ""

        val streamDataMatch = spankbangStreamDataRegex.find(html)?.groupValues?.get(1) ?: return ""
        val urls = urlRegex.findAll(streamDataMatch).map { it.value.replace("\\/", "/") }.toList()

        // Prefer HLS master.m3u8 first
        val hlsUrl = urls.firstOrNull { it.contains("master.m3u8") }
        if (hlsUrl != null) return hlsUrl

        // Fallback to highest quality mp4
        val mp4Url = urls.firstOrNull { it.contains("1080p.mp4") || it.contains("720p.mp4") }
            ?: urls.firstOrNull { it.contains("480p.mp4") || it.contains(".mp4") }
            
        return mp4Url ?: ""
    }

    private fun getMockVideos(source: String, query: String): List<AdultVideoItem> {
        val capitalizedQuery = query.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val suffix = if (source == "xnxx") "on XNXX" else "on SpankBang"
        
        // Return 10 standard demo videos to prevent empty screens when offline or blocked by ISP/DNS
        return List(10) { i ->
            AdultVideoItem(
                title = "[$source] Premium $capitalizedQuery Scene ${i + 1} $suffix",
                // Standard public test streams so they can actually play and test the player
                pageUrl = "https://test-streams.mux.dev/x36xhg/movie.m3u8", 
                thumbnailUrl = "https://picsum.photos/300/200?random=$i",
                duration = "${10 + i * 3}:15",
                source = source
            )
        }
    }
}

