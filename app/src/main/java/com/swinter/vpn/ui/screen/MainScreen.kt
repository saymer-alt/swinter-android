package com.swinter.vpn.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swinter.vpn.data.VpnConfig
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.data.model.AppConfig
import kotlinx.coroutines.delay
import com.swinter.vpn.data.model.Server
import com.swinter.vpn.ui.component.StatusOrb
import com.swinter.vpn.ui.component.VpnButton
import com.swinter.vpn.ui.theme.*
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: VpnState,
    appConfig: AppConfig,
    legacyConfig: VpnConfig,
    statusMessage: String,
    connectedSince: Long?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onServerSelect: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onTestLatency: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val activeServer = appConfig.activeServer
    val favorites = appConfig.servers.filter { it.isFavorite }.take(5)
        .ifEmpty { appConfig.servers.take(5) }

    // Connection timer
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(connectedSince, state) {
        if (state == VpnState.CONNECTED && connectedSince != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - connectedSince) / 1000
                delay(1000)
            }
        } else {
            elapsedSeconds = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated background
        com.swinter.vpn.ui.component.LiquidBackground(state = state)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        // Status orb
        StatusOrb(state = state)

        Spacer(Modifier.height(24.dp))

        // Status text
        Text(
            text = when (state) {
                VpnState.DISCONNECTED -> "Отключено"
                VpnState.CONNECTING -> "Подключение..."
                VpnState.CONNECTED -> "Защищено"
                VpnState.DISCONNECTING -> "Отключение..."
            },
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontSize = 28.sp
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = when (state) {
                VpnState.DISCONNECTED -> "Трафик не защищён"
                VpnState.CONNECTING -> statusMessage.ifBlank { "Устанавливаем TURN-туннель" }
                VpnState.CONNECTED -> activeServer?.let { "${it.proto.displayName} → ${it.name.ifBlank { it.address }}" } ?: "Через TURN → VPS"
                VpnState.DISCONNECTING -> "Закрываем соединение"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        // Active server card
        if (activeServer != null) {
            ActiveServerCard(
                server = activeServer,
                onTestLatency = onTestLatency,
            )
            Spacer(Modifier.height(16.dp))
        }

        // Connect/Disconnect button
        ConnectButton(
            state = state,
            isConfigured = activeServer != null || legacyConfig.isConfigured,
            onConnect = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onConnect()
            },
            onDisconnect = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onDisconnect()
            },
            onSettingsClick = onSettingsClick,
        )

        // Stats bar (only when connected)
        AnimatedVisibility(visible = state == VpnState.CONNECTED) {
            StatsBar(elapsedSeconds = elapsedSeconds)
        }

        Spacer(Modifier.height(20.dp))

        // Favorites
        if (favorites.isNotEmpty()) {
            Text(
                text = "СЕРВЕРЫ",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            favorites.forEach { server ->
                ServerRow(
                    server = server,
                    isActive = server.id == appConfig.activeServerID,
                    onClick = { onServerSelect(server.id) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
    } // Box
}

@Composable
private fun ActiveServerCard(
    server: Server,
    onTestLatency: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = VpnGreen,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name.ifBlank { server.address },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.proto.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = VpnCyan,
                    )
                    if (server.turnOverlay != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "TURN",
                            style = MaterialTheme.typography.labelSmall,
                            color = VpnPurple,
                        )
                    }
                    server.latencyMs?.let { ms ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${ms}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                ms < 100 -> VpnGreen
                                ms < 300 -> androidx.compose.ui.graphics.Color(0xFFFFAA00)
                                else -> VpnRed
                            },
                        )
                    }
                }
            }
            if (onTestLatency != null) {
                IconButton(onClick = onTestLatency, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = "Test latency",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectButton(
    state: VpnState,
    isConfigured: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    when (state) {
        VpnState.DISCONNECTED -> {
            if (isConfigured) {
                VpnButton(
                    text = "Подключиться",
                    icon = Icons.Filled.Bolt,
                    onClick = onConnect,
                    color = VpnCyan
                )
            } else {
                VpnButton(
                    text = "Не настроено",
                    icon = Icons.Filled.Warning,
                    onClick = onSettingsClick,
                    color = TextTertiary,
                    enabled = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Добавьте сервер на вкладке Servers",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
        VpnState.CONNECTING -> {
            VpnButton(
                text = "Подключение...",
                icon = Icons.Filled.HourglassTop,
                onClick = {},
                color = VpnPurple,
                enabled = false
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDisconnect) {
                Text("Отменить", color = VpnRed)
            }
        }
        VpnState.CONNECTED -> {
            VpnButton(
                text = "Отключить",
                icon = Icons.Filled.Close,
                onClick = onDisconnect,
                color = VpnRed
            )
        }
        VpnState.DISCONNECTING -> {
            VpnButton(
                text = "Отключение...",
                icon = Icons.Filled.HourglassTop,
                onClick = {},
                color = VpnPurple,
                enabled = false
            )
        }
    }
}

@Composable
private fun StatsBar(elapsedSeconds: Long) {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timeStr = if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "TIME", value = timeStr)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ServerRow(
    server: Server,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) DarkSurfaceVariant.copy(alpha = 0.85f) else DarkSurface.copy(alpha = 0.75f)
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isActive,
                onClick = onClick,
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
                )
            }
            Text(
                text = server.proto.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = VpnCyan,
            )
            server.latencyMs?.let { ms ->
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${ms}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        ms < 100 -> VpnGreen
                        ms < 300 -> androidx.compose.ui.graphics.Color(0xFFFFAA00)
                        else -> VpnRed
                    },
                )
            }
        }
    }
}
