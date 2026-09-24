package app.startool.android.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(
    val statusCode: Int,
    val body: String?,
)

fun interface HttpFetcher {
    suspend fun fetch(url: String): HttpResponse
}

class DefaultHttpFetcher(
    private val timeoutMs: Int = 10_000,
) : HttpFetcher {

    override suspend fun fetch(url: String): HttpResponse = withContext(Dispatchers.IO) {
        var currentUrl = url
        var redirects = 0
        while (redirects < 5) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json, application/json")
                setRequestProperty("User-Agent", "StarTool-Android")
                instanceFollowRedirects = true
            }

            try {
                val statusCode = connection.responseCode
                if (statusCode in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = URL(URL(currentUrl), location).toString()
                        redirects++
                        continue
                    }
                }
                val stream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                return@withContext HttpResponse(statusCode, body)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects: $redirects")
    }
}
