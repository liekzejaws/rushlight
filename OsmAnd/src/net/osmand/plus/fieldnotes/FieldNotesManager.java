/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.fieldnotes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central manager for FieldNotes — the Rushlight shared map annotation system.
 *
 * Architecture follows ATAK's CotDispatcher pattern:
 * - Internal dispatch: listeners notified of local CRUD events (this phase)
 * - External dispatch: P2P mesh broadcast via Rushlight transport (Phase 2)
 * - Tool dispatch: LLM query/create tools (Phase 3)
 *
 * Lazily initialized in OsmandApplication (same pattern as SecurityManager).
 */
public class FieldNotesManager {

	private static final Log LOG = PlatformUtil.getLog(FieldNotesManager.class);

	private final OsmandApplication app;
	private final FieldNotesDbHelper dbHelper;
	private final FieldNoteSigner signer;
	private final CopyOnWriteArrayList<FieldNoteListener> listeners = new CopyOnWriteArrayList<>();

	// Track which notes this device has voted on (DB-backed with in-memory cache)
	private final Set<String> votedNoteIds;

	/**
	 * Listener interface for FieldNote events.
	 * Follows ATAK's observer pattern — internal components (map layer, tools panel)
	 * register to receive updates without tight coupling.
	 */
	public interface FieldNoteListener {
		void onFieldNoteAdded(@NonNull FieldNote note);
		void onFieldNoteDeleted(@NonNull String noteId);
		default void onFieldNoteScoreChanged(@NonNull String noteId, int newScore) {}
	}

	/**
	 * Broadcaster interface for P2P sync.
	 * Decouples FieldNotes from the transport layer — registered by OsmandApplication
	 * via a lambda that resolves TransportManager at call time.
	 */
	public interface FieldNoteBroadcaster {
		void broadcast(@NonNull JSONObject noteJson);
	}

	@Nullable
	private FieldNoteBroadcaster broadcaster;

	public FieldNotesManager(@NonNull OsmandApplication app) {
		this.app = app;
		this.dbHelper = new FieldNotesDbHelper(app);
		this.signer = new FieldNoteSigner(app);

		// Load persisted vote state into in-memory cache
		this.votedNoteIds = new HashSet<>(dbHelper.getAllVotedNoteIds());

		// ATAK-style stale event cleanup on init
		int expired = dbHelper.deleteExpiredNotes();
		if (expired > 0) {
			LOG.info("FieldNotes startup: cleaned " + expired + " expired notes");
		}

		LOG.info("FieldNotesManager initialized. " + dbHelper.getNoteCount() + " active notes, "
				+ votedNoteIds.size() + " votes loaded.");
	}

	/**
	 * Get the FieldNote signer for direct access (e.g., panic wipe).
	 */
	@NonNull
	public FieldNoteSigner getSigner() {
		return signer;
	}

	/**
	 * Get the DB helper for direct access (e.g., panic wipe).
	 */
	@NonNull
	public FieldNotesDbHelper getDbHelper() {
		return dbHelper;
	}

	// --- Public API ---

	/**
	 * Create a new FieldNote at the given coordinates.
	 * Generates content-addressed ID and device author hash automatically.
	 *
	 * @return the created FieldNote, or null on failure
	 */
	@Nullable
	public FieldNote createNote(double lat, double lon,
			@NonNull FieldNote.Category category,
			@NonNull String title, @NonNull String note, int ttlHours) {

		String authorId = getDeviceAuthorId();
		long timestamp = System.currentTimeMillis();

		FieldNote fieldNote = new FieldNote(lat, lon, category, title, note,
				timestamp, authorId, ttlHours);

		// Step 5: Sign the note before storage and broadcast
		signer.sign(fieldNote);

		if (dbHelper.addNote(fieldNote)) {
			notifyNoteAdded(fieldNote);
			// Phase 2: Broadcast to connected P2P peers
			if (broadcaster != null) {
				try {
					broadcaster.broadcast(toSyncJson(fieldNote));
				} catch (JSONException e) {
					LOG.error("Broadcast serialize failed: " + e.getMessage());
				}
			}
			return fieldNote;
		}
		return null;
	}

