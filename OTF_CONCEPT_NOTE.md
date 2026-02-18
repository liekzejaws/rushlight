# Rushlight: OTF Internet Freedom Fund - Concept Note

## Project Name
Rushlight — Offline-First Survival Computer for Internet Shutdown Environments

## Problem Statement

Internet shutdowns are a growing tool of authoritarian control. Access Now documented 283 shutdowns across 39 countries in 2023 alone. During these blackouts, at-risk communities — journalists, activists, humanitarian workers — lose access to:

- **Navigation and maps** needed for safe movement
- **Reference knowledge** for medical, legal, and survival information
- **Secure communication channels** for coordination
- **AI-assisted analysis** of complex situations

Current tools address these needs individually but require internet connectivity. No single solution provides comprehensive offline capability with security hardening for hostile environments.

## Solution

Rushlight is an open-source Android application built on OsmAnd's proven offline mapping platform. It integrates six critical capabilities into a single app that works entirely without internet:

1. **Offline Maps & Navigation** — Full turn-by-turn navigation using pre-downloaded regional maps and GPS
2. **On-Device AI Assistant** — Local language model (llama.cpp) with RAG integration for contextual, sourced answers
3. **Offline Wikipedia** — Full Wikipedia access via ZIM files in 300+ languages
4. **Peer-to-Peer Content Sharing** — Share maps, knowledge bases, AI models, and the app itself via Bluetooth/WiFi Direct
5. **Morse Code Communications** — Send/receive via flashlight or audio when all other channels are compromised
6. **Security Suite** — Encrypted storage, biometric lock, emergency wipe, and stealth mode (hidden app with dialer code access)

## Target Users

- Journalists in conflict zones and under authoritarian regimes
- Human rights activists facing surveillance and detention risk
- Humanitarian workers in disaster areas with destroyed infrastructure
- Researchers and election observers in restricted environments
- At-risk communities subject to internet shutdowns

## Technical Approach

- **Platform:** Android (covers 75%+ of global smartphone users, especially in target regions)
- **Architecture:** All processing on-device, zero server dependency
- **AI Runtime:** llama.cpp for efficient on-device inference, supporting models from 1B-7B parameters
- **Maps:** OsmAnd's OpenStreetMap-based engine, trusted by humanitarian organizations
- **Knowledge:** Kiwix ZIM format for offline Wikipedia (established standard)
- **P2P Transport:** Bluetooth Low Energy for discovery, WiFi Direct for file transfer
- **Security:** SQLCipher encrypted storage, Android Keystore integration, component-level stealth
- **License:** GPLv3 (fully open-source)

## Current Status

- Working prototype (v0.1) with all six modules implemented
- Tested on Pixel 9a running Android 15
- Source code available on GitHub
- ~14,700+ lines of custom code across 104 Java files
- Built on OsmAnd's mature codebase (10+ years, millions of users)

## Requested Funding & Deliverables

### Phase 1: Security Hardening (3 months)
- Professional security audit by independent firm
- Penetration testing of P2P transport layer
- Cryptographic review of encrypted storage
- Remediation of any findings

### Phase 2: Usability & Field Testing (3 months)
- UX testing with journalists and activists (via partner organizations)
- Field testing in relevant environments
- Performance optimization on low-end devices
- Accessibility improvements

### Phase 3: Localization & Distribution (3 months)
- Multi-language UI localization (Arabic, Farsi, Burmese, Ukrainian, Spanish, French)
- Regional content packages (pre-bundled maps + Wikipedia for high-priority regions)
- APK sideloading distribution for regions where app stores are restricted
- Documentation and training materials

### Phase 4: Sustainability (3 months)
- Community contributor onboarding
- Partnership with digital rights organizations
- Long-term maintenance plan
- Feature roadmap based on field feedback

## Sustainability Plan

- Core infrastructure leverages OsmAnd's existing community and update pipeline
- Open-source model enables community contributions
- Partnerships with organizations like Access Now, CPJ, EFF for distribution
- No recurring server costs (fully offline architecture)
- Modular design allows features to be maintained independently

## Why OTF

OTF's mission to support internet freedom directly aligns with Rushlight's purpose. Rushlight specifically addresses OTF priority areas:

- **Censorship circumvention:** P2P sharing enables content distribution without internet
- **Surveillance resistance:** Stealth mode, encrypted storage, and emergency wipe protect users
- **Offline resilience:** Every feature works without any network connectivity
- **Open source:** GPLv3 license, full transparency
- **Sustainability:** Built on proven platform with clear maintenance path

## Team

[To be filled — project lead, security advisor, UX researcher]

## Contact

[To be filled]
