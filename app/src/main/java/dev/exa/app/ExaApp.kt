package dev.exa.app

import android.app.Application
import dev.exa.app.core.GeoAssets
import libv2ray.Libv2ray

class ExaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            GeoAssets.ensure(this)
            Libv2ray.touch()
            Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        } catch (_: Throwable) {
            // Native library is absent on unsupported ABIs; the UI still opens
            // and reports the error when connecting.
        }
    }
}
