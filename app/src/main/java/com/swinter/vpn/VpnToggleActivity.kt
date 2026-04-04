package com.swinter.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.data.VpnStateStore
import com.swinter.vpn.service.TurnVpnService

class VpnToggleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state = VpnStateStore.get(this)
        when (state) {
            VpnState.CONNECTED, VpnState.CONNECTING -> {
                val intent = Intent(this, TurnVpnService::class.java).apply {
                    action = TurnVpnService.ACTION_STOP
                }
                startService(intent)
            }
            VpnState.DISCONNECTED -> {
                val needsPermission = VpnService.prepare(this) != null
                if (needsPermission) {
                    // Must go to MainActivity for permission dialog
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        action = "com.swinter.vpn.TOGGLE"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } else {
                    val intent = Intent(this, TurnVpnService::class.java).apply {
                        action = TurnVpnService.ACTION_QUICK_CONNECT
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
            VpnState.DISCONNECTING -> { /* ignore */ }
        }

        finish()
    }
}
