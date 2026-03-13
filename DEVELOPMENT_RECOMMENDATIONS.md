# Rushlight: Recommendations for Continued Development

## Context

Rushlight is at v1.4.0 (build 5901) with all core features implemented: offline maps, local LLM with RAG, FieldNotes with ECDSA signing and P2P gossip sync, Morse code comms, security suite, and panel system. The project has a clear roadmap (v1.4 hardening → v1.5 localization → v1.6 Passage module → v2.0 Meshtastic → v2.1 Medic module) but several foundational gaps should be addressed before expanding features further.

These recommendations are ordered by impact and urgency.

---

## 1. CRITICAL: Fix CI/CD Pipeline (No Tests Run in CI)

**Problem:** The GitHub Actions workflow (`build-debug-apk.yml`) builds the APK but **never executes the 28 unit test classes**. Tests exist for crypto, P2P transport, Morse DSP, FieldNotes, and security — but they only run locally.

**Recommendation:**
- Add `./gradlew testNightlyFreeLegacyArm64DebugUnitTest` before the APK build step
- Fail the build on test failure
- Add `./gradlew lint` with baseline enforcement
- Files: `.github/workflows/build-debug-apk.yml`, `.github/workflows/build-release-apk.yml`

**Why now:** Every commit since CI was set up could have introduced regressions silently. This is the single highest-ROI change.

---

## 2. HIGH: Upgrade Outdated/Unstable Dependencies

| Dependency | Current | Issue | Action |
|-----------|---------|-------|--------|
| `androidx.biometric` | `1.2.0-alpha05` | Alpha — API may change, Play Store may reject | Upgrade to latest stable (1.2.0+) |
| `org.bouncycastle:bcpkix-jdk15on` | `1.56` (2016) | 8 years old, potential CVEs | Upgrade to 1.78+ |
| `ai-core-release.aar` | Bundled 43MB | No version tracking, no update path | Document version, add update procedure |

- Files: `OsmAnd/build-common.gradle`, `versions.gradle`

---

## 3. HIGH: Harden P2P Transfer Reliability

**Current gaps** (documented in design doc as planned but not implemented):
- **Transfer resume:** Interrupted WiFi Direct transfers restart from zero
- **Checksum verification:** No integrity check on received files
- **Multi-file queue:** Can only transfer one file at a time

**Recommendation:** Implement in this order:
1. SHA-256 checksum verification on received content (low effort, high value)
2. Transfer resume with byte-offset tracking (medium effort)
3. Multi-file sequential queue (medium effort)

- Files: `OsmAnd/src/net/osmand/plus/plugins/p2pshare/` (TransportManager, transfer-related classes)

---

## 4. HIGH: Persist Vote State for FieldNotes

**Problem:** Voted note IDs are tracked in-memory only — lost on app restart. Users can re-vote on the same notes after restarting the app, corrupting trust scores.

**Recommendation:** Store voted note IDs in the existing SQLCipher database with a simple `voted_notes` table (note_id TEXT PRIMARY KEY, vote_direction INTEGER, voted_at INTEGER).

- Files: `OsmAnd/src/net/osmand/plus/fieldnotes/FieldNotesManager.java`, `FieldNotesDbHelper.java`

---

## 5. MEDIUM: Add Integration Tests for Critical Paths

**Current state:** 28 unit test classes with good coverage of individual components, but no integration tests for:
- P2P gossip sync + FieldNotes voting (end-to-end flow)
- Panic wipe completeness (verify all artifacts actually deleted)
- FieldNote signature verification with tampered data
- LLM tool dispatch error handling (malformed queries)
- Key rotation after panic wipe (new identity, old notes still readable)

**Recommendation:** Add 5-8 integration test classes covering the critical cross-module flows above. Prioritize the panic wipe completeness test — security features that aren't tested are security theater.

- Files: `OsmAnd/test-unit/java/net/osmand/plus/security/`, `OsmAnd/test-unit/java/net/osmand/plus/fieldnotes/`

---

## 6. MEDIUM: Battery & Performance Optimization

**Problem:** The roadmap targets <15%/hour during navigation and <3s LLM first-token on 3GB RAM devices. No benchmarks enforce these yet.

