package com.swinter.vpn.data

import android.content.Context
import com.google.gson.Gson

data class VpnConfig(
    var turnSource: TurnSource = TurnSource.VK,

    // VK
    var vkLink: String = "",

    // MAX
    var maxToken: String = "",
    var maxTargetUserID: String = "",

    // MAX Call
    var maxCallServerPubkey: String = "",

    // Server
    var peerAddr: String = "",

    // WireGuard
    var wgPrivateKey: String = "",
    var wgPublicKey: String = "",
    var wgAddress: String = "10.7.0.2/32",
    var wgDNS: String = "1.1.1.1",

    // Advanced
    var turnHost: String = "",
    var useUDP: Boolean = false,
    var numConns: Int = 16,
    var mtu: Int = 1280,

    // Per-app VPN
    var perAppEnabled: Boolean = false,
    var perAppBypass: Boolean = true, // true = bypass mode, false = proxy mode
    var perAppList: List<String> = emptyList(),

    // Resolved TURN credentials (filled at connect time)
    var turnUser: String = "",
    var turnPass: String = "",
    var turnAddr: String = ""
) {
    val isConfigured: Boolean
        get() = when (turnSource) {
            TurnSource.VK -> vkLink.isNotBlank() && peerAddr.isNotBlank() &&
                    wgPrivateKey.isNotBlank() && wgPublicKey.isNotBlank()
            TurnSource.MAX -> maxTargetUserID.isNotBlank() &&
                    peerAddr.isNotBlank() && wgPrivateKey.isNotBlank() && wgPublicKey.isNotBlank()
            TurnSource.MAX_CALL -> maxToken.isNotBlank() && maxTargetUserID.isNotBlank() &&
                    maxCallServerPubkey.isNotBlank()
        }

    fun toGoConfigJSON(): String {
        val map = mutableMapOf<String, Any>(
            "peerAddr" to peerAddr,
            "wgPrivateKey" to wgPrivateKey,
            "wgPublicKey" to wgPublicKey,
            "wgAddress" to wgAddress,
            "wgDNS" to wgDNS,
            "useUDP" to useUDP,
            "numConns" to numConns,
            "mtu" to mtu
        )
        if (turnUser.isNotBlank()) map["turnUser"] = turnUser
        if (turnPass.isNotBlank()) map["turnPass"] = turnPass
        if (turnAddr.isNotBlank()) map["turnAddr"] = turnAddr
        if (turnHost.isNotBlank()) map["turnHost"] = turnHost
        if (vkLink.isNotBlank()) map["vkLink"] = vkLink
        return Gson().toJson(map)
    }

    fun toJSON(): String = Gson().toJson(this)

    companion object {
        private const val PREFS_KEY = "vpn_config"

        fun fromJSON(json: String): VpnConfig? = try {
            Gson().fromJson(json, VpnConfig::class.java)
        } catch (e: Exception) {
            null
        }

        fun load(context: Context): VpnConfig {
            val prefs = context.getSharedPreferences("swinter", Context.MODE_PRIVATE)
            val json = prefs.getString(PREFS_KEY, null) ?: return VpnConfig()
            return fromJSON(json) ?: VpnConfig()
        }

        fun save(context: Context, config: VpnConfig) {
            context.getSharedPreferences("swinter", Context.MODE_PRIVATE)
                .edit()
                .putString(PREFS_KEY, config.toJSON())
                .apply()
        }
    }
}
