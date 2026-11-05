package dev.exa.app.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.exa.app.MainActivity
import dev.exa.app.core.ProfileStore
import dev.exa.app.core.VpnController
import dev.exa.app.core.VpnState

/**
 * Quick Settings tile: one tap to connect the active profile or disconnect.
 * If the VPN permission was not granted yet, opens the app.
 */
class ExaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render(VpnController.state.value)
    }

    override fun onClick() {
        when (VpnController.state.value) {
            is VpnState.Connected, is VpnState.Connecting -> {
                VpnController.stop(this)
                render(VpnState.Disconnected)
            }
            else -> {
                val profile = ProfileStore.active(this)
                if (profile == null || VpnService.prepare(this) != null) {
                    openApp()
                } else {
                    VpnController.start(this, profile)
                    render(VpnState.Connecting(profile))
                }
            }
        }
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            is VpnState.Connected -> Tile.STATE_ACTIVE
            is VpnState.Connecting -> Tile.STATE_ACTIVE
            is VpnState.Error -> Tile.STATE_INACTIVE
            is VpnState.Disconnected -> Tile.STATE_INACTIVE
        }
        tile.label = "EXA"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                is VpnState.Connected -> state.profile.name
                is VpnState.Connecting -> "Подключение…"
                is VpnState.Error -> "Ошибка"
                is VpnState.Disconnected -> "Выключено"
            }
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
