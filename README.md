# 🔦 Rushlight

> *A flame that needs no wire.*

**Offline field intelligence for Android.** AI assistant, encrypted P2P comms, shared map annotations, offline Wikipedia, morse code, and navigation — all running locally on your phone with zero internet required.

[![License](https://img.shields.io/badge/license-AGPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0+-green.svg)](https://developer.android.com/about/versions/oreo)
[![Status](https://img.shields.io/badge/status-v1.3-brightgreen.svg)](https://github.com/liekzejaws/rushlight/releases)
[![Fork of OsmAnd](https://img.shields.io/badge/fork%20of-OsmAnd-lightgrey.svg)](https://github.com/osmandapp/OsmAnd)

Built for journalists, activists, humanitarian workers, and anyone who needs to operate in low-connectivity or high-surveillance environments. **No cloud. No accounts. No telemetry. Everything stays on your device.**

---

## Why This Exists

In late 2025, Iran's government shut down internet access during political unrest — jamming Starlink, blocking VPNs, cutting data. Millions of people had smartphones in their pockets that were suddenly useless. They couldn't navigate to safety. They couldn't access emergency information. They couldn't coordinate.

This pattern repeats. Myanmar. Russia. China. Venezuela. 375 million people face active internet censorship. Billions more face connectivity failures during crises.

Existing tools fail at exactly the moment they're needed most. Cloud-based AI assistants are blocked or surveilled. Offline maps work, but only if you know precise street names — impossible when you're under stress, in an unfamiliar area, or operating in a language that isn't yours.

Rushlight is the layer that keeps your phone useful when the network is hostile.

---

## 📸 Demo

[![Rushlight - Offline AI for Activists](https://img.youtube.com/vi/-ursT48AsOs/hqdefault.jpg)](https://youtube.com/shorts/-ursT48AsOs)

> *Click to watch the demo video*

<p align="center">
  <img src="docs/screenshots/rushlight-classic-theme.png" width="22%" alt="Security & Themes"/>
  <img src="docs/screenshots/rushlight-screenshot-03-tools.png" width="22%" alt="Theme Picker"/>
  <img src="docs/screenshots/rushlight-screenshot-04-aichat.png" width="22%" alt="AI Assistant"/>
  <img src="docs/screenshots/rushlight-screenshot-05-morse.png" width="22%" alt="Morse Code"/>
</p>

---

## ✨ What It Does

| Feature | Description |
|---------|-------------|
| 📌 **FieldNotes** | Shared map annotations that sync device-to-device — like Dark Souls messages for the real world. Pin notes to GPS coordinates, sign them with ECDSA P-256, and let them propagate through the mesh. The on-device AI can query them: *"Is there water nearby?"* |
| 🤖 **Offline AI** | On-device LLM (llama.cpp) with RAG pipeline — answers questions from offline Wikipedia, 26 field guides, and nearby FieldNotes. No internet. Ever. |
| 📡 **P2P Sharing** | BLE discovery + WiFi Direct transfer. Share maps, AI models, FieldNotes, and data device-to-device. ECDH + ChaCha20-Poly1305 encrypted. |
| 🗺️ **Navigation** | Full OsmAnd mapping stack — offline vector maps, turn-by-turn routing, POI search. |
| 📖 **Offline Wikipedia** | ZIM file reader with full-text search. The AI can cite Wikipedia in its responses. |
| 📚 **Field Guides** | 26 bundled guides across 11 categories: First Aid, Water, Fire, Shelter, Navigation, Signaling, Food, Security, Engineering, Automotive, Electrical. |
| 💡 **Morse Code** | Flashlight and audio transmission, camera/mic reception. AI-powered error correction. No accessories required. |
| 🛡️ **Security** | Duress PIN, panic wipe, stealth mode (hidden from launcher, accessible via dialer code), AES-256-GCM encrypted chat storage, ECDSA-signed FieldNotes. |
| 🎨 **Pip-Boy Skin** | Retro-futuristic green CRT terminal aesthetic. Because why not. |

---

## 📌 FieldNotes — Shared Map Intelligence

FieldNotes is Rushlight's flagship feature: a **decentralized, cryptographically signed annotation system** for offline maps.

Think of it as Dark Souls-style player messages, but for real-world field intelligence. Someone who passed through a checkpoint yesterday leaves information that reaches you today — no internet at any step.

**How it works:**
1. Long-press the map to drop a note at any GPS coordinate
2. Choose a category (water, shelter, hazard, cache, route, medical, signal, intel)
3. Your note is signed with your device's ECDSA P-256 keypair and stored locally
4. When another Rushlight user comes within BLE/WiFi range, notes gossip between devices automatically
5. Notes accumulate confirmations as they propagate and can be upvoted/downvoted for trust scoring
6. The on-device AI can query notes spatially — ask *"what's nearby?"* and it reads the collective field intelligence

**Seeing it in action:**

> *"What's the safest route to the crossing?"*
> → AI pulls FieldNotes within 10km: "Highway 7 flagged checkpoint-heavy, 3 confirmations in last 48h" + "forest track passable on foot, 2 users confirmed yesterday"
> → Route recommendation based on current ground conditions, not pre-war maps

> *"Is there water nearby?"*
> → Water source annotation 1.2km northeast, confirmed clean by 2 users 3 days ago
> → *"Confirmed clean water 1.2km northeast — last verified 3 days ago"*

> *"The bridge on the north road is washed out."*
> → *"Got it — should I log that as a FieldNote so others know?"*

This is RAG with live crowd-sourced hyperlocal data instead of a static document corpus. **No other offline AI assistant has this.**

**Why it matters:**

- **No infrastructure needed.** Notes sync peer-to-peer. No servers, no internet, no accounts.
- **Tamper-evident.** Every note carries a cryptographic signature. Recipients can verify the note hasn't been modified in transit.
- **Privacy-preserving.** Author identity is a public key hash — anonymous but unique and verifiable. Panic wipe generates a fresh identity.
- **Self-cleaning.** Notes expire after their TTL (default: 1 week). Stale intelligence is automatically purged.
- **AI-integrated.** The LLM can both read and write FieldNotes — ask it a question about your surroundings and it queries the annotation database. Tell it about a hazard and it creates a note for others.

**Technical details:**

| Component | Implementation |
|-----------|---------------|
| Storage | SQLite with content-addressed IDs (SHA-256 of core fields) |
| Signing | ECDSA P-256, Android Keystore-backed keypairs |
| Sync | JSON gossip over BLE + WiFi Direct mesh |
| Spatial queries | Bounding-box SQL pre-filter + Haversine post-filter |
| Trust | Upvote/downvote scoring, confirmation counting across peers |
| LLM tools | `query_fieldnotes` and `create_fieldnote` wired into on-device AI |
| Categories | 8 types with distinct icons and colors on the map overlay |
| Expiry | TTL-based cleanup (CoT stale-event pattern from ATAK) |

---

## 🎯 Who It's For

**Primary users — high-threat environments:**
- **Activists** in Iran, China, Myanmar, Russia, Venezuela facing internet shutdowns and surveillance
- **Journalists** covering conflict zones or authoritarian regimes
- **Human rights defenders** operating under targeted restrictions
- **Humanitarian workers** in disaster and infrastructure-degraded areas

**Secondary users:**
- Preppers and survivalists who want genuine offline capability
- Rural communities with limited connectivity
- Emergency responders when networks fail
- Anyone who thinks their phone should work without a data connection

---

## 🏗️ Architecture

```
User Query
    ↓
QueryClassifier (11 query types)
    ↓ (parallel search)
    ├── ZimSearchAdapter       → Offline Wikipedia
    ├── GuideSearchAdapter     → 26 bundled field guides
    ├── MapDataAdapter         → Nearby map POIs
    ├── PlaceSearch            → Named location lookup
    └── FieldNotes tools       → Nearby annotations from P2P mesh
    ↓
PromptBuilder (adaptive context blending)
    ↓
LlmManager → llama.cpp JNI → Streaming response
                                    ↓ (optional)
                            create_fieldnote → map pin for others

FieldNotes Sync:
Device A ──BLE/WiFi──→ Device B ──BLE/WiFi──→ Device C
   ↓ sign (ECDSA P-256)    ↓ verify + store       ↓ gossip forward
   ↓ store locally          ↓ increment confirms   ↓ vote up/down
```

**Core stack:**
- **OsmAnd** (Apache 2.0) — maps and navigation foundation
- **llama.cpp** — on-device LLM inference via JNI. Supports any GGUF model.
- **Kiwix ZIM** — offline Wikipedia reader
- **SQLCipher** — encrypted local storage
- **BLE + WiFi Direct** — zero-infrastructure P2P and FieldNotes gossip
- **ECDSA P-256** — FieldNote signing and author verification

---

## 🔒 Security Model

- **AES-256-GCM** encrypted chat storage via SQLCipher (Android Keystore-backed keys)
- **ECDH P-256 + ChaCha20-Poly1305** for all P2P transfers
- **ECDSA P-256** signing on all FieldNotes — tamper-evident, verifiable authorship
- **Duress PIN** — triggers configurable data wipe under coercion
- **Panic Wipe** — one-tap destruction of all sensitive data + signing keypair (fresh anonymous identity)
- **Stealth Mode** — hidden from launcher, accessible only via dialer code (`*#73784#`)
- **Zero telemetry** — no analytics, no crash reporting, no network callbacks
- **No accounts** — no registration, no cloud sync, nothing

Full threat model (user personas, attack scenarios, mitigations): [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md)

---

## 🧩 Planned Modules

**Passage** — for refugees, border crossers, and people navigating hostile legal environments. Adds: legal rights by country (offline), emergency phrases in 40+ languages, shelter and embassy directories, checkpoint overlays from the FieldNotes mesh, dead man's switch check-in. Built on top of Rushlight's existing offline maps + P2P + LLM — no second install.

**Medic** — optional clinical knowledge pack for field medical workers. Adds deep drug dosing, WHO triage protocols, field surgical guides, and MSF/ICRC treatment reference to the existing first aid content. +300-800MB optional download. Targets humanitarian medical teams operating without reliable infrastructure.

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

## 🤝 Contributing

Rushlight is a solo project in active development. The core stack is working. What's needed now:

**Testing (Good First Contributions)**
- [ ] **FieldNotes: multi-device sync** — run two devices, drop a note on one, confirm it appears on the other within BLE range. Document any failures.
- [ ] **FieldNotes: gossip propagation** — 3 devices in a chain (A→B→C, B out of range of A). Confirm A's note reaches C.
- [ ] **FieldNotes: signature verification** — manually tamper with a note in the SQLite DB and confirm it's rejected on the receiving device.
- [ ] **LLM inference benchmarks** — run the standard query suite on your device, report latency + RAM. Especially wanted: mid-range devices (Snapdragon 700 series, 4-6GB RAM).
- [ ] **Low-end device testing** — Android 8-10, 2-3GB RAM. Does it build? Does the AI run? What breaks?
- [ ] **P2P stress test** — 5+ devices, rapid FieldNote creation. Does gossip converge? Any note loss?
- [ ] **Morse code** — test audio receive on different microphones. What's the reliable range?

**Code Contributions**
- [ ] **FieldNotes categories** — what annotation types matter most in real field scenarios? Should we add custom categories?
- [ ] **Performance tuning** — LLM inference on constrained hardware. GGUF quantization sweet spots per device tier.
- [ ] **UI polish** — the Pip-Boy skin needs love
- [ ] **Accessibility** — screen readers, font scaling, high-contrast
- [ ] **Threat model review** — review the FieldNotes crypto signing and P2P trust model. [THREAT-MODEL.md](docs/THREAT-MODEL.md)
- [ ] **Meshtastic integration** — LoRa bridge for FieldNotes propagation over miles (hardware required: T-Beam ~$45)

If you work in humanitarian tech, digital rights, mesh networking, or just think this is a cool problem — open an issue or start a discussion. No formal process, just talk.

See [CONTRIBUTING.md](CONTRIBUTING.md) for full details. See [ROADMAP.md](ROADMAP.md) for where this is going.

---

## 📍 Roadmap

Current: **v1.3** — FieldNotes + Offline AI + P2P + Morse + Security features complete.

Upcoming milestones: [ROADMAP.md](ROADMAP.md)

---

## 📄 License

Rushlight additions are licensed under **AGPLv3**. The OsmAnd base is Apache 2.0. See [LICENSE](LICENSE) for details.

---

## 🙏 Built On

- [OsmAnd](https://osmand.net/) ([GitHub](https://github.com/osmandapp/OsmAnd)) — the offline maps foundation
- [llama.cpp](https://github.com/ggerganov/llama.cpp) — on-device LLM inference
- [Kiwix](https://kiwix.org/) — offline Wikipedia
- [Briar](https://briarproject.org/) — P2P architecture inspiration

---

*Rushlight is supported by grant applications to [NLnet Foundation](https://nlnet.nl/), [Open Technology Fund](https://www.opentech.fund/), and [Mozilla Foundation](https://foundation.mozilla.org/). If you use this in the field, I'd love to hear about it.*
