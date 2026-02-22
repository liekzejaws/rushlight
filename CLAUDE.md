# CLAUDE.md - Rushlight Project Context

> **READ THIS FIRST** - This file provides context for any AI assistant working on Rushlight.

---

## Project: Rushlight (Survival/Grid-Down App)

**What is this?**
An offline-first Android survival app forked from OsmAnd. A personal survival computer that works when networks fail, governments censor, or infrastructure collapses.

**Package ID:** `io.rushlight.app`
**Branch:** `main`
**Internal code name:** `lampp` (Java packages use `net.osmand.plus.lampp.*`)

---

## Current Status (February 2026)

### Version: v1.3.0 (build 5900)

### Completed Phases

| Phase | Feature | Commit |
|-------|---------|--------|
| 1-3 | Fork, rebrand, strip features | `cb273bdb32` |
| 4 | Offline Wikipedia via ZIM/libkiwix | `9823752a4b` |
| 5 | Local LLM AI assistant (llama.cpp) | `5738bd3864` |
| 6 | P2P Content Sharing (BLE/WiFi Direct/BT) | `72fd2dc451` |
| 7 | RAG pipeline (Wikipedia + POI + location) | `684e3e2b0d` |
| 8 | Location-aware queries, map data integration | `c51d5b02b4` |
| v0.1 | Panel system, Morse code, Pip-Boy themes | `e275941d2a` |
| 9A | Rebrand from Lampp to Rushlight | `96e805c207` |
| 9B | Security Suite (encrypted chat, panic wipe, biometric lock) | `96e805c207` |
| v1.0 | FieldNotes local storage + map overlay | `96e805c207` |
| v1.1 | FieldNotes P2P sync + LLM tool integration | `c57e549210` |
| v1.2 | FieldNotes voting/trust + bug fixes | `c57e549210` |
| v1.3 | FieldNotes ECDSA P-256 crypto signing | `c57e549210` |

### Working Features
- **FieldNotes** - Shared map annotations with P2P sync, LLM tools, voting, ECDSA signing
- **Offline maps** - Full OsmAnd mapping with offline vector maps
- **Offline Wikipedia** - Browse ZIM files via libkiwix integration
- **Local LLM** - GGUF model chat via Ai-Core AAR (llama.cpp) with RAG + FieldNotes tools
- **P2P Sharing** - BLE discovery, WiFi Direct transfer, FieldNotes gossip sync
- **Morse Code** - Send/receive via flashlight and audio, DSP decoding
- **Panel System** - Side panel with 5 tabs, 3 themes (Pip-Boy/Modern/Classic)
- **Security Suite** - Encrypted chat (SQLCipher), panic wipe (incl. signing key), biometric lock

### Known Issues
- Requires Android 11+ (API 30) for AI features

---

## Build Instructions

```bash
cd D:/OsmAnd/OsmAnd

# Set Java (Android Studio JBR)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# Build debug APK
./gradlew assembleNightlyFreeLegacyArm64Debug

# APK output location
# OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk
```

### Install on Device
```bash
ADB="/c/Users/ironf/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$ADB install -r OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk
$ADB shell am start -n io.rushlight.app/net.osmand.plus.activities.MapActivity
```

---

## Key Files

### Rushlight Custom Code
| Path | Purpose |
|------|---------|
| `OsmAnd/src/net/osmand/plus/fieldnotes/` | FieldNotes: data model, DB, signer, manager, map layer, UI (7 files) |
| `OsmAnd/src/net/osmand/plus/ai/` | LLM + RAG + FieldNote tools (17 files) |
| `OsmAnd/src/net/osmand/plus/lampp/` | Panel system + theming (12 files) |
| `OsmAnd/src/net/osmand/plus/morse/` | Morse code comms (14 files) |
| `OsmAnd/src/net/osmand/plus/plugins/p2pshare/` | P2P sharing + FieldNote sync (16 files) |
| `OsmAnd/src/net/osmand/plus/security/` | Security suite (4 files) |
| `OsmAnd/src/net/osmand/plus/wikipedia/Zim*.java` | ZIM/Wikipedia integration |
| `OsmAnd/libs/ai-core-release.aar` | llama.cpp native library (43 MB) |
| `OsmAnd/build.gradle` | Package ID: `io.rushlight.app` |

### Configuration
| Path | Purpose |
|------|---------|
| `OsmAnd/AndroidManifest.xml` | Permissions, SDK requirements |
| `OsmAnd/build-common.gradle` | Dependencies (libkiwix, SQLCipher, biometric) |
| `gradle.properties` | Build settings |

---

## Architecture

```
+------------------------------------------------------------------+
|                   Rushlight v1.3 (io.rushlight.app)                |
+------------------------------------------------------------------+
|                                                                    |
|  +---------------------------+  +------------------------------+  |
|  |     Panel System          |  |    Stock OsmAnd (Modified)   |  |
|  |  Tab Bar + 5 Panels      |  |  Offline Maps    Navigation  |  |
|  |  AI Chat | Wiki | P2P    |  |  GPS/Compass     Waypoints   |  |
|  |  Morse   | Tools         |  |  Track Record    Elevation   |  |
|  |  3 Theme Presets          |  |  Sensors         Star Map    |  |
|  +---------------------------+  +------------------------------+  |
|                                                                    |
|  +---------------------------+  +------------------------------+  |
|  |     FieldNotes System     |  |     AI / LLM Engine          |  |
|  |  Geo-pinned annotations   |  |  llama.cpp + RAG Pipeline    |  |
|  |  ECDSA P-256 signing      |  |  Wikipedia + POI + FieldNotes|  |
|  |  P2P gossip sync          |  |  query_fieldnotes tool       |  |
|  |  Voting/trust scoring     |  |  create_fieldnote tool       |  |
|  +---------------------------+  +------------------------------+  |
|                                                                    |
|  +---------------------------+  +------------------------------+  |
|  |     Security Suite        |  |     P2P Share System         |  |
|  |  Encrypted Chat (SQLCipher)|  |  BLE Discovery              |  |
|  |  Panic Wipe + key destroy |  |  WiFi Direct + BT            |  |
|  |  Biometric Panel Lock     |  |  FieldNote gossip transport   |  |
|  |  Stealth Mode + Duress PIN|  |  APK Self-Spreading          |  |
|  +---------------------------+  +------------------------------+  |
|                                                                    |
|  +---------------------------+                                    |
|  |     Morse Code Comms      |                                    |
|  |  Flashlight + Audio TX/RX |                                    |
|  |  DSP Decoding (Goertzel)  |                                    |
|  |  LLM Error Correction     |                                    |
|  +---------------------------+                                    |
|                                                                    |
+------------------------------------------------------------------+
|  Android (API 30+)  |  ARM64  |  No internet required           |
+------------------------------------------------------------------+
```

---

## Reference Documentation

| File | Location |
|------|----------|
| Design document | `D:\OsmAnd\OsmAnd\LAMPP_DESIGN_DOC.md` |
| Morse code spec | `D:\OsmAnd\OsmAnd\MORSE-CODE-SPEC.md` |

---

## Important Notes

1. **Internal code uses `lampp`** - Java packages (`net.osmand.plus.lampp.*`), preference keys (`LAMPP_*`), resource filenames (`lampp_*.xml`) all use the original codename. Only user-facing strings say "Rushlight".
2. **AI requires API 30+** - Ai-Core AAR has minSdk 30 requirement
3. **Test on Pixel 9a** - Physical device for realistic performance
4. **Security dependencies** - SQLCipher 4.5.4, AndroidX Biometric 1.2.0-alpha05

---

*Last updated: February 22, 2026*
