package com.swinter.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.data.VpnStateStore
import com.swinter.vpn.receiver.VpnShortcuts
import com.swinter.vpn.receiver.VpnToggleReceiver
import com.swinter.vpn.ui.screen.*
import com.swinter.vpn.ui.theme.DarkBackground
import com.swinter.vpn.ui.theme.DarkSurface
import com.swinter.vpn.ui.theme.SwinterTheme
import com.swinter.vpn.ui.theme.TextSecondary
import com.swinter.vpn.ui.theme.VpnCyan
import com.swinter.vpn.viewmodel.VpnViewModel

sealed class Tab(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Tab("home", "Home", Icons.Filled.Shield)
    data object Servers : Tab("servers", "Servers", Icons.Filled.Dns)
    data object Config : Tab("config", "Config", Icons.Filled.Tune)
    data object Settings : Tab("settings_tab", "Settings", Icons.Filled.Settings)
}

val tabs = listOf(Tab.Home, Tab.Servers, Tab.Config, Tab.Settings)

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingViewModel?.onVpnPermissionGranted()
        }
    }

    private var pendingViewModel: VpnViewModel? = null
    private var toggleVm: VpnViewModel? = null
    private var shouldToggleOnCreate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shouldToggleOnCreate = intent?.action == VpnToggleReceiver.ACTION_TOGGLE
        VpnShortcuts.update(this)

        addOnNewIntentListener { newIntent ->
            if (newIntent.action == VpnToggleReceiver.ACTION_TOGGLE) {
                val vm = toggleVm ?: return@addOnNewIntentListener
                when (VpnStateStore.get(this)) {
                    VpnState.CONNECTED, VpnState.CONNECTING -> vm.disconnect()
                    VpnState.DISCONNECTED -> vm.connect()
                    else -> {}
                }
            }
        }

        setContent {
            SwinterTheme {
                val vm: VpnViewModel = viewModel()
                val state by vm.state.collectAsState()
                val legacyConfig by vm.legacyConfig.collectAsState()
                val appConfig by vm.appConfig.collectAsState()
                val statusMessage by vm.statusMessage.collectAsState()
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    vm.onRequestVpnPermission = { intent ->
                        pendingViewModel = vm
                        vpnPermissionLauncher.launch(intent)
                    }
                }
                LaunchedEffect(Unit) { toggleVm = vm }
                LaunchedEffect(Unit) {
                    if (shouldToggleOnCreate) {
                        shouldToggleOnCreate = false
                        when (VpnStateStore.get(this@MainActivity)) {
                            VpnState.CONNECTED, VpnState.CONNECTING -> vm.disconnect()
                            VpnState.DISCONNECTED -> vm.connect()
                            else -> {}
                        }
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Show bottom bar only on tab routes
                val showBottomBar = currentRoute in tabs.map { it.route }

                Scaffold(
                    containerColor = DarkBackground,
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = DarkSurface,
                            ) {
                                tabs.forEach { tab ->
                                    NavigationBarItem(
                                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                                        label = { Text(tab.title) },
                                        selected = currentRoute == tab.route,
                                        onClick = {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = VpnCyan,
                                            selectedTextColor = VpnCyan,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary,
                                            indicatorColor = DarkSurface,
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Tab.Home.route,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable(Tab.Home.route) {
                            MainScreen(
                                state = state,
                                appConfig = appConfig,
                                legacyConfig = legacyConfig,
                                statusMessage = statusMessage,
                                connectedSince = vm.connectedSince.collectAsState().value,
                                onConnect = { vm.connect() },
                                onDisconnect = { vm.disconnect() },
                                onServerSelect = { serverId ->
                                    vm.setActiveServer(serverId)
                                },
                                onSettingsClick = {
                                    navController.navigate(Tab.Settings.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onTestLatency = {
                                    appConfig.activeServerID?.let { vm.testServerLatency(it) }
                                },
                            )
                        }
                        composable(Tab.Servers.route) {
                            var showQRScanner by remember { mutableStateOf(false) }

                            if (showQRScanner) {
                                QRScannerLauncher(
                                    onResult = { result ->
                                        showQRScanner = false
                                        vm.importFromURI(result)
                                    },
                                    onDismiss = { showQRScanner = false }
                                )
                            }

                            ServerListScreen(
                                appConfig = appConfig,
                                onServerSelect = { serverId -> vm.setActiveServer(serverId) },
                                onServerEdit = { serverId ->
                                    navController.navigate("server_edit/$serverId")
                                },
                                onAddServer = {
                                    navController.navigate("server_edit/new")
                                },
                                onDeleteServer = { serverId -> vm.removeServer(serverId) },
                                onToggleFavorite = { serverId -> vm.toggleFavorite(serverId) },
                                onTestAll = { vm.testAllServers() },
                                onScanQR = { showQRScanner = true },
                                onAddSubscription = { url -> vm.addSubscription(url) },
                                onRefreshSubscription = { subId -> vm.refreshSubscription(subId) },
                                onRemoveSubscription = { subId -> vm.removeSubscription(subId) },
                            )
                        }
                        composable(Tab.Config.route) {
                            ConfigScreen(
                                appConfig = appConfig,
                                onUpdateConfig = { update -> vm.updateAppConfig(update) },
                            )
                        }
                        composable(Tab.Settings.route) {
                            SettingsScreen(
                                config = legacyConfig,
                                onConfigUpdate = { update -> vm.updateConfig { it.update() } },
                                onBack = { navController.navigate(Tab.Home.route) },
                                onOpenAppSelector = { navController.navigate("apps") },
                                onMaxLogin = { navController.navigate("max_login") }
                            )
                        }
                        composable("server_edit/{serverId}") { backStackEntry ->
                            val serverId = backStackEntry.arguments?.getString("serverId") ?: "new"
                            val existingServer = if (serverId != "new") {
                                appConfig.servers.find { it.id == serverId }
                            } else null
                            ServerEditScreen(
                                server = existingServer,
                                onSave = { server ->
                                    if (existingServer != null) {
                                        vm.updateServer(server)
                                    } else {
                                        vm.addServer(server)
                                    }
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable("max_login") {
                            MaxLoginScreen(onBack = { navController.popBackStack() })
                        }
                        composable("apps") {
                            AppSelectorScreen(
                                selectedApps = legacyConfig.perAppList,
                                onSelectionChanged = { selected ->
                                    vm.updateConfig { it.copy(perAppList = selected) }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