	/**
	 * Create a FieldNote with default TTL (1 week).
	 */
	@Nullable
	public FieldNote createNote(double lat, double lon,
			@NonNull FieldNote.Category category,
			@NonNull String title, @NonNull String note) {
		return createNote(lat, lon, category, title, note, FieldNote.DEFAULT_TTL_HOURS);
	}

	/**
	 * Delete a FieldNote by ID.
	 * Only the note author should delete (enforced in UI, not here).
	 */
	public boolean deleteNote(@NonNull String noteId) {
		if (dbHelper.deleteNote(noteId)) {
			notifyNoteDeleted(noteId);
			return true;
		}
		return false;
	}

	/**
	 * Get a single FieldNote by ID.
	 */
	@Nullable
	public FieldNote getNoteById(@NonNull String id) {
		return dbHelper.getNoteById(id);
	}

	/**
	 * Get all non-expired FieldNotes.
	 * Used by the map overlay layer.
	 */
	@NonNull
	public List<FieldNote> getAllNotes() {
		return dbHelper.getAllNotes();
	}

	/**
	 * Spatial query for notes within a radius.
	 * Backend for the future LLM tool: query_fieldnotes.
	 */
	@NonNull
	public List<FieldNote> getNotesInRadius(double lat, double lon, double radiusKm) {
		return dbHelper.getNotesInRadius(lat, lon, radiusKm, null, 0);
	}

	/**
	 * Spatial query with category filter and result limit.
	 */
	@NonNull
	public List<FieldNote> getNotesInRadius(double lat, double lon, double radiusKm,
			@Nullable String[] categories, int maxResults) {
		return dbHelper.getNotesInRadius(lat, lon, radiusKm, categories, maxResults);
	}

	/**
	 * Get total count of active (non-expired) notes.
	 */
	public int getNoteCount() {
		return dbHelper.getNoteCount();
	}

	/**
	 * Manually trigger expired note cleanup.
	 */
	public int cleanupExpired() {
		return dbHelper.deleteExpiredNotes();
	}

	// --- Voting / Trust (Step 4) ---

	/**
	 * Upvote a FieldNote. Each device can only vote once per note (in-memory tracking).
	 * Broadcasts the vote to connected peers.
	 *
	 * @return true if vote was applied, false if already voted or note not found
	 */
	public boolean upvoteNote(@NonNull String noteId) {
		if (votedNoteIds.contains(noteId)) return false;
		FieldNote note = dbHelper.getNoteById(noteId);
		if (note == null) return false;

		int newScore = note.getScore() + 1;
		dbHelper.updateScore(noteId, newScore);
		note.setScore(newScore);
		dbHelper.addVote(noteId, 1);
		votedNoteIds.add(noteId);
		broadcastVote(noteId, 1);
		notifyScoreChanged(noteId, newScore);
		return true;
	}

	/**
	 * Downvote a FieldNote. Each device can only vote once per note (in-memory tracking).
	 * Broadcasts the vote to connected peers.
	 *
	 * @return true if vote was applied, false if already voted or note not found
	 */
	public boolean downvoteNote(@NonNull String noteId) {
		if (votedNoteIds.contains(noteId)) return false;
		FieldNote note = dbHelper.getNoteById(noteId);
		if (note == null) return false;

		int newScore = note.getScore() - 1;
		dbHelper.updateScore(noteId, newScore);
		note.setScore(newScore);
		dbHelper.addVote(noteId, -1);
		votedNoteIds.add(noteId);
		broadcastVote(noteId, -1);
		notifyScoreChanged(noteId, newScore);
		return true;
	}

	/**
	 * Check if this device has already voted on a note.
	 */
	public boolean hasVoted(@NonNull String noteId) {
		return votedNoteIds.contains(noteId);
	}

	/**
	 * Broadcast a vote to connected P2P peers.
	 * Reuses the FieldNote broadcast channel with a different JSON type field.
	 */
	private void broadcastVote(@NonNull String noteId, int delta) {
		if (broadcaster == null) return;
		try {
			JSONObject json = new JSONObject();
			json.put("type", "fieldnote_vote");
			json.put("v", 1);
			json.put("id", noteId);
			json.put("delta", delta);
			json.put("author", getDeviceAuthorId());
			broadcaster.broadcast(json);
		} catch (JSONException e) {
			LOG.error("Vote broadcast failed: " + e.getMessage());
		}
	}

