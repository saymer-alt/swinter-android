package com.swinter.vpn.receiver

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.swinter.vpn.R
import com.swinter.vpn.VpnToggleActivity
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.data.VpnStateStore

object VpnShortcuts {

    private const val SHORTCUT_ID = "toggle_vpn"

    fun update(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val state = VpnStateStore.get(context)
        val isOn = state == VpnState.CONNECTED || state == VpnState.CONNECTING

        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel(if (isOn) "VPN ON" else "VPN OFF")
            .setLongLabel(if (isOn) "VPN ON — нажми чтобы выключить" else "VPN OFF — нажми чтобы включить")
            .setIcon(Icon.createWithResource(
                context,
                if (isOn) R.drawable.ic_shortcut_on else R.drawable.ic_shortcut_off
            ))
            .setIntent(Intent("com.swinter.vpn.SHORTCUT_TOGGLE").apply {
                setClass(context, VpnToggleActivity::class.java)
            })
            .build()

        manager.dynamicShortcuts = listOf(shortcut)
    }
}
