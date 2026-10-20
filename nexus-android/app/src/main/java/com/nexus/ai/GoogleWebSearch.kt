package com.nexus.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class GoogleSearchText(
    val ok: Boolean,
    val text: String,
)

object GoogleWebSearch {
    suspend fun search(query: String): GoogleSearchText = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
            val url = URL("https://www.google.com/search?hl=uk&gbv=1&q=$q")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 17; Pixel 7 Pro) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36"
                )
                setRequestProperty("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.7")
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText().take(700_000) }
                .orEmpty()
            connection.disconnect()

            if (code !in 200..299 || body.isBlank()) {
                return@withContext GoogleSearchText(false, "Google не повернув результати.")
            }

            val lower = body.lowercase()
            if ("unusual traffic" in lower || "our systems have detected" in lower || "/sorry/" in lower) {
                return@withContext GoogleSearchText(false, "Google тимчасово заблокував автоматичний пошук.")
            }

            val cleaned = body
                .replace(Regex("(?is)<script.*?</script>"), " ")
                .replace(Regex("(?is)<style.*?</style>"), " ")
                .replace(Regex("(?is)<noscript.*?</noscript>"), " ")
                .replace(Regex("(?is)<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(18_000)

            if (cleaned.length < 120) {
                GoogleSearchText(false, "Не вдалося прочитати результати Google.")
            } else {
                GoogleSearchText(true, cleaned)
            }
        } catch (e: Exception) {
            GoogleSearchText(false, e.message ?: "Помилка веб-пошуку.")
        }
    }
}
