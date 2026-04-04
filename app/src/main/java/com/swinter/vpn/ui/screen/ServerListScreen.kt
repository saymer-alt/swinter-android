package com.swinter.vpn.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swinter.vpn.data.model.AppConfig
import com.swinter.vpn.data.model.Server
import com.swinter.vpn.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    appConfig: AppConfig,
    onServerSelect: (String) -> Unit,
    onServerEdit: (String) -> Unit,
    onAddServer: () -> Unit,
    onDeleteServer: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTestAll: () -> Unit,
    onScanQR: () -> Unit = {},
    onAddSubscription: (String) -> Unit = {},
    onRefreshSubscription: (String) -> Unit = {},
    onRemoveSubscription: (String) -> Unit = {},
) {
    val standaloneServers = appConfig.servers.filter { it.subscriptionID == null }
    val subscriptionGroups = appConfig.subscriptions.map { sub ->
        sub to appConfig.servers.filter { it.subscriptionID == sub.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Серверы") },
                actions = {
                    IconButton(onClick = onScanQR) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "QR")
                    }
                    IconButton(onClick = onAddServer) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить")
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Test all button
            item {
                Button(
                    onClick = onTestAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Проверить все серверы", color = TextPrimary)
                }
            }

            // Standalone servers section
            if (standaloneServers.isNotEmpty()) {
                item {
                    Text(
                        text = "СЕРВЕРЫ",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(standaloneServers, key = { it.id }) { server ->
                    ServerListRow(
                        server = server,
                        isActive = server.id == appConfig.activeServerID,
                        onSelect = { onServerSelect(server.id) },
                        onEdit = { onServerEdit(server.id) },
                        onDelete = { onDeleteServer(server.id) },
                        onToggleFavorite = { onToggleFavorite(server.id) },
                    )
                }
            }

            // Subscription groups
            subscriptionGroups.forEach { (sub, servers) ->
                if (servers.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                tint = VpnCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${sub.name.ifBlank { sub.url }} (${servers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRefreshSubscription(sub.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = VpnCyan, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onRemoveSubscription(sub.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = VpnRed, modifier = Modifier.size(16.dp))
                            }
                        }
                        sub.lastUpdated?.let { ts ->
                            val ago = (System.currentTimeMillis() - ts) / 60000
                            Text(
                                text = "Обновлено: ${if (ago < 60) "${ago}мин назад" else "${ago / 60}ч назад"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                modifier = Modifier.padding(start = 20.dp)
                            )
                        }
                    }
                    items(servers, key = { it.id }) { server ->
                        ServerListRow(
                            server = server,
                            isActive = server.id == appConfig.activeServerID,
                            onSelect = { onServerSelect(server.id) },
                            onEdit = { onServerEdit(server.id) },
                            onDelete = { onDeleteServer(server.id) },
                            onToggleFavorite = { onToggleFavorite(server.id) },
                        )
                    }
                }
            }

            // Empty state
            if (appConfig.servers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Dns,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Нет серверов",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onAddServer) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Добавить сервер")
                        }
                    }
                }
            }

            // Add subscription
            item {
                AddSubscriptionRow(onAdd = onAddSubscription)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AddSubscriptionRow(onAdd: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        if (!expanded) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Добавить подписку")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("URL подписки", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("https://example.com/sub") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { expanded = false; url = "" }) {
                            Text("Отмена")
                        }
                        Button(
                            onClick = {
                                if (url.isNotBlank()) {
                                    onAdd(url)
                                    expanded = false
                                    url = ""
                                }
                            },
                        ) {
                            Text("Добавить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerListRow(
    server: Server,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) DarkSurfaceVariant else DarkSurface
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isActive,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = VpnGreen,
                    unselectedColor = TextTertiary,
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name.ifBlank { server.address },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.proto.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = VpnCyan,
                    )
                    if (server.turnOverlay != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "+TURN",
                            style = MaterialTheme.typography.labelSmall,
                            color = VpnPurple,
                        )
                    }
                    if (server.isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFFFFAA00),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Latency
            server.latencyMs?.let { ms ->
                Text(
                    text = "${ms}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        ms < 100 -> VpnGreen
                        ms < 300 -> androidx.compose.ui.graphics.Color(0xFFFFAA00)
                        else -> VpnRed
                    },
                )
                Spacer(Modifier.width(4.dp))
            }

            // Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Menu",
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (server.isFavorite) "Убрать из избранного" else "В избранное") },
                        onClick = { showMenu = false; onToggleFavorite() },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = VpnRed) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = VpnRed) }
                    )
                }
            }
        }
    }
}
