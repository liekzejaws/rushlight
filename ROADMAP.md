# 🗺️ Rushlight Roadmap

This is a living document. Priorities shift based on field feedback, grant deliverables, and what the community finds most useful.

Current stable: **v1.3** (FieldNotes + Offline AI + P2P + Morse + Security)

---

## v1.4 — Hardening & Release (Next)

**Goal:** Shippable APK on GitHub Releases. Reproducible build. Ready for NGO beta testing.

- [ ] GitHub Release with signed APK
- [ ] F-Droid submission (reproducible build setup)
- [ ] Performance: LLM inference benchmarks across device tiers (target: <3s on 3GB RAM)
- [ ] Performance: battery drain during navigation <15%/hour
- [ ] FieldNotes: stress test P2P sync with 5+ devices
- [ ] Low-end device support: Android 8-10, 2GB RAM
- [ ] CI/CD pipeline (GitHub Actions, automated build on push)
- [ ] Third-party security audit (targeting Cure53 or Radically Open Security)

**Success criteria:** 3+ NGO partners using the beta APK. Security audit contracted.

---

## v1.5 — Security Audit Response + Localization

**Goal:** Audit complete, results published. App usable by non-English speakers in target regions.

- [ ] Security audit complete, report published
- [ ] All critical findings addressed, mitigations documented
- [ ] UI translation: Arabic, Farsi, Spanish, Chinese, Russian (priority order)
- [ ] Onboarding polish — works for non-technical users under stress
- [ ] User documentation and quick-start guides (all languages)
- [ ] FieldNotes: downvote/reputation propagation over mesh

**Success criteria:** Audit published. App usable by non-technical Arabic/Farsi speaker without help.

---

## v1.6 — Passage Module

**Goal:** Add the refugee/border-crossing knowledge pack as an optional module.

- [ ] Legal rights by country (offline database — what police can/can't do, how to invoke rights)
- [ ] Emergency phrases in 40+ languages (on-device, no internet)
- [ ] Shelter + embassy directory by region (offline-cached)
- [ ] Checkpoint overlays from FieldNotes mesh (hazard category + intel category)
- [ ] Dead man's switch: if user misses check-in, sends encrypted location to pre-configured contact
- [ ] Grant: UNHCR Innovation Fund application

**Partners to target:** UNHCR, IRC (International Rescue Committee), Access Now, Article 19

---

## v2.0 — Meshtastic LoRa Integration

**Goal:** Extend FieldNotes propagation range from Bluetooth (~30m) to field-scale (miles) via LoRa radio.

Hardware required: [Meshtastic T-Beam](https://meshtastic.org/) (~$45, BT to phone)

- [ ] Meshtastic Android SDK integration
- [ ] FieldNotes gossip over LoRa channel
- [ ] Automatic fallback: BLE mesh when no Meshtastic, LoRa when available
- [ ] Range testing in field scenarios
- [ ] Power management for extended T-Beam battery life

**Why this matters:** BLE gives 30m range between devices — useful for camps and urban areas. LoRa gives miles of range without infrastructure — the difference between a neighborhood mesh and a regional intelligence network.

---

## v2.1 — Medic Module

**Goal:** Optional clinical knowledge pack for field medical workers.

- [ ] Deep drug dosing reference (WHO, MSF formularies)
- [ ] Field triage protocols (ATLS, TCCC)
- [ ] WHO essential medicines list
- [ ] Field surgical guides
- [ ] MSF/ICRC treatment reference
- [ ] ~300-800MB optional download (separate from base install)
- [ ] Grant: MSF Operational Centre, ICRC, Wellcome Trust

**Target users:** MSF, Red Cross field teams, FEMA community response, wilderness EMTs

---

## Long-Term Vision

- **F-Droid as primary distribution** — no Google Play dependency, privacy-respecting install path
- **Tor integration** — route initial app download + update check through Tor (defeats app store surveillance)
- **Stealth download** — APK disguised as innocuous utility for censored regions
- **OpenCollective** — community funding for ongoing maintenance
- **Governance** — transition from solo maintainer to core team once community grows
- **Guardian Project umbrella** — target integration into the digital security toolkit standard (like Orbot, Briar)

---

## What's Not On The Roadmap (And Why)

**iOS port** — OsmAnd's iOS codebase is separate and not open source in the same way. Not worth fragmenting effort until Android is solid.

**Web/desktop version** — defeats the point. The value is the phone you already have in your pocket.

**Cloud sync** — explicitly out of scope. The whole point is no cloud.

**Real-time location sharing** — persistent tracking of live positions is a surveillance liability. FieldNotes is annotation-based (what you *saw*, not where you *are*). Live positions are opt-in only and ephemeral.

---

*Have a priority you'd push up or down? Open an issue or start a discussion.*