**Recommendation:**
- Add a CI performance regression gate using the existing benchmarking system (DeviceCapabilityDetector, StartupProfiler)
- Profile Morse code camera/flash usage — continuous camera is a known battery drain
- Add executor lifecycle management to LlmManager (verify `shutdown()` on app close to prevent thread leaks)
- Test inference timeout and recovery in LlmManager

- Files: `OsmAnd/src/net/osmand/plus/ai/LlmManager.java`, `OsmAnd/src/net/osmand/plus/morse/`

---

## 7. MEDIUM: Prepare for v1.5 Localization

**Problem:** v1.5 targets Arabic, Farsi, Spanish, Chinese, Russian. RTL languages (Arabic, Farsi) require layout mirroring which is non-trivial in an OsmAnd fork.

**Recommendation:**
- Audit all custom layouts in `lampp/`, `fieldnotes/`, `ai/`, `security/` for RTL compatibility (`android:layoutDirection`, `start`/`end` vs `left`/`right`)
- Extract all hardcoded user-facing strings to `strings.xml` (if any remain)
- Set up translation workflow (Weblate or Crowdin) before v1.5 development begins
- Test with system locale set to Arabic to catch layout breakage early

---

## 8. MEDIUM: Security Audit Preparation

**Problem:** v1.4 roadmap lists a third-party security audit but it hasn't happened yet.

**Recommendation — prepare audit scope document covering:**
- SQLCipher key derivation (PBKDF2 iterations — currently 10,000, OWASP minimum)
- Panic wipe completeness (temporary files, caches, system clipboard)
- Duress PIN timing-attack resistance (constant-time comparison already implemented — good)
- FieldNote ECDSA signing (key storage, signature verification, replay protection)
- P2P transport encryption (ECDH + AES-256-GCM — already implemented)
- APK self-spreading integrity (no version check — could propagate outdated/malicious APK)

**Critical finding:** The APK self-spreading feature has no version verification. A compromised device could spread a tampered APK. Add APK signature verification before installation.

---

## 9. LOW: Architecture Improvements

- **Dependency injection:** Consider Hilt for manager lifecycle (SecurityManager, LlmManager, P2pShareManager, FieldNotesManager all take OsmandApplication singleton). Not urgent but reduces coupling.
- **P2P state machine documentation:** TransportManager handles BLE/WiFi Direct/Bluetooth with concurrent states. Document the state machine to reduce race condition risk.
- **Database thread safety:** Verify all EncryptedChatStorage operations are called from consistent threads. Add synchronized wrapper if multi-threaded access is possible.

---

## 10. FEATURE ROADMAP PRIORITIES (v1.5+)

Based on the design doc's mission (survival tool for infrastructure collapse, censorship, displacement), I recommend this prioritization:

### Highest Impact Next Features
1. **Meshtastic LoRa integration (v2.0)** — Extends P2P range from ~30m BLE to miles. This is the single biggest capability gap. Consider pulling this ahead of the Passage module.
2. **Offline knowledge packs** — Survival guides, plant ID, radio frequencies. High value, low complexity. Could ship as downloadable ZIM-like packs.
3. **Transfer resume + checksum** — Without these, P2P sharing is fragile in the exact conditions Rushlight targets (unreliable connections, moving devices).

### Consider Deferring
- **Medic module (v2.1)** — Clinical knowledge packs require medical review and liability considerations. Partner with MSF/ICRC before building.
- **Localization to 5 languages** — High effort. Consider starting with Spanish + Arabic only, add others based on user demand.

---

## Summary: Recommended Priority Order

| Priority | Item | Effort | Impact |
|----------|------|--------|--------|
| 1 | CI/CD: Run tests in pipeline | Small | Prevents all future regressions |
| 2 | Upgrade biometric + BouncyCastle deps | Small | Removes production blockers |
| 3 | P2P checksum verification | Small | Data integrity for sharing |
| 4 | Persist FieldNotes vote state | Small | Correctness fix |
| 5 | APK self-spread signature verification | Medium | Security-critical |
| 6 | P2P transfer resume | Medium | Reliability in target conditions |
| 7 | Integration tests for security flows | Medium | Audit readiness |
| 8 | Battery/performance benchmarks | Medium | User experience |
| 9 | Meshtastic LoRa (pull ahead to v1.5?) | Large | Transformative capability |
| 10 | RTL layout audit for localization | Medium | v1.5 prerequisite |
