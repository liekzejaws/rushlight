# Contributing to Rushlight

Thanks for looking. Rushlight is a solo project and contributions — code, testing, documentation, translation, or just field feedback — genuinely move it forward.

No formal process. No CLA. Just open an issue or a PR.

---

## Where to Start

### 🧪 Testing (Highest Value Right Now)

The biggest gap is real-device testing. Emulators don't catch the things that matter — BLE timing, battery behavior, LLM latency on real hardware, P2P edge cases.

**FieldNotes sync (2 devices required)**
```
1. Build the app on two devices
2. Device A: drop a FieldNote on the map
3. Bring Device B within BLE range
4. Confirm the note appears on Device B within ~30 seconds
5. Report: device models, Android versions, time-to-sync, any failures
```
Open an issue tagged `testing/fieldnotes-sync` with your results.

**FieldNotes gossip propagation (3 devices required)**
```
Arrange: A --- B --- C  (A and C out of range of each other)
1. A drops a note
2. B comes into range of A, syncs
3. B moves to C's range
4. Confirm C receives A's note via B
5. Report: how long the full propagation took
```

**FieldNotes tamper resistance**
```
1. A drops a FieldNote, B receives it
2. On B's device: use sqlite3 to manually edit the note text in the database
3. Attempt to forward the tampered note to Device C
4. Confirm C rejects or flags the tampered note
5. Report: what happens (rejection, warning, silent acceptance?)
```

**LLM inference benchmarks**
Run the standard query suite and report results. Most wanted: mid-range devices (Snapdragon 700 series, Dimensity 700/800, 4-6GB RAM). Also wanted: anything 2GB RAM or older than Android 11.

Queries to time:
- "Find the nearest water source"
- "What are the symptoms of dehydration?"
- "How do I signal for rescue?"
- "Is there a shelter nearby?" (with FieldNotes populated)

Report: device model, Android version, RAM, query latency (ms), memory footprint (MB peak).

**P2P stress test (5+ devices)**
```
1. Set up 5+ devices all within BLE range
2. Each device drops 10 FieldNotes rapidly (~1 per second)
3. After 2 minutes, check each device's note count
4. All devices should converge to the same 50 notes
5. Report: convergence time, any note loss, memory behavior
```

**Morse code receive**
Test audio receive on your microphone at different distances. What's the reliable range for audio receive vs. camera receive?

---

### 🐛 Bug Reports

Before filing: check if the issue is [already reported](https://github.com/liekzejaws/rushlight/issues).

Include:
- Device model + Android version
- Steps to reproduce
- Expected vs. actual behavior
- Logcat output if relevant (`adb logcat | grep rushlight`)

---

### 💻 Code Contributions

The stack is Kotlin + Java (OsmAnd fork), with C++ via JNI for llama.cpp and Kiwix. If you're comfortable in any of those, there's work to do.

**Good places to start:**

`FieldNotes/`
- Custom annotation categories — what's missing? (e.g., "refugee camp", "SIGINT risk", "air quality")
- Annotation expiry UI — show note age visually on the map overlay
- Filter panel — let users filter notes by category, age, trust level

`LlmManager/`
- Quantization benchmarking — which GGUF formats perform best per device tier?
- Context window management — smarter truncation when FieldNotes fill the context
- Streaming UI — improve the token-by-token display

`P2P/`
- Meshtastic LoRa bridge — extend FieldNotes propagation via T-Beam
- Ephemeral ID rotation — currently per-session, explore per-connection
- BLE timing optimization — reduce discovery latency

`UI/`
- Pip-Boy skin polish — the aesthetic needs love (green CRT glow, scan lines, animations)
- Accessibility — screen reader support, font scaling, high-contrast mode
- Onboarding flow — must work for a non-technical user under stress

**Architecture notes:** The OsmAnd codebase is large and not always friendly to new contributors. Before touching maps or routing, read their [architecture docs](https://github.com/osmandapp/OsmAnd/blob/master/OsmAnd/ARCHITECTURE.md) first. The Rushlight additions are mostly in `/OsmAnd/src/net/osmand/plus/rushlight/`.

---

### 🌍 Translations

Priority languages (in order): **Arabic, Farsi, Spanish, Chinese (Simplified), Russian**

These are the languages spoken by people in Rushlight's primary target regions (Iran, China, Russia, Venezuela, MENA/LatAm). If you're a native speaker in any of these, even partial translations of the core UI strings are valuable.

Strings file: `OsmAnd/res/values/strings.xml`

Open a PR with translated strings in `OsmAnd/res/values-XX/strings.xml` (where XX is the ISO 639-1 code).

**Critical strings first:** onboarding, FieldNotes UI, AI query interface, panic wipe confirmation.

---

### 🔐 Security Research

Rushlight is designed for high-risk users. Security findings are taken seriously.

**Threat model:** [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md) — read this first to understand the intended security properties and explicit out-of-scope items.

**Areas most wanted:**
- FieldNotes crypto signing — ECDSA P-256 via Android Keystore. Is the signing correct? Can signatures be forged, replayed, or stripped?
- P2P trust model — can a Sybil attacker corrupt the reputation system? How?
- Gossip protocol — can a malicious node inject fake notes that appear verified?
- Stealth mode — does the dialer code approach actually hide the app from standard forensic tools?
- AES-256-GCM chat storage — is key derivation/storage correct?

**Responsible disclosure:** Email [maintainer contact in GitHub profile]. Please allow 30 days before public disclosure. I'll credit researchers in the audit report.

---

### 📖 Documentation

- **Field scenarios** — if you've used something like this in the field (disaster response, journalism, activism), describe how you would have used FieldNotes. This shapes priorities.
- **Threat model gaps** — things in [THREAT-MODEL.md](docs/THREAT-MODEL.md) that seem wrong or incomplete
- **Build process** — anything that didn't work in the build instructions
- **First-run experience** — anything confusing in the onboarding

---

## What We're Not Looking For (Right Now)

- Feature requests that require internet — offline-first is non-negotiable
- Cloud sync, accounts, or any centralized service
- iOS port — not yet, Android first
- Monetization features

---

## Code Style

Follow OsmAnd's existing conventions in files you touch. Kotlin preferred for new code. Don't reformat files you're not changing — it makes diffs unreadable.

Run `./gradlew lint` before submitting a PR.

---

*Questions? Open an issue or start a Discussion. This project is small enough that you'll get a direct response.*
