# 🚨 RescueNet — Saving Lives In Disasters

> Offline-first Android emergency alert app that creates a self-healing Bluetooth/WiFi mesh network for disaster scenarios. Send SOS, medical, and rescue signals without internet or cell towers — messages hop peer-to-peer with GPS coordinates, falling back to direct SMS when network is available.

---

## The Problem

When disaster strikes, the first thing that fails is the cell tower. RescueNet solves this by turning every Android phone into a mesh node using its existing Bluetooth and WiFi radios — no SIM, no internet, no central server needed.

---

## Key Features

- 🔴 **One-tap SOS / Medical / Trapped / Safe alerts** with GPS coordinates
- 📡 **Mesh networking** — messages hop up to 5 devices away automatically
- 🗺️ **Live offline map** — every peer's position and status via OSMDroid
- 📱 **SMS fallback** — sends directly to emergency contact when network detected
- 📦 **Offline queue** — alerts saved and auto-sent when a peer comes in range
- 🔄 **Auto-reconnect** — mesh restarts every 10 seconds if connection drops
- 🏷️ **Hop counter** — each message shows how many devices it travelled through

---

## How It Works

```
[Victim Phone]
      │  Bluetooth / WiFi (Nearby Connections)
      ▼
[Phone A] ──► [Phone B] ──► [Rescue Coordinator]
                                    │
                          Network available?
                                    │
                            SMS to emergency contact
```

1. Every phone advertises itself and scans for peers simultaneously
2. On alert — app checks for network first → **SMS if available**, mesh if not
3. Mesh peers relay the message onward, incrementing a hop counter
4. At **hop 5** the message stops — prevents infinite relay loops
5. UUID deduplication ensures each message is displayed exactly once

---

## Tech Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 11 |
| Min / Target SDK | Android 7.0 / Android 14 | API 24 / 34 |
| Mesh Networking | Google Nearby Connections | 19.1.0 |
| GPS | FusedLocationProviderClient | 21.2.0 |
| Offline Map | OSMDroid | 6.1.18 |
| Serialisation | Gson | 2.10.1 |
| UI | AndroidX + Material Components | 1.11.0 |
| SMS / Network | SmsManager + ConnectivityManager | Built-in |

---

## Architecture

Single-Activity app with 7 focused classes:

```
MainActivity
├── MeshManager       ← Nearby Connections, peer discovery, relay + queue
├── LocationTracker   ← FusedLocationProvider, GPS polling every 10s
├── NetworkSmsHelper  ← Network check → SMS dispatch
├── Message           ← Data model (type, GPS, sender, hopCount, UUID)
├── UserNode          ← Peer state (status, role, last-seen)
└── MessageAdapter    ← RecyclerView, colour-coded alert cards
```

---

## Setup

```bash
git clone https://github.com/yourusername/RescueNet.git
# Open in Android Studio → Sync Gradle → Run on physical device
```

> ⚠️ Nearby Connections requires a **physical device** — mesh will not work on emulators.

To set your emergency SMS contact, update `NetworkSmsHelper.java`:
```java
public static final String EMERGENCY_NUMBER = "+XX-XXXXXXXXXX";
```

---

## Alert Types

| Alert | Colour | Meaning |
|---|---|---|
| 🔴 SOS | Red | Life-threatening emergency |
| 🟡 Medical | Yellow | Medical assistance needed |
| 🟠 Trapped | Orange | Physically stuck, send rescue |
| 🟢 Safe | Green | User is unharmed |
| 🔵 Govt Alert | Blue | Official authority broadcast |

---

## Permissions Required

`BLUETOOTH`, `BLUETOOTH_SCAN/CONNECT/ADVERTISE`, `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`, `SEND_SMS`, `ACCESS_NETWORK_STATE`, `INTERNET`

---

## Limitations

- Hop range ~50–100m per device; total reach depends on peer density
- Requires Google Play Services (no GMS devices not supported)
- Continuous BT + WiFi scanning increases battery usage
- Messages transmitted as plain JSON (no encryption yet)

---

## Roadmap

- [ ] End-to-end encryption
- [ ] Role-based alert filtering
- [ ] Pre-cached offline map tiles for known disaster zones
- [ ] Message delivery acknowledgement
- [ ] Battery saver scan mode

---

<div align="center">
  <em>Built for the moments when nothing else works.</em>
</div>
