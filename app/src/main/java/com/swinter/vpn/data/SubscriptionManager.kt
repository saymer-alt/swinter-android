package com.swinter.vpn.data

import android.util.Base64
import android.util.Log
import com.swinter.vpn.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Fetches and parses server subscription URLs.
 * Supports:
 * - Base64-encoded URI lists (standard proxy subscription format)
 * - Swinter/Shadowrocket .conf format
 * - Plain URI lists
 */
object SubscriptionManager {

    private const val TAG = "SubscriptionManager"

    suspend fun fetchSubscription(sub: Subscription): List<Server> = withContext(Dispatchers.IO) {
        try {
            val content = URL(sub.url).readText()
            val servers = parseContent(content, sub.id)
            Log.i(TAG, "Fetched ${servers.size} servers from ${sub.url}")
            servers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch ${sub.url}: ${e.message}")
            emptyList()
        }
    }

    /** Parse a single protocol URI into a Server. Returns null if unrecognized. */
    fun parseServerURI(uri: String): Server? = parseURI(uri.trim(), subscriptionId = null)

    private fun parseContent(content: String, subscriptionId: String): List<Server> {
        val trimmed = content.trim()

        // Check if it's a .conf format
        if (trimmed.contains("[General]") || trimmed.contains("[Rule]") || trimmed.contains("[Proxy Group]")) {
            // Parse as .conf — doesn't contain servers directly, but has rules/DNS
            return emptyList()
        }

        // Try Base64 decode
        val decoded = try {
            String(Base64.decode(trimmed, Base64.DEFAULT))
        } catch (_: Exception) {
            trimmed
        }

        // Parse as URI list (one per line)
        return decoded.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { parseURI(it, subscriptionId) }
    }

    private fun parseURI(uri: String, subscriptionId: String?): Server? {
        return when {
            uri.startsWith("vless://") -> parseVLESS(uri, subscriptionId)
            uri.startsWith("vmess://") -> parseVMess(uri, subscriptionId)
            uri.startsWith("trojan://") -> parseTrojan(uri, subscriptionId)
            uri.startsWith("ss://") -> parseShadowsocks(uri, subscriptionId)
            uri.startsWith("hysteria2://") || uri.startsWith("hy2://") -> parseHysteria2(uri, subscriptionId)
            uri.startsWith("wg://") || uri.startsWith("wireguard://") -> parseWireGuard(uri, subscriptionId)
            uri.startsWith("awg://") || uri.startsWith("amneziawg://") -> parseAmneziaWG(uri, subscriptionId)
            else -> null
        }
    }

