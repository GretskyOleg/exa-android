package dev.exa.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.exa.app.core.AppSettings
import dev.exa.app.core.LatencyTester
import dev.exa.app.core.Profile
import dev.exa.app.core.ProfileStore
import dev.exa.app.core.SettingsStore
import dev.exa.app.core.VpnController
import dev.exa.app.ui.ExaRoot
import dev.exa.app.ui.ExaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingProfile: Profile? = null
    private var pendingLink by mutableStateOf<String?>(null)
    private lateinit var vpnPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingLink = incomingLink(intent)

        notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val profile = pendingProfile
            pendingProfile = null
            if (result.resultCode == Activity.RESULT_OK && profile != null) {
                VpnController.start(this, profile)
            }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ExaTheme {
                ExaRoot(
                    onConnect = { profile -> connect(profile) },
                    onDisconnect = { VpnController.stop(this) },
                    incomingLink = pendingLink,
                    onIncomingConsumed = { pendingLink = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink = incomingLink(intent)
    }

    private fun incomingLink(intent: Intent?): String? {
        val raw = intent?.dataString?.trim() ?: return null
        if (raw.length > 100_000) return null
        return raw.takeIf { it.startsWith("vless://", ignoreCase = true) }
    }

    private fun connect(profile: Profile) {
        val settings = SettingsStore.load(this)
        val selected = if (settings.autoNetworkProfile) {
            chooseByNetwork(settings) ?: profile
        } else {
            profile
        }

        if (!settings.smartConnect) {
            prepareAndStart(selected)
            return
        }

        Toast.makeText(this, "EXA Pulse: ищем самый быстрый профиль…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val profiles = ProfileStore.load(this@MainActivity)
            val best = LatencyTester.fastest(profiles, settings)
            withContext(Dispatchers.Main) {
                prepareAndStart(best ?: selected)
            }
        }
    }

    private fun chooseByNetwork(settings: AppSettings): Profile? {
        val cm = getSystemService(ConnectivityManager::class.java)
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        val onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val id = if (onWifi) settings.wifiProfileId else settings.mobileProfileId
        if (id == null) return null
        return ProfileStore.load(this).firstOrNull { it.id == id }
    }

    private fun prepareAndStart(profile: Profile) {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            pendingProfile = profile
            vpnPermissionLauncher.launch(prepare)
        } else {
            VpnController.start(this, profile)
        }
    }
}
