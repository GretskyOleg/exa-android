package dev.exa.app.core

import android.content.Context
import java.io.File

/**
 * Xray needs geoip.dat/geosite.dat next to its asset directory. The files are
 * shipped in the APK and copied to filesDir on first launch / after an update.
 */
object GeoAssets {
    const val VERSION = "202611042203"
    private val FILES = listOf("geoip.dat", "geosite.dat")
    private const val MARKER = ".geodata-version"

    fun ensure(context: Context) {
        val dir = context.filesDir
        val marker = File(dir, MARKER)
        if (marker.isFile && marker.readText().trim() == VERSION &&
            FILES.all { File(dir, it).length() > 1024 }
        ) {
            return
        }
        FILES.forEach { name ->
            val target = File(dir, name)
            runCatching {
                context.assets.open(name).use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                }
            }.onFailure {
                VpnLog.add("geo: failed to install $name: ${it.message}")
            }
        }
        runCatching { marker.writeText(VERSION) }
    }
}
