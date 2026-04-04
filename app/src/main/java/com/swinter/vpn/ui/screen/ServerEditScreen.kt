package com.swinter.vpn.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.swinter.vpn.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    server: Server?,
    onSave: (Server) -> Unit,
    onBack: () -> Unit,
) {
    val isNew = server == null

    var name by remember { mutableStateOf(server?.name ?: "") }
    var address by remember { mutableStateOf(server?.address ?: "") }
    var proto by remember { mutableStateOf(server?.proto ?: ProxyProtocol.WIREGUARD) }

    // WireGuard
    var wgPrivateKey by remember { mutableStateOf(server?.wireGuardConfig?.privateKey ?: "") }
    var wgPublicKey by remember { mutableStateOf(server?.wireGuardConfig?.publicKey ?: "") }
    var wgPresharedKey by remember { mutableStateOf(server?.wireGuardConfig?.presharedKey ?: "") }
    var wgPeerAddress by remember { mutableStateOf(server?.wireGuardConfig?.peerAddress ?: "") }
    var wgAddress by remember { mutableStateOf(server?.wireGuardConfig?.wgAddress ?: "10.7.0.2/32") }
    var wgDNS by remember { mutableStateOf(server?.wireGuardConfig?.dns ?: "1.1.1.1") }
    var wgMTU by remember { mutableStateOf((server?.wireGuardConfig?.mtu ?: 1280).toString()) }

    // AmneziaWG
    var awgPrivateKey by remember { mutableStateOf(server?.amneziaWGConfig?.privateKey ?: "") }
    var awgPublicKey by remember { mutableStateOf(server?.amneziaWGConfig?.publicKey ?: "") }
    var awgPresharedKey by remember { mutableStateOf(server?.amneziaWGConfig?.presharedKey ?: "") }
    var awgPeerAddress by remember { mutableStateOf(server?.amneziaWGConfig?.peerAddress ?: "") }
    var awgAddress by remember { mutableStateOf(server?.amneziaWGConfig?.wgAddress ?: "10.8.0.2/24") }
    var awgDNS by remember { mutableStateOf(server?.amneziaWGConfig?.dns ?: "1.1.1.1") }
    var awgMTU by remember { mutableStateOf((server?.amneziaWGConfig?.mtu ?: 1280).toString()) }
    var awgJc by remember { mutableStateOf((server?.amneziaWGConfig?.jc ?: 4).toString()) }
    var awgJmin by remember { mutableStateOf((server?.amneziaWGConfig?.jmin ?: 40).toString()) }
    var awgJmax by remember { mutableStateOf((server?.amneziaWGConfig?.jmax ?: 70).toString()) }
    var awgS1 by remember { mutableStateOf((server?.amneziaWGConfig?.s1 ?: 0).toString()) }
    var awgS2 by remember { mutableStateOf((server?.amneziaWGConfig?.s2 ?: 0).toString()) }
    var awgH1 by remember { mutableStateOf((server?.amneziaWGConfig?.h1 ?: 1).toString()) }
    var awgH2 by remember { mutableStateOf((server?.amneziaWGConfig?.h2 ?: 2).toString()) }
    var awgH3 by remember { mutableStateOf((server?.amneziaWGConfig?.h3 ?: 3).toString()) }
    var awgH4 by remember { mutableStateOf((server?.amneziaWGConfig?.h4 ?: 4).toString()) }

    // VLESS
    var vlessUUID by remember { mutableStateOf(server?.vlessConfig?.uuid ?: "") }
    var vlessFlow by remember { mutableStateOf(server?.vlessConfig?.flow ?: "") }
    var vlessTransport by remember { mutableStateOf(server?.vlessConfig?.transport ?: ProxyTransport.TCP) }
    var vlessTLS by remember { mutableStateOf(server?.vlessConfig?.tls ?: TLSMode.TLS) }
    var vlessSNI by remember { mutableStateOf(server?.vlessConfig?.sni ?: "") }
    var vlessFingerprint by remember { mutableStateOf(server?.vlessConfig?.fingerprint ?: "chrome") }
    var vlessRealityPubKey by remember { mutableStateOf(server?.vlessConfig?.realityPublicKey ?: "") }
    var vlessRealityShortId by remember { mutableStateOf(server?.vlessConfig?.realityShortId ?: "") }
    var vlessWsPath by remember { mutableStateOf(server?.vlessConfig?.wsPath ?: "") }
    var vlessGrpcService by remember { mutableStateOf(server?.vlessConfig?.grpcServiceName ?: "") }

    // VMess
    var vmessUUID by remember { mutableStateOf(server?.vmessConfig?.uuid ?: "") }
    var vmessAlterId by remember { mutableStateOf((server?.vmessConfig?.alterId ?: 0).toString()) }
    var vmessSecurity by remember { mutableStateOf(server?.vmessConfig?.security ?: "auto") }
    var vmessTransport by remember { mutableStateOf(server?.vmessConfig?.transport ?: ProxyTransport.TCP) }
    var vmessTLS by remember { mutableStateOf(server?.vmessConfig?.tls ?: TLSMode.NONE) }
    var vmessSNI by remember { mutableStateOf(server?.vmessConfig?.sni ?: "") }
    var vmessWsPath by remember { mutableStateOf(server?.vmessConfig?.wsPath ?: "") }

    // Trojan
    var trojanPassword by remember { mutableStateOf(server?.trojanConfig?.password ?: "") }
    var trojanTransport by remember { mutableStateOf(server?.trojanConfig?.transport ?: ProxyTransport.TCP) }
    var trojanTLS by remember { mutableStateOf(server?.trojanConfig?.tls ?: TLSMode.TLS) }
    var trojanSNI by remember { mutableStateOf(server?.trojanConfig?.sni ?: "") }
    var trojanWsPath by remember { mutableStateOf(server?.trojanConfig?.wsPath ?: "") }

    // Shadowsocks
    var ssMethod by remember { mutableStateOf(server?.shadowsocksConfig?.method ?: "aes-256-gcm") }
    var ssPassword by remember { mutableStateOf(server?.shadowsocksConfig?.password ?: "") }

    // Hysteria2
    var hy2Password by remember { mutableStateOf(server?.hysteria2Config?.password ?: "") }
    var hy2Obfs by remember { mutableStateOf(server?.hysteria2Config?.obfs ?: "") }
    var hy2ObfsPassword by remember { mutableStateOf(server?.hysteria2Config?.obfsPassword ?: "") }
    var hy2SNI by remember { mutableStateOf(server?.hysteria2Config?.sni ?: "") }

    // SOCKS5
    var socks5User by remember { mutableStateOf(server?.socks5Config?.username ?: "") }
    var socks5Pass by remember { mutableStateOf(server?.socks5Config?.password ?: "") }
    var socks5TLS by remember { mutableStateOf(server?.socks5Config?.tls ?: false) }

    // HTTP
    var httpUser by remember { mutableStateOf(server?.httpConfig?.username ?: "") }
    var httpPass by remember { mutableStateOf(server?.httpConfig?.password ?: "") }
    var httpTLS by remember { mutableStateOf(server?.httpConfig?.tls ?: false) }

    // TURN overlay
    var turnEnabled by remember { mutableStateOf(server?.turnOverlay != null) }
    var turnSource by remember { mutableStateOf(server?.turnOverlay?.source ?: TurnSource.MAX) }
    var turnVkLink by remember { mutableStateOf(server?.turnOverlay?.vkLink ?: "") }
    var turnMaxToken by remember { mutableStateOf(server?.turnOverlay?.maxToken ?: "") }
    var turnMaxTargetUserID by remember { mutableStateOf(server?.turnOverlay?.maxTargetUserID ?: "") }
    var turnUseUDP by remember { mutableStateOf(server?.turnOverlay?.useUDP ?: false) }
    var turnNumConns by remember { mutableStateOf((server?.turnOverlay?.numConns ?: 4).toString()) }

    fun buildServer(): Server {
        val base = server?.copy(
            name = name,
            address = address,
            proto = proto,
        ) ?: Server(name = name, address = address, proto = proto)

        val withProto = when (proto) {
            ProxyProtocol.WIREGUARD -> base.copy(
                wireGuardConfig = WireGuardConfig(
                    privateKey = wgPrivateKey, publicKey = wgPublicKey, presharedKey = wgPresharedKey,
                    peerAddress = wgPeerAddress, wgAddress = wgAddress, dns = wgDNS,
                    mtu = wgMTU.toIntOrNull() ?: 1280,
                )
            )
            ProxyProtocol.AMNEZIAWG -> base.copy(
                amneziaWGConfig = AmneziaWGConfig(
                    privateKey = awgPrivateKey, publicKey = awgPublicKey, presharedKey = awgPresharedKey,
                    peerAddress = awgPeerAddress, wgAddress = awgAddress, dns = awgDNS,
                    mtu = awgMTU.toIntOrNull() ?: 1280,
                    jc = awgJc.toIntOrNull() ?: 4, jmin = awgJmin.toIntOrNull() ?: 40,
                    jmax = awgJmax.toIntOrNull() ?: 70, s1 = awgS1.toIntOrNull() ?: 0,
                    s2 = awgS2.toIntOrNull() ?: 0, h1 = awgH1.toIntOrNull() ?: 1,
                    h2 = awgH2.toIntOrNull() ?: 2, h3 = awgH3.toIntOrNull() ?: 3,
                    h4 = awgH4.toIntOrNull() ?: 4,
                )
            )
            ProxyProtocol.VLESS -> base.copy(
                vlessConfig = VLESSConfig(
                    uuid = vlessUUID, flow = vlessFlow, transport = vlessTransport,
                    tls = vlessTLS, sni = vlessSNI, fingerprint = vlessFingerprint,
                    realityPublicKey = vlessRealityPubKey, realityShortId = vlessRealityShortId,
                    wsPath = vlessWsPath, grpcServiceName = vlessGrpcService,
                )
            )
            ProxyProtocol.VMESS -> base.copy(
                vmessConfig = VMessConfig(
                    uuid = vmessUUID, alterId = vmessAlterId.toIntOrNull() ?: 0,
                    security = vmessSecurity, transport = vmessTransport, tls = vmessTLS,
                    sni = vmessSNI, wsPath = vmessWsPath,
                )
            )
            ProxyProtocol.TROJAN -> base.copy(
                trojanConfig = TrojanConfig(
                    password = trojanPassword, transport = trojanTransport, tls = trojanTLS,
                    sni = trojanSNI, wsPath = trojanWsPath,
                )
            )
            ProxyProtocol.SHADOWSOCKS -> base.copy(
                shadowsocksConfig = ShadowsocksConfig(method = ssMethod, password = ssPassword)
            )
            ProxyProtocol.HYSTERIA2 -> base.copy(
                hysteria2Config = Hysteria2Config(
                    password = hy2Password, obfs = hy2Obfs, obfsPassword = hy2ObfsPassword, sni = hy2SNI,
                )
            )
            ProxyProtocol.SOCKS5 -> base.copy(
                socks5Config = Socks5Config(username = socks5User, password = socks5Pass, tls = socks5TLS)
            )
            ProxyProtocol.HTTP -> base.copy(
                httpConfig = HTTPProxyConfig(username = httpUser, password = httpPass, tls = httpTLS)
            )
        }

        val turnOverlay = if (turnEnabled && proto.supportsTurn) {
            TurnOverlayConfig(
                source = turnSource, vkLink = turnVkLink, maxToken = turnMaxToken,
                maxTargetUserID = turnMaxTargetUserID, useUDP = turnUseUDP,
                numConns = turnNumConns.toIntOrNull() ?: 4,
            )
        } else null

        return withProto.copy(turnOverlay = turnOverlay)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Новый сервер" else "Редактировать") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(buildServer()) }) {
                        Text("Сохранить", color = VpnCyan)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic info
            VpnCard(title = "Основное", icon = Icons.Filled.Info) {
                VpnTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = "Название сервера", icon = Icons.Filled.Label
                )
                VpnTextField(
                    value = address, onValueChange = { address = it },
                    placeholder = "host:port", icon = Icons.Filled.Cloud
                )
            }

            // Protocol picker
            VpnCard(title = "Протокол", icon = Icons.Filled.Shield) {
                ProtocolPicker(selected = proto, onSelect = { proto = it })
            }

            // Protocol-specific fields
            when (proto) {
                ProxyProtocol.WIREGUARD -> WireGuardFields(
                    privateKey = wgPrivateKey, onPrivateKey = { wgPrivateKey = it },
                    publicKey = wgPublicKey, onPublicKey = { wgPublicKey = it },
                    peerAddress = wgPeerAddress, onPeerAddress = { wgPeerAddress = it },
                    wgAddress = wgAddress, onWgAddress = { wgAddress = it },
                    dns = wgDNS, onDns = { wgDNS = it },
                    mtu = wgMTU, onMtu = { wgMTU = it },
                )
                ProxyProtocol.AMNEZIAWG -> AmneziaWGFields(
                    privateKey = awgPrivateKey, onPrivateKey = { awgPrivateKey = it },
                    publicKey = awgPublicKey, onPublicKey = { awgPublicKey = it },
                    presharedKey = awgPresharedKey, onPresharedKey = { awgPresharedKey = it },
                    peerAddress = awgPeerAddress, onPeerAddress = { awgPeerAddress = it },
                    wgAddress = awgAddress, onWgAddress = { awgAddress = it },
                    dns = awgDNS, onDns = { awgDNS = it },
                    mtu = awgMTU, onMtu = { awgMTU = it },
                    jc = awgJc, onJc = { awgJc = it },
                    jmin = awgJmin, onJmin = { awgJmin = it },
                    jmax = awgJmax, onJmax = { awgJmax = it },
                    s1 = awgS1, onS1 = { awgS1 = it },
                    s2 = awgS2, onS2 = { awgS2 = it },
                    h1 = awgH1, onH1 = { awgH1 = it },
                    h2 = awgH2, onH2 = { awgH2 = it },
                    h3 = awgH3, onH3 = { awgH3 = it },
                    h4 = awgH4, onH4 = { awgH4 = it },
                )
                ProxyProtocol.VLESS -> VLESSFields(
                    uuid = vlessUUID, onUUID = { vlessUUID = it },
                    flow = vlessFlow, onFlow = { vlessFlow = it },
                    transport = vlessTransport, onTransport = { vlessTransport = it },
                    tls = vlessTLS, onTLS = { vlessTLS = it },
                    sni = vlessSNI, onSNI = { vlessSNI = it },
                    fingerprint = vlessFingerprint, onFingerprint = { vlessFingerprint = it },
                    realityPubKey = vlessRealityPubKey, onRealityPubKey = { vlessRealityPubKey = it },
                    realityShortId = vlessRealityShortId, onRealityShortId = { vlessRealityShortId = it },
                    wsPath = vlessWsPath, onWsPath = { vlessWsPath = it },
                    grpcService = vlessGrpcService, onGrpcService = { vlessGrpcService = it },
                )
                ProxyProtocol.VMESS -> VMessFields(
                    uuid = vmessUUID, onUUID = { vmessUUID = it },
                    security = vmessSecurity, onSecurity = { vmessSecurity = it },
                    transport = vmessTransport, onTransport = { vmessTransport = it },
                    tls = vmessTLS, onTLS = { vmessTLS = it },
                    sni = vmessSNI, onSNI = { vmessSNI = it },
                    wsPath = vmessWsPath, onWsPath = { vmessWsPath = it },
                )
                ProxyProtocol.TROJAN -> TrojanFields(
                    password = trojanPassword, onPassword = { trojanPassword = it },
                    transport = trojanTransport, onTransport = { trojanTransport = it },
                    tls = trojanTLS, onTLS = { trojanTLS = it },
                    sni = trojanSNI, onSNI = { trojanSNI = it },
                    wsPath = trojanWsPath, onWsPath = { trojanWsPath = it },
                )
                ProxyProtocol.SHADOWSOCKS -> ShadowsocksFields(
                    method = ssMethod, onMethod = { ssMethod = it },
                    password = ssPassword, onPassword = { ssPassword = it },
                )
                ProxyProtocol.HYSTERIA2 -> Hysteria2Fields(
                    password = hy2Password, onPassword = { hy2Password = it },
                    obfs = hy2Obfs, onObfs = { hy2Obfs = it },
                    obfsPassword = hy2ObfsPassword, onObfsPassword = { hy2ObfsPassword = it },
                    sni = hy2SNI, onSNI = { hy2SNI = it },
                )
                ProxyProtocol.SOCKS5 -> SimpleAuthFields(
                    username = socks5User, onUsername = { socks5User = it },
                    password = socks5Pass, onPassword = { socks5Pass = it },
                    tls = socks5TLS, onTLS = { socks5TLS = it },
                    title = "SOCKS5",
                )
                ProxyProtocol.HTTP -> SimpleAuthFields(
                    username = httpUser, onUsername = { httpUser = it },
                    password = httpPass, onPassword = { httpPass = it },
                    tls = httpTLS, onTLS = { httpTLS = it },
                    title = "HTTP Proxy",
                )
            }

            // TURN overlay (only WG/AWG)
            if (proto.supportsTurn) {
                VpnCard(title = "TURN Overlay", icon = Icons.Filled.CellTower) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Включить TURN", color = TextSecondary)
                        Switch(checked = turnEnabled, onCheckedChange = { turnEnabled = it })
                    }
                    if (turnEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TurnSource.entries.forEach { source ->
                                FilterChip(
                                    selected = turnSource == source,
                                    onClick = { turnSource = source },
                                    label = { Text(source.displayName) },
                                )
                            }
                        }
                        when (turnSource) {
                            TurnSource.VK -> VpnTextField(
                                value = turnVkLink, onValueChange = { turnVkLink = it },
                                placeholder = "https://vk.com/call/join/...", icon = Icons.Filled.Link
                            )
                            TurnSource.MAX -> {
                                VpnTextField(
                                    value = turnMaxTargetUserID, onValueChange = { turnMaxTargetUserID = it },
                                    placeholder = "User ID для звонка", icon = Icons.Filled.Person,
                                    keyboardType = KeyboardType.Number
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("UDP режим", color = TextSecondary)
                            Switch(checked = turnUseUDP, onCheckedChange = { turnUseUDP = it })
                        }
                        VpnTextField(
                            value = turnNumConns, onValueChange = { turnNumConns = it },
                            placeholder = "Connections (4)", icon = Icons.Filled.Tune,
                            keyboardType = KeyboardType.Number
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// Reusable field composables

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProtocolPicker(selected: ProxyProtocol, onSelect: (ProxyProtocol) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProxyProtocol.entries.forEach { proto ->
            FilterChip(
                selected = selected == proto,
                onClick = { onSelect(proto) },
                label = { Text(proto.displayName, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun TransportPicker(selected: ProxyTransport, onSelect: (ProxyTransport) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ProxyTransport.entries.forEach { t ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(t) },
                label = { Text(t.displayName, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun TLSPicker(selected: TLSMode, onSelect: (TLSMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TLSMode.entries.forEach { m ->
            FilterChip(
                selected = selected == m,
                onClick = { onSelect(m) },
                label = { Text(m.displayName, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun WireGuardFields(
    privateKey: String, onPrivateKey: (String) -> Unit,
    publicKey: String, onPublicKey: (String) -> Unit,
    peerAddress: String, onPeerAddress: (String) -> Unit,
    wgAddress: String, onWgAddress: (String) -> Unit,
    dns: String, onDns: (String) -> Unit,
    mtu: String, onMtu: (String) -> Unit,
) {
    VpnCard(title = "WireGuard", icon = Icons.Filled.Lock) {
        VpnTextField(value = privateKey, onValueChange = onPrivateKey, placeholder = "Private Key", icon = Icons.Filled.VpnKey, isSecure = true)
        VpnTextField(value = publicKey, onValueChange = onPublicKey, placeholder = "Server Public Key", icon = Icons.Filled.VpnKey)
        VpnTextField(value = peerAddress, onValueChange = onPeerAddress, placeholder = "Peer Address (optional)", icon = Icons.Filled.Cloud)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            VpnTextField(value = wgAddress, onValueChange = onWgAddress, placeholder = "10.7.0.2/32", icon = Icons.Filled.Router, modifier = Modifier.weight(1f))
            VpnTextField(value = dns, onValueChange = onDns, placeholder = "1.1.1.1", icon = Icons.Filled.Dns, modifier = Modifier.weight(1f))
        }
        VpnTextField(value = mtu, onValueChange = onMtu, placeholder = "MTU (1280)", icon = Icons.Filled.Tune, keyboardType = KeyboardType.Number)
    }
}

@Composable
private fun AmneziaWGFields(
    privateKey: String, onPrivateKey: (String) -> Unit,
    publicKey: String, onPublicKey: (String) -> Unit,
    presharedKey: String, onPresharedKey: (String) -> Unit,
    peerAddress: String, onPeerAddress: (String) -> Unit,
    wgAddress: String, onWgAddress: (String) -> Unit,
    dns: String, onDns: (String) -> Unit,
    mtu: String, onMtu: (String) -> Unit,
    jc: String, onJc: (String) -> Unit,
    jmin: String, onJmin: (String) -> Unit,
    jmax: String, onJmax: (String) -> Unit,
    s1: String, onS1: (String) -> Unit,
    s2: String, onS2: (String) -> Unit,
    h1: String, onH1: (String) -> Unit,
    h2: String, onH2: (String) -> Unit,
    h3: String, onH3: (String) -> Unit,
    h4: String, onH4: (String) -> Unit,
) {
    VpnCard(title = "AmneziaWG", icon = Icons.Filled.Lock) {
        VpnTextField(value = privateKey, onValueChange = onPrivateKey, placeholder = "Private Key", icon = Icons.Filled.VpnKey, isSecure = true)
        VpnTextField(value = publicKey, onValueChange = onPublicKey, placeholder = "Server Public Key", icon = Icons.Filled.VpnKey)
        VpnTextField(value = presharedKey, onValueChange = onPresharedKey, placeholder = "Preshared Key (optional)", icon = Icons.Filled.VpnKey, isSecure = true)
        VpnTextField(value = peerAddress, onValueChange = onPeerAddress, placeholder = "Peer Address (optional)", icon = Icons.Filled.Cloud)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            VpnTextField(value = wgAddress, onValueChange = onWgAddress, placeholder = "10.8.0.2/24", icon = Icons.Filled.Router, modifier = Modifier.weight(1f))
            VpnTextField(value = dns, onValueChange = onDns, placeholder = "1.1.1.1", icon = Icons.Filled.Dns, modifier = Modifier.weight(1f))
        }
        VpnTextField(value = mtu, onValueChange = onMtu, placeholder = "MTU (1280)", icon = Icons.Filled.Tune, keyboardType = KeyboardType.Number)
        Text("Обфускация", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            VpnTextField(value = jc, onValueChange = onJc, placeholder = "Jc", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            VpnTextField(value = jmin, onValueChange = onJmin, placeholder = "Jmin", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            VpnTextField(value = jmax, onValueChange = onJmax, placeholder = "Jmax", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            VpnTextField(value = s1, onValueChange = onS1, placeholder = "S1", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            VpnTextField(value = s2, onValueChange = onS2, placeholder = "S2", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            VpnTextField(value = h1, onValueChange = onH1, placeholder = "H1", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            VpnTextField(value = h2, onValueChange = onH2, placeholder = "H2", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            VpnTextField(value = h3, onValueChange = onH3, placeholder = "H3", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            VpnTextField(value = h4, onValueChange = onH4, placeholder = "H4", icon = Icons.Filled.Tag, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun VLESSFields(
    uuid: String, onUUID: (String) -> Unit,
    flow: String, onFlow: (String) -> Unit,
    transport: ProxyTransport, onTransport: (ProxyTransport) -> Unit,
    tls: TLSMode, onTLS: (TLSMode) -> Unit,
    sni: String, onSNI: (String) -> Unit,
    fingerprint: String, onFingerprint: (String) -> Unit,
    realityPubKey: String, onRealityPubKey: (String) -> Unit,
    realityShortId: String, onRealityShortId: (String) -> Unit,
    wsPath: String, onWsPath: (String) -> Unit,
    grpcService: String, onGrpcService: (String) -> Unit,
) {
    VpnCard(title = "VLESS", icon = Icons.Filled.Security) {
        VpnTextField(value = uuid, onValueChange = onUUID, placeholder = "UUID", icon = Icons.Filled.Fingerprint)
        VpnTextField(value = flow, onValueChange = onFlow, placeholder = "Flow (xtls-rprx-vision)", icon = Icons.Filled.SwapHoriz)
        Text("Transport", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        TransportPicker(selected = transport, onSelect = onTransport)
        Text("TLS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        TLSPicker(selected = tls, onSelect = onTLS)
        VpnTextField(value = sni, onValueChange = onSNI, placeholder = "SNI", icon = Icons.Filled.Language)
        VpnTextField(value = fingerprint, onValueChange = onFingerprint, placeholder = "Fingerprint (chrome)", icon = Icons.Filled.Fingerprint)
        if (tls == TLSMode.REALITY) {
            VpnTextField(value = realityPubKey, onValueChange = onRealityPubKey, placeholder = "Reality Public Key", icon = Icons.Filled.Key)
            VpnTextField(value = realityShortId, onValueChange = onRealityShortId, placeholder = "Reality Short ID", icon = Icons.Filled.Tag)
        }
        if (transport == ProxyTransport.WS) {
            VpnTextField(value = wsPath, onValueChange = onWsPath, placeholder = "WebSocket Path", icon = Icons.Filled.Route)
        }
        if (transport == ProxyTransport.GRPC) {
            VpnTextField(value = grpcService, onValueChange = onGrpcService, placeholder = "gRPC Service Name", icon = Icons.Filled.Route)
        }
    }
}

@Composable
private fun VMessFields(
    uuid: String, onUUID: (String) -> Unit,
    security: String, onSecurity: (String) -> Unit,
    transport: ProxyTransport, onTransport: (ProxyTransport) -> Unit,
    tls: TLSMode, onTLS: (TLSMode) -> Unit,
    sni: String, onSNI: (String) -> Unit,
    wsPath: String, onWsPath: (String) -> Unit,
) {
    VpnCard(title = "VMess", icon = Icons.Filled.Security) {
        VpnTextField(value = uuid, onValueChange = onUUID, placeholder = "UUID", icon = Icons.Filled.Fingerprint)
        VpnTextField(value = security, onValueChange = onSecurity, placeholder = "Security (auto)", icon = Icons.Filled.Shield)
        Text("Transport", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        TransportPicker(selected = transport, onSelect = onTransport)
        Text("TLS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        TLSPicker(selected = tls, onSelect = onTLS)
        VpnTextField(value = sni, onValueChange = onSNI, placeholder = "SNI", icon = Icons.Filled.Language)
        if (transport == ProxyTransport.WS) {
            VpnTextField(value = wsPath, onValueChange = onWsPath, placeholder = "WebSocket Path", icon = Icons.Filled.Route)
        }
    }
}

@Composable
private fun TrojanFields(
    password: String, onPassword: (String) -> Unit,
    transport: ProxyTransport, onTransport: (ProxyTransport) -> Unit,
    tls: TLSMode, onTLS: (TLSMode) -> Unit,
    sni: String, onSNI: (String) -> Unit,
    wsPath: String, onWsPath: (String) -> Unit,
) {
    VpnCard(title = "Trojan", icon = Icons.Filled.Security) {
        VpnTextField(value = password, onValueChange = onPassword, placeholder = "Password", icon = Icons.Filled.Key, isSecure = true)
        Text("Transport", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        TransportPicker(selected = transport, onSelect = onTransport)
        Text("TLS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        TLSPicker(selected = tls, onSelect = onTLS)
        VpnTextField(value = sni, onValueChange = onSNI, placeholder = "SNI", icon = Icons.Filled.Language)
        if (transport == ProxyTransport.WS) {
            VpnTextField(value = wsPath, onValueChange = onWsPath, placeholder = "WebSocket Path", icon = Icons.Filled.Route)
        }
    }
}

@Composable
private fun ShadowsocksFields(
    method: String, onMethod: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
) {
    VpnCard(title = "Shadowsocks", icon = Icons.Filled.Security) {
        VpnTextField(value = method, onValueChange = onMethod, placeholder = "Method (aes-256-gcm)", icon = Icons.Filled.Shield)
        VpnTextField(value = password, onValueChange = onPassword, placeholder = "Password", icon = Icons.Filled.Key, isSecure = true)
    }
}

@Composable
private fun Hysteria2Fields(
    password: String, onPassword: (String) -> Unit,
    obfs: String, onObfs: (String) -> Unit,
    obfsPassword: String, onObfsPassword: (String) -> Unit,
    sni: String, onSNI: (String) -> Unit,
) {
    VpnCard(title = "Hysteria2", icon = Icons.Filled.Security) {
        VpnTextField(value = password, onValueChange = onPassword, placeholder = "Password", icon = Icons.Filled.Key, isSecure = true)
        VpnTextField(value = sni, onValueChange = onSNI, placeholder = "SNI", icon = Icons.Filled.Language)
        VpnTextField(value = obfs, onValueChange = onObfs, placeholder = "Obfs type (salamander)", icon = Icons.Filled.Shuffle)
        VpnTextField(value = obfsPassword, onValueChange = onObfsPassword, placeholder = "Obfs password", icon = Icons.Filled.Key)
    }
}

@Composable
private fun SimpleAuthFields(
    username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    tls: Boolean, onTLS: (Boolean) -> Unit,
    title: String,
) {
    VpnCard(title = title, icon = Icons.Filled.Security) {
        VpnTextField(value = username, onValueChange = onUsername, placeholder = "Username", icon = Icons.Filled.Person)
        VpnTextField(value = password, onValueChange = onPassword, placeholder = "Password", icon = Icons.Filled.Key, isSecure = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TLS", color = TextSecondary)
            Switch(checked = tls, onCheckedChange = onTLS)
        }
    }
}
