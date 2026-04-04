package com.swinter.vpn.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swinter.vpn.data.MaxAuthService
import com.swinter.vpn.data.VpnConfig
import com.swinter.vpn.ui.component.VpnCard
import com.swinter.vpn.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: VpnConfig,
    onConfigUpdate: (VpnConfig.() -> VpnConfig) -> Unit,
    onBack: () -> Unit,
    onOpenAppSelector: () -> Unit,
    onMaxLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    var maxLoggedIn by remember { mutableStateOf(MaxAuthService.isLoggedIn(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // MAX Account
            VpnCard(title = "MAX Аккаунт", icon = Icons.Filled.Message) {
                if (maxLoggedIn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Вы вошли в MAX", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text(MaxAuthService.getMaskedPhone(context), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        TextButton(onClick = {
                            MaxAuthService.logout(context)
                            maxLoggedIn = false
                            onConfigUpdate { copy(maxToken = "") }
                        }) {
                            Text("Выйти", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Button(
                        onClick = { onMaxLogin() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Войти в MAX")
                    }
                }
                Text(
                    text = "MAX используется только для получения TURN-серверов. Данные хранятся на устройстве и никуда не передаются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            // Per-app VPN
            VpnCard(title = "Приложения", icon = Icons.Filled.Apps) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Раздельное туннелирование", color = TextSecondary)
                    Switch(
                        checked = config.perAppEnabled,
                        onCheckedChange = { onConfigUpdate { copy(perAppEnabled = it) } }
                    )
                }
                AnimatedVisibility(visible = config.perAppEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Режим обхода", color = TextSecondary)
                            Switch(
                                checked = config.perAppBypass,
                                onCheckedChange = { onConfigUpdate { copy(perAppBypass = it) } }
                            )
                        }
                        Text(
                            text = if (config.perAppBypass) {
                                "Выбранные приложения обходят VPN"
                            } else {
                                "Только выбранные приложения идут через VPN"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                        OutlinedButton(
                            onClick = onOpenAppSelector,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            val count = config.perAppList.size
                            Text(if (count == 0) "Выбрать приложения" else "Выбрано: $count")
                        }
                    }
                }
            }

            // Import/Export
            VpnCard(title = "Конфигурация", icon = Icons.Filled.Settings) {
                Text(
                    "Экспорт всех серверов и настроек в JSON, или импорт из буфера обмена.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val appCfg = com.swinter.vpn.data.model.AppConfig.load(context)
                            val json = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(appCfg)
                            clipboard.setPrimaryClip(ClipData.newPlainText("swinter_config", json))
                            Toast.makeText(context, "Конфиг скопирован", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Экспорт")
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (clip != null) {
                                try {
                                    val imported = com.google.gson.Gson().fromJson(clip, com.swinter.vpn.data.model.AppConfig::class.java)
                                    if (imported != null && imported.servers.isNotEmpty()) {
                                        com.swinter.vpn.data.model.AppConfig.save(context, imported)
                                        Toast.makeText(context, "Импортировано ${imported.servers.size} серверов!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Try legacy format
                                        val legacy = VpnConfig.fromJSON(clip)
                                        if (legacy != null) {
                                            onConfigUpdate { legacy }
                                            Toast.makeText(context, "Legacy конфиг импортирован!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Нет валидного конфига в буфере", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    val legacy = VpnConfig.fromJSON(clip)
                                    if (legacy != null) {
                                        onConfigUpdate { legacy }
                                        Toast.makeText(context, "Legacy конфиг импортирован!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Нет валидного конфига в буфере", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Импорт")
                    }
                }
            }

            // About
            VpnCard(title = "О приложении", icon = Icons.Filled.Info) {
                Text("Swinter v1.0", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    "VPN-клиент с поддержкой TURN overlay, множества протоколов и умного роутинга.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
