# LAMPP Design Document

> **Last updated:** February 10, 2026
> **Branch:** `priceless-borg`
> **Package:** `io.lampp.app`
> **Device:** Pixel 9a (arm64)

---

## What Is LAMPP?

An offline-first Android survival computer forked from OsmAnd. Works when networks fail, governments censor, or infrastructure collapses. Combines offline mapping, a local AI assistant (llama.cpp), offline Wikipedia (ZIM/libkiwix), and peer-to-peer content sharing over Bluetooth/WiFi Direct — all running entirely on-device with zero internet dependency.

---

## 1. What We Stripped From Stock OsmAnd

### Disabled Plugins (7)
| Plugin | Why Removed |
|--------|-------------|
| WeatherPlugin | Requires online weather API |
| AisTrackerPlugin | Ship tracking — niche, online-dependent |
| SkiMapsPlugin | Ski resort specific — not survival-relevant |
| ParkingPositionPlugin | Car parking — not core survival |
| OsmEditingPlugin | Uploads to OpenStreetMap — requires internet |
| MapillaryPlugin | Street-level imagery — requires internet |
| VehicleMetricsPlugin | Car OBD diagnostics — not core survival |

### Disabled Services
| Service | How Disabled | File |
|---------|-------------|------|
| Cloud Backup | `LAMPP_CLOUD_DISABLED = true`, early return | `BackupHelper.java` |
| Analytics | `LAMPP_ANALYTICS_DISABLED = true`, early return | `AnalyticsHelper.java` |
| Promotional Banners | `LAMPP_PROMOTIONS_DISABLED = true`, early return | `DiscountHelper.java` |
| In-App Purchases | `LAMPP_ALL_FEATURES_UNLOCKED = true`, all checks short-circuited | `InAppPurchaseUtils.java` |
| Cloud Restore UI | Conditionally hidden | `FirstUsageActionsBottomSheet.java` |
| Cloud Settings UI | Conditionally hidden | `MainSettingsFragment.java` |
| Live Updates Purchase Gate | Bypassed | `LiveUpdatesFragment.java` |

### Retained OsmAnd Plugins (10)
WikipediaPlugin (extended with ZIM), OsmandRasterMapsPlugin, OsmandMonitoringPlugin, SRTMPlugin, NauticalMapsPlugin, AudioVideoNotesPlugin, ExternalSensorsPlugin, StarWatcherPlugin, AccessibilityPlugin, OsmandDevelopmentPlugin.

---

## 2. What We Built — Feature Inventory

### 2A. LAMPP Panel System (the "Pip-Boy Shell")

A side-panel UI system that overlays OsmAnd's map. Slides in from the right edge with a vertical tab bar, supporting 5 tabs and 3 visual theme presets.

**Files:** `net.osmand.plus.lampp.*` (12 Java files)

| Component | File | Description |
|-----------|------|-------------|
| Panel Manager | `LamppPanelManager.java` | Coordinates tab bar + fragment lifecycle; handles open/close/restore/theme-refresh |
| Side Tab Bar | `LamppSideTabBar.java` | Custom vertical `LinearLayout` on right edge; animated icon transitions, theme-aware coloring |
| Tab Enum | `LamppTab.java` | 5 tabs: MAP (sentinel/close), AI_CHAT, WIKI, P2P, TOOLS |
| Base Panel | `LamppPanelFragment.java` | Abstract base — slide-in animation, drag-to-resize (3 snap points), scrim overlay, Pip-Boy effects integration |
| Interceptor | `InterceptorFrameLayout.java` | Custom FrameLayout intercepting horizontal drags for panel resize |
| Theme Presets | `LamppStylePreset.java` | Enum: PIP_BOY (green CRT), MODERN (blue material), CLASSIC (OsmAnd native); 14+ color methods each |
| Theme Utils | `LamppThemeUtils.java` | ContextThemeWrapper overlays, retro font application, color resolution |
| Tools Panel | `ToolsFragment.java` | 3 preset selection cards + "More settings" button |
| Settings | `LamppSettingsFragment.java` | AI inference params, RAG config, Pip-Boy visual effect toggles |

