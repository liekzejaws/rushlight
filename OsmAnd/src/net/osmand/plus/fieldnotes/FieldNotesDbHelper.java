package net.osmand.plus.fieldnotes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection;
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQLite database helper for FieldNotes.
 *
 * Follows the MapMarkersDbHelper pattern: uses OsmAnd's SQLiteAPI wrapper
 * (app.getSQLiteAPI()) rather than Android's SQLiteOpenHelper or Room.
 *
 * Spatial queries use a bounding-box pre-filter + Haversine distance check
 * in Java, avoiding any SpatiaLite dependency.
 */
public class FieldNotesDbHelper {

	private static final Log LOG = PlatformUtil.getLog(FieldNotesDbHelper.class);

	private static final int DB_VERSION = 3;
	public static final String DB_NAME = "fieldnotes_db";

	// Table and column names
	private static final String TABLE_NAME = "field_notes";
	private static final String COL_ID = "note_id";
	private static final String COL_LAT = "lat";
	private static final String COL_LON = "lon";
	private static final String COL_CATEGORY = "category";
	private static final String COL_TITLE = "title";
	private static final String COL_NOTE = "note";
	private static final String COL_TIMESTAMP = "timestamp";
	private static final String COL_AUTHOR_ID = "author_id";
	private static final String COL_TTL_HOURS = "ttl_hours";
	private static final String COL_CONFIRMATIONS = "confirmations";
	private static final String COL_SCORE = "score";
	private static final String COL_SIGNATURE = "signature";       // Step 5: crypto signing
	private static final String COL_PUBLIC_KEY = "public_key";     // Step 5: crypto signing

	// Voted notes table (v3) — persists which notes this device has voted on
	private static final String VOTED_TABLE = "voted_notes";
	private static final String VOTED_COL_NOTE_ID = "note_id";
	private static final String VOTED_COL_DIRECTION = "vote_direction";
	private static final String VOTED_COL_VOTED_AT = "voted_at";

	private static final String VOTED_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
			VOTED_TABLE + " (" +
			VOTED_COL_NOTE_ID + " TEXT PRIMARY KEY, " +
			VOTED_COL_DIRECTION + " INTEGER, " +
			VOTED_COL_VOTED_AT + " INTEGER);";

	private static final String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
			TABLE_NAME + " (" +
			COL_ID + " TEXT PRIMARY KEY, " +
			COL_LAT + " REAL, " +
			COL_LON + " REAL, " +
			COL_CATEGORY + " TEXT, " +
			COL_TITLE + " TEXT, " +
			COL_NOTE + " TEXT, " +
			COL_TIMESTAMP + " INTEGER, " +
			COL_AUTHOR_ID + " TEXT, " +
			COL_TTL_HOURS + " INTEGER, " +
			COL_CONFIRMATIONS + " INTEGER, " +
			COL_SCORE + " INTEGER, " +
			COL_SIGNATURE + " TEXT, " +
			COL_PUBLIC_KEY + " TEXT);";

	private static final String TABLE_SELECT = "SELECT " +
			COL_ID + ", " + COL_LAT + ", " + COL_LON + ", " +
			COL_CATEGORY + ", " + COL_TITLE + ", " + COL_NOTE + ", " +
			COL_TIMESTAMP + ", " + COL_AUTHOR_ID + ", " + COL_TTL_HOURS + ", " +
			COL_CONFIRMATIONS + ", " + COL_SCORE + ", " +
			COL_SIGNATURE + ", " + COL_PUBLIC_KEY +
			" FROM " + TABLE_NAME;

	// Earth radius in km for Haversine
	private static final double EARTH_RADIUS_KM = 6371.0;

	private final OsmandApplication app;

	public FieldNotesDbHelper(@NonNull OsmandApplication app) {
		this.app = app;
	}

	// --- Connection management (MapMarkersDbHelper pattern) ---

	@Nullable
	private SQLiteConnection openConnection(boolean readonly) {
		SQLiteConnection conn = app.getSQLiteAPI().getOrCreateDatabase(DB_NAME, readonly);
		if (conn == null) {
			return null;
		}
		if (conn.getVersion() < DB_VERSION) {
			if (readonly) {
				conn.close();
				conn = app.getSQLiteAPI().getOrCreateDatabase(DB_NAME, false);
			}
			if (conn != null) {
				int version = conn.getVersion();
				conn.setVersion(DB_VERSION);
				if (version == 0) {
					onCreate(conn);
				} else {
					onUpgrade(conn, version, DB_VERSION);
				}
			}
		}
		return conn;
	}

	private void onCreate(@NonNull SQLiteConnection db) {
		db.execSQL(TABLE_CREATE);
		db.execSQL(VOTED_TABLE_CREATE);
		LOG.info("FieldNotes database created (v" + DB_VERSION + ")");
	}

