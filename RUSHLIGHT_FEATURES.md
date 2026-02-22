# Rushlight — Offline Field Intelligence Platform

## Project Overview

Rushlight is an offline-first field intelligence application for Android, built as a hardened fork of [OsmAnd](https://osmand.net/) (Apache 2.0 licensed). It provides AI-powered assistance, encrypted communications, and practical knowledge tools — all functioning without any internet connection.

**Target users:** Journalists, activists, humanitarian workers, and anyone operating in low-connectivity or high-surveillance environments.

**Package:** `io.rushlight.app`
**Min SDK:** Android 8.0 (API 26), AI features require Android 11 (API 30)
**Repository:** https://github.com/liekzejaws/rushlight

---

## Technical Architecture

### Core Stack
- **Platform:** Android (Java), OsmAnd fork using `nightlyFree` build flavor
- **On-Device LLM:** [llama.cpp](https://github.com/ggerganov/llama.cpp) via JNI through the Ai-Core AAR. Supports any GGUF model (Llama, Phi, Qwen, DeepSeek, Mistral, etc.)
- **RAG Pipeline:** Retrieval-Augmented Generation combining offline Wikipedia, 26 bundled field guides, and local map POI data to ground LLM responses in factual context
- **P2P Communications:** BLE advertising for peer discovery + WiFi Direct for bulk data transfer, encrypted end-to-end
- **Maps & Navigation:** Full OsmAnd mapping stack with offline vector maps, routing, and POI search

### RAG Pipeline Detail
```
User Query
    ↓
QueryClassifier (11 query types, keyword + regex classification)
    ↓ (parallel search)
    ├── ZimSearchAdapter → Offline Wikipedia (ZIM format)
    ├── GuideSearchAdapter → 26 bundled field guides (JSON)
    ├── MapDataAdapter → Nearby POIs from map data
    └── PlaceSearch → Named location lookup
    ↓
PromptBuilder (adaptive context blending, topic-specific templates)
    ↓
LlmManager → Native llama.cpp inference → Streaming response
```

### Key Source Paths
| Component | Path |
|-----------|------|
| FieldNotes | `OsmAnd/src/net/osmand/plus/fieldnotes/` |
| LLM + Tools | `OsmAnd/src/net/osmand/plus/ai/` |
| RAG Pipeline | `OsmAnd/src/net/osmand/plus/ai/rag/` |
| P2P Transport | `OsmAnd/src/net/osmand/plus/plugins/p2pshare/` |
| Security | `OsmAnd/src/net/osmand/plus/security/` |
| UI Framework | `OsmAnd/src/net/osmand/plus/lampp/` |
| Morse Code | `OsmAnd/src/net/osmand/plus/morse/` |
| Field Guides | `OsmAnd/assets/guides/` |

---

## Security Model

Rushlight implements defense-in-depth security designed for high-risk environments:

### Data Protection
- **Chat Storage:** AES-256-GCM encryption via SQLCipher. Per-conversation encryption with Android Keystore-backed keys
- **P2P Encryption:** ECDH P-256 key exchange + ChaCha20-Poly1305 authenticated encryption for all peer-to-peer transfers
- **FieldNote Signing:** ECDSA P-256 signatures on all FieldNotes. Android Keystore-backed device keypairs. Signatures cover all immutable content fields, enabling tamper detection without any central authority
- **At Rest:** All sensitive data encrypted on-device. No plaintext storage of conversations or keys

### Threat Mitigation
- **Duress PIN:** Secondary PIN that triggers configurable data wipe (chat only / chat+models / everything) when entered under coercion
- **Panic Wipe:** One-tap emergency destruction of all sensitive data
- **Stealth Mode:** Hides app from launcher; accessible only via dialer code (e.g., `*#73784#`)
- **Biometric Lock:** Optional fingerprint/face authentication to access the panel
- **PIN Lockout:** Progressive delay after failed attempts (2s, 4s, 8s, 16s)

### Privacy Guarantees
- **Zero telemetry:** No analytics, no crash reporting, no network callbacks
- **Fully offline:** All features work without internet. No data ever leaves the device unless explicitly sent via P2P
- **No accounts:** No user registration, no cloud sync, no server dependency

---

## Feature Inventory

### 1. FieldNotes — Decentralized Map Annotations
Shared geo-pinned annotations that sync between devices via BLE/WiFi Direct mesh. Inspired by ATAK's Cursor on Target (CoT) event model and Dark Souls-style player messages.

**Data Model:** Each FieldNote is a geo-pinned annotation with a content-addressed ID (SHA-256 of core fields), enabling deduplication when the same note arrives via multiple P2P paths. Notes carry a TTL for automatic expiry — stale intelligence is purged on app startup.

**Categories:** 8 annotation types optimized for civilian field use: Water Source, Shelter, Hazard, Supply Cache, Route Info, Medical, Signal/Comms, Intelligence. Each has a distinct icon and color on the map overlay.

**Crypto Signing (ECDSA P-256):** Every FieldNote is signed with the creating device's ECDSA P-256 keypair (Android Keystore-backed). The signature covers all immutable content fields. Recipients verify signatures on receipt — tamper-evident and verifiable without any central authority. Author identity is derived from the public key hash: anonymous, unique, and cryptographically bound to the signing key.

**P2P Gossip Sync:** Notes propagate through the Rushlight mesh via a JSON gossip protocol. When a peer receives a new note, it stores it locally, increments confirmations, and re-broadcasts to its own connected peers. Existing notes arriving again just increment the confirmation counter. Vote packets also propagate via the mesh.

**Voting/Trust:** Users can upvote or downvote FieldNotes. Score affects map display: low-scored notes fade out, notes at score -7 or below are hidden entirely. This is crowd-sourced content moderation without any central server.

**LLM Tool Integration:** The on-device AI has two FieldNote tools: `query_fieldnotes` (spatial search by radius, category filter, result limit) and `create_fieldnote` (the AI can create notes from conversation context). Ask *"is there water nearby?"* and the AI queries the annotation database. Tell it *"the bridge is washed out"* and it creates a note for other users.

**Key Source Paths:**
| Component | Path |
|-----------|------|
| Data Model | `OsmAnd/src/net/osmand/plus/fieldnotes/FieldNote.java` |
| Manager | `OsmAnd/src/net/osmand/plus/fieldnotes/FieldNotesManager.java` |
| Crypto Signer | `OsmAnd/src/net/osmand/plus/fieldnotes/FieldNoteSigner.java` |
| DB Helper | `OsmAnd/src/net/osmand/plus/fieldnotes/FieldNotesDbHelper.java` |
| Map Layer | `OsmAnd/src/net/osmand/plus/fieldnotes/FieldNotesLayer.java` |
| View Dialog | `OsmAnd/src/net/osmand/plus/fieldnotes/ViewFieldNoteDialog.java` |
| LLM Tools | `OsmAnd/src/net/osmand/plus/ai/ToolDispatcher.java` |

### 2. Offline AI Assistant
On-device LLM inference with RAG-augmented responses. The AI draws from offline Wikipedia, 26 bundled field guides covering survival, engineering, automotive, and electrical topics, local map data, and nearby FieldNotes. Responses include source citations. Multiple conversation threads with encrypted persistence.

### 3. Offline Wikipedia
ZIM file reader with full-text search. Browse and search Wikipedia articles entirely offline. Integrated into the RAG pipeline so the AI can cite Wikipedia in responses.

### 4. P2P Content Sharing
Device-to-device transfer of maps, AI models, Wikipedia databases, and custom content. Uses BLE for peer discovery and WiFi Direct for high-speed bulk transfer. All transfers encrypted with ECDH + ChaCha20-Poly1305. Zero infrastructure required — no servers, no internet, no accounts.

### 5. Morse Code
Flashlight and audio transmission with camera and microphone reception. Supports AI-powered error correction for garbled transmissions. Optional GPS coordinate appending. Configurable speed (WPM) and audio frequency.

### 6. Field Knowledge Base
26 bundled guides across 11 categories: First Aid, Water, Fire, Shelter, Navigation, Signaling, Food, Security, Engineering, Automotive, and Electrical. Searchable with keyword indexing. Extensible — users can add custom JSON guide files.

### 7. Navigation & Maps
Full OsmAnd mapping capabilities: offline vector maps, turn-by-turn routing, POI search, track recording, and coordinate tools. Enhanced with Rushlight's AI — ask natural language questions about nearby locations.

### 8. Theme System
Three visual presets: **Pip-Boy** (retro-futuristic green CRT terminal), **Modern** (sleek teal), and **Classic** (standard OsmAnd). CRT scan lines, phosphor glow, and monospace font effects available in Pip-Boy mode.

---

## Grant Alignment

### Open Technology Fund (OTF)
Rushlight directly addresses OTF's mission of supporting internet freedom technologies:

- **Censorship Resistance:** Fully offline operation means the app functions identically in internet-shutdown environments. No server to block, no API to censor
- **Encrypted Communications:** P2P messaging with forward-secrecy-capable encryption. No centralized infrastructure to compromise
- **Decentralized Field Intelligence:** FieldNotes enable groups to build shared situational awareness without any central server. Cryptographic signatures ensure content integrity without requiring trust in infrastructure. This is the kind of tool that works when everything else has been shut down
- **Tool Safety:** Duress PIN, panic wipe (including signing keypair destruction for identity reset), and stealth mode protect users facing device seizure or coerced access
- **Offline Intelligence:** AI assistant and knowledge base provide critical information access when internet is unavailable or surveilled. FieldNotes extend this to crowd-sourced intelligence from the mesh network

### NLnet Foundation
Rushlight aligns with NLnet's focus on open internet technology and user empowerment:

- **Open Source:** Built on Apache 2.0 licensed OsmAnd. All Rushlight additions follow the same open license
- **Privacy by Design:** Zero telemetry, on-device processing, encrypted storage. FieldNote author identity is a public key hash — anonymous, verifiable, and under user control. Users control their data completely
- **Decentralized Architecture:** P2P communication requires no servers. FieldNotes gossip through the mesh with no central coordination. Knowledge sharing happens directly between devices
- **Next Generation Internet:** On-device AI inference represents the future of privacy-preserving AI — compute stays with the user. FieldNotes demonstrate decentralized content moderation (voting/trust) without platform gatekeepers
- **Cryptographic Trust:** ECDSA P-256 signatures on FieldNotes establish content authenticity without certificate authorities or identity providers — a building block for decentralized trust on the next-generation internet

---

## Build & Test Instructions

### Prerequisites
- Android Studio (Arctic Fox or later) with Android SDK
- JDK 17 (bundled with Android Studio)
- Android device or emulator running Android 8.0+ (API 26+), AI features require Android 11+ (API 30+)
- ~16GB disk space for full build

### Build
```bash
git clone https://github.com/liekzejaws/rushlight.git
cd rushlight

# Set JAVA_HOME to Android Studio's bundled JBR
export JAVA_HOME="/path/to/Android Studio/jbr"

# Build debug APK (arm64)
./gradlew assembleNightlyFreeLegacyArm64Debug
```

The APK will be at:
```
OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk
```

### Install
```bash
adb install -r OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk
adb shell am start -n io.rushlight.app/net.osmand.plus.activities.MapActivity
```

### Verify
1. **First Launch:** Onboarding overlay appears with Rushlight branding and permission requests
2. **AI Chat:** Side panel → AI tab → model auto-loads → ask a question → response streams with source citations
3. **Wikipedia:** Side panel → Wiki tab → search articles → browse offline
4. **Guides:** Side panel → Guides tab → 11 category grid → tap any category → browse guides
5. **Morse:** Side panel → Morse tab → type message → send via flashlight or audio
6. **P2P:** Side panel → P2P tab → shows peer discovery (requires second device)
7. **Security:** Side panel → Tools tab → Emergency Wipe, PIN setup, Stealth Mode options
8. **Demo Pre-Flight:** Tools tab → Prepare Demo → comprehensive readiness checklist

### Content Setup for Full Demo
- **AI Model:** Download a GGUF model (e.g., Phi-3-mini-4k Q4_K_M, ~2.3GB) and place in the app's models directory
- **Wikipedia:** Download a ZIM file from [Kiwix](https://wiki.kiwix.org/wiki/Content) and open it in the Wiki tab
- **Maps:** Download offline map data through OsmAnd's map manager

---

## Project Status

Rushlight v1.3 is a working field-ready application demonstrating the core thesis: a fully offline, privacy-first field intelligence platform with decentralized shared situational awareness is both technically feasible and practically useful.

### Version History
| Version | Milestone |
|---------|-----------|
| v0.1 | Fork, rebrand, panel system, Morse code, Pip-Boy themes |
| v0.4 | Offline AI (llama.cpp), RAG pipeline, offline Wikipedia, security suite |
| v1.0 | FieldNotes local storage + map overlay |
| v1.1 | FieldNotes P2P sync + LLM tool integration |
| v1.2 | FieldNotes voting/trust + bug fixes |
| v1.3 | FieldNotes ECDSA P-256 crypto signing (current) |

### Roadmap — Areas for Future Development
- **FieldNotes image attachments** — photos embedded in annotations (text-only in v1.3)
- **Multi-hop mesh relay** — FieldNotes propagation beyond direct BLE/WiFi range
- **FieldNotes map clustering** — visual grouping when many notes are in a small area
- **Model fine-tuning** for domain-specific performance (medical, legal, technical)
- **iOS port** for broader reach
- **Hardware testing** across diverse Android devices and form factors
- **Field testing** with target user populations (journalists, humanitarian workers)
- **Accessibility audit** and internationalization
- **Formal security audit** of the FieldNotes crypto signing and P2P trust model
