# CLAUDE.md - Lampp Project Context

> **READ THIS FIRST** - This file provides context for any AI assistant working on Lampp.

---

## Project: Lampp (Survival/Grid-Down App)

**What is this?**
An offline-first Android survival app forked from OsmAnd. A personal survival computer that works when networks fail, governments censor, or infrastructure collapses.

**Package ID:** `io.lampp.app`
**Branch:** `priceless-borg`
**Worktree:** `C:/Users/ironf/.claude-worktrees/OsmAnd/priceless-borg`

---

## Current Status (February 2026)

### Completed Phases

| Phase | Feature | Commit |
|-------|---------|--------|
| 1-3 | Fork, rebrand, strip features | `cb273bdb32` |
| 4 | Offline Wikipedia via ZIM/libkiwix | `9823752a4b` |
| 5 | Local LLM AI assistant (llama.cpp) | `5738bd3864` |

### Working Features
- **Offline maps** - Full OsmAnd mapping with offline vector maps
- **Offline Wikipedia** - Browse ZIM files via libkiwix integration
- **Local LLM** - TinyLlama chat via Ai-Core AAR (llama.cpp)
- **AI Assistant** - Drawer menu item for chat interface

### Known Issues
- Chat UI scroll behavior (messages run off top of screen)
- Requires Android 11+ (API 30) for AI features

---

## Phase 6: P2P Content Sharing (IN PROGRESS)

Share maps, Wikipedia, models between phones without internet.

### Technical Approach
1. **BLE Beaconing** - Fast peer discovery (10-50m)
2. **WiFi Direct** - High-speed transfer (250 Mbps)
3. **Bluetooth Classic** - Fallback (720 Kbps)

### Plugin Structure
```
OsmAnd/src/net/osmand/plus/plugins/p2pshare/
├── P2pSharePlugin.java           # Main plugin
├── P2pShareManager.java          # Core logic
├── ContentManifest.java          # Shareable content list
├── discovery/                    # BLE peer discovery
├── transport/                    # WiFi Direct + BT transfer
├── protocol/                     # File transfer protocol
└── ui/                           # Fragments and adapters
```

### Features
- Share maps (.obf), ZIMs, LLM models (.gguf)
- **APK self-spreading** - Share app to devices without it
- Resume interrupted transfers
- Checksum verification

### Files to Modify
- `PluginsHelper.java` - Register plugin
- `OsmAndCustomizationConstants.java` - Add drawer ID
- `AndroidManifest.xml` - Add WiFi Direct permissions

---

## Future Phases

### Phase 7: Security Features
- Panic wipe, stealth mode, duress PIN
- Encrypted storage (SQLCipher)

### Phase 8: AI Terrain Navigator
- "I see a hill to my north" → estimated location

---

## Build Instructions

```bash
# Navigate to worktree
cd C:/Users/ironf/.claude-worktrees/OsmAnd/priceless-borg

# Set Java (Android Studio JBR)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# Build debug APK
./gradlew :OsmAnd:assembleNightlyFreeLegacyArm64Debug

# APK output location
# OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk
```

### Install on Device
```bash
ADB="/c/Users/ironf/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$ADB install -r OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk
$ADB shell monkey -p io.lampp.app -c android.intent.category.LAUNCHER 1
```

---

## Key Files

### Lampp Custom Code
| Path | Purpose |
|------|---------|
| `OsmAnd/src/net/osmand/plus/ai/` | LLM integration (LlmManager, LlmChatFragment) |
| `OsmAnd/src/net/osmand/plus/wikipedia/Zim*.java` | ZIM/Wikipedia integration |
| `OsmAnd/libs/ai-core-release.aar` | llama.cpp native library (43 MB) |
| `OsmAnd/build.gradle` | Package ID: `io.lampp.app` |

### OsmAnd Core
| Path | Purpose |
|------|---------|
| `OsmAnd/src/net/osmand/plus/plugins/` | Plugin system |
| `OsmAnd/src/net/osmand/plus/activities/MapActivity*.java` | Main map activity |
| `OsmAnd/src/net/osmand/plus/activities/MapActivityActions.java` | Drawer menu items |

### Configuration
| Path | Purpose |
|------|---------|
| `OsmAnd/AndroidManifest.xml` | Permissions, SDK requirements |
| `OsmAnd/build-common.gradle` | Dependencies |
| `gradle.properties` | Build settings |

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  Lampp App                       │
├────────────────────┬────────────────────────────┤
│  FROM OSMAND       │  LAMPP ADDITIONS           │
│  ────────────      │  ───────────────           │
│  • Offline maps    │  • Local LLM (llama.cpp)   │
│  • GPS/compass     │  • Full Wikipedia (ZIM)    │
│  • Navigation      │  • AI Assistant UI         │
│  • Waypoints       │                            │
│  • Track recording │  PLANNED:                  │
│  • Elevation data  │  • P2P sharing (Phase 6)   │
│  • Plugin system   │  • Security (Phase 7)      │
│                    │  • AI Navigator (Phase 8)  │
└────────────────────┴────────────────────────────┘
```

---

## Reference Documentation

| File | Location |
|------|----------|
| Original fork plan | `D:\OsmAnd\OsmAnd\osmand_fork_plan.md` |
| Project overview | `D:\OsmAnd\OsmAnd\claude.md` |
| Briar (for P2P) | https://github.com/briar/briar |

---

## Important Notes

1. **Always use this worktree** - `priceless-borg` branch has all Lampp work
2. **Don't use practical-johnson** - That was abandoned, branch deleted
3. **AI requires API 30+** - Ai-Core AAR has minSdk 30 requirement
4. **Test on Pixel 9a** - Physical device for realistic performance

---

*Last updated: February 5, 2026*