**Pip-Boy Visual Effects** (`lampp/effects/`):
| Effect | File | Description |
|--------|------|-------------|
| CRT Scan Lines | `ScanLineOverlayView.java` | Tiled BitmapShader (1x4px pattern), ~12% opacity horizontal lines |
| Phosphor Glow | `PhosphorGlowView.java` | LinearGradient edge glow on 4 edges, breathing ValueAnimator (alpha 0.17-0.33, 3s cycle) |
| Terminal Cursor | `TerminalCursorBlinker.java` | Handler-based blinking `\u2588` block cursor at 530ms (classic terminal rate) |
| Retro Font | (in LamppThemeUtils) | Share Tech Mono applied recursively to all TextViews; `res/font/share_tech_mono.ttf` |

All effects are toggleable via boolean preferences and only active when PIP_BOY preset is selected.

**Integration points in stock OsmAnd:**
- `MapActivity.java` — 3 insertions: field declaration, `onCreate()` init, `onBackPressed()` intercept, public accessor
- `res/layout/main.xml` — `lamppPanelContainer` FrameLayout + `lampp_side_tab_bar` include

---

### 2B. Local LLM AI Assistant

On-device AI chat powered by llama.cpp (via Ai-Core AAR). Supports GGUF model files, streaming token generation, and RAG (Retrieval-Augmented Generation) with Wikipedia and POI data.

**Files:** `net.osmand.plus.ai.*` (16 Java files)

| Component | File | Description |
|-----------|------|-------------|
| LLM Manager | `LlmManager.java` | Loads GGUF models, configures inference (threads, ctx size, temperature), streaming output |
| Chat UI | `LlmChatFragment.java` | Chat panel (extends LamppPanelFragment); RecyclerView with message bubbles, send button, streaming display |
| Model Manager | `LlmModelsFragment.java` | Download/delete/view GGUF model files in `llm_models/` directory |

**RAG Pipeline** (`ai/rag/`):
| Component | File | Description |
|-----------|------|-------------|
| RAG Manager | `RagManager.java` | Orchestrates context retrieval from multiple sources |
| Prompt Builder | `PromptBuilder.java` | Constructs prompts with injected RAG context |
| Query Classifier | `QueryClassifier.java` | Routes queries to appropriate RAG sources |
| ZIM Search | `ZimSearchAdapter.java` | Searches offline ZIM files for Wikipedia articles |
| Map Data | `MapDataAdapter.java` | Bridges OsmAnd map data into RAG context |
| POI Source | `PoiSource.java` | Nearby POI amenity search |
| POI Resolver | `PoiTypeResolver.java` | Resolves POI type queries |
| Location Context | `LocationContext.java` | GPS-aware context injection |
| Article Source | `ArticleSource.java` | Article data model for RAG |
| HTML Extractor | `HtmlTextExtractor.java` | Strips HTML to plain text for context |
| Place Result | `PlaceResult.java` | Place search result model |
| RAG Callback | `RagCallback.java` | Async callback interface |
| RAG Response | `RagResponse.java` | Response data model |

**Configurable settings** (14 preferences in `OsmandSettings.java`):
- Inference: threads (1-8), context window (2048-8192), max tokens (256-4096), temperature (0.0-1.5)
- RAG: Wikipedia enabled, POI search enabled, max sources (1-5), context token budget (500-6000), POI radius (100m-10km)
- Effects: scanlines, glow, retro font, cursor blink (all boolean)

**Dependencies:**
- `libs/ai-core-release.aar` — llama.cpp native library (43 MB AAR)
- Requires Android 11+ (API 30) for Ai-Core

---

### 2C. Offline Wikipedia (ZIM Browser)

Browse offline Wikipedia via Kiwix ZIM file format. Integrated into OsmAnd's existing WikipediaPlugin with new ZIM-specific UI.

