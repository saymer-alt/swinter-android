package com.swinter.vpn.data.model

import java.util.UUID

enum class ProxyProtocol(val displayName: String, val supportsTurn: Boolean = false) {
    WIREGUARD("WireGuard", supportsTurn = true),
    AMNEZIAWG("AmneziaWG", supportsTurn = true),
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    HYSTERIA2("Hysteria2"),
    SOCKS5("SOCKS5"),
    HTTP("HTTP")
}

enum class ProxyTransport(val displayName: String) {
    TCP("TCP"), WS("WebSocket"), GRPC("gRPC"), H2("HTTP/2"), QUIC("QUIC")
}

enum class TLSMode(val displayName: String) {
    NONE("None"), TLS("TLS"), REALITY("Reality")
}

// Protocol-specific configs
data class WireGuardConfig(
    val privateKey: String = "",
    val publicKey: String = "",
    val presharedKey: String = "",
    val peerAddress: String = "",
    val wgAddress: String = "10.7.0.2/32",
    val dns: String = "1.1.1.1",
    val mtu: Int = 1280,
)

data class AmneziaWGConfig(
    val privateKey: String = "",
    val publicKey: String = "",
    val presharedKey: String = "",
    val peerAddress: String = "",
    val wgAddress: String = "10.8.0.2/24",
    val dns: String = "1.1.1.1",
    val mtu: Int = 1280,
    val jc: Int = 4,
    val jmin: Int = 40,
    val jmax: Int = 70,
    val s1: Int = 0,
    val s2: Int = 0,
    val h1: Int = 1,
    val h2: Int = 2,
    val h3: Int = 3,
    val h4: Int = 4,
)

data class VLESSConfig(
    val uuid: String = "",
    val flow: String = "",
    val transport: ProxyTransport = ProxyTransport.TCP,
    val tls: TLSMode = TLSMode.TLS,
    val sni: String = "",
    val fingerprint: String = "chrome",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val wsPath: String = "",
    val grpcServiceName: String = "",
)

data class VMessConfig(
    val uuid: String = "",
    val alterId: Int = 0,
    val security: String = "auto",
    val transport: ProxyTransport = ProxyTransport.TCP,
    val tls: TLSMode = TLSMode.NONE,
    val sni: String = "",
    val wsPath: String = "",
)

data class TrojanConfig(
    val password: String = "",
    val transport: ProxyTransport = ProxyTransport.TCP,
    val tls: TLSMode = TLSMode.TLS,
    val sni: String = "",
    val wsPath: String = "",
)

data class ShadowsocksConfig(
    val method: String = "aes-256-gcm",
    val password: String = "",
)

data class Hysteria2Config(
    val password: String = "",
    val obfs: String = "",
    val obfsPassword: String = "",
    val uploadMbps: Int = 100,
    val downloadMbps: Int = 100,
    val sni: String = "",
)

data class Socks5Config(
    val username: String = "",
    val password: String = "",
    val tls: Boolean = false,
)

data class HTTPProxyConfig(
    val username: String = "",
    val password: String = "",
    val tls: Boolean = false,
)

// TURN overlay
enum class TurnSource(val displayName: String) {
    VK("VK TURN"), MAX("MAX TURN")
}

data class TurnOverlayConfig(
    val source: TurnSource = TurnSource.MAX,
    val vkLink: String = "",
    val maxToken: String = "",
    val maxTargetUserID: String = "",
    val useUDP: Boolean = false,
    val numConns: Int = 16,
)