	/**
	 * Receive a vote from a P2P peer.
	 * Applies the delta to the local note's score.
	 */
	public void receiveVoteFromPeer(@NonNull String jsonStr, @NonNull String peerName) {
		try {
			JSONObject json = new JSONObject(jsonStr);
			if (!"fieldnote_vote".equals(json.optString("type"))) return;

			String noteId = json.getString("id");
			int delta = json.getInt("delta");

			FieldNote note = dbHelper.getNoteById(noteId);
			if (note == null) return;

			int newScore = note.getScore() + delta;
			dbHelper.updateScore(noteId, newScore);
			LOG.info("Vote from " + peerName + ": "
					+ noteId.substring(0, Math.min(8, noteId.length()))
					+ " delta=" + delta + " newScore=" + newScore);
			notifyScoreChanged(noteId, newScore);
		} catch (JSONException e) {
			LOG.error("Invalid vote packet from " + peerName + ": " + e.getMessage());
		}
	}

	private void notifyScoreChanged(@NonNull String noteId, int newScore) {
		for (FieldNoteListener listener : listeners) {
			try {
				listener.onFieldNoteScoreChanged(noteId, newScore);
			} catch (Exception e) {
				LOG.error("Listener error on scoreChanged: " + e.getMessage());
			}
		}
	}

	// --- P2P sync (Phase 2) ---

	/**
	 * Set the broadcaster for P2P sync.
	 * Called from OsmandApplication.getFieldNotesManager() with a lambda
	 * that resolves the transport at call time.
	 */
	public void setBroadcaster(@Nullable FieldNoteBroadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}

	/**
	 * Receive a FieldNote from a P2P peer.
	 * Validates, deduplicates (check-then-insert), increments confirmations.
	 *
	 * @param jsonStr  JSON string from the transport layer
	 * @param peerName Human-readable peer identifier for logging
	 * @return true if the note was new (inserted), false if duplicate or invalid
	 */
	public boolean receiveFromPeer(@NonNull String jsonStr, @NonNull String peerName) {
		FieldNote note = fromSyncJson(jsonStr);
		if (note == null) {
			LOG.warn("Ignoring invalid FieldNote from peer: " + peerName);
			return false;
		}
		if (note.isExpired()) {
			LOG.info("Ignoring expired FieldNote from peer: " + peerName);
			return false;
		}

		// Verify signature if present (hard enforcement — reject invalid signatures)
		if (FieldNoteSigner.isSigned(note)) {
			boolean valid = FieldNoteSigner.verify(note);
			if (!valid) {
				LOG.warn("REJECTING FieldNote from " + peerName + " with INVALID signature: "
						+ note.getId().substring(0, Math.min(8, note.getId().length())));
				return false;
			}
			LOG.info("FieldNote from " + peerName + " signature verified: "
					+ note.getId().substring(0, Math.min(8, note.getId().length())));
		}

		// Check for duplicate (content-addressed ID)
		FieldNote existing = dbHelper.getNoteById(note.getId());
		if (existing != null) {
			// Already have it — increment confirmations
			int newConfirms = Math.max(existing.getConfirmations(), note.getConfirmations()) + 1;
			dbHelper.updateConfirmations(note.getId(), newConfirms);
			LOG.debug("Duplicate FieldNote from " + peerName
					+ ", updated confirms=" + newConfirms);
			return false;
		}

		// New note — insert + notify listeners (map layer refreshes)
		dbHelper.addNote(note);
		LOG.info("Received new FieldNote from " + peerName + ": " + note);
		notifyNoteAdded(note);
		return true;
	}

	/**
	 * Gossip all local FieldNotes to the currently connected peer.
	 * Called by P2pShareManager.onConnected() after a peer connects.
	 */
	public void gossipAllNotesToPeer() {
		if (broadcaster == null) return;
		List<FieldNote> notes = getAllNotes();
		if (notes.isEmpty()) return;
		LOG.info("Gossiping " + notes.size() + " FieldNotes to connected peer");
		for (FieldNote note : notes) {
			try {
				broadcaster.broadcast(toSyncJson(note));
			} catch (JSONException e) {
				LOG.error("Gossip serialize failed for note " + note.getId());
			}
		}
	}