**Files:** `net.osmand.plus.wikipedia.Zim*.java` (~7 files)

| Component | File | Description |
|-----------|------|-------------|
| ZIM Browser | `ZimBrowserFragment.java` | Wikipedia browser panel (extends LamppPanelFragment) |
| File Manager | `ZimFileManager.java` | ZIM file lifecycle management |
| Download Manager | `ZimDownloadManager.java` | Download ZIM files from Kiwix catalog |
| Catalog Parser | `KiwixCatalogParser.java` | Parses Kiwix OPDS catalog for available ZIMs |
| Catalog Adapter | `ZimCatalogAdapter.java` | RecyclerView adapter for catalog browsing |
| Catalog Item | `ZimCatalogItem.java` | Catalog item data model |
| Article Viewer | `ZimArticleDialogFragment.java` | Article display dialog |

**Dependencies:**
- `org.kiwix:libkiwix:2.4.1` — ZIM file reading
- `com.getkeepsafe.relinker:relinker:1.4.5` — Native library loader for libkiwix

---

### 2D. P2P Content Sharing

Share maps, Wikipedia ZIMs, LLM models, and the app APK directly between devices without internet. Uses BLE for discovery, WiFi Direct for high-speed transfer, Bluetooth Classic as fallback.

**Files:** `net.osmand.plus.plugins.p2pshare.*` (16 Java files)

| Component | File | Description |
|-----------|------|-------------|
| Plugin | `P2pSharePlugin.java` | OsmAnd plugin registration, enabled by default |
| Share Manager | `P2pShareManager.java` | Core coordinator; implements PeerDiscoveryCallback + TransportCallback |
| Content Manifest | `ContentManifest.java` | Scans .obf/.zim/.gguf/APK; JSON serialization for exchange; loads saved exclusion prefs |
| Shareable Content | `ShareableContent.java` | Content item model (filename, size, type, checksum, isShared) |
| Content Type | `ContentType.java` | Enum: MAP, ZIM, MODEL, APK |

**Discovery** (`discovery/`):
| Component | File | Description |
|-----------|------|-------------|
| Discovery Manager | `PeerDiscoveryManager.java` | BLE beacon-based peer discovery (10-50m range) |
| Discovered Peer | `DiscoveredPeer.java` | Peer model with state machine: DISCOVERED → CONNECTING → CONNECTED → TRANSFERRING → DISCONNECTED |

**Transport** (`transport/`):
| Component | File | Description |
|-----------|------|-------------|
| Transport Manager | `TransportManager.java` | Selects WiFi Direct (primary) or Bluetooth (fallback) |
| WiFi Direct | `WifiDirectTransport.java` | High-speed file transfer (~250 Mbps) |
| Bluetooth | `BluetoothTransport.java` | Fallback transport (~720 Kbps) |
| Callback | `TransportCallback.java` | 6 callback methods: connected, disconnected, failed, manifest, progress, complete |

**UI** (`ui/`):
| Component | File | Description |
|-----------|------|-------------|
| Share Fragment | `P2pShareFragment.java` | Main P2P panel: peer list, scanning, content summary, transfer progress card |
| Content Config | `ContentConfigBottomSheet.java` | Configure which content to share (with persistence to SharedPreferences) |
| Peer Content | `PeerContentBottomSheet.java` | Browse connected peer's manifest, select files to download |
| Peers Adapter | `NearbyPeersAdapter.java` | RecyclerView adapter with state-based icons |
| Content Adapter | `ContentListAdapter.java` | Checkbox list of shareable content items |

**Features implemented:**
- BLE peer discovery with signal strength → distance estimation
- WiFi Direct primary transport with Bluetooth Classic fallback
- Content manifest exchange (JSON over transport)
- Content exclusion persistence across app restarts
- Peer content browsing and selective download
- Transfer progress UI with filename, progress bar, percentage, cancel button
- APK self-spreading (share the app itself)

