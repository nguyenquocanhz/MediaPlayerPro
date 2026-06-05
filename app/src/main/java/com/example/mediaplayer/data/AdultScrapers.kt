package com.example.mediaplayer.data

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import java.net.URLEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

object SafeIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SafeInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        return try {
            if (decoder is JsonDecoder) {
                val element = decoder.decodeJsonElement()
                if (element is JsonPrimitive) {
                    element.intOrNull ?: element.content.toIntOrNull() ?: 0
                } else {
                    0
                }
            } else {
                decoder.decodeInt()
            }
        } catch (e: Exception) {
            0
        }
    }
}

@Serializable
data class AvdbEpisodeLink(
    val slug: String = "",
    val link_embed: String = ""
)

@Serializable
data class AvdbEpisodes(
    val server_name: String = "",
    val server_data: Map<String, AvdbEpisodeLink> = emptyMap()
)

@Serializable
data class AvdbMovieItem(
    val id: Int,
    val name: String = "",
    val slug: String = "",
    val origin_name: String? = "",
    val movie_code: String? = "",
    val category: List<String> = emptyList(),
    val actor: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val poster_url: String? = "",
    val thumb_url: String? = "",
    val country: List<String> = emptyList(),
    val year: String? = "",
    val quality: String? = "",
    val time: String? = "",
    val description: String? = "",
    val type_name: String? = "",
    val episodes: AvdbEpisodes? = null
)

@Serializable
data class AvdbMoviesResponse(
    val code: Int = 0,
    val msg: String = "",
    @Serializable(with = SafeIntSerializer::class)
    val page: Int = 1,
    @Serializable(with = SafeIntSerializer::class)
    val pagecount: Int = 1,
    @Serializable(with = SafeIntSerializer::class)
    val limit: Int = 20,
    val total: Int = 0,
    val list: List<AvdbMovieItem> = emptyList()
)

object AdultScrapers {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun fetchHtml(urlString: String): String {
        val maxRetries = 2
        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                val client = HttpClientFactory.client
                val response: HttpResponse = client.get(urlString) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (response.status == HttpStatusCode.OK) {
                    return response.bodyAsText()
                }
            } catch (e: Exception) {
                lastException = e
                e.printStackTrace()
            }
            if (attempt < maxRetries) {
                delay(1000L)
            }
        }
        lastException?.printStackTrace()
        return ""
    }

    // --- AVDBAPI.COM Endpoints (Việt hóa) ---
    
    suspend fun fetchAvdbMovies(
        page: Int,
        t: String = "",
        year: String = "",
        sortDirection: String = "desc",
        h: String = "",
        query: String = ""
    ): AvdbMoviesResponse {
        val urlBuilder = java.lang.StringBuilder("https://avdbapi.com/vi/api.php/provide/vod?ac=detail")
        urlBuilder.append("&pg=").append(page)
        
        if (query.isNotBlank()) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            urlBuilder.append("&wd=").append(encoded)
        }
        if (t.isNotBlank()) {
            urlBuilder.append("&t=").append(t)
        }
        if (year.isNotBlank()) {
            urlBuilder.append("&year=").append(year)
        }
        if (sortDirection.isNotBlank()) {
            urlBuilder.append("&sort_direction=").append(sortDirection)
        }
        if (h.isNotBlank()) {
            urlBuilder.append("&h=").append(h)
        }
        
        val url = urlBuilder.toString()
        val responseText = fetchHtml(url)
        if (responseText.isBlank()) {
            return AvdbMoviesResponse(msg = "Empty response")
        }
        return try {
            json.decodeFromString<AvdbMoviesResponse>(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            AvdbMoviesResponse(msg = e.message ?: "JSON parse error")
        }
    }
 
    suspend fun getLatestAvdbMovies(page: Int): AvdbMoviesResponse {
        return fetchAvdbMovies(page = page)
    }
 
    suspend fun getAvdbMovieDetail(id: Int): AvdbMoviesResponse {
        val url = "https://avdbapi.com/vi/api.php/provide/vod?ac=detail&ids=$id"
        val responseText = fetchHtml(url)
        if (responseText.isBlank()) {
            return AvdbMoviesResponse(msg = "Empty response")
        }
        return try {
            json.decodeFromString<AvdbMoviesResponse>(responseText)
        } catch (e: Exception) {
            e.printStackTrace()
            AvdbMoviesResponse(msg = e.message ?: "JSON parse error")
        }
    }
 
    suspend fun searchAvdbMovies(query: String, page: Int): AvdbMoviesResponse {
        return fetchAvdbMovies(page = page, query = query)
    }

    suspend fun getAvdbStreamUrl(embedUrl: String): String {
        if (embedUrl.isBlank()) return ""
        if (embedUrl.contains("upload18.org") || embedUrl.contains("upload18.cc")) {
            return embedUrl
        }
        val html = fetchHtml(embedUrl)
        if (html.isBlank()) return ""

        try {
            // Find "m3u8": "/play/token_hash?hash=..."
            val m3u8Regex = Regex("""m3u8\s*:\s*"([^"]+)"""")
            val fileRegex = Regex("""file\s*:\s*"([^"]+\.m3u8[^"]*)"""")
            val sourceRegex = Regex("""source\s*:\s*"([^"]+\.m3u8[^"]*)"""")

            val match = m3u8Regex.find(html)?.groupValues?.get(1)
                ?: fileRegex.find(html)?.groupValues?.get(1)
                ?: sourceRegex.find(html)?.groupValues?.get(1)
                ?: return ""
            
            // Combine with embedUrl host
            val uri = java.net.URI(embedUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: "upload18.org"
            
            val relativePath = match.replace("\\/", "/")
            return "$scheme://$host$relativePath"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}
