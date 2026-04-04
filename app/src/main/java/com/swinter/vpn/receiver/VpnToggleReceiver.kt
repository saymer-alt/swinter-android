package com.swinter.vpn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.swinter.vpn.data.VpnConfig
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.data.VpnStateStore
import com.swinter.vpn.service.TurnVpnService

class VpnToggleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE = "com.swinter.vpn.TOGGLE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val state = VpnStateStore.get(context)

        when (state) {
            VpnState.CONNECTED, VpnState.CONNECTING -> {
                val stopIntent = Intent(context, TurnVpnService::class.java).apply {
                    action = TurnVpnService.ACTION_STOP
                }
                context.startService(stopIntent)
            }
            VpnState.DISCONNECTED -> {
                // Check if VPN permission is already granted (prepare() returns null)
                val needsPermission = VpnService.prepare(context) != null
                if (needsPermission) {
                    // Must open Activity for permission dialog
                    val activityIntent = Intent(context, com.swinter.vpn.MainActivity::class.java).apply {
                        action = ACTION_TOGGLE
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(activityIntent)
                } else {
                    // Permission granted — start VPN directly from background
                    val startIntent = Intent(context, TurnVpnService::class.java).apply {
                        action = TurnVpnService.ACTION_QUICK_CONNECT
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startIntent)
                    } else {
                        context.startService(startIntent)
                    }
                }
            }
            VpnState.DISCONNECTING -> { /* ignore */ }
        }
    }
}