**Permissions added to AndroidManifest.xml:**
- `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `NEARBY_WIFI_DEVICES`
- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH`, `BLUETOOTH_ADMIN`
- `<uses-feature android:name="android.hardware.bluetooth" />`
- `<uses-feature android:name="android.hardware.wifi.direct" />`

---

### 2E. Resources & Theming

| Resource | File(s) | Description |
|----------|---------|-------------|
| Colors | `res/values/lampp_colors.xml` | ~80 color definitions across 3 presets |
| Styles | `res/values/lampp_styles.xml` | ThemeOverlay styles (PipBoy + Modern) overriding ~15 OsmAnd attrs |
| Strings | `res/values/strings.xml` | ~70 LAMPP-specific strings (settings, P2P, ZIM, effects) |
| Dimensions | `res/values/lampp_dimens.xml` | Tab/panel dimensions |
| Settings XML | `res/xml/lampp_settings.xml` | PreferenceScreen: AI Model, RAG Context, Pip-Boy Effects |
| Font | `res/font/share_tech_mono.ttf` | Share Tech Mono from Google Fonts (~38 KB) |
| Drawables | `res/drawable/bg_lampp_*.xml` | Panel, tab bar, drag handle, card backgrounds |
| Layouts | `res/layout/fragment_p2p_share.xml`, `bottom_sheet_*.xml`, `item_*.xml`, `lampp_*.xml` | ~10 LAMPP-specific layouts |
| App Icon | `res/mipmap-*/icon_nightly.xml` | LAMPP launcher icon (adaptive) |

---

## 3. Stock OsmAnd Features We Keep & Use

These are the core survival-relevant features that make the OsmAnd base valuable:

| Feature | Why It Matters |
|---------|---------------|
| **Offline vector maps** | Navigate without internet; download country-level maps |
| **GPS + compass** | Position tracking, heading, elevation |
| **Turn-by-turn navigation** | Routing for car, bike, foot, with voice guidance |
| **Waypoints & favorites** | Mark locations, save points of interest |
| **GPX track recording** | Record and replay routes |
| **Elevation data (SRTM)** | Terrain analysis, contour lines, hillshade |
| **Nautical charts** | Water navigation |
| **Audio/video notes** | Geotagged recordings |
| **External sensors** | ANT+/BLE heart rate, speed, cadence |
| **Star map** | Celestial navigation |
| **Raster map overlays** | Satellite imagery, custom tile sources |
| **Development tools** | Debug info, widget testing |

---

## 4. Roadmap — What We Intend to Build

### Near-Term (Next Sessions)

#### AI Chat Polish
- **Markdown rendering** in AI responses (bold, lists, code blocks)
- **Conversation persistence** — save/load chat history across sessions
- **System prompt customization** — let users set personality/role
- **Multi-model switching** — quick-switch between loaded GGUF models
- **Response copy/share** — long-press to copy AI response text

#### P2P Share Hardening
- **Transfer resume** — resume interrupted file transfers from last byte
- **Multi-file queue** — download multiple files sequentially
- **Transfer history** — log of past transfers
- **Checksum verification** on received files
- **Permission flow** — proper runtime permission handling for BLE/WiFi

#### UX Refinements
- **Panel state persistence** — remember panel width/position across sessions
- **Tab badges** — show notification dots (e.g., new peer discovered, transfer complete)
- **Onboarding** — first-launch tutorial explaining LAMPP features
- **Dark map sync** — auto-switch OsmAnd map to dark renderer when Pip-Boy is active

### Medium-Term (Planned Features)

#### Security Suite
- **Panic wipe** — emergency button to wipe sensitive data (chat history, models, ZIMs)
- **Duress PIN** — alternate PIN that triggers selective data destruction
- **Stealth mode** — hide app from launcher, only accessible via dialer code
- **Encrypted storage** — SQLCipher for chat history and user data
- **Screen lock** — PIN/biometric lock for the LAMPP panel system

#### Mesh Networking
- **Multi-hop relay** — pass messages/files through intermediate devices
- **Delay-tolerant networking** — store-and-forward for disconnected peers
- **Group broadcast** — send alerts/messages to all nearby LAMPP devices
- **Dead drops** — leave encrypted messages at GPS coordinates

