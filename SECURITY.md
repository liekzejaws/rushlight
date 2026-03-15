# Security Policy

## Reporting a Vulnerability

Rushlight is a security-sensitive application used by journalists, activists, and humanitarian workers in hostile environments. We take security issues seriously.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please report vulnerabilities privately:

1. **GitHub Security Advisories** (preferred): Use [GitHub's private vulnerability reporting](https://github.com/liekzejaws/rushlight/security/advisories/new)
2. **Direct contact**: Reach out via the maintainer's [GitHub profile](https://github.com/liekzejaws)

### What to include

- Description of the vulnerability
- Steps to reproduce
- Affected component (FieldNotes crypto, P2P transport, panic wipe, encrypted chat, etc.)
- Potential impact assessment
- Suggested fix (if you have one)

### Response timeline

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 7 days
- **Disclosure embargo**: 30 days from report (coordinated disclosure)

We will credit reporters in the release notes unless they prefer to remain anonymous.

## Scope

Areas of particular interest for security review:

| Component | Files | What to look for |
|-----------|-------|------------------|
| FieldNotes ECDSA signing | `OsmAnd/src/net/osmand/plus/fieldnotes/FieldNoteSigner.java` | Key generation, signature verification, trust model |
| Encrypted chat | `OsmAnd/src/net/osmand/plus/security/` | SQLCipher usage, key derivation, panic wipe completeness |
| P2P transport | `OsmAnd/src/net/osmand/plus/plugins/p2pshare/` | BLE/WiFi Direct auth, gossip protocol integrity |
| Panic wipe | `OsmAnd/src/net/osmand/plus/security/PanicWipeManager.java` | Data destruction completeness, key material cleanup |

See [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md) for the full threat model.

## Supported Versions

| Version | Supported |
|---------|-----------|
| v1.4.x (current) | Yes |
| < v1.4 | No |
