# 🔦 Rushlight

> **Offline field intelligence for Android.** AI assistant, encrypted P2P comms, offline Wikipedia, morse code, and navigation — all running locally on your phone with zero internet required.

[![License](https://img.shields.io/badge/license-AGPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0+-green.svg)](https://developer.android.com/about/versions/oreo)
[![Status](https://img.shields.io/badge/status-v0.4%20prototype-orange.svg)](https://github.com/liekzejaws/rushlight/releases)
[![Fork of OsmAnd](https://img.shields.io/badge/fork%20of-OsmAnd-lightgrey.svg)](https://github.com/osmandapp/OsmAnd)

Built for journalists, activists, humanitarian workers, and anyone who needs to operate in low-connectivity or high-surveillance environments. **No cloud. No accounts. No telemetry. Everything stays on your device.**

---

## 📸 Screenshots

<!-- TODO: Add screenshots of Pip-Boy skin, AI chat, P2P, morse -->
> *Demo screenshots coming soon. Build instructions below if you want to try it now.*

---

## ✨ What It Does

| Feature | Description |
|---------|-------------|
| 🤖 **Offline AI** | On-device LLM (llama.cpp) with RAG pipeline — asks questions, gets answers from offline Wikipedia + 26 field guides. No internet ever. |
| 📡 **P2P Sharing** | BLE discovery + WiFi Direct file transfer. Share maps, AI models, and data device-to-device. ECDH + ChaCha20-Poly1305 encrypted. |
| 🗺️ **Navigation** | Full OsmAnd mapping stack — offline vector maps, turn-by-turn routing, POI search. |
| 📖 **Offline Wikipedia** | ZIM file reader with full-text search. The AI can cite Wikipedia in its responses. |
| 📚 **Field Guides** | 26 bundled guides across 11 categories: First Aid, Water, Fire, Shelter, Navigation, Signaling, Food, Security, Engineering, Automotive, Electrical. |
| 💡 **Morse Code** | Flashlight and audio transmission, camera/mic reception. AI-powered error correction. No accessories required. |
| 🛡️ **Security** | Duress PIN, panic wipe, stealth mode (hidden from launcher, accessible via dialer code), AES-256-GCM encrypted chat storage. |
| 🎨 **Pip-Boy Skin** | Retro-futuristic green CRT terminal aesthetic. Because why not. |

---

## 🎯 Who It's For

- **Journalists** covering conflict zones or authoritarian regimes
- **Activists** operating under internet surveillance or shutdowns
- **Humanitarian workers** in disaster or infrastructure-degraded areas
- **Preppers / survivalists** who want genuine offline capability
- **Anyone** who thinks their phone should work without a data connection

---

## 🏗️ Architecture

```
User Query
    ↓
QueryClassifier (11 query types)
    ↓ (parallel)
    ├── ZimSearchAdapter     → Offline Wikipedia
    ├── GuideSearchAdapter   → 26 bundled field guides
    ├── MapDataAdapter       → Nearby map POIs
    └── PlaceSearch          → Named location lookup
    ↓
PromptBuilder (adaptive context blending)
    ↓
LlmManager → llama.cpp JNI → Streaming response
```

**Core stack:**
- **OsmAnd** (Apache 2.0) — maps and navigation foundation
- **llama.cpp** — on-device LLM inference via JNI. Supports any GGUF model.
- **Kiwix ZIM** — offline Wikipedia reader
- **SQLCipher** — encrypted local storage
- **BLE + WiFi Direct** — zero-infrastructure P2P

---

## 🚀 Building

### Prerequisites
- Android Studio (Arctic Fox+) with Android SDK
- JDK 17 (bundled with Android Studio)
- Android 8.0+ device or emulator (AI requires Android 11+)
- ~16GB disk space

### Build
```bash
git clone https://github.com/liekzejaws/rushlight.git
cd rushlight

export JAVA_HOME="/path/to/Android Studio/jbr"

./gradlew assembleNightlyFreeLegacyArm64Debug
```

APK output:
```
OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk
```

### Install
```bash
adb install -r <path-to-apk>
adb shell am start -n io.rushlight.app/net.osmand.plus.activities.MapActivity
```

### First Run
1. App launches with Rushlight onboarding
2. Drop a GGUF model (e.g. Phi-3-mini Q4_K_M, ~2.3GB) into the models directory
3. Download a Wikipedia ZIM from [Kiwix](https://wiki.kiwix.org/wiki/Content)
4. Download offline maps via the built-in map manager
5. Ask it anything

---

## 🔒 Security Model

- **AES-256-GCM** encrypted chat storage via SQLCipher (Android Keystore-backed keys)
- **ECDH P-256 + ChaCha20-Poly1305** for all P2P transfers
- **Duress PIN** — triggers configurable data wipe under coercion
- **Panic Wipe** — one-tap destruction of all sensitive data
- **Stealth Mode** — hidden from launcher, accessible only via dialer code (`*#73784#`)
- **Zero telemetry** — no analytics, no crash reporting, no network callbacks
- **No accounts** — no registration, no cloud sync, nothing

---

## 🤝 Contributing

Rushlight is a solo project in active development. The core stack is working — what's needed now:

- **Testing** on diverse Android hardware (especially mid-range and older devices)
- **Performance tuning** for LLM inference on constrained hardware
- **P2P stress testing** across multiple devices
- **Knowledge pack curation** — what field guide content is most valuable?
- **UI polish** — the Pip-Boy skin needs love
- **Accessibility** — screen readers, font scaling, high-contrast

If you work in humanitarian tech, digital rights, or just think this is a cool problem — open an issue or PR. No formal process yet, just talk to me.

**Good first issues:** Coming soon — check [Issues](https://github.com/liekzejaws/rushlight/issues).

---

## 📄 License

Rushlight additions are licensed under **AGPLv3**. The OsmAnd base is Apache 2.0. See [LICENSE](LICENSE) for details.

---

## 🙏 Built On

- [OsmAnd](https://osmand.net/) — the offline maps foundation
- [llama.cpp](https://github.com/ggerganov/llama.cpp) — on-device LLM inference
- [Kiwix](https://kiwix.org/) — offline Wikipedia
- [Briar](https://briarproject.org/) — P2P architecture inspiration

---

*Rushlight is supported by grant applications to [NLnet Foundation](https://nlnet.nl/), [Open Technology Fund](https://www.opentech.fund/), and [Mozilla Foundation](https://foundation.mozilla.org/). If you use this in the field, I'd love to hear about it.*
