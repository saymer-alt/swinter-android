# Swinter VPN (Android)

Multi-protocol VPN client with MAX/VK TURN overlay for censorship circumvention.

## Supported Protocols

- **WireGuard** — standard WG direct or via TURN
- **AmneziaWG** — obfuscated WG with DPI bypass (Jc/Jmin/Jmax/S1/S2/H1-H4)
- **VLESS** — TCP/WebSocket/gRPC/HTTP2/QUIC, TLS/Reality
- **VMess** — TCP/WS/gRPC/HTTP2, TLS/Reality
- **Trojan** — TCP/WS, TLS/Reality
- **Shadowsocks** — AEAD (aes-256-gcm, chacha20-poly1305)
- **Hysteria2** — QUIC-based with obfuscation
- **SOCKS5 / HTTP** — proxy protocols

## TURN Overlay

WireGuard/AmneziaWG traffic can be routed through VK/MAX TURN servers (whitelisted by ISPs):

```
Phone → DTLS/TURN → VK/MAX relay (whitelisted) → VPS → Internet
```

TURN sources:
- **VK** — VK call link
- **MAX** — MAX Messenger SMS auth (with 2FA support)

## Building

### Prerequisites
- Android Studio / JDK 17+
- Android SDK 34, NDK
- Go 1.23+, gomobile

### Build Go library
```bash
cd golib/  # or wherever the Go source is
gomobile bind -target=android -androidapi=24 -o app/libs/Vpnlib.aar ./vpnlib
```

### Build APK
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
app/src/main/java/com/swinter/vpn/
├── data/
│   ├── model/Server.kt        # Server configs (9 protocols)
│   ├── model/AppConfig.kt     # App-wide config with server list
│   ├── MaxAuthService.kt      # MAX SMS + 2FA auth (binary TCP protocol)
│   ├── ConfigParser.kt        # .conf file parser
│   └── VpnConfig.kt           # Legacy VPN config
├── service/
│   └── TurnVpnService.kt      # Android VpnService — TUN, routing, Go lib bridge
├── viewmodel/
│   └── VpnViewModel.kt        # Connection logic, TURN resolution
├── ui/screen/
│   ├── MainScreen.kt          # Home — connect/disconnect, server card
│   ├── ServerListScreen.kt    # Server list — add/edit/delete/favorites
│   ├── ServerEditScreen.kt    # Server form — all 9 protocols
│   ├── ConfigScreen.kt        # Routing rules, DNS, proxy groups
│   ├── SettingsScreen.kt      # MAX account, split tunnel, import/export
│   └── MaxLoginScreen.kt      # MAX SMS login + 2FA
└── widget/
    └── VpnWidgetProvider.kt    # Home screen widget
```

### Go Library (Vpnlib.aar)

Shared with iOS. Source in `golib/vpnlib/`:
- `vpnlib.go` — StartVPN, StartVPNWithConfig, StopVPN, socket FD extraction
- `config.go` — ServerConfig with nested protocol configs
- `tun2socks.go` — gvisor netstack for VLESS/VMess/Trojan/SS
- `proxy_vless.go`, `proxy_vmess.go`, etc. — protocol dialers
- `tunwrap_android.go` — Android TUN device wrapper

## Features

- 4-tab UI: Home / Servers / Config / Settings
- 9 protocol support with dynamic forms
- MAX SMS + 2FA authentication
- TURN overlay for WG/AWG
- Per-app VPN (split tunneling)
- Server latency testing
- .conf import, subscription URLs
- Home screen widget
- QR code scanner for server import
- Export/import config as JSON