#### Offline Knowledge Base
- **Survival guides** — bundled offline reference (first aid, water purification, shelter building)
- **Plant identification** — offline image classifier for edible/medicinal plants
- **Radio frequencies** — offline database of emergency/amateur radio frequencies
- **Unit converter** — offline conversion tool (distance, weight, temperature, coordinates)

### Long-Term (Aspirational)

#### AI Terrain Navigator
- **Visual positioning** — "I see a hill to my north" + compass heading → estimate location on map
- **Terrain description matching** — match verbal terrain descriptions to DEM data
- **Route planning from description** — "Find me a path along the ridgeline avoiding the valley"

#### Offline AI Capabilities
- **OCR** — read text from photos (signs, documents) offline
- **Image classification** — identify objects, landmarks, terrain features
- **Translation** — offline language translation via on-device models
- **Voice interface** — speech-to-text → AI → text-to-speech pipeline

#### Community Features
- **Shared waypoints** — sync favorite locations over P2P
- **Situation reports** — structured reports (hazards, resources, safe zones) shared via mesh
- **Map annotations** — draw on shared map layers distributed via P2P

---

## 5. Architecture Overview

```
+------------------------------------------------------------------+
|                        LAMPP (io.lampp.app)                       |
+------------------------------------------------------------------+
|                                                                    |
|  +---------------------------+  +------------------------------+  |
|  |     LAMPP Panel System    |  |    Stock OsmAnd (Modified)   |  |
|  |  +---------+-----------+  |  |                              |  |
|  |  | Tab Bar | Panel Mgr |  |  |  Offline Maps    Navigation  |  |
|  |  +---------+-----------+  |  |  GPS/Compass     Waypoints   |  |
|  |  | AI Chat | Wiki      |  |  |  Track Record    Elevation   |  |
|  |  | P2P     | Tools     |  |  |  Raster Tiles    Nautical    |  |
|  |  +---------+-----------+  |  |  Sensors         Star Map    |  |
|  |  | Theme Presets (3)   |  |  |                              |  |
|  |  | Pip-Boy Effects (4) |  |  +------------------------------+  |
|  +---------------------------+                                    |
|                                                                    |
|  +---------------------------+  +------------------------------+  |
|  |     AI / LLM Engine      |  |     P2P Share System         |  |
|  |  llama.cpp (Ai-Core AAR) |  |  BLE Discovery               |  |
|  |  GGUF Model Loading      |  |  WiFi Direct Transport       |  |
|  |  Streaming Inference      |  |  Bluetooth Fallback          |  |
|  |  RAG Pipeline:            |  |  Content Manifest Exchange   |  |
|  |   - Wikipedia (ZIM)      |  |  APK Self-Spreading          |  |
|  |   - POI Search           |  |                              |  |
|  |   - Location Context     |  +------------------------------+  |
|  +---------------------------+                                    |
|                                                                    |
|  +---------------------------+  +------------------------------+  |
|  |   Wikipedia (ZIM/Kiwix)  |  |     Stripped/Disabled        |  |
|  |  libkiwix ZIM Reader     |  |  Cloud backup     Analytics  |  |
|  |  Catalog Browser/DL      |  |  Promotions       IAP gates  |  |
|  |  Article Viewer          |  |  7 online plugins            |  |
|  |  RAG Search Adapter      |  |                              |  |
|  +---------------------------+  +------------------------------+  |
|                                                                    |
+------------------------------------------------------------------+
|  Android (API 30+)  |  ARM64  |  No internet required           |
+------------------------------------------------------------------+
```

---

## 6. File Map

