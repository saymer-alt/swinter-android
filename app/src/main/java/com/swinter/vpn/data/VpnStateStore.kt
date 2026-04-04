package com.swinter.vpn.data

import android.content.Context

object VpnStateStore {
    private const val PREFS = "vpn_state"
    private const val KEY_STATE = "state"

    fun get(context: Context): VpnState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_STATE, "DISCONNECTED")) {
            "CONNECTING" -> VpnState.CONNECTING
            "CONNECTED" -> VpnState.CONNECTED
            "DISCONNECTING" -> VpnState.DISCONNECTING
            else -> VpnState.DISCONNECTED
        }
    }

    fun set(context: Context, state: VpnState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, state.name)
            .apply()
    }
}