// Server
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val address: String = "",
    val proto: ProxyProtocol = ProxyProtocol.WIREGUARD,
    val wireGuardConfig: WireGuardConfig? = null,
    val amneziaWGConfig: AmneziaWGConfig? = null,
    val vlessConfig: VLESSConfig? = null,
    val vmessConfig: VMessConfig? = null,
    val trojanConfig: TrojanConfig? = null,
    val shadowsocksConfig: ShadowsocksConfig? = null,
    val hysteria2Config: Hysteria2Config? = null,
    val socks5Config: Socks5Config? = null,
    val httpConfig: HTTPProxyConfig? = null,
    val turnOverlay: TurnOverlayConfig? = null,
    val latencyMs: Int? = null,
    val enabled: Boolean = true,
    val isFavorite: Boolean = false,
    val subscriptionID: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toGoConfigJSON(): String {
        val map = mutableMapOf<String, Any>()
        when (proto) {
            ProxyProtocol.WIREGUARD -> {
                val wg = wireGuardConfig ?: return "{}"
                map["protocol"] = "wireguard"
                map["address"] = address
                val wgMap = mutableMapOf<String, Any>(
                    "privateKey" to wg.privateKey,
                    "publicKey" to wg.publicKey,
                    "peerAddress" to (if (wg.peerAddress.isNotBlank()) wg.peerAddress else address),
                    "wgAddress" to wg.wgAddress,
                    "dns" to wg.dns,
                    "mtu" to wg.mtu,
                )
                if (wg.presharedKey.isNotBlank()) wgMap["presharedKey"] = wg.presharedKey
                map["wireguard"] = wgMap
            }
            ProxyProtocol.AMNEZIAWG -> {
                val awg = amneziaWGConfig ?: return "{}"
                map["protocol"] = "amneziawg"
                map["address"] = address
                val wgMap = mutableMapOf<String, Any>(
                    "privateKey" to awg.privateKey,
                    "publicKey" to awg.publicKey,
                    "peerAddress" to (if (awg.peerAddress.isNotBlank()) awg.peerAddress else address),
                    "wgAddress" to awg.wgAddress,
                    "dns" to awg.dns,
                    "mtu" to awg.mtu,
                    "jc" to awg.jc,
                    "jmin" to awg.jmin,
                    "jmax" to awg.jmax,
                    "s1" to awg.s1,
                    "s2" to awg.s2,
                    "h1" to awg.h1,
                    "h2" to awg.h2,
                    "h3" to awg.h3,
                    "h4" to awg.h4,
                )
                if (awg.presharedKey.isNotBlank()) wgMap["presharedKey"] = awg.presharedKey
                map["wireguard"] = wgMap
            }
            ProxyProtocol.VLESS -> {
                val v = vlessConfig ?: return "{}"
                map["protocol"] = "vless"
                map["address"] = address
                map["vless"] = mapOf(
                    "uuid" to v.uuid,
                    "flow" to v.flow,
                    "transport" to v.transport.name.lowercase(),
                    "tls" to v.tls.name.lowercase(),
                    "sni" to v.sni,
                    "fingerprint" to v.fingerprint,
                    "realityPublicKey" to v.realityPublicKey,
                    "realityShortId" to v.realityShortId,
                    "wsPath" to v.wsPath,
                    "grpcServiceName" to v.grpcServiceName,
                )
            }
            ProxyProtocol.VMESS -> {
                val v = vmessConfig ?: return "{}"
                map["protocol"] = "vmess"
                map["address"] = address
                map["vmess"] = mapOf(
                    "uuid" to v.uuid,
                    "alterId" to v.alterId,
                    "security" to v.security,
                    "transport" to v.transport.name.lowercase(),
                    "tls" to v.tls.name.lowercase(),
                    "sni" to v.sni,
                    "wsPath" to v.wsPath,
                )
            }
            ProxyProtocol.TROJAN -> {
                val t = trojanConfig ?: return "{}"
                map["protocol"] = "trojan"
                map["address"] = address
                map["trojan"] = mapOf(
                    "password" to t.password,
                    "transport" to t.transport.name.lowercase(),
                    "tls" to t.tls.name.lowercase(),
                    "sni" to t.sni,
                    "wsPath" to t.wsPath,
                )
            }
            ProxyProtocol.SHADOWSOCKS -> {
                val ss = shadowsocksConfig ?: return "{}"
                map["protocol"] = "shadowsocks"
                map["address"] = address
                map["shadowsocks"] = mapOf(
                    "method" to ss.method,
                    "password" to ss.password,
                )
            }
            ProxyProtocol.HYSTERIA2 -> {
                val h2 = hysteria2Config ?: return "{}"
                map["protocol"] = "hysteria2"
                map["address"] = address
                map["hysteria2"] = mapOf(
                    "password" to h2.password,
                    "obfs" to h2.obfs,
                    "obfsPassword" to h2.obfsPassword,
                    "uploadMbps" to h2.uploadMbps,
                    "downloadMbps" to h2.downloadMbps,
                    "sni" to h2.sni,
                )
            }
            ProxyProtocol.SOCKS5 -> {
                val s = socks5Config ?: return "{}"
                map["protocol"] = "socks5"
                map["address"] = address
                map["socks5"] = mapOf(
                    "username" to s.username,
                    "password" to s.password,
                    "tls" to s.tls,
                )
            }
            ProxyProtocol.HTTP -> {
                val h = httpConfig ?: return "{}"
                map["protocol"] = "http"
                map["address"] = address
                map["http"] = mapOf(
                    "username" to h.username,
                    "password" to h.password,
                    "tls" to h.tls,
                )
            }
        }
        // TURN overlay
        turnOverlay?.let { turn ->
            map["useUDP"] = turn.useUDP
            map["numConns"] = turn.numConns
            if (turn.vkLink.isNotBlank()) map["vkLink"] = turn.vkLink
        }
        return com.google.gson.Gson().toJson(map)
    }

    val isWireGuardFamily: Boolean
        get() = proto == ProxyProtocol.WIREGUARD || proto == ProxyProtocol.AMNEZIAWG

    val wgAddress: String
        get() = when (proto) {
            ProxyProtocol.WIREGUARD -> wireGuardConfig?.wgAddress ?: "10.7.0.2/32"
            ProxyProtocol.AMNEZIAWG -> amneziaWGConfig?.wgAddress ?: "10.8.0.2/24"
            else -> "10.7.0.2/32"
        }

    val wgDNS: String
        get() = when (proto) {
            ProxyProtocol.WIREGUARD -> wireGuardConfig?.dns ?: "1.1.1.1"
            ProxyProtocol.AMNEZIAWG -> amneziaWGConfig?.dns ?: "1.1.1.1"
            else -> "1.1.1.1"
        }

    val mtu: Int
        get() = when (proto) {
            ProxyProtocol.WIREGUARD -> wireGuardConfig?.mtu ?: 1280
            ProxyProtocol.AMNEZIAWG -> amneziaWGConfig?.mtu ?: 1280
            else -> 1280
        }
}