### New Packages
```
OsmAnd/src/net/osmand/plus/
  lampp/                          # Panel system + theming (12 files)
    effects/                      # Pip-Boy visual effects (3 files)
  ai/                             # LLM inference + chat UI (3 files)
    rag/                          # RAG pipeline (13 files)
  plugins/p2pshare/               # P2P sharing plugin (5 files)
    discovery/                    # BLE peer discovery (2 files)
    transport/                    # WiFi Direct + Bluetooth (4 files)
    ui/                           # Share UI fragments (3 files)
      adapters/                   # RecyclerView adapters (2 files)
  wikipedia/Zim*.java             # ZIM browser extensions (7 files)
```

### Modified Stock Files
```
activities/MapActivity.java       # Panel manager init + back press
plugins/PluginsHelper.java        # Plugin curation (7 disabled, 1 added)
settings/backend/OsmandSettings.java  # 14 LAMPP_ preferences
inapp/InAppPurchaseUtils.java     # All purchases unlocked
backup/BackupHelper.java          # Cloud disabled
feedback/AnalyticsHelper.java     # Analytics disabled
helpers/DiscountHelper.java       # Promotions disabled
settings/fragments/MainSettingsFragment.java  # Cloud UI hidden
firstusage/FirstUsageActionsBottomSheet.java  # Restore UI hidden
liveupdates/LiveUpdatesFragment.java  # Purchase gate bypassed
settings/fragments/SettingsScreenType.java    # LAMPP_SETTINGS entry
wikipedia/WikipediaPlugin.java    # ZIM support added
```

### Build/Config
```
OsmAnd/build.gradle               # nightlyFree flavor: io.lampp.app
OsmAnd/build-common.gradle        # libkiwix + Ai-Core dependencies
OsmAnd/AndroidManifest.xml        # BLE + WiFi Direct permissions
OsmAnd/libs/ai-core-release.aar   # llama.cpp native library (43 MB)
res/layout/main.xml               # Panel container + tab bar
res/mipmap-*/icon_nightly.xml     # LAMPP adaptive icon
```

---

## 7. Build & Deploy

```bash
# Set Java
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# Build
cd D:/OsmAnd/OsmAnd
./gradlew assembleNightlyFreeLegacyArm64Debug

# APK path
# OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk

# Install
ADB="/c/Users/ironf/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$ADB -s 5A081JEBF10683 install -r OsmAnd/build/outputs/apk/nightlyFreeLegacyArm64/debug/OsmAnd-nightlyFree-legacy-arm64-debug.apk
$ADB -s 5A081JEBF10683 shell am start -n io.lampp.app/net.osmand.plus.activities.MapActivity
```

---

## 8. Development History

| Commit | Phase | Description |
|--------|-------|-------------|
| `cb273bdb32` | 1-3 | Initial fork: rebrand to io.lampp.app, strip cloud/IAP/analytics, disable 7 plugins |
| `9823752a4b` | 4 | Offline Wikipedia via Kiwix ZIM file support |
| `5738bd3864` | 5 | Local LLM AI assistant via llama.cpp (Ai-Core AAR) |
| `736ba4ee85` | — | Add CLAUDE.md project documentation |
| `2b31a0621b` | 6.1 | P2P Share plugin skeleton |
| `3e67e53605` | 6.2 | Content configuration UI for P2P |
| `44eb5f762a` | 6.3 | BLE peer discovery |
| `00cba5e0eb` | 6.4 | WiFi Direct file transfer |
| `72fd2dc451` | 6.5 | Bluetooth Classic fallback transport |
| `1b6ebab629` | 7 | RAG integration: LLM + Wikipedia ZIM |
| `684e3e2b0d` | 7 | Reduce RAG context budget for small models |
| `ac8bf45c25` | 8 | Location-aware queries + map data integration |
| `c51d5b02b4` | 8 | Disable POI search by default for offline focus |
| *(uncommitted)* | — | LAMPP panel system, side tab bar, theming (3 presets), settings screen |
| *(uncommitted)* | — | Pip-Boy visual effects (scan lines, glow, retro font, cursor blink) |
| *(uncommitted)* | — | P2P Share completion (persistence, peer browsing, transfer progress) |

---

*This document is intended as a reference for development sessions. Update it as features are added or plans change.*
