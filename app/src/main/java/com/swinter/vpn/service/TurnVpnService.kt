package com.swinter.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.swinter.vpn.MainActivity
import com.swinter.vpn.data.TurnSource
import com.swinter.vpn.data.VpnConfig
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.data.VpnStateStore
import com.swinter.vpn.receiver.VpnShortcuts
import com.swinter.vpn.widget.VpnWidgetProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import vpnlib.Vpnlib as Swinterlib
import java.net.InetAddress

class TurnVpnService : VpnService() {

    companion object {
        const val TAG = "TurnVpnService"
        const val ACTION_START = "com.swinter.vpn.START"
        const val ACTION_STOP = "com.swinter.vpn.STOP"
        const val ACTION_QUICK_CONNECT = "com.swinter.vpn.QUICK_CONNECT"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val EXTRA_TURN_IPS = "turn_ips"
        const val EXTRA_API_IPS = "api_ips"
        const val EXTRA_WG_ADDRESS = "wg_address"
        const val EXTRA_DNS = "dns"
        const val EXTRA_MTU = "mtu"
        const val EXTRA_PER_APP_ENABLED = "per_app_enabled"
        const val EXTRA_PER_APP_BYPASS = "per_app_bypass"
        const val EXTRA_PER_APP_LIST = "per_app_list"
        const val CHANNEL_ID = "vpn_channel"
        const val NOTIFICATION_ID = 1

        const val STATE_ACTION = "com.swinter.vpn.STATE_CHANGED"
        const val EXTRA_STATE = "state"

        // VK/TURN subnets to exclude from tunnel (same as iOS)
        val EXCLUDED_SUBNETS = listOf(
            "155.212.0.0" to 16,    // MAX/VK TURN servers
            "87.240.128.0" to 18,   // VK
            "93.186.224.0" to 22,   // VK
            "95.142.192.0" to 21,   // VK
            "95.213.0.0" to 18,     // Mail.ru / VK
            "185.32.185.0" to 24,   // VK Calls
            "5.255.211.0" to 24,    // Yandex
        )
    }

