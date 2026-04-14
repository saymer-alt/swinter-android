# Swinter (Android) — VPN over MAX Messenger TURN relays

**A VPN that hides inside MAX Messenger voice calls.** Your WireGuard/AmneziaWG traffic is wrapped in DTLS and relayed through MAX's own TURN servers — the same infrastructure MAX uses for audio calls. Russian ISPs can't block these relays without breaking the messenger itself, so the VPN keeps working when direct WG, Outline, and Shadowsocks all go dark.

> **License:** [The Unlicense](LICENSE) — public domain. Do whatever you want. No attribution, no warranty, no lawyers.

---

## Why MAX-TURN?

Modern RKN-era DPI blocks VPN protocols on signature, blocks datacenter IP ranges wholesale, and blocks anything that looks like a "foreign" endpoint. What it **can't** block is MAX Messenger — that's government-pushed infrastructure with hard whitelisting baked into carrier networks. MAX-TURN exploits exactly that: we log into MAX as a normal user, allocate TURN credentials the same way a MAX voice call does, and then pipe our VPN traffic through those relays.

```
 Phone                 MAX relay (whitelisted)         Your VPS
   │                           │                          │
   │── DTLS/TURN ──────────────▶                          │
   │   (looks like a MAX call) │── TURN-relayed UDP ─────▶│
   │                           │                          │──▶ Internet
```

From the ISP's perspective the phone is making a MAX voice call. From the VPS's perspective a WireGuard peer is connecting from a MAX IP. Neither side sees anything unusual.

### How we get TURN credentials

`MaxAuthService.kt` speaks MAX's native protocol — a 10-byte header + MessagePack frames over TLS to `api.oneme.ru:443`, with `deviceType=ANDROID` (the `WEB` value blocks SMS auth). After SMS + optional 2FA you get a persistent auth token. The shared Go library (`vpnlib/max_creds.go`) then uses that token to initiate a call (opcode 78), captures the TURN credentials out of the response, and hangs up. Brief phantom ring on the target user, nothing more.

`vpnlib/turn.go` wraps everything in DTLS (via `pion/dtls` + `pion/turn`) and exposes a local `net.Conn` that WireGuard-go treats as its UDP transport. Credentials are short-lived, so the lib re-auths on demand.

> **Note:** the Android build currently ships with MAX SMS + binary-TCP auth only. The MAX Call WebRTC path (which the iOS version has via `MaxCallService.swift`) is not yet wired into the Android client — SMS auth is the single credential source for now.

---

## TURN credential sources

| Source | What it is | Status |
|---|---|---|
| **MAX** | MAX Messenger SMS + 2FA auth, then binary TCP call allocation. **Primary.** | Production |
| VK | Legacy: TURN credentials scraped from a VK call link. Kept around as a fallback. | Working, deprecated |
| MAX Call WebRTC | DataChannel-based TURN via MAX WebRTC signaling. | iOS only (planned for Android) |

---

## Bonus: also a full multi-protocol VPN client

Because the TUN side is protocol-agnostic, Swinter is also a complete VPN app on its own:

- **WireGuard** — standard WG, direct or over MAX-TURN
- **AmneziaWG** — obfuscated WG with DPI bypass (`Jc/Jmin/Jmax/S1/S2/H1-H4`)
- **VLESS** — TCP/WebSocket/gRPC/HTTP2/QUIC, TLS/Reality
- **VMess** — TCP/WS/gRPC/HTTP2, TLS/Reality
- **Trojan** — TCP/WS, TLS/Reality
- **Shadowsocks** — AEAD (`aes-256-gcm`, `chacha20-poly1305`)
- **Hysteria2** — QUIC-based with obfuscation
- **SOCKS5 / HTTP** — proxy protocols

Use it as a drop-in replacement for v2rayNG / Amnezia / Outline and ignore MAX-TURN entirely if you want.

---

## Building

### Prerequisites
- Android Studio / JDK 17+
- Android SDK 34, NDK
- Go 1.23+ with `gomobile` (`go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init`)

### Build the Go library
```bash
cd golib/   # or wherever the Go source sits
gomobile bind -target=android -androidapi=24 -o app/libs/Vpnlib.aar ./vpnlib
```

### Build the APK
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Architecture

```
app/src/main/java/com/swinter/vpn/
├── data/
│   ├── model/Server.kt         # Server configs (9 protocols)
│   ├── model/AppConfig.kt      # App-wide config with server list
│   ├── MaxAuthService.kt       # ★ MAX SMS + 2FA auth (binary TCP protocol)
│   ├── ConfigParser.kt         # .conf file parser
│   └── VpnConfig.kt            # Legacy VPN config
├── service/
│   └── TurnVpnService.kt       # Android VpnService — TUN, routing, Go lib bridge
├── viewmodel/
│   └── VpnViewModel.kt         # Connection logic, TURN resolution
├── ui/screen/
│   ├── MainScreen.kt           # Home — connect/disconnect, server card
│   ├── ServerListScreen.kt     # Server list — add/edit/delete/favorites
│   ├── ServerEditScreen.kt     # Server form — all 9 protocols
│   ├── ConfigScreen.kt         # Routing rules, DNS, proxy groups
│   ├── SettingsScreen.kt       # MAX account, split tunnel, import/export
│   └── MaxLoginScreen.kt       # ★ MAX SMS login + 2FA
└── widget/
    └── VpnWidgetProvider.kt    # Home screen widget
```

★ = MAX-TURN core

### Go library (`Vpnlib.aar`)

Shared with iOS. Source in `golib/vpnlib/`:
- `max_creds.go` — ★ MAX TURN credential extraction
- `turn.go` — ★ DTLS + TURN relay wrapper
- `vpnlib.go` — `StartVPN`, `StartVPNWithConfig`, `StopVPN`, socket FD extraction
- `config.go` — `ServerConfig` with nested protocol configs
- `tun2socks.go` — gvisor netstack for VLESS/VMess/Trojan/SS
- `proxy_vless.go`, `proxy_vmess.go`, etc.
- `tunwrap_android.go` — Android TUN device wrapper

---

## Features

- **MAX-TURN overlay** — WG/AWG inside MAX voice-call relays
- **MAX SMS + 2FA authentication** with persistent token storage
- 4-tab UI: Home / Servers / Config / Settings
- 9 protocol support with dynamic forms
- Per-app VPN (split tunneling)
- Server latency testing
- `.conf` import, subscription URLs, QR scanner
- JSON export/import
- Home-screen widget

---

## Related

- iOS version: [`somewhat-industries/swinter-ios`](https://github.com/somewhat-industries/swinter-ios)
- MAX binary TCP protocol: `api.oneme.ru:443`, standard TLS, `deviceType=ANDROID` for SMS auth
- DTLS/TURN stack: [`pion/dtls`](https://github.com/pion/dtls), [`pion/turn`](https://github.com/pion/turn)

No warranty. No support. Use at your own risk. If you get your MAX account rate-limited for making too many phantom calls, that's on you.
