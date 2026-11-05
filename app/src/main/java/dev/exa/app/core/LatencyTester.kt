package dev.exa.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import libv2ray.Libv2ray

object LatencyTester {
    private const val TEST_URL = "https://www.gstatic.com/generate_204"

    suspend fun measure(profile: Profile, settings: AppSettings): Long? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(10000) {
                runCatching {
                    val delay = Libv2ray.measureOutboundDelay(
                        XrayConfig.build(profile, settings),
                        TEST_URL
                    )
                    delay.takeIf { it > 0 }
                }.getOrNull()
            }
        }

    suspend fun fastest(profiles: List<Profile>, settings: AppSettings): Profile? {
        var best: Profile? = null
        var bestDelay = Long.MAX_VALUE
        for (profile in profiles) {
            val delay = measure(profile, settings) ?: continue
            if (delay < bestDelay) {
                best = profile
                bestDelay = delay
            }
        }
        return best
    }
}