    private var vpnFd: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: run {
                    Log.e(TAG, "No config JSON")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val turnIPs = intent.getStringArrayExtra(EXTRA_TURN_IPS) ?: emptyArray()
                val apiIPs = intent.getStringArrayExtra(EXTRA_API_IPS) ?: emptyArray()
                val wgAddress = intent.getStringExtra(EXTRA_WG_ADDRESS) ?: "10.7.0.2"
                val dns = intent.getStringExtra(EXTRA_DNS) ?: "1.1.1.1"
                val mtu = intent.getIntExtra(EXTRA_MTU, 1280)
                val perAppEnabled = intent.getBooleanExtra(EXTRA_PER_APP_ENABLED, false)
                val perAppBypass = intent.getBooleanExtra(EXTRA_PER_APP_BYPASS, true)
                val perAppList = intent.getStringArrayExtra(EXTRA_PER_APP_LIST) ?: emptyArray()

                startForeground(NOTIFICATION_ID, buildNotification("Подключение..."))
                broadcastState("CONNECTING")

                startVpn(configJson, turnIPs, apiIPs, wgAddress, dns, mtu, perAppEnabled, perAppBypass, perAppList)
            }
            ACTION_QUICK_CONNECT -> {
                // Background toggle — load config, resolve TURN, connect
                val cfg = VpnConfig.load(this)
                if (!cfg.isConfigured) {
                    Log.e(TAG, "Quick connect: config not configured")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification("Подключение..."))
                broadcastState("CONNECTING")
                quickConnect(cfg)
            }
        }
        return START_STICKY
    }

    private fun startVpn(
        configJson: String,
        turnIPs: Array<String>,
        apiIPs: Array<String>,
        wgAddress: String,
        dns: String,
        mtu: Int,
        perAppEnabled: Boolean,
        perAppBypass: Boolean,
        perAppList: Array<String>
    ) {
        vpnThread = Thread {
            try {
                val builder = Builder()
                    .setSession("Swinter")
                    .setMtu(mtu)
                    .addDnsServer(dns)

                // Set tunnel address
                val addressParts = wgAddress.split("/")
                val ip = addressParts[0]
                val prefix = if (addressParts.size > 1) addressParts[1].toIntOrNull() ?: 32 else 32
                builder.addAddress(ip, prefix)

                // Route all traffic through tunnel
                val parsed = try { Gson().fromJson(configJson, Map::class.java) } catch (_: Exception) { null }
                val protocol = parsed?.get("protocol") as? String ?: ""
                val isWireGuardFamily = protocol == "wireguard" || protocol == "amneziawg"

                builder.addRoute("0.0.0.0", 0)
                builder.addRoute("::", 0)

                // Resolve server endpoint IP to exclude from tunnel
                // (prevents routing loop for direct WireGuard/AmneziaWG)
                val endpointIPs = mutableListOf<String>()
                try {
                    val parsed = Gson().fromJson(configJson, Map::class.java)
                    val address = parsed["address"] as? String
                    // Also check nested wireguard.peerAddress
                    val wgConf = parsed["wireguard"] as? Map<*, *>
                    val peerAddress = wgConf?.get("peerAddress") as? String
                    val endpoint = peerAddress ?: address
                    if (endpoint != null) {
                        val host = endpoint.split(":").first()
                        val addrs = InetAddress.getAllByName(host)
                        for (addr in addrs) {
                            endpointIPs.add(addr.hostAddress ?: continue)
                        }
                        Log.i(TAG, "Resolved endpoint $host -> $endpointIPs")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve endpoint for exclusion: $e")
                }

                // Exclude TURN server IPs, API IPs, and endpoint IPs from tunnel
                val allExcludeIPs = turnIPs.toList() + apiIPs.toList() + endpointIPs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+: use excludeRoute
                    for (ipStr in allExcludeIPs) {
                        try {
                            val addr = InetAddress.getByName(ipStr)
                            builder.excludeRoute(IpPrefix(addr, if (addr.address.size == 4) 32 else 128))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to exclude IP $ipStr: $e")
                        }
                    }
                    // Exclude known VK subnets
                    for ((subnet, prefixLen) in EXCLUDED_SUBNETS) {
                        try {
                            builder.excludeRoute(IpPrefix(InetAddress.getByName(subnet), prefixLen))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to exclude subnet $subnet/$prefixLen: $e")
                        }
                    }
                }
                // For API < 33: app is excluded via addDisallowedApplication(self) below,
                // so Go library's TURN connections bypass VPN tunnel automatically.

                // Per-app VPN routing
                // Note: addAllowedApplication and addDisallowedApplication are mutually exclusive
                val selfPackage = packageName
                if (perAppEnabled && perAppList.isNotEmpty()) {
                    if (perAppBypass) {
                        // Bypass mode: selected apps skip VPN, everything else goes through
                        for (pkg in perAppList) {
                            try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {}
                        }
                        // Also bypass ourselves
                        try { builder.addDisallowedApplication(selfPackage) } catch (_: Exception) {}
                    } else {
                        // Proxy mode: only selected apps go through VPN
                        for (pkg in perAppList) {
                            if (pkg != selfPackage) {
                                try { builder.addAllowedApplication(pkg) } catch (_: Exception) {}
                            }
                        }
                        // Don't add ourselves in proxy mode — we must not route through tunnel
                    }
                } else {
                    // Exclude ourselves from VPN routing
                    try { builder.addDisallowedApplication(selfPackage) } catch (_: Exception) {}
                }

                val fd = builder.establish() ?: run {
                    Log.e(TAG, "VPN establish() returned null")
                    broadcastState("DISCONNECTED")
                    stopSelf()
                    return@Thread
                }
                vpnFd = fd

                val tunFd = fd.detachFd()
                Log.i(TAG, "TUN fd: $tunFd, starting Go VPN...")

                try {
                    // Use StartVPNWithConfig for multi-protocol support
                    // Falls back to StartVPN for WireGuard+TURN
                    try {
                        Swinterlib.startVPNWithConfig(tunFd, configJson)
                    } catch (e: NoSuchMethodError) {
                        // Older Swinterlib.aar without StartVPNWithConfig — fallback
                        Log.w(TAG, "StartVPNWithConfig not available, falling back to StartVPN")
                        Swinterlib.startVPN(tunFd, configJson)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "StartVPN error: $e")
                    broadcastState("DISCONNECTED")
                    stopSelf()
                    return@Thread
                }

                // WireGuard sockets are protected by addDisallowedApplication(self) + excludeRoute(endpoint)

                Log.i(TAG, "VPN connected")
                broadcastState("CONNECTED")
                updateNotification("Защищено — VPN подключён")

            } catch (e: Exception) {
                Log.e(TAG, "VPN thread error", e)
                broadcastState("DISCONNECTED")
                stopSelf()
            }
        }.also { it.start() }
    }

    private fun quickConnect(cfg: VpnConfig) {
        Thread {
            try {
                // Resolve TURN credentials if needed
                var turnIPs = emptyList<String>()
                var apiIPs = emptyList<String>()
                var resolvedCfg = cfg

                if (cfg.turnSource != TurnSource.MAX_CALL) {
                    for (attempt in 1..3) {
                        try {
                            val result = when (cfg.turnSource) {
                                TurnSource.VK -> Swinterlib.resolveConfig(cfg.vkLink)
                                TurnSource.MAX -> Swinterlib.resolveConfigMAX(
                                    cfg.maxToken,
                                    cfg.maxTargetUserID.toLongOrNull() ?: 0L
                                )
                                else -> throw IllegalStateException()
                            }
                            val parsed = Gson().fromJson<Map<String, Any>>(
                                result, object : TypeToken<Map<String, Any>>() {}.type
                            )
                            @Suppress("UNCHECKED_CAST")
                            turnIPs = (parsed["turnIPs"] as? List<String>) ?: emptyList()
                            @Suppress("UNCHECKED_CAST")
                            apiIPs = (parsed["apiIPs"] as? List<String>) ?: emptyList()
                            resolvedCfg = cfg.copy(
                                turnUser = parsed["turnUser"] as? String ?: "",
                                turnPass = parsed["turnPass"] as? String ?: "",
                                turnAddr = parsed["turnAddr"] as? String ?: ""
                            )
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "Quick connect resolve attempt $attempt failed: ${e.message}")
                            if (attempt == 3) throw e
                            Thread.sleep(1500)
                        }
                    }
                }

                startVpn(
                    resolvedCfg.toGoConfigJSON(),
                    turnIPs.toTypedArray(),
                    apiIPs.toTypedArray(),
                    resolvedCfg.wgAddress,
                    resolvedCfg.wgDNS,
                    resolvedCfg.mtu,
                    resolvedCfg.perAppEnabled,
                    resolvedCfg.perAppBypass,
                    resolvedCfg.perAppList.toTypedArray()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Quick connect failed", e)
                broadcastState("DISCONNECTED")
                stopSelf()
            }
        }.start()
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN...")
        broadcastState("DISCONNECTING")

        try {
            Swinterlib.stopVPN()
        } catch (e: Exception) {
            Log.w(TAG, "StopVPN error: $e")
        }
        try {
            Swinterlib.stopSingBox()
        } catch (_: Exception) {}

        vpnFd?.close()
        vpnFd = null
        vpnThread = null

        broadcastState("DISCONNECTED")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    private fun broadcastState(state: String) {
        sendBroadcast(Intent(STATE_ACTION).apply {
            putExtra(EXTRA_STATE, state)
            setPackage(packageName)
        })
        // Persist for widget/shortcut
        val vpnState = when (state) {
            "CONNECTING" -> VpnState.CONNECTING
            "CONNECTED" -> VpnState.CONNECTED
            "DISCONNECTING" -> VpnState.DISCONNECTING
            else -> VpnState.DISCONNECTED
        }
        VpnStateStore.set(this, vpnState)
        VpnWidgetProvider.updateAll(this)
        VpnShortcuts.update(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Статус VPN подключения"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.apply {
            setContentTitle("Swinter")
            setContentText(text)
            setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            setContentIntent(pendingIntent)
            setOngoing(true)
        }.build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
