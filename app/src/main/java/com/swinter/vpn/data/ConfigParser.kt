package com.swinter.vpn.data

import com.swinter.vpn.data.model.*

/**
 * Parses Shadowrocket/Swinter .conf format.
 * Returns routing rules, DNS config, proxy groups, TURN config.
 */
data class ParsedConfig(
    val rules: MutableList<RoutingRule> = mutableListOf(),
    var dns: DNSConfig = DNSConfig(),
    val groups: MutableList<ProxyGroup> = mutableListOf(),
    var turnOverlay: TurnOverlayConfig? = null,
    var updateURL: String = "",
)

object ConfigParser {

    fun parse(content: String): ParsedConfig {
        val result = ParsedConfig()
        var currentSection = ""

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.drop(1).dropLast(1).lowercase()
                continue
            }

            when (currentSection) {
                "general" -> parseGeneral(line, result)
                "turn" -> parseTURN(line, result)
                "proxy group" -> parseProxyGroup(line, result)
                "rule" -> parseRule(line, result)
            }
        }

        return result
    }

    private fun parseGeneral(line: String, result: ParsedConfig) {
        val parts = line.split("=", limit = 2).map { it.trim() }
        if (parts.size != 2) return
        val key = parts[0].lowercase()
        val value = parts[1]

        when (key) {
            "dns-server" -> {
                val servers = value.split(",").map { it.trim() }
                result.dns = result.dns.copy(
                    servers = servers.filter { !it.startsWith("https://") },
                    dohURL = servers.firstOrNull { it.startsWith("https://") } ?: "",
                )
            }
            "fallback-dns-server" -> {
                if (value != "system") {
                    result.dns = result.dns.copy(
                        fallbackServers = value.split(",").map { it.trim() }
                    )
                }
            }
            "update-url" -> result.updateURL = value
        }
    }

    private fun parseTURN(line: String, result: ParsedConfig) {
        val parts = line.split("=", limit = 2).map { it.trim() }
        if (parts.size != 2) return
        val key = parts[0].lowercase()
        val value = parts[1]

        if (result.turnOverlay == null) {
            result.turnOverlay = TurnOverlayConfig()
        }

        when (key) {
            "source" -> result.turnOverlay = result.turnOverlay?.copy(
                source = if (value == "vk") com.swinter.vpn.data.model.TurnSource.VK else com.swinter.vpn.data.model.TurnSource.MAX
            )
            "target-user-id" -> result.turnOverlay = result.turnOverlay?.copy(maxTargetUserID = value)
            "use-udp" -> result.turnOverlay = result.turnOverlay?.copy(useUDP = value.lowercase() == "true")
            "num-conns" -> result.turnOverlay = result.turnOverlay?.copy(numConns = value.toIntOrNull() ?: 4)
            "vk-link" -> result.turnOverlay = result.turnOverlay?.copy(vkLink = value)
        }
    }

    private fun parseProxyGroup(line: String, result: ParsedConfig) {
        val mainParts = line.split("=", limit = 2).map { it.trim() }
        if (mainParts.size != 2) return

        val name = mainParts[0]
        val params = mainParts[1].split(",").map { it.trim() }
        val typeStr = params.firstOrNull() ?: return

        val type = when (typeStr.lowercase()) {
            "url-test" -> GroupType.URL_TEST
            "fallback" -> GroupType.FALLBACK
            else -> GroupType.SELECT
        }

        var testURL = "https://www.gstatic.com/generate_204"
        var interval = 300

        for (param in params.drop(1)) {
            val kv = param.split("=", limit = 2).map { it.trim() }
            if (kv.size != 2) continue
            when (kv[0].lowercase()) {
                "interval" -> interval = kv[1].toIntOrNull() ?: 300
                "url" -> testURL = kv[1]
            }
        }

        result.groups.add(ProxyGroup(name = name, type = type, testURL = testURL, testInterval = interval))
    }

    private fun parseRule(line: String, result: ParsedConfig) {
        val parts = line.split(",", limit = 3).map { it.trim() }
        if (parts.size < 2) return

        val typeStr = parts[0].uppercase()

        if (typeStr == "FINAL") {
            val action = parseAction(parts[1])
            result.rules.add(RoutingRule(type = RuleType.FINAL, value = "", action = action))
            return
        }

        if (parts.size < 3) return
        val value = parts[1]
        val actionStr = parts[2].uppercase().replace(",NO-RESOLVE", "")
        val action = parseAction(actionStr)

        val type = when (typeStr) {
            "DOMAIN" -> RuleType.DOMAIN
            "DOMAIN-SUFFIX" -> RuleType.DOMAIN_SUFFIX
            "DOMAIN-KEYWORD" -> RuleType.DOMAIN_KEYWORD
            "IP-CIDR", "IP-CIDR6" -> RuleType.IP_CIDR
            "GEOIP" -> RuleType.GEOIP
            else -> return
        }

        result.rules.add(RoutingRule(type = type, value = value, action = action))
    }

    private fun parseAction(str: String): RuleAction {
        return when (str.trim()) {
            "DIRECT" -> RuleAction.DIRECT
            "REJECT", "REJECT-NO-DROP" -> RuleAction.REJECT
            else -> RuleAction.PROXY
        }
    }
}
