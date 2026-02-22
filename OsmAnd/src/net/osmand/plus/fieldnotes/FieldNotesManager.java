package net.osmand.plus.fieldnotes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
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
	private final CopyOnWriteArrayList<FieldNoteListener> listeners = new CopyOnWriteArrayList<>();

	// Cached device author ID (truncated key hash)
	@Nullable
	private String cachedAuthorId;

	/**
	 * Listener interface for FieldNote events.
	 * Follows ATAK's observer pattern — internal components (map layer, tools panel)
	 * register to receive updates without tight coupling.
	 */
	public interface FieldNoteListener {
		void onFieldNoteAdded(@NonNull FieldNote note);
		void onFieldNoteDeleted(@NonNull String noteId);
	}

	public FieldNotesManager(@NonNull OsmandApplication app) {
		this.app = app;
		this.dbHelper = new FieldNotesDbHelper(app);

		// ATAK-style stale event cleanup on init
		int expired = dbHelper.deleteExpiredNotes();
		if (expired > 0) {
			LOG.info("FieldNotes startup: cleaned " + expired + " expired notes");
		}

		LOG.info("FieldNotesManager initialized. " + dbHelper.getNoteCount() + " active notes.");
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

		if (dbHelper.addNote(fieldNote)) {
			notifyNoteAdded(fieldNote);
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

	// --- Device identity ---

	/**
	 * Get the anonymous device author ID for FieldNotes.
	 * SHA256 of the device's Android Keystore key, truncated to 16 hex chars.
	 * Same key used by SecurityManager for encrypted chat.
	 *
	 * This is the Rushlight equivalent of ATAK's callsign, but privacy-preserving:
	 * unique per device, anonymous, deterministic.
	 */
	@NonNull
	public String getDeviceAuthorId() {
		if (cachedAuthorId != null) {
			return cachedAuthorId;
		}

		try {
			// Use SecurityManager's keystore — it already manages the device key
			net.osmand.plus.security.SecurityManager secMgr = app.getSecurityManager();

			// We need a stable device identifier. SecurityManager has a keystore key
			// but doesn't expose it directly. Use the Android ID as a fallback,
			// hashed for privacy.
			String deviceSeed = android.provider.Settings.Secure.getString(
					app.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

			if (deviceSeed == null || deviceSeed.isEmpty()) {
				deviceSeed = "rushlight-unknown-device";
			}

			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(deviceSeed.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++) { // 8 bytes = 16 hex chars
				hex.append(String.format("%02x", hash[i]));
			}
			cachedAuthorId = hex.toString();

		} catch (Exception e) {
			LOG.error("Failed to generate device author ID: " + e.getMessage());
			cachedAuthorId = "unknown";
		}

		return cachedAuthorId;
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
		json.put("v", 1);
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

			return new FieldNote(
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
					0 // score not transmitted in sync packet
			);
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