	private void onUpgrade(@NonNull SQLiteConnection db, int oldVersion, int newVersion) {
		LOG.info("FieldNotes database upgraded from v" + oldVersion + " to v" + newVersion);
		if (oldVersion < 2) {
			// Step 5: Add crypto signing columns (nullable for backward compat)
			db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_SIGNATURE + " TEXT");
			db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_PUBLIC_KEY + " TEXT");
			LOG.info("FieldNotes: added signature + public_key columns (v2)");
		}
		if (oldVersion < 3) {
			db.execSQL(VOTED_TABLE_CREATE);
			LOG.info("FieldNotes: added voted_notes table (v3)");
		}
	}

	// --- CRUD operations ---

	/**
	 * Insert a FieldNote. Uses INSERT OR IGNORE to handle content-addressed
	 * ID collisions (same note arriving via multiple P2P paths).
	 */
	public boolean addNote(@NonNull FieldNote note) {
		SQLiteConnection db = openConnection(false);
		if (db == null) return false;
		try {
			db.execSQL(
					"INSERT OR IGNORE INTO " + TABLE_NAME + " (" +
							COL_ID + ", " + COL_LAT + ", " + COL_LON + ", " +
							COL_CATEGORY + ", " + COL_TITLE + ", " + COL_NOTE + ", " +
							COL_TIMESTAMP + ", " + COL_AUTHOR_ID + ", " + COL_TTL_HOURS + ", " +
							COL_CONFIRMATIONS + ", " + COL_SCORE + ", " +
							COL_SIGNATURE + ", " + COL_PUBLIC_KEY +
							") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					new Object[]{
							note.getId(), note.getLat(), note.getLon(),
							note.getCategory().getKey(), note.getTitle(), note.getNote(),
							note.getTimestamp(), note.getAuthorId(), note.getTtlHours(),
							note.getConfirmations(), note.getScore(),
							note.getSignature(), note.getPublicKey()
					});
			LOG.info("FieldNote added: " + note);
			return true;
		} catch (Exception e) {
			LOG.error("Failed to add FieldNote: " + e.getMessage());
			return false;
		} finally {
			db.close();
		}
	}

	/**
	 * Delete a FieldNote by its content-addressed ID.
	 */
	public boolean deleteNote(@NonNull String id) {
		SQLiteConnection db = openConnection(false);
		if (db == null) return false;
		try {
			db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COL_ID + " = ?",
					new Object[]{id});
			LOG.info("FieldNote deleted: " + id);
			return true;
		} catch (Exception e) {
			LOG.error("Failed to delete FieldNote: " + e.getMessage());
			return false;
		} finally {
			db.close();
		}
	}

	/**
	 * Get a single FieldNote by ID, or null if not found.
	 */
	@Nullable
	public FieldNote getNoteById(@NonNull String id) {
		SQLiteConnection db = openConnection(true);
		if (db == null) return null;
		try {
			SQLiteCursor cursor = db.rawQuery(
					TABLE_SELECT + " WHERE " + COL_ID + " = ?",
					new String[]{id});
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						return readNote(cursor);
					}
				} finally {
					cursor.close();
				}
			}
		} catch (Exception e) {
			LOG.error("Failed to get FieldNote by ID: " + e.getMessage());
		} finally {
			db.close();
		}
		return null;
	}

	/**
	 * Get all non-expired FieldNotes.
	 */
	@NonNull
	public List<FieldNote> getAllNotes() {
		List<FieldNote> notes = new ArrayList<>();
		SQLiteConnection db = openConnection(true);
		if (db == null) return notes;
		try {
			SQLiteCursor cursor = db.rawQuery(TABLE_SELECT, null);
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						do {
							FieldNote note = readNote(cursor);
							if (!note.isExpired()) {
								notes.add(note);
							}
						} while (cursor.moveToNext());
					}
				} finally {
					cursor.close();
				}
			}
		} catch (Exception e) {
			LOG.error("Failed to get all FieldNotes: " + e.getMessage());
		} finally {
			db.close();
		}
		return notes;
	}

	/**
	 * Spatial query: get FieldNotes within a radius of a point.
	 * Uses bounding-box SQL pre-filter + Haversine post-filter in Java.
	 * Optionally filter by categories.
	 *
	 * This is the backend for the future LLM tool: query_fieldnotes.
	 *
	 * @param lat        center latitude
	 * @param lon        center longitude
	 * @param radiusKm   search radius in kilometers
	 * @param categories optional category filter (null = all categories)
	 * @param maxResults maximum number of results (0 = unlimited)
	 * @return list of FieldNotes sorted by distance (nearest first)
	 */
	@NonNull
	public List<FieldNote> getNotesInRadius(double lat, double lon, double radiusKm,
			@Nullable String[] categories, int maxResults) {

		// Bounding box pre-filter: ~111km per degree latitude
		double latDelta = radiusKm / 111.0;
		double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

		double minLat = lat - latDelta;
		double maxLat = lat + latDelta;
		double minLon = lon - lonDelta;
		double maxLon = lon + lonDelta;

		List<FieldNote> notes = new ArrayList<>();
		SQLiteConnection db = openConnection(true);
		if (db == null) return notes;

		try {
			StringBuilder query = new StringBuilder(TABLE_SELECT);
			query.append(" WHERE ")
					.append(COL_LAT).append(" BETWEEN ? AND ? AND ")
					.append(COL_LON).append(" BETWEEN ? AND ?");

			List<String> args = new ArrayList<>();
			args.add(String.valueOf(minLat));
			args.add(String.valueOf(maxLat));
			args.add(String.valueOf(minLon));
			args.add(String.valueOf(maxLon));

			// Category filter
			if (categories != null && categories.length > 0) {
				StringBuilder catFilter = new StringBuilder(" AND " + COL_CATEGORY + " IN (");
				for (int i = 0; i < categories.length; i++) {
					if (i > 0) catFilter.append(",");
					catFilter.append("?");
					args.add(categories[i]);
				}
				catFilter.append(")");
				query.append(catFilter);
			}

			SQLiteCursor cursor = db.rawQuery(query.toString(),
					args.toArray(new String[0]));

			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						do {
							FieldNote note = readNote(cursor);
							if (!note.isExpired()) {
								double dist = haversineDistance(lat, lon,
										note.getLat(), note.getLon());
								if (dist <= radiusKm) {
									notes.add(note);
								}
							}
						} while (cursor.moveToNext());
					}
				} finally {
					cursor.close();
				}
			}
		} catch (Exception e) {
			LOG.error("Failed spatial query: " + e.getMessage());
		} finally {
			db.close();
		}

		// Sort by distance (nearest first)
		notes.sort((a, b) -> {
			double distA = haversineDistance(lat, lon, a.getLat(), a.getLon());
			double distB = haversineDistance(lat, lon, b.getLat(), b.getLon());
			return Double.compare(distA, distB);
		});

		// Apply max results limit
		if (maxResults > 0 && notes.size() > maxResults) {
			return new ArrayList<>(notes.subList(0, maxResults));
		}

		return notes;
	}

	/**
	 * Delete all expired FieldNotes.
	 * Called on app startup — equivalent to ATAK's stale event cleanup.
	 *
	 * @return number of notes deleted
	 */
	public int deleteExpiredNotes() {
		SQLiteConnection db = openConnection(false);
		if (db == null) return 0;

		int deleted = 0;
		try {
			// Get all notes and check expiry in Java (TTL=0 means permanent)
			SQLiteCursor cursor = db.rawQuery(
					"SELECT " + COL_ID + ", " + COL_TIMESTAMP + ", " + COL_TTL_HOURS +
							" FROM " + TABLE_NAME, null);
			if (cursor != null) {
				List<String> expiredIds = new ArrayList<>();
				try {
					long now = System.currentTimeMillis();
					if (cursor.moveToFirst()) {
						do {
							int ttl = cursor.getInt(2);
							if (ttl > 0) {
								long ts = cursor.getLong(1);
								long expiryMs = ts + ((long) ttl * 3600_000L);
								if (now > expiryMs) {
									expiredIds.add(cursor.getString(0));
								}
							}
						} while (cursor.moveToNext());
					}
				} finally {
					cursor.close();
				}

				// Delete expired notes
				for (String id : expiredIds) {
					db.execSQL("DELETE FROM " + TABLE_NAME +
							" WHERE " + COL_ID + " = ?", new Object[]{id});
					deleted++;
				}
			}
			if (deleted > 0) {
				LOG.info("Cleaned up " + deleted + " expired FieldNotes");
			}
		} catch (Exception e) {
			LOG.error("Failed to clean expired FieldNotes: " + e.getMessage());
		} finally {
			db.close();
		}
		return deleted;
	}

	/**
	 * Get total count of non-expired FieldNotes.
	 */
	public int getNoteCount() {
		SQLiteConnection db = openConnection(true);
		if (db == null) return 0;
		try {
			SQLiteCursor cursor = db.rawQuery(
					"SELECT COUNT(*) FROM " + TABLE_NAME, null);
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						return cursor.getInt(0);
					}
				} finally {
					cursor.close();
				}
			}
		} catch (Exception e) {
			LOG.error("Failed to count FieldNotes: " + e.getMessage());
		} finally {
			db.close();
		}
		return 0;
	}

	/**
	 * Update confirmations count for a note (P2P sync, Phase 2).
	 */
	public boolean updateConfirmations(@NonNull String id, int confirmations) {
		SQLiteConnection db = openConnection(false);
		if (db == null) return false;
		try {
			db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_CONFIRMATIONS + " = ? WHERE " +
					COL_ID + " = ?", new Object[]{confirmations, id});
			return true;
		} catch (Exception e) {
			LOG.error("Failed to update confirmations: " + e.getMessage());
			return false;
		} finally {
			db.close();
		}
	}

	/**
	 * Update score for a note (voting, Phase 4).
	 */
	public boolean updateScore(@NonNull String id, int score) {
		SQLiteConnection db = openConnection(false);
		if (db == null) return false;
		try {
			db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_SCORE + " = ? WHERE " +
					COL_ID + " = ?", new Object[]{score, id});
			return true;
		} catch (Exception e) {
			LOG.error("Failed to update score: " + e.getMessage());
			return false;
		} finally {
			db.close();
		}
	}

	// --- Vote persistence (v3) ---

	/**
	 * Record that this device voted on a note.
	 * @param noteId note content-addressed ID
	 * @param direction +1 for upvote, -1 for downvote
	 */
	public boolean addVote(@NonNull String noteId, int direction) {
		SQLiteConnection db = openConnection(false);
		if (db == null) return false;
		try {
			db.execSQL("INSERT OR REPLACE INTO " + VOTED_TABLE + " (" +
							VOTED_COL_NOTE_ID + ", " + VOTED_COL_DIRECTION + ", " + VOTED_COL_VOTED_AT +
							") VALUES (?, ?, ?)",
					new Object[]{noteId, direction, System.currentTimeMillis()});
			return true;
		} catch (Exception e) {
			LOG.error("Failed to record vote: " + e.getMessage());
			return false;
		} finally {
			db.close();
		}
	}

	/**
	 * Check if this device has voted on a note.
	 */
	public boolean hasVoted(@NonNull String noteId) {
		SQLiteConnection db = openConnection(true);
		if (db == null) return false;
		try {
			SQLiteCursor cursor = db.rawQuery(
					"SELECT 1 FROM " + VOTED_TABLE + " WHERE " + VOTED_COL_NOTE_ID + " = ?",
					new String[]{noteId});
			if (cursor != null) {
				try {
					return cursor.moveToFirst();
				} finally {
					cursor.close();
				}
			}
		} catch (Exception e) {
			LOG.error("Failed to check vote: " + e.getMessage());
		} finally {
			db.close();
		}
		return false;
	}

	/**
	 * Get all voted note IDs for loading into the in-memory cache.
	 */
	@NonNull
	public Set<String> getAllVotedNoteIds() {
		Set<String> ids = new HashSet<>();
		SQLiteConnection db = openConnection(true);
		if (db == null) return ids;
		try {
			SQLiteCursor cursor = db.rawQuery(
					"SELECT " + VOTED_COL_NOTE_ID + " FROM " + VOTED_TABLE, null);
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						do {
							ids.add(cursor.getString(0));
						} while (cursor.moveToNext());
					}
				} finally {
					cursor.close();
				}
			}
		} catch (Exception e) {
			LOG.error("Failed to load voted note IDs: " + e.getMessage());
		} finally {
			db.close();
		}
		return ids;
	}

	/**
	 * Wipe all FieldNotes data — notes and votes.
	 * Called during panic wipe to remove sensitive geo-pinned data.
	 */
	public void wipeAll() {
		SQLiteConnection db = openConnection(false);
		if (db == null) return;
		try {
			db.execSQL("DELETE FROM " + TABLE_NAME);
			db.execSQL("DELETE FROM " + VOTED_TABLE);
			LOG.info("FieldNotes database wiped (panic wipe)");
		} catch (Exception e) {
			LOG.error("Failed to wipe FieldNotes: " + e.getMessage());
		} finally {
			db.close();
		}
	}

	// --- Private helpers ---

	/**
	 * Read a FieldNote from the current cursor position.
	 * Column order must match TABLE_SELECT.
	 */
	@NonNull
	private FieldNote readNote(@NonNull SQLiteCursor cursor) {
		return new FieldNote(
				cursor.getString(0),                          // id
				cursor.getDouble(1),                          // lat
				cursor.getDouble(2),                          // lon
				FieldNote.Category.fromKey(cursor.getString(3)), // category
				cursor.getString(4),                          // title
				cursor.getString(5),                          // note
				cursor.getLong(6),                             // timestamp
				cursor.getString(7),                          // authorId
				cursor.getInt(8),                             // ttlHours
				cursor.getInt(9),                             // confirmations
				cursor.getInt(10),                            // score
				cursor.getString(11),                         // signature (nullable)
				cursor.getString(12)                          // publicKey (nullable)
		);
	}

	/**
	 * Haversine formula: distance between two lat/lon points in kilometers.
	 */
	private static double haversineDistance(double lat1, double lon1,
			double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLon / 2) * Math.sin(dLon / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return EARTH_RADIUS_KM * c;
	}
}
