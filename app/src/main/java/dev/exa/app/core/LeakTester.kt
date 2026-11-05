package dev.exa.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

object LeakTester {
    private const val HOST = "api.ipify.org"
    private const val MAX = 64 * 1024

    suspend fun directIp(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://$HOST?format=json").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = false
            conn.inputStream.use { input ->
                val body = String(readLimited(input, MAX), Charsets.UTF_8)
                JSONObject(body).optString("ip").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    suspend fun tunnelIp(socksPort: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)
                socket.soTimeout = 8000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val greeting = readFully(input, 2)
                require(greeting[0].toInt() == 0x05 && greeting[1].toInt() == 0x00) {
                    "SOCKS auth failed"
                }

                val hostBytes = HOST.toByteArray(Charsets.US_ASCII)
                val request = byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()) +
                    hostBytes +
                    byteArrayOf((80 shr 8).toByte(), (80 and 0xFF).toByte())
                out.write(request)
                out.flush()

                val reply = readFully(input, 4)
                require(reply[0].toInt() == 0x05 && reply[1].toInt() == 0x00) {
                    "SOCKS connect failed"
                }
                val addressLength = when (reply[3].toInt()) {
                    0x01 -> 4
                    0x04 -> 16
                    0x03 -> readFully(input, 1)[0].toInt()
                    else -> 0
                }
                if (addressLength > 0) readFully(input, addressLength)
                readFully(input, 2)

                out.write(
                    "GET / HTTP/1.0\r\nHost: $HOST\r\nConnection: close\r\n\r\n"
                        .toByteArray(Charsets.US_ASCII)
                )
                out.flush()

                val body = String(readLimited(input, MAX), Charsets.UTF_8)
                body.substringAfter("\r\n\r\n").trim().substringBefore("\n").trim()
                    .takeIf { it.isNotBlank() && it.length < 64 }
            }
        }.getOrNull()
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) error("unexpected EOF")
            offset += read
        }
        return buffer
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val read = input.read(buffer)
            if (read < 0) break
            val allowed = minOf(read, limit - out.size())
            out.write(buffer, 0, allowed)
        }
        return out.toByteArray()
    }
}
