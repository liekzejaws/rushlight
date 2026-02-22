# FieldNotes — Claude Code Build Brief
*For: Rushlight Android app | Feature: Shared offline map annotations*

---

## What We're Building

A Dark Souls-style shared annotation system for the Rushlight offline map. Users leave notes pinned to GPS coordinates. Notes sync through the Briar P2P mesh to other Rushlight users nearby — no internet required.

The local LLM also has access to these annotations to answer questions like "is there water nearby?" or "what's the safest route?"

---

## Prior Art — Study These First

### ATAK (Android Tactical Assault Kit)
The US military's field situational awareness app. Does exactly this. Public domain.

**Source:** https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV

Key things to study in the ATAK codebase:
- How they implement map markers / overlays on top of a map view
- The **Cursor on Target (CoT)** data format — their XML schema for geospatial events
- How they handle annotation sync between devices
- Their SQLite schema for storing local map objects

We don't need to use CoT directly (it's XML, we'll use JSON) but the data model is instructive. Borrow the concepts, not the format.

### Meshtastic
LoRa mesh networking app that shares GPS positions as map pins. Good reference for the sync packet design.

**Source:** https://github.com/meshtastic/Meshtastic-Android

Study:
- How they serialize position/waypoint packets (protobuf)
- How they handle conflict resolution when the same waypoint arrives from multiple paths
- Their channel/contact model for trust

---

## Data Model

### Annotation (local SQLite)

```kotlin
data class FieldNote(
    val id: String,          // SHA256(lat+lon+text+authorId+timestamp) — content-addressed
    val lat: Double,
    val lon: Double,
    val category: String,    // water | shelter | hazard | cache | route | medical | signal | intel
    val title: String,       // short label shown on map pin
    val note: String,        // full note text
    val timestamp: Long,     // unix ms
    val authorId: String,    // device public key hash (anonymous but unique)
    val ttlHours: Int,       // how long before expiry (default 168 = 1 week)
    val confirmations: Int,  // how many devices have seen and forwarded this
    val score: Int,          // net thumbs up/down
    val signature: String?   // crypto signature (Phase 2, null for now)
)
```

### Sync Packet (over Briar mesh)

```json
{
  "type": "fieldnote",
  "v": 1,
  "id": "sha256hash",
  "lat": 40.7128,
  "lon": -74.0060,
  "category": "hazard",
  "title": "Checkpoint",
  "note": "Heavy presence, checked 3x today. Avoid between 8am-6pm.",
  "ts": 1740196800000,
  "author": "device-hash",
  "ttl": 168,
  "confirms": 3
}
```

Packet is small — fits in a single Briar message. No images, text only for Phase 1.

---

## OsmAnd Integration

OsmAnd supports custom map overlays via the **OsmAnd AIDL API** (Android inter-process communication). Rushlight can add, update, and remove map markers programmatically.

Key AIDL calls:
```java
// Add a marker
osmandHelper.addMapMarker(lat, lon, title);

// Add a point with custom icon
osmandHelper.addFavoriteLocation(lat, lon, category, title, description, color, icon);

// Remove by ID
osmandHelper.removePoint(pointId);
```

Reference: https://github.com/osmandapp/OsmAnd/tree/master/OsmAnd/src/net/osmand/aidl

Each FieldNote category maps to a distinct OsmAnd icon and color:
- water → blue droplet
- shelter → green house
- hazard → red warning triangle
- cache → brown box
- route → orange arrow
- medical → white cross on red
- signal → purple antenna
- intel → yellow eye

---

## Briar Sync Integration

Briar handles P2P transport. FieldNotes piggyback on Briar's existing message transport.

**Approach:** Dedicated Briar "private group" or broadcast channel for FieldNotes sync. Each annotation is sent as a Briar message. When a device receives an annotation it hasn't seen (check by ID), it:
1. Validates the packet schema
2. Stores it locally
3. Increments `confirms` counter
4. Re-broadcasts to its own Briar contacts (gossip protocol)

This is epidemic/gossip propagation — same as how Briar already handles group messages. No new protocol needed, just a new message type.

**Deduplication:** Content-addressed IDs (SHA256 of core fields) mean the same annotation arriving via multiple paths is recognized as a duplicate and not stored twice.

---

## LLM Integration

The local LLM (Rushlight's on-device model) gets a tool: `query_fieldnotes`.

```
Tool: query_fieldnotes
Input: { lat, lon, radius_km, categories[], max_results }
Output: list of FieldNote objects sorted by distance

Example call:
query_fieldnotes({ lat: 40.71, lon: -74.00, radius_km: 5, categories: ["water", "shelter"] })
```

The LLM uses this when answering location-aware questions:
- "Is there water nearby?" → query water category within 5km → answer with specific annotation
- "Where can I shelter?" → query shelter category → list options with distances
- "What should I know about the route north?" → query all categories along the bearing

LLM can also **write** FieldNotes from conversation:
- User says "the bridge on the north road is washed out"
- LLM responds "Logged that as a FieldNote — others in the area will see it"
- Calls `create_fieldnote({ lat: current, lon: current, category: "route", title: "Bridge washed out", note: "North road bridge impassable as of [date]" })`

---

## Build Order

### Step 1 — Local only (start here)
- SQLite schema + FieldNote data class
- CRUD operations (create, read, list by radius, delete expired)
- OsmAnd AIDL integration — display annotations as map pins
- Basic UI: long-press map to create annotation, tap pin to view note
- Category picker, title + note fields
- TTL/expiry cleanup on app start

### Step 2 — Briar sync
- FieldNote serialization to JSON packet
- Briar message listener for incoming FieldNote packets
- Deduplication by ID
- Gossip rebroadcast to contacts
- Sync status indicator (how many confirmations)

### Step 3 — LLM integration
- `query_fieldnotes` tool implementation
- `create_fieldnote` tool implementation
- Wire into Rushlight's existing LLM tool dispatch

### Step 4 — Voting + trust (later)
- Thumbs up/down UI on annotation view
- Vote packet type in Briar sync
- Score calculation and display
- Low-score annotation fading

### Step 5 — Crypto signing (later)
- Generate device keypair on first run
- Sign annotations on creation
- Verify signatures on receipt
- Phase 2: online server sync with blockchain-backed ledger

---

## Files to Read First

Before starting, read these for Rushlight context:
- `projects/rushlight/modules/fieldnotes.md` — full feature spec
- `projects/rushlight/HARDWARE-TESTING.md` — hardware context
- `projects/rushlight/BRANDING.md` — app identity

---

## Key Constraints

- **Offline first** — nothing requires internet, ever
- **Packet size** — keep sync packets under 1KB, text only
- **No images** in Phase 1 — text annotations only
- **Privacy** — author IDs are device hashes, not names. No PII.
- **Battery** — sync should be opportunistic, not polling. Use Briar's existing connection events.
- **OsmAnd** — Rushlight uses OsmAnd as its map. Don't build a new map view.

---

## References

| Project | URL | What to borrow |
|---------|-----|----------------|
| ATAK-CIV | https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV | Map overlay architecture, CoT data model concepts |
| Meshtastic Android | https://github.com/meshtastic/Meshtastic-Android | Sync packet design, waypoint conflict resolution |
| OsmAnd AIDL | https://github.com/osmandapp/OsmAnd/tree/master/OsmAnd/src/net/osmand/aidl | Map marker API |
| Briar | https://code.briarproject.org/briar/briar | P2P transport (already integrated in Rushlight) |
