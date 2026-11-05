package dev.exa.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.exa.app.MainActivity
import dev.exa.app.R
import dev.exa.app.core.HevTunnel
import dev.exa.app.core.Profile
import dev.exa.app.core.ProfileStore
import dev.exa.app.core.SettingsStore
import dev.exa.app.core.VpnController
import dev.exa.app.core.VpnLog
import dev.exa.app.core.XrayBridge
import dev.exa.app.core.XrayConfig
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ExaVpnService : VpnService() {
    companion object {
        const val ACTION_START = "dev.exa.app.action.START"
        const val ACTION_STOP = "dev.exa.app.action.STOP"
        const val EXTRA_PROFILE = "profile"
        private const val CHANNEL_ID = "exa_vpn"
        private const val NOTIFICATION_ID = 7
    }

    private var tun: ParcelFileDescriptor? = null
    private var running = false
    private var active: Profile? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val profile = intent.getStringExtra(EXTRA_PROFILE)?.let { raw ->
                    runCatching { Profile.fromJson(JSONObject(raw)) }.getOrNull()
                } ?: ProfileStore.active(this)
                if (profile != null) startVpn(profile) else stopVpn()
            }
            else -> {
                val profile = ProfileStore.active(this)
                if (profile != null) startVpn(profile) else stopVpn()
            }
        }
        return START_STICKY
    }

    private fun startVpn(profile: Profile) {
        stopVpn(stopService = false)
        active = profile
        createChannel()
        startForegroundCompat(notification("Подключение…", profile.name))
        VpnController.setConnecting(profile)

        try {
            val settings = SettingsStore.load(this)
            val builder = Builder()
                .setSession("EXA")
                .setMtu(settings.mtu.coerceIn(1280, 9000))
                .addAddress("10.111.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .setBlocking(false)

            // IPv6 is optional: some carriers reject the route, and v4 must work.
            runCatching {
                builder.addAddress("fd00:111::2", 128)
                builder.addRoute("::", 0)
            }.onFailure { VpnLog.add("vpn: ipv6 unavailable: ${it.message}") }

            settings.dnsServers.take(4).forEach { dns ->
                runCatching { builder.addDnsServer(dns) }
            }

            // Per-app routing: if a whitelist exists, only those apps use the
            // VPN. Otherwise all apps except the explicit exclusions do.
            val allowed = settings.allowedApps - settings.excludedApps - packageName
            if (allowed.isNotEmpty()) {
                allowed.take(300).forEach { runCatching { builder.addAllowedApplication(it) } }
                // EXA itself is not in the allow list, so its xray upstream
                // already bypasses the TUN. Never call addDisallowedApplication
                // here: Builder forbids mixing allow and disallow lists.
            } else {
                settings.excludedApps.filter { it != packageName }.take(300)
                    .forEach { runCatching { builder.addDisallowedApplication(it) } }
                // The app's own xray upstream must bypass the TUN, otherwise it loops.
                runCatching { builder.addDisallowedApplication(packageName) }
                    .onFailure { VpnLog.add("vpn: disallow app failed: ${it.message}") }
            }

            val fd = builder.establish() ?: error("VPN permission denied")
            tun = fd

            val error = XrayBridge.start(XrayConfig.build(profile, settings))
            if (error != null) error("xray: $error")

            // startLoop is asynchronous: do not report "connected" until the
            // local SOCKS inbound actually accepts connections.
            if (!waitForSocks(XrayConfig.SOCKS_PORT)) {
                error("Xray не открыл SOCKS 127.0.0.1:${XrayConfig.SOCKS_PORT}")
            }

            if (!HevTunnel.start(this, fd.fd, XrayConfig.SOCKS_PORT, settings.mtu)) {
                error("hev tunnel failed")
            }

            running = true
            VpnController.setConnected(profile)
            notify(notification("Подключено", profile.name))
            VpnLog.add("vpn: connected ${profile.name} (${profile.host}:${profile.port})")
        } catch (t: Throwable) {
            VpnLog.add("vpn: ${t.message ?: t.javaClass.simpleName}")
            stopVpn()
            VpnController.setError(t.message ?: "Не удалось подключиться")
        }
    }

    private fun waitForSocks(port: Int, timeoutMs: Long = 3500): Boolean {
        val ready = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val worker = Thread({
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val ok = runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 250)
                    }
                }.isSuccess
                if (ok) {
                    ready.set(true)
                    break
                }
                runCatching { Thread.sleep(120) }
            }
            done.countDown()
        }, "exa-socks-probe")
        worker.isDaemon = true
        worker.start()
        runCatching { done.await(timeoutMs + 500, TimeUnit.MILLISECONDS) }
        return ready.get()
    }

    private fun stopVpn(stopService: Boolean = true) {
        HevTunnel.stop()
        XrayBridge.stop()
        runCatching { tun?.close() }
        tun = null
        running = false
        active = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        VpnController.setDisconnected()
        VpnLog.add("vpn: stopped")
        if (stopService) stopSelf()
    }

    override fun onRevoke() {
        VpnLog.add("vpn: revoked by system")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startForegroundCompat(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(title: String, profileName: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ExaVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_exa)
            .setContentTitle("EXA — $title")
            .setContentText(profileName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_exa, "Отключить", stop)
            .build()
    }

    private fun notify(n: Notification) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        }
    }
}
