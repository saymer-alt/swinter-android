package com.swinter.vpn.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swinter.vpn.data.model.*
import com.swinter.vpn.ui.component.VpnCard
import com.swinter.vpn.ui.component.VpnTextField
import com.swinter.vpn.ui.component.VpnTextField
import com.swinter.vpn.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    appConfig: AppConfig,
    onUpdateConfig: ((AppConfig) -> AppConfig) -> Unit,
) {
    var showAddRule by remember { mutableStateOf(false) }
    var showAddGroup by remember { mutableStateOf(false) }
    var dnsExpanded by remember { mutableStateOf(false) }
    var scenesExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Конфигурация") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Proxy Groups
            item {
                ProxyGroupsSection(
                    groups = appConfig.groups,
                    servers = appConfig.servers,
                    showAdd = showAddGroup,
                    onToggleAdd = { showAddGroup = !showAddGroup },
                    onAddGroup = { group ->
                        onUpdateConfig { it.copy(groups = it.groups + group) }
                        showAddGroup = false
                    },
                    onDeleteGroup = { groupId ->
                        onUpdateConfig { it.copy(groups = it.groups.filter { g -> g.id != groupId }) }
                    },
                )
            }

            // Routing Rules
            item {
                RoutingRulesSection(
                    rules = appConfig.routingRules,
                    showAdd = showAddRule,
                    onToggleAdd = { showAddRule = !showAddRule },
                    onAddRule = { rule ->
                        onUpdateConfig { it.copy(routingRules = it.routingRules + rule) }
                        showAddRule = false
                    },
                    onDeleteRule = { ruleId ->
                        onUpdateConfig { it.copy(routingRules = it.routingRules.filter { r -> r.id != ruleId }) }
                    },
                    onToggleRule = { ruleId ->
                        onUpdateConfig { config ->
                            config.copy(routingRules = config.routingRules.map { r ->
                                if (r.id == ruleId) r.copy(enabled = !r.enabled) else r
                            })
                        }
                    },
                    onApplyPreset = { preset ->
                        onUpdateConfig { it.copy(routingRules = it.routingRules + preset) }
                    },
                )
            }

            // DNS
            item {
                DNSSection(
                    dns = appConfig.dns,
                    expanded = dnsExpanded,
                    onToggle = { dnsExpanded = !dnsExpanded },
                    onUpdate = { newDns -> onUpdateConfig { it.copy(dns = newDns) } },
                )
            }

            // Scenes
            item {
                ScenesSection(
                    scenes = appConfig.scenes,
                    servers = appConfig.servers,
                    groups = appConfig.groups,
                    expanded = scenesExpanded,
                    onToggle = { scenesExpanded = !scenesExpanded },
                    onToggleScene = { sceneId ->
                        onUpdateConfig { config ->
                            config.copy(scenes = config.scenes.map { s ->
                                if (s.id == sceneId) s.copy(enabled = !s.enabled) else s
                            })
                        }
                    },
                    onDeleteScene = { sceneId ->
                        onUpdateConfig { it.copy(scenes = it.scenes.filter { s -> s.id != sceneId }) }
                    },
                    onAddScene = { scene ->
                        onUpdateConfig { it.copy(scenes = it.scenes + scene) }
                    },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// MARK: - Proxy Groups

@Composable
private fun ProxyGroupsSection(
    groups: List<ProxyGroup>,
    servers: List<Server>,
    showAdd: Boolean,
    onToggleAdd: () -> Unit,
    onAddGroup: (ProxyGroup) -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    VpnCard(title = "Proxy Groups", icon = Icons.Filled.Layers) {
        groups.forEach { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name.ifBlank { "Unnamed" }, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${group.type.name} (${group.serverIDs.size} серверов)",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { onDeleteGroup(group.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = VpnRed, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (groups.isEmpty()) {
            Text("Нет групп", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(visible = showAdd) {
            AddGroupForm(servers = servers, onAdd = onAddGroup)
        }
        if (!showAdd) {
            TextButton(onClick = onToggleAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Добавить группу")
            }
        }
    }
}

@Composable
private fun AddGroupForm(servers: List<Server>, onAdd: (ProxyGroup) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GroupType.SELECT) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VpnTextField(value = name, onValueChange = { name = it }, placeholder = "Название группы", icon = Icons.Filled.Label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupType.entries.forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { type = t },
                    label = { Text(t.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Button(
            onClick = { if (name.isNotBlank()) onAdd(ProxyGroup(name = name, type = type)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Добавить")
        }
    }
}

// MARK: - Routing Rules

@Composable
private fun RoutingRulesSection(
    rules: List<RoutingRule>,
    showAdd: Boolean,
    onToggleAdd: () -> Unit,
    onAddRule: (RoutingRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleRule: (String) -> Unit,
    onApplyPreset: (List<RoutingRule>) -> Unit,
) {
    VpnCard(title = "Routing Rules", icon = Icons.Filled.Shuffle) {
        rules.forEach { rule ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleRule(rule.id) },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${rule.type.label}: ${rule.value}",
                        color = if (rule.enabled) TextPrimary else TextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    rule.action.name,
                    color = when (rule.action) {
                        RuleAction.PROXY -> VpnCyan
                        RuleAction.DIRECT -> VpnGreen
                        RuleAction.REJECT -> VpnRed
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                IconButton(onClick = { onDeleteRule(rule.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = VpnRed, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (rules.isEmpty()) {
            Text("Нет правил маршрутизации", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        // Presets
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onApplyPreset(ruDirectPreset()) }) {
                Text("RU Direct", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { onApplyPreset(blockedProxyPreset()) }) {
                Text("Blocked Proxy", style = MaterialTheme.typography.labelSmall)
            }
        }

        AnimatedVisibility(visible = showAdd) {
            AddRuleForm(onAdd = onAddRule)
        }
        if (!showAdd) {
            TextButton(onClick = onToggleAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Добавить правило")
            }
        }
    }
}

@Composable
private fun AddRuleForm(onAdd: (RoutingRule) -> Unit) {
    var type by remember { mutableStateOf(RuleType.DOMAIN_SUFFIX) }
    var value by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(RuleAction.PROXY) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RuleType.entries.filter { it != RuleType.FINAL }.forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = { type = t },
                    label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        VpnTextField(value = value, onValueChange = { value = it }, placeholder = "Значение (напр. google.com)", icon = Icons.Filled.Edit)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RuleAction.entries.forEach { a ->
                FilterChip(
                    selected = action == a,
                    onClick = { action = a },
                    label = { Text(a.name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Button(
            onClick = { if (value.isNotBlank()) onAdd(RoutingRule(type = type, value = value, action = action)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Добавить")
        }
    }
}

// MARK: - DNS

@Composable
private fun DNSSection(
    dns: DNSConfig,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (DNSConfig) -> Unit,
) {
    var servers by remember { mutableStateOf(dns.servers.joinToString(", ")) }
    var dohURL by remember { mutableStateOf(dns.dohURL) }

    VpnCard(title = "DNS", icon = Icons.Filled.Dns) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(dns.servers.joinToString(", ").ifBlank { "По умолчанию" }, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null, tint = TextTertiary
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                VpnTextField(
                    value = servers, onValueChange = { servers = it },
                    placeholder = "1.1.1.1, 8.8.8.8", icon = Icons.Filled.Dns
                )
                VpnTextField(
                    value = dohURL, onValueChange = { dohURL = it },
                    placeholder = "DoH URL (optional)", icon = Icons.Filled.Link
                )
                Button(
                    onClick = {
                        onUpdate(DNSConfig(
                            servers = servers.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            dohURL = dohURL,
                        ))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Применить")
                }
            }
        }
    }
}

// MARK: - Scenes

@Composable
private fun ScenesSection(
    scenes: List<Scene>,
    servers: List<Server>,
    groups: List<ProxyGroup>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onToggleScene: (String) -> Unit,
    onDeleteScene: (String) -> Unit,
    onAddScene: (Scene) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }

    VpnCard(title = "Сцены (автоматизация)", icon = Icons.Filled.PlayCircle) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${scenes.size} сцен", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null, tint = TextTertiary
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                scenes.forEach { scene ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = scene.enabled,
                            onCheckedChange = { onToggleScene(scene.id) },
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(scene.name.ifBlank { "Без имени" }, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                            Text(
                                scene.conditions.joinToString(", ") { "${it.type.name}: ${it.value}" },
                                color = TextTertiary,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "→ ${scene.action.type.name}",
                                color = VpnCyan,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = { onDeleteScene(scene.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = VpnRed, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                if (scenes.isEmpty()) {
                    Text("Сцены автоматически переключают VPN по условиям (WiFi SSID, время, тип сети).", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(4.dp))

                AnimatedVisibility(visible = showAdd) {
                    AddSceneForm(
                        servers = servers,
                        groups = groups,
                        onAdd = { scene ->
                            onAddScene(scene)
                            showAdd = false
                        }
                    )
                }
                if (!showAdd) {
                    TextButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить сцену")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSceneForm(
    servers: List<Server>,
    groups: List<ProxyGroup>,
    onAdd: (Scene) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var conditionType by remember { mutableStateOf(SceneConditionType.SSID) }
    var conditionValue by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf(SceneActionType.CONNECT) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VpnTextField(
            value = name, onValueChange = { name = it },
            placeholder = "Название сцены", icon = Icons.Filled.Label
        )

        Text("Условие", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SceneConditionType.entries.forEach { t ->
                FilterChip(
                    selected = conditionType == t,
                    onClick = { conditionType = t },
                    label = {
                        Text(
                            when (t) {
                                SceneConditionType.SSID -> "WiFi SSID"
                                SceneConditionType.NETWORK_TYPE -> "Сеть"
                                SceneConditionType.TIME_RANGE -> "Время"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
        VpnTextField(
            value = conditionValue, onValueChange = { conditionValue = it },
            placeholder = when (conditionType) {
                SceneConditionType.SSID -> "Название WiFi сети"
                SceneConditionType.NETWORK_TYPE -> "wifi или cellular"
                SceneConditionType.TIME_RANGE -> "09:00-18:00"
            },
            icon = Icons.Filled.Edit
        )

        Text("Действие", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SceneActionType.entries.forEach { a ->
                FilterChip(
                    selected = actionType == a,
                    onClick = { actionType = a },
                    label = {
                        Text(
                            when (a) {
                                SceneActionType.CONNECT -> "Подключить"
                                SceneActionType.DISCONNECT -> "Отключить"
                                SceneActionType.SWITCH_SERVER -> "Сервер"
                                SceneActionType.SWITCH_GROUP -> "Группа"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        Button(
            onClick = {
                if (name.isNotBlank() && conditionValue.isNotBlank()) {
                    onAdd(
                        Scene(
                            name = name,
                            conditions = listOf(SceneCondition(type = conditionType, value = conditionValue)),
                            action = SceneAction(type = actionType),
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Добавить сцену")
        }
    }
}

// MARK: - Presets

private fun ruDirectPreset(): List<RoutingRule> = listOf(
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "ru", action = RuleAction.DIRECT),
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "su", action = RuleAction.DIRECT),
    RoutingRule(type = RuleType.DOMAIN_KEYWORD, value = "yandex", action = RuleAction.DIRECT),
    RoutingRule(type = RuleType.DOMAIN_KEYWORD, value = "mail.ru", action = RuleAction.DIRECT),
    RoutingRule(type = RuleType.DOMAIN_KEYWORD, value = "vk.com", action = RuleAction.DIRECT),
    RoutingRule(type = RuleType.GEOIP, value = "RU", action = RuleAction.DIRECT),
)

private fun blockedProxyPreset(): List<RoutingRule> = listOf(
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "youtube.com", action = RuleAction.PROXY),
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "googlevideo.com", action = RuleAction.PROXY),
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "instagram.com", action = RuleAction.PROXY),
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "facebook.com", action = RuleAction.PROXY),
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "twitter.com", action = RuleAction.PROXY),
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "x.com", action = RuleAction.PROXY),
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "linkedin.com", action = RuleAction.PROXY),
    RoutingRule(type = RuleType.DOMAIN_SUFFIX, value = "discord.com", action = RuleAction.PROXY),
)