	// --- Device identity ---

	/**
	 * Get the anonymous device author ID for FieldNotes.
	 * Derived from the ECDSA signing public key hash (Step 5), truncated to 16 hex chars.
	 *
	 * This is the Rushlight equivalent of ATAK's callsign, but privacy-preserving:
	 * unique per device keypair, anonymous, verifiable against the public key.
	 *
	 * If the signing keypair is cleared (panic wipe), a new one is generated on
	 * next use, giving the device a fresh identity.
	 */
	@NonNull
	public String getDeviceAuthorId() {
		return signer.getAuthorId();
	}

	// --- JSON serialization (ready for Phase 2 P2P sync) ---

	/**
	 * Serialize a FieldNote to the P2P sync JSON format.
	 * Packet is designed to be &lt;1KB for mesh transport efficiency.
	 *
	 * Format matches FIELDNOTES-BUILD.md sync packet spec.
	 */
	@NonNull
	public static JSONObject toSyncJson(@NonNull FieldNote note) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("type", "fieldnote");
		json.put("v", 2);
		json.put("id", note.getId());
		json.put("lat", note.getLat());
		json.put("lon", note.getLon());
		json.put("category", note.getCategory().getKey());
		json.put("title", note.getTitle());
		json.put("note", note.getNote());
		json.put("ts", note.getTimestamp());
		json.put("author", note.getAuthorId());
		json.put("ttl", note.getTtlHours());
		json.put("confirms", note.getConfirmations());
		// Step 5: include signature + public key (nullable for backward compat)
		if (note.getSignature() != null) {
			json.put("sig", note.getSignature());
		}
		if (note.getPublicKey() != null) {
			json.put("pubkey", note.getPublicKey());
		}
		return json;
	}

	/**
	 * Deserialize a FieldNote from a P2P sync JSON packet.
	 * Validates schema version and required fields.
	 *
	 * @return the parsed FieldNote, or null if the packet is invalid
	 */
	@Nullable
	public static FieldNote fromSyncJson(@NonNull String jsonStr) {
		try {
			JSONObject json = new JSONObject(jsonStr);

			// Validate packet type and version
			if (!"fieldnote".equals(json.optString("type"))) {
				LOG.warn("Invalid sync packet type: " + json.optString("type"));
				return null;
			}
			int version = json.optInt("v", 0);
			if (version < 1) {
				LOG.warn("Unsupported sync packet version: " + version);
				return null;
			}

			FieldNote note = new FieldNote(
					json.getString("id"),
					json.getDouble("lat"),
					json.getDouble("lon"),
					FieldNote.Category.fromKey(json.getString("category")),
					json.getString("title"),
					json.getString("note"),
					json.getLong("ts"),
					json.getString("author"),
					json.optInt("ttl", FieldNote.DEFAULT_TTL_HOURS),
					json.optInt("confirms", 1),
					0, // score not transmitted in sync packet
					json.optString("sig", null),    // Step 5: signature (nullable)
					json.optString("pubkey", null)  // Step 5: public key (nullable)
			);
			return note;
		} catch (JSONException e) {
			LOG.error("Failed to parse sync packet: " + e.getMessage());
			return null;
		}
	}

	// --- Listener management (ATAK CotDispatcher pattern) ---

	public void addListener(@NonNull FieldNoteListener listener) {
		listeners.addIfAbsent(listener);
	}

	public void removeListener(@NonNull FieldNoteListener listener) {
		listeners.remove(listener);
	}

	private void notifyNoteAdded(@NonNull FieldNote note) {
		for (FieldNoteListener listener : listeners) {
			try {
				listener.onFieldNoteAdded(note);
			} catch (Exception e) {
				LOG.error("Listener error on noteAdded: " + e.getMessage());
			}
		}
	}

	private void notifyNoteDeleted(@NonNull String noteId) {
		for (FieldNoteListener listener : listeners) {
			try {
				listener.onFieldNoteDeleted(noteId);
			} catch (Exception e) {
				LOG.error("Listener error on noteDeleted: " + e.getMessage());
			}
		}
	}
}
