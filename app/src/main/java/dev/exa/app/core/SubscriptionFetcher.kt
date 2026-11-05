package dev.exa.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

data class SubscriptionResult(
    val body: String,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val title: String? = null
)

object SubscriptionFetcher {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_REDIRECTS = 4
    private const val USER_AGENT = "EXA/0.1.0 (Android)"

    /**
     * HTTPS only: the app does not enable cleartext traffic.
     * Follows up to 4 HTTPS redirects, handles gzip/deflate and enforces a 2 MB limit.
     */
    suspend fun fetch(url: String): SubscriptionResult = withContext(Dispatchers.IO) {
        var current = normalize(url)
        var redirects = 0

        while (true) {
            val conn = open(current)
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Подписка вернула HTTP $code без Location")
                    if (redirects >= MAX_REDIRECTS) {
                        throw IOException("Слишком много редиректов (HTTP $code)")
                    }
                    redirects++
                    current = resolve(current, location)
                    continue
                }
                if (code !in 200..299) {
                    throw IOException("Подписка вернула HTTP $code")
                }
                val info = parseUserInfo(conn.getHeaderField("Subscription-Userinfo"))
                val title = parseTitle(
                    conn.getHeaderField("Profile-Title"),
                    conn.getHeaderField("Content-Disposition")
                )
                val body = conn.inputStream.use { input ->
                    val stream = when (conn.contentEncoding?.lowercase()) {
                        "gzip" -> GZIPInputStream(input)
                        "deflate" -> InflaterInputStream(input)
                        else -> input
                    }
                    stream.use { readLimited(it) }
                }
                val safe = validate(body)
                return@withContext SubscriptionResult(
                    body = safe,
                    upload = info[0],
                    download = info[1],
                    total = info[2],
                    expire = info[3],
                    title = title
                )
            } finally {
                conn.disconnect()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IOException("Не удалось загрузить подписку")
    }

    private fun normalize(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) throw IOException("Пустой URL подписки")
        if (value.length > 2048) throw IOException("Слишком длинный URL подписки")
        if (value.startsWith("http://", ignoreCase = true)) {
            throw IOException("Нужен https:// — HTTP отключён из соображений безопасности")
        }
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw IOException("Некорректный URL подписки")
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw IOException("Нужен https:// URL подписки")
        }
        if (uri.host.isNullOrBlank()) throw IOException("В URL нет домена")
        return value
    }

    private fun resolve(base: String, location: String): String {
        val next = runCatching { URI(base).resolve(location.trim()).toString() }.getOrNull()
            ?: throw IOException("Некорректный редирект подписки")
        val uri = runCatching { URI(next) }.getOrNull()
            ?: throw IOException("Некорректный редирект подписки")
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw IOException("Редирект на небезопасный адрес запрещён")
        }
        if (uri.host.isNullOrBlank()) throw IOException("Редирект без домена")
        if (next.length > 2048) throw IOException("Слишком длинный адрес редиректа")
        return next
    }

    private fun open(url: String): HttpURLConnection {
        val parsed = URL(url)
        val conn = parsed.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "text/plain, */*")
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Cache-Control", "no-cache")
        return conn
    }

    private fun readLimited(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val read = input.read(buf)
            if (read < 0) break
            if (out.size() + read > MAX_BYTES) {
                throw IOException("Подписка больше 2 МБ")
            }
            out.write(buf, 0, read)
        }
        if (out.size() == 0) throw IOException("Пустой ответ подписки")
        return out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
    }

    /** Header format: `upload=123; download=456; total=789; expire=1700000000`. */
    private fun parseUserInfo(header: String?): LongArray {
        val out = longArrayOf(0, 0, 0, 0)
        if (header.isNullOrBlank()) return out
        Regex("([A-Za-z]+)\\s*=\\s*(\\d+)").findAll(header).forEach { match ->
            val value = match.groupValues[2].toLongOrNull()?.coerceAtLeast(0) ?: return@forEach
            when (match.groupValues[1].lowercase()) {
                "upload" -> out[0] = value
                "download" -> out[1] = value
                "total" -> out[2] = value
                "expire" -> out[3] = value
            }
        }
        return out
    }

    /** Providers send `Profile-Title` (plain or base64:) and/or a filename. */
    private fun parseTitle(profileTitle: String?, disposition: String?): String? {
        val raw = profileTitle?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            val decoded = if (raw.startsWith("base64:", ignoreCase = true)) {
                runCatching {
                    String(
                        android.util.Base64.decode(raw.substringAfter(':').trim(), android.util.Base64.DEFAULT),
                        Charsets.UTF_8
                    )
                }.getOrNull()
            } else {
                runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            }
            val clean = decoded?.trim()?.take(64)
            if (!clean.isNullOrBlank()) return clean
        }
        if (!disposition.isNullOrBlank()) {
            val star = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
                .find(disposition)?.groupValues?.getOrNull(1)
            val plain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                .find(disposition)?.groupValues?.getOrNull(1)
            val name = (star ?: plain)?.trim()
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                ?.substringBeforeLast('.')
                ?.trim()
                ?.take(64)
            if (!name.isNullOrBlank()) return name
        }
        return null
    }

    private fun validate(body: String): String {        val trimmed = body.trimStart()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        ) {
            throw IOException("Сервер вернул HTML вместо подписки — проверь URL и токен")
        }
        return body
    }
}
