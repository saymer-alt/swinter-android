package com.swinter.vpn.data.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.UUID

// Proxy Groups
enum class GroupType { URL_TEST, FALLBACK, SELECT }

data class ProxyGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: GroupType = GroupType.SELECT,
    val serverIDs: List<String> = emptyList(),
    val testURL: String = "https://www.gstatic.com/generate_204",
    val testInterval: Int = 300,
    val tolerance: Int = 50,
)

// Scenes
enum class SceneConditionType { SSID, NETWORK_TYPE, TIME_RANGE }
enum class SceneActionType { CONNECT, DISCONNECT, SWITCH_SERVER, SWITCH_GROUP }

data class SceneCondition(
    val type: SceneConditionType = SceneConditionType.SSID,
    val value: String = "",
)

data class SceneAction(
    val type: SceneActionType = SceneActionType.CONNECT,
    val targetID: String? = null,
)

data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val conditions: List<SceneCondition> = emptyList(),
    val action: SceneAction = SceneAction(),
    val enabled: Boolean = true,
)

// Routing
enum class RuleType(val label: String) {
    DOMAIN("DOMAIN"),
    DOMAIN_SUFFIX("DOMAIN-SUFFIX"),
    DOMAIN_KEYWORD("DOMAIN-KEYWORD"),
    IP_CIDR("IP-CIDR"),
    GEOIP("GEOIP"),
    FINAL("FINAL"),
}

enum class RuleAction { PROXY, DIRECT, REJECT }

data class RoutingRule(
    val id: String = UUID.randomUUID().toString(),
    val type: RuleType = RuleType.DOMAIN_SUFFIX,
    val value: String = "",
    val action: RuleAction = RuleAction.PROXY,
    val enabled: Boolean = true,
)

// DNS
data class DNSConfig(
    val servers: List<String> = listOf("1.1.1.1", "8.8.8.8"),
    val dohURL: String = "",
    val fallbackServers: List<String> = emptyList(),
)

// Subscription
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val serverIDs: List<String> = emptyList(),
    val lastUpdated: Long? = null,
    val autoUpdate: Boolean = true,
    val updateIntervalHours: Int = 24,
)

// Global Settings
data class GlobalSettings(
    val alwaysOn: Boolean = false,
    val disconnectOnSleep: Boolean = false,
    val ssidExceptions: List<String> = emptyList(),
)

// Root Config
data class AppConfig(
    val servers: List<Server> = emptyList(),
    val activeServerID: String? = null,
    val groups: List<ProxyGroup> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val routingRules: List<RoutingRule> = emptyList(),
    val dns: DNSConfig = DNSConfig(),
    val subscriptions: List<Subscription> = emptyList(),
    val settings: GlobalSettings = GlobalSettings(),
) {
    val activeServer: Server?
        get() = activeServerID?.let { id -> servers.find { it.id == id } } ?: servers.firstOrNull()

    companion object {
        private const val CONFIG_FILE = "swinter_config.json"
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(context: Context): AppConfig {
            val file = File(context.filesDir, CONFIG_FILE)
            if (!file.exists()) return migrateFromLegacy(context)
            return try {
                gson.fromJson(file.readText(), AppConfig::class.java)
            } catch (e: Exception) {
                AppConfig()
            }
        }

        fun save(context: Context, config: AppConfig) {
            val file = File(context.filesDir, CONFIG_FILE)
            file.writeText(gson.toJson(config))
        }

        private fun migrateFromLegacy(context: Context): AppConfig {
            val prefs = context.getSharedPreferences("swinter", Context.MODE_PRIVATE)
            val json = prefs.getString("vpn_config", null) ?: return AppConfig()
            return try {
                val old = gson.fromJson(json, LegacyConfig::class.java)
                val server = Server(
                    name = "Legacy Server",
                    address = old.peerAddr,
                    proto = ProxyProtocol.WIREGUARD,
                    wireGuardConfig = WireGuardConfig(
                        privateKey = old.wgPrivateKey,
                        publicKey = old.wgPublicKey,
                        peerAddress = old.peerAddr,
                        wgAddress = old.wgAddress,
                        dns = old.wgDNS,
                        mtu = old.mtu,
                    ),
                    turnOverlay = if (old.maxToken.isNotEmpty() || old.vkLink.isNotEmpty()) {
                        TurnOverlayConfig(
                            source = if (old.maxToken.isEmpty()) TurnSource.VK else TurnSource.MAX,
                            vkLink = old.vkLink,
                            maxToken = old.maxToken,
                            maxTargetUserID = old.maxTargetUserID,
                            useUDP = old.useUDP,
                            numConns = old.numConns,
                        )
                    } else null,
                )
                AppConfig(servers = listOf(server), activeServerID = server.id)
            } catch (e: Exception) {
                AppConfig()
            }
        }
    }
}

private data class LegacyConfig(
    val vkLink: String = "",
    val maxToken: String = "",
    val maxTargetUserID: String = "",
    val peerAddr: String = "",
    val wgPrivateKey: String = "",
    val wgPublicKey: String = "",
    val wgAddress: String = "10.7.0.2/32",
    val wgDNS: String = "1.1.1.1",
    val useUDP: Boolean = false,
    val numConns: Int = 16,
    val mtu: Int = 1280,
)
