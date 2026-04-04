package com.swinter.vpn.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.swinter.vpn.data.TurnSource
import com.swinter.vpn.data.VpnConfig
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.data.model.AppConfig
import com.swinter.vpn.data.model.ProxyProtocol
import com.swinter.vpn.data.model.Server
import com.swinter.vpn.service.TurnVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vpnlib.Vpnlib as Swinterlib

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val TAG = "VpnViewModel"
        const val MAX_RETRIES = 5
        const val RETRY_DELAY_MS = 1500L
    }

    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state

    // Legacy config — kept for backward compat during migration
    private val _legacyConfig = MutableStateFlow(VpnConfig.load(application))
    val legacyConfig: StateFlow<VpnConfig> = _legacyConfig

    // New AppConfig
    private val _appConfig = MutableStateFlow(AppConfig.load(application))
    val appConfig: StateFlow<AppConfig> = _appConfig

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    // Network stats
    private val _connectedSince = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _connectedSince

    // Callback for VPN permission request
    var onRequestVpnPermission: ((Intent) -> Unit)? = null

    // Auto-reconnect state
    private var userInitiatedDisconnect = false
    private var wasConnected = false
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 10
    private var networkAvailable = true
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val stateStr = intent?.getStringExtra(TurnVpnService.EXTRA_STATE) ?: return
            val newState = when (stateStr) {
                "CONNECTING" -> VpnState.CONNECTING
                "CONNECTED" -> VpnState.CONNECTED
                "DISCONNECTING" -> VpnState.DISCONNECTING
                else -> VpnState.DISCONNECTED
            }
            val previousState = _state.value
            _state.value = newState

            when (newState) {
                VpnState.CONNECTED -> {
                    wasConnected = true
                    _connectedSince.value = System.currentTimeMillis()
                    cancelReconnect()
                }
                VpnState.DISCONNECTED -> {
                    val wasPreviouslyConnected = wasConnected
                    wasConnected = false
                    _connectedSince.value = null
                    if (wasPreviouslyConnected && !userInitiatedDisconnect) {
                        Log.i(TAG, "Unexpected disconnect, scheduling reconnect")
                        scheduleReconnect()
                    }
                }
                else -> {}
            }
        }
    }

    init {
        val filter = IntentFilter(TurnVpnService.STATE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(stateReceiver, filter)
        }
        startNetworkMonitor()
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
        stopNetworkMonitor()
        reconnectJob?.cancel()
        super.onCleared()
    }

    // MARK: - AppConfig operations

    fun updateAppConfig(update: (AppConfig) -> AppConfig) {
        val newConfig = update(_appConfig.value)
        _appConfig.value = newConfig
        AppConfig.save(getApplication(), newConfig)
    }

    fun addServer(server: Server) {
        updateAppConfig { config ->
            val newServers = config.servers + server
            val activeId = config.activeServerID ?: server.id
            config.copy(servers = newServers, activeServerID = activeId)
        }
    }

    fun removeServer(serverId: String) {
        updateAppConfig { config ->
            val newServers = config.servers.filter { it.id != serverId }
            val newGroups = config.groups.map { g ->
                g.copy(serverIDs = g.serverIDs.filter { it != serverId })
            }
            val activeId = if (config.activeServerID == serverId) newServers.firstOrNull()?.id else config.activeServerID
            config.copy(servers = newServers, groups = newGroups, activeServerID = activeId)
        }
    }

    fun updateServer(server: Server) {
        updateAppConfig { config ->
            config.copy(servers = config.servers.map { if (it.id == server.id) server else it })
        }
    }

    fun setActiveServer(serverId: String) {
        updateAppConfig { it.copy(activeServerID = serverId) }
    }

    fun toggleFavorite(serverId: String) {
        updateAppConfig { config ->
            config.copy(servers = config.servers.map {
                if (it.id == serverId) it.copy(isFavorite = !it.isFavorite) else it
            })
        }
    }

    // MARK: - Legacy config operations (for backward compat)

    fun updateConfig(update: (VpnConfig) -> VpnConfig) {
        val newConfig = update(_legacyConfig.value)
        _legacyConfig.value = newConfig
        VpnConfig.save(getApplication(), newConfig)
    }

    // MARK: - Network Monitor

    private fun startNetworkMonitor() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasUnavailable = !networkAvailable
                networkAvailable = true
                if (wasUnavailable && wasConnected && !userInitiatedDisconnect) {
                    Log.i(TAG, "Network restored, triggering reconnect")
                    scheduleReconnect()
                }
            }
            override fun onLost(network: Network) {
                networkAvailable = false
            }
        }
        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    private fun stopNetworkMonitor() {
        networkCallback?.let {
            try {
                val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }

    // MARK: - VPN Detection

    private fun isOtherVpnActive(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    // MARK: - Auto-Reconnect

    private fun scheduleReconnect() {
        if (userInitiatedDisconnect) return
        if (reconnectAttempt >= maxReconnectAttempts) return

        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val delaySec = minOf(2L shl reconnectAttempt, 60L)
            reconnectAttempt++

            _statusMessage.value = "Переподключение ($reconnectAttempt/$maxReconnectAttempts) через ${delaySec}с..."
            _state.value = VpnState.CONNECTING
            Log.i(TAG, "Auto-reconnect #$reconnectAttempt in ${delaySec}s")

            delay(delaySec * 1000)

            if (!networkAvailable) {
                _statusMessage.value = "Ожидание сети..."
                return@launch
            }

            connect()
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
    }

    fun connect() {
        if (_state.value != VpnState.DISCONNECTED && _state.value != VpnState.CONNECTING) return

        val context = getApplication<Application>()

        if (isOtherVpnActive()) {
            _statusMessage.value = "Другой VPN активен — отключите его, иначе MAX/VK серверы будут недоступны"
            _state.value = VpnState.DISCONNECTED
            return
        }

        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            onRequestVpnPermission?.invoke(prepareIntent)
            return
        }

        userInitiatedDisconnect = false
        _state.value = VpnState.CONNECTING

        val activeServer = _appConfig.value.activeServer
        if (activeServer != null && activeServer.address.isNotBlank()) {
            viewModelScope.launch {
                try {
                    doConnectWithServer(context, activeServer)
                } catch (e: Exception) {
                    Log.e(TAG, "Connect with server failed", e)
                    _statusMessage.value = "Ошибка: ${e.message}"
                    _state.value = VpnState.DISCONNECTED
                }
            }
        } else {
            // Fallback to legacy config
            viewModelScope.launch {
                try {
                    doConnectLegacy(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Connect failed", e)
                    _statusMessage.value = "Ошибка: ${e.message}"
                    _state.value = VpnState.DISCONNECTED
                }
            }
        }
    }

    private suspend fun doConnectWithServer(context: Context, server: Server) {
        val legacyCfg = _legacyConfig.value
        var turnIPs = emptyList<String>()
        var apiIPs = emptyList<String>()
        var configJson = server.toGoConfigJSON()

        // Resolve TURN credentials if TURN overlay is enabled
        val turn = server.turnOverlay
        if (turn != null && server.isWireGuardFamily) {
            _statusMessage.value = "Получаем TURN credentials..."
            var lastError: Exception? = null
            for (attempt in 1..MAX_RETRIES) {
                try {
                    _statusMessage.value = "TURN credentials (попытка $attempt/$MAX_RETRIES)..."
                    val result = withContext(Dispatchers.IO) {
                        when (turn.source) {
                            com.swinter.vpn.data.model.TurnSource.VK ->
                                Swinterlib.resolveConfig(turn.vkLink)
                            com.swinter.vpn.data.model.TurnSource.MAX -> {
                                val maxToken = com.swinter.vpn.data.MaxAuthService.getToken(context) ?: turn.maxToken
                                Swinterlib.resolveConfigMAX(maxToken, turn.maxTargetUserID.toLongOrNull() ?: 0L)
                            }
                        }
                    }

                    val parsed = Gson().fromJson<Map<String, Any>>(
                        result, object : TypeToken<Map<String, Any>>() {}.type
                    )

                    @Suppress("UNCHECKED_CAST")
                    turnIPs = (parsed["turnIPs"] as? List<String>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    apiIPs = (parsed["apiIPs"] as? List<String>) ?: emptyList()

                    // Merge resolved TURN credentials into config JSON
                    val configMap = Gson().fromJson<MutableMap<String, Any>>(
                        configJson, object : TypeToken<MutableMap<String, Any>>() {}.type
                    )
                    configMap["turnUser"] = parsed["turnUser"] as? String ?: ""
                    configMap["turnPass"] = parsed["turnPass"] as? String ?: ""
                    configMap["turnAddr"] = parsed["turnAddr"] as? String ?: ""
                    configJson = Gson().toJson(configMap)

                    Log.i(TAG, "TURN resolved: turnIPs=$turnIPs")
                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "TURN resolve attempt $attempt failed: ${e.message}")
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
                }
            }
            if (lastError != null) throw lastError
        }

        // Start VPN service
        _statusMessage.value = "Запускаем VPN..."
        val intent = Intent(context, TurnVpnService::class.java).apply {
            action = TurnVpnService.ACTION_START
            putExtra(TurnVpnService.EXTRA_CONFIG_JSON, configJson)
            putExtra(TurnVpnService.EXTRA_TURN_IPS, turnIPs.toTypedArray())
            putExtra(TurnVpnService.EXTRA_API_IPS, apiIPs.toTypedArray())
            putExtra(TurnVpnService.EXTRA_WG_ADDRESS, server.wgAddress)
            putExtra(TurnVpnService.EXTRA_DNS, server.wgDNS)
            putExtra(TurnVpnService.EXTRA_MTU, server.mtu)
            putExtra(TurnVpnService.EXTRA_PER_APP_ENABLED, legacyCfg.perAppEnabled)
            putExtra(TurnVpnService.EXTRA_PER_APP_BYPASS, legacyCfg.perAppBypass)
            putExtra(TurnVpnService.EXTRA_PER_APP_LIST, legacyCfg.perAppList.toTypedArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private suspend fun doConnectLegacy(context: Context) {
        val cfg = _legacyConfig.value
        _statusMessage.value = "Получаем TURN credentials..."
        var turnIPs = emptyList<String>()
        var apiIPs = emptyList<String>()

        if (cfg.turnSource != TurnSource.MAX_CALL) {
            var lastError: Exception? = null
            for (attempt in 1..MAX_RETRIES) {
                try {
                    _statusMessage.value = "TURN credentials (попытка $attempt/$MAX_RETRIES)..."
                    val result = withContext(Dispatchers.IO) {
                        when (cfg.turnSource) {
                            TurnSource.VK -> Swinterlib.resolveConfig(cfg.vkLink)
                            TurnSource.MAX -> {
                                val maxToken = com.swinter.vpn.data.MaxAuthService.getToken(context) ?: cfg.maxToken
                                Swinterlib.resolveConfigMAX(maxToken, cfg.maxTargetUserID.toLongOrNull() ?: 0L)
                            }
                            else -> throw IllegalStateException("MAX_CALL not supported here")
                        }
                    }

                    val parsed = Gson().fromJson<Map<String, Any>>(
                        result, object : TypeToken<Map<String, Any>>() {}.type
                    )

                    @Suppress("UNCHECKED_CAST")
                    turnIPs = (parsed["turnIPs"] as? List<String>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    apiIPs = (parsed["apiIPs"] as? List<String>) ?: emptyList()

                    updateConfig { it.copy(
                        turnUser = parsed["turnUser"] as? String ?: "",
                        turnPass = parsed["turnPass"] as? String ?: "",
                        turnAddr = parsed["turnAddr"] as? String ?: ""
                    ) }

                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "TURN resolve attempt $attempt failed: ${e.message}")
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
                }
            }
            if (lastError != null) throw lastError
        }

        _statusMessage.value = "Запускаем VPN..."
        val currentConfig = _legacyConfig.value
        val intent = Intent(context, TurnVpnService::class.java).apply {
            action = TurnVpnService.ACTION_START
            putExtra(TurnVpnService.EXTRA_CONFIG_JSON, currentConfig.toGoConfigJSON())
            putExtra(TurnVpnService.EXTRA_TURN_IPS, turnIPs.toTypedArray())
            putExtra(TurnVpnService.EXTRA_API_IPS, apiIPs.toTypedArray())
            putExtra(TurnVpnService.EXTRA_WG_ADDRESS, currentConfig.wgAddress)
            putExtra(TurnVpnService.EXTRA_DNS, currentConfig.wgDNS)
            putExtra(TurnVpnService.EXTRA_MTU, currentConfig.mtu)
            putExtra(TurnVpnService.EXTRA_PER_APP_ENABLED, currentConfig.perAppEnabled)
            putExtra(TurnVpnService.EXTRA_PER_APP_BYPASS, currentConfig.perAppBypass)
            putExtra(TurnVpnService.EXTRA_PER_APP_LIST, currentConfig.perAppList.toTypedArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun disconnect() {
        if (_state.value != VpnState.CONNECTED && _state.value != VpnState.CONNECTING) return

        userInitiatedDisconnect = true
        cancelReconnect()
        _state.value = VpnState.DISCONNECTING
        val context = getApplication<Application>()
        val intent = Intent(context, TurnVpnService::class.java).apply {
            action = TurnVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun onVpnPermissionGranted() {
        connect()
    }

    // MARK: - Latency testing

    fun testServerLatency(serverId: String) {
        viewModelScope.launch {
            val server = _appConfig.value.servers.find { it.id == serverId } ?: return@launch
            if (server.address.isBlank()) return@launch
            try {
                val latency = withContext(Dispatchers.IO) {
                    try {
                        Swinterlib.pingServer(server.address).toInt()
                    } catch (_: Exception) {
                        // Fallback: TCP connect
                        val start = System.currentTimeMillis()
                        val host = server.address.split(":").first()
                        val addr = java.net.InetAddress.getByName(host)
                        if (addr.isReachable(3000)) (System.currentTimeMillis() - start).toInt() else -1
                    }
                }
                updateAppConfig { config ->
                    config.copy(servers = config.servers.map {
                        if (it.id == serverId) it.copy(latencyMs = if (latency >= 0) latency else null) else it
                    })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Latency test failed for ${server.address}: ${e.message}")
            }
        }
    }

    fun testAllServers() {
        _appConfig.value.servers.forEach { server ->
            testServerLatency(server.id)
        }
    }

    // MARK: - Subscription management

    fun addSubscription(url: String, name: String = "") {
        val sub = com.swinter.vpn.data.model.Subscription(
            name = name.ifBlank { url.take(40) },
            url = url,
        )
        updateAppConfig { it.copy(subscriptions = it.subscriptions + sub) }
        refreshSubscription(sub.id)
    }

    fun refreshSubscription(subscriptionId: String) {
        viewModelScope.launch {
            val sub = _appConfig.value.subscriptions.find { it.id == subscriptionId } ?: return@launch
            _statusMessage.value = "Обновляем подписку..."
            try {
                val newServers = com.swinter.vpn.data.SubscriptionManager.fetchSubscription(sub)
                if (newServers.isNotEmpty()) {
                    updateAppConfig { config ->
                        // Remove old servers from this subscription
                        val otherServers = config.servers.filter { it.subscriptionID != subscriptionId }
                        val updatedSub = sub.copy(
                            serverIDs = newServers.map { it.id },
                            lastUpdated = System.currentTimeMillis()
                        )
                        config.copy(
                            servers = otherServers + newServers,
                            subscriptions = config.subscriptions.map { if (it.id == subscriptionId) updatedSub else it }
                        )
                    }
                    _statusMessage.value = "Обновлено: ${newServers.size} серверов"
                } else {
                    _statusMessage.value = "Нет серверов в подписке"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Ошибка обновления: ${e.message}"
            }
        }
    }

    fun removeSubscription(subscriptionId: String) {
        updateAppConfig { config ->
            config.copy(
                servers = config.servers.filter { it.subscriptionID != subscriptionId },
                subscriptions = config.subscriptions.filter { it.id != subscriptionId }
            )
        }
    }

    // MARK: - .conf import

    fun importConfContent(content: String) {
        val parsed = com.swinter.vpn.data.ConfigParser.parse(content)
        updateAppConfig { config ->
            config.copy(
                routingRules = config.routingRules + parsed.rules,
                dns = if (parsed.dns.servers.isNotEmpty()) parsed.dns else config.dns,
                groups = config.groups + parsed.groups,
            )
        }
        _statusMessage.value = "Импортировано: ${parsed.rules.size} правил, ${parsed.groups.size} групп"
    }

    // MARK: - QR / URI import

    fun importFromURI(uri: String): Boolean {
        val sub = com.swinter.vpn.data.model.Subscription(name = "QR Import", url = "")
        val server = com.swinter.vpn.data.SubscriptionManager.parseServerURI(uri)
        if (server != null) {
            addServer(server)
            _statusMessage.value = "Сервер добавлен: ${server.name.ifBlank { server.address }}"
            return true
        }

        // Try as JSON config
        try {
            val imported = Gson().fromJson(uri, com.swinter.vpn.data.model.AppConfig::class.java)
            if (imported != null && imported.servers.isNotEmpty()) {
                updateAppConfig { config ->
                    config.copy(
                        servers = config.servers + imported.servers,
                        activeServerID = config.activeServerID ?: imported.servers.firstOrNull()?.id
                    )
                }
                _statusMessage.value = "Импортировано ${imported.servers.size} серверов"
                return true
            }
        } catch (_: Exception) {}

        // Try as .conf
        if (uri.contains("[General]") || uri.contains("[Rule]")) {
            importConfContent(uri)
            return true
        }

        _statusMessage.value = "Не удалось распознать формат"
        return false
    }
}