    private fun parseWireGuard(uri: String, subId: String?): Server? {
        // wg://privateKey@host:port?publicKey=...&address=...&dns=...#name
        try {
            val withoutScheme = uri.removePrefix("wg://").removePrefix("wireguard://")
            val (main, fragment) = splitFragment(withoutScheme)
            val atIndex = main.indexOf('@')
            val privateKey = main.substring(0, atIndex)
            val rest = main.substring(atIndex + 1)
            val (hostPort, params) = splitQuery(rest)
            val (host, port) = splitHostPort(hostPort)
            val queryParams = parseQueryParams(params)

            return Server(
                name = fragment.ifBlank { "$host:$port" },
                address = "$host:$port",
                proto = ProxyProtocol.WIREGUARD,
                wireGuardConfig = WireGuardConfig(
                    privateKey = privateKey,
                    publicKey = queryParams["publicKey"] ?: queryParams["publickey"] ?: "",
                    presharedKey = queryParams["presharedKey"] ?: "",
                    peerAddress = "$host:$port",
                    wgAddress = queryParams["address"] ?: "10.7.0.2/32",
                    dns = queryParams["dns"] ?: "1.1.1.1",
                    mtu = queryParams["mtu"]?.toIntOrNull() ?: 1280,
                ),
                subscriptionID = subId,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse WG URI: $e")
            return null
        }
    }

    private fun parseAmneziaWG(uri: String, subId: String?): Server? {
        // awg://privateKey@host:port?publicKey=...&address=...&jc=4&jmin=40&jmax=70#name
        try {
            val withoutScheme = uri.removePrefix("awg://").removePrefix("amneziawg://")
            val (main, fragment) = splitFragment(withoutScheme)
            val atIndex = main.indexOf('@')
            val privateKey = main.substring(0, atIndex)
            val rest = main.substring(atIndex + 1)
            val (hostPort, params) = splitQuery(rest)
            val (host, port) = splitHostPort(hostPort)
            val queryParams = parseQueryParams(params)

            return Server(
                name = fragment.ifBlank { "$host:$port" },
                address = "$host:$port",
                proto = ProxyProtocol.AMNEZIAWG,
                amneziaWGConfig = AmneziaWGConfig(
                    privateKey = privateKey,
                    publicKey = queryParams["publicKey"] ?: queryParams["publickey"] ?: "",
                    presharedKey = queryParams["presharedKey"] ?: "",
                    peerAddress = "$host:$port",
                    wgAddress = queryParams["address"] ?: "10.8.0.2/24",
                    dns = queryParams["dns"] ?: "1.1.1.1",
                    mtu = queryParams["mtu"]?.toIntOrNull() ?: 1280,
                    jc = queryParams["jc"]?.toIntOrNull() ?: 4,
                    jmin = queryParams["jmin"]?.toIntOrNull() ?: 40,
                    jmax = queryParams["jmax"]?.toIntOrNull() ?: 70,
                    s1 = queryParams["s1"]?.toIntOrNull() ?: 0,
                    s2 = queryParams["s2"]?.toIntOrNull() ?: 0,
                    h1 = queryParams["h1"]?.toIntOrNull() ?: 1,
                    h2 = queryParams["h2"]?.toIntOrNull() ?: 2,
                    h3 = queryParams["h3"]?.toIntOrNull() ?: 3,
                    h4 = queryParams["h4"]?.toIntOrNull() ?: 4,
                ),
                subscriptionID = subId,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse AWG URI: $e")
            return null
        }
    }

    private fun parseVLESS(uri: String, subId: String?): Server? {
        // vless://UUID@host:port?params#name
        try {
            val withoutScheme = uri.removePrefix("vless://")
            val (main, fragment) = splitFragment(withoutScheme)
            val name = fragment

            val atIndex = main.indexOf('@')
            val uuid = main.substring(0, atIndex)
            val rest = main.substring(atIndex + 1)

            val (hostPort, params) = splitQuery(rest)
            val (host, port) = splitHostPort(hostPort)

            val queryParams = parseQueryParams(params)

            return Server(
                name = name,
                address = "$host:$port",
                proto = ProxyProtocol.VLESS,
                vlessConfig = VLESSConfig(
                    uuid = uuid,
                    flow = queryParams["flow"] ?: "",
                    transport = parseTransport(queryParams["type"]),
                    tls = parseTLSMode(queryParams["security"]),
                    sni = queryParams["sni"] ?: "",
                    fingerprint = queryParams["fp"] ?: "chrome",
                    realityPublicKey = queryParams["pbk"] ?: "",
                    realityShortId = queryParams["sid"] ?: "",
                    wsPath = queryParams["path"] ?: "",
                    grpcServiceName = queryParams["serviceName"] ?: "",
                ),
                subscriptionID = subId,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse VLESS URI: $e")
            return null
        }
    }

    private fun parseVMess(uri: String, subId: String?): Server? {
        // vmess://base64json
        try {
            val encoded = uri.removePrefix("vmess://")
            val json = String(Base64.decode(encoded, Base64.DEFAULT))
            val map = com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, Any>

            val host = map["add"]?.toString() ?: return null
            val port = (map["port"]?.toString()?.toDoubleOrNull()?.toInt()) ?: 443
            val name = map["ps"]?.toString() ?: "$host:$port"

            return Server(
                name = name,
                address = "$host:$port",
                proto = ProxyProtocol.VMESS,
                vmessConfig = VMessConfig(
                    uuid = map["id"]?.toString() ?: "",
                    alterId = map["aid"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0,
                    security = map["scy"]?.toString() ?: "auto",
                    transport = parseTransport(map["net"]?.toString()),
                    tls = if (map["tls"]?.toString() == "tls") TLSMode.TLS else TLSMode.NONE,
                    sni = map["sni"]?.toString() ?: "",
                    wsPath = map["path"]?.toString() ?: "",
                ),
                subscriptionID = subId,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse VMess URI: $e")
            return null
        }
    }

    private fun parseTrojan(uri: String, subId: String?): Server? {
        // trojan://password@host:port?params#name
        try {
            val withoutScheme = uri.removePrefix("trojan://")
            val (main, fragment) = splitFragment(withoutScheme)

            val atIndex = main.indexOf('@')
            val password = main.substring(0, atIndex)
            val rest = main.substring(atIndex + 1)

            val (hostPort, params) = splitQuery(rest)
            val (host, port) = splitHostPort(hostPort)
            val queryParams = parseQueryParams(params)

            return Server(
                name = fragment,
                address = "$host:$port",
                proto = ProxyProtocol.TROJAN,
                trojanConfig = TrojanConfig(
                    password = password,
                    transport = parseTransport(queryParams["type"]),
                    tls = TLSMode.TLS,
                    sni = queryParams["sni"] ?: "",
                    wsPath = queryParams["path"] ?: "",
                ),
                subscriptionID = subId,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Trojan URI: $e")
            return null
        }
    }

    private fun parseShadowsocks(uri: String, subId: String?): Server? {
        // ss://base64(method:password)@host:port#name
        // or ss://base64(method:password@host:port)#name
        try {
            val withoutScheme = uri.removePrefix("ss://")
            val (main, fragment) = splitFragment(withoutScheme)

            val atIndex = main.indexOf('@')
            if (atIndex >= 0) {
                val encoded = main.substring(0, atIndex)
                val decoded = try { String(Base64.decode(encoded, Base64.DEFAULT)) } catch (_: Exception) { encoded }
                val colonIdx = decoded.indexOf(':')
                val method = decoded.substring(0, colonIdx)
                val password = decoded.substring(colonIdx + 1)

                val hostPort = main.substring(atIndex + 1)
                val (host, port) = splitHostPort(hostPort)

                return Server(
                    name = fragment,
                    address = "$host:$port",
                    proto = ProxyProtocol.SHADOWSOCKS,
                    shadowsocksConfig = ShadowsocksConfig(method = method, password = password),
                    subscriptionID = subId,
                )
            } else {
                // Fully encoded
                val decoded = String(Base64.decode(main, Base64.DEFAULT))
                val colonIdx = decoded.indexOf(':')
                val method = decoded.substring(0, colonIdx)
                val rest = decoded.substring(colonIdx + 1)
                val atIdx = rest.indexOf('@')
                val password = rest.substring(0, atIdx)
                val (host, port) = splitHostPort(rest.substring(atIdx + 1))

                return Server(
                    name = fragment,
                    address = "$host:$port",
                    proto = ProxyProtocol.SHADOWSOCKS,
                    shadowsocksConfig = ShadowsocksConfig(method = method, password = password),
                    subscriptionID = subId,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SS URI: $e")
            return null
        }
    }

    private fun parseHysteria2(uri: String, subId: String?): Server? {
        // hysteria2://password@host:port?params#name
        try {
            val withoutScheme = uri.removePrefix("hysteria2://").removePrefix("hy2://")
            val (main, fragment) = splitFragment(withoutScheme)

            val atIndex = main.indexOf('@')
            val password = main.substring(0, atIndex)
            val rest = main.substring(atIndex + 1)

            val (hostPort, params) = splitQuery(rest)
            val (host, port) = splitHostPort(hostPort)
            val queryParams = parseQueryParams(params)

            return Server(
                name = fragment,
                address = "$host:$port",
                proto = ProxyProtocol.HYSTERIA2,
                hysteria2Config = Hysteria2Config(
                    password = password,
                    sni = queryParams["sni"] ?: "",
                    obfs = queryParams["obfs"] ?: "",
                    obfsPassword = queryParams["obfs-password"] ?: "",
                ),
                subscriptionID = subId,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Hysteria2 URI: $e")
            return null
        }
    }

    // Helpers

    private fun splitFragment(s: String): Pair<String, String> {
        val idx = s.lastIndexOf('#')
        return if (idx >= 0) {
            s.substring(0, idx) to java.net.URLDecoder.decode(s.substring(idx + 1), "UTF-8")
        } else {
            s to ""
        }
    }

    private fun splitQuery(s: String): Pair<String, String> {
        val idx = s.indexOf('?')
        return if (idx >= 0) s.substring(0, idx) to s.substring(idx + 1) else s to ""
    }

    private fun splitHostPort(s: String): Pair<String, Int> {
        val lastColon = s.lastIndexOf(':')
        return if (lastColon >= 0) {
            s.substring(0, lastColon) to (s.substring(lastColon + 1).toIntOrNull() ?: 443)
        } else {
            s to 443
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { param ->
            val kv = param.split("=", limit = 2)
            if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else null
        }.toMap()
    }

    private fun parseTransport(type: String?): ProxyTransport {
        return when (type?.lowercase()) {
            "ws", "websocket" -> ProxyTransport.WS
            "grpc" -> ProxyTransport.GRPC
            "h2", "http" -> ProxyTransport.H2
            "quic" -> ProxyTransport.QUIC
            else -> ProxyTransport.TCP
        }
    }

    private fun parseTLSMode(security: String?): TLSMode {
        return when (security?.lowercase()) {
            "reality" -> TLSMode.REALITY
            "tls" -> TLSMode.TLS
            "none", "" -> TLSMode.NONE
            else -> TLSMode.TLS
        }
    }
}
