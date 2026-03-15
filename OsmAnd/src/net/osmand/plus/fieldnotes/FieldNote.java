/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.fieldnotes;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.R;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Rushlight FieldNote — a geo-pinned annotation on the map.
 *
 * Inspired by ATAK's Cursor on Target (CoT) event model:
 * - Content-addressed ID (SHA256 of core fields) for P2P dedup — like CoT UIDs
 * - TTL-based expiry — maps to CoT's time/start/stale validity window
 * - Category system — simplified version of ATAK's MIL-STD-2525 type hierarchy
 * - Anonymous author ID — device key hash, like ATAK callsigns but privacy-preserving
 *
 * Phase 1: Local storage + map display only.
 * Phase 2: P2P sync via Rushlight mesh (BLE/WiFi Direct).
 * Phase 3: LLM tool integration (query_fieldnotes / create_fieldnote).
 */
public class FieldNote {

	/**
	 * FieldNote categories — simplified MIL-STD-2525 type hierarchy.
	 * Each category maps to a distinct icon and color for map display.
	 * ATAK uses hierarchical codes like "a-f-G-I" (friendly-ground-infrastructure);
	 * we use a flat 8-category enum optimized for civilian field use.
	 */
	public enum Category {
		WATER("water", "Water Source", R.drawable.ic_action_anchor, 0xFF2196F3),
		SHELTER("shelter", "Shelter", R.drawable.ic_action_home_dark, 0xFF4CAF50),
		HAZARD("hazard", "Hazard", R.drawable.ic_action_alert, 0xFFFF4444),
		CACHE("cache", "Supply Cache", R.drawable.ic_action_storage, 0xFF795548),
		ROUTE("route", "Route Info", R.drawable.ic_action_gdirections_dark, 0xFFFF9800),
		MEDICAL("medical", "Medical", R.drawable.ic_action_plus_dark, 0xFFE91E63),
		SIGNAL("signal", "Signal/Comms", R.drawable.ic_action_antenna, 0xFF9C27B0),
		INTEL("intel", "Intelligence", R.drawable.ic_action_info_outlined, 0xFFFFEB3B);

		private final String key;
		private final String displayName;
		@DrawableRes
		private final int iconRes;
		private final int color;

		Category(@NonNull String key, @NonNull String displayName,
				@DrawableRes int iconRes, int color) {
			this.key = key;
			this.displayName = displayName;
			this.iconRes = iconRes;
			this.color = color;
		}

		@NonNull
		public String getKey() {
			return key;
		}

		@NonNull
		public String getDisplayName() {
			return displayName;
		}

		@DrawableRes
		public int getIconRes() {
			return iconRes;
		}

		/**
		 * ARGB color for map pin tinting and UI accents.
		 */
		public int getColor() {
			return color;
		}

		/**
		 * Look up Category by its string key (used in DB and JSON sync packets).
		 * Returns INTEL as fallback if key is unrecognized.
		 */
		@NonNull
		public static Category fromKey(@Nullable String key) {
			if (key != null) {
				for (Category c : values()) {
					if (c.key.equals(key)) {
						return c;
					}
				}
			}
			return INTEL; // fallback
		}
	}

	// Default TTL: 1 week (168 hours) — matches CoT "stale" window for tactical relevance
	public static final int DEFAULT_TTL_HOURS = 168;

	// Core fields — all included in content-addressed ID hash
	private String id;
	private double lat;
	private double lon;
	private Category category;
	private String title;
	private String note;
	private long timestamp;
	private String authorId;

	// Metadata fields — not part of ID hash
	private int ttlHours;
	private int confirmations; // how many devices have seen this (P2P, Phase 2)
	private int score;         // net thumbs up/down (voting, Phase 4)

	// Crypto signing fields (Step 5) — nullable for backward compat with unsigned notes
	@Nullable private String signature;  // Base64 ECDSA P-256 signature
	@Nullable private String publicKey;  // Base64 X509-encoded EC public key

	/**
	 * Create a new FieldNote with auto-generated content-addressed ID.
	 */
	public FieldNote(double lat, double lon, @NonNull Category category,
			@NonNull String title, @NonNull String note,
			long timestamp, @NonNull String authorId, int ttlHours) {
		this.lat = lat;
		this.lon = lon;
		this.category = category;
		this.title = title;
		this.note = note;
		this.timestamp = timestamp;
		this.authorId = authorId;
		this.ttlHours = ttlHours;
		this.confirmations = 1; // self-confirmation
		this.score = 0;
		this.id = generateId();
	}

	/**
	 * Reconstruct a FieldNote from database (all fields provided).
	 */
	public FieldNote(@NonNull String id, double lat, double lon,
			@NonNull Category category, @NonNull String title, @NonNull String note,
			long timestamp, @NonNull String authorId, int ttlHours,
			int confirmations, int score,
			@Nullable String signature, @Nullable String publicKey) {
		this.id = id;
		this.lat = lat;
		this.lon = lon;
		this.category = category;
		this.title = title;
		this.note = note;
		this.timestamp = timestamp;
		this.authorId = authorId;
		this.ttlHours = ttlHours;
		this.confirmations = confirmations;
		this.score = score;
		this.signature = signature;
		this.publicKey = publicKey;
	}

	// --- ID generation ---

	/**
	 * Content-addressed ID: SHA256 of core fields.
	 * Same concept as ATAK CoT UIDs — ensures identical annotations arriving
	 * via multiple P2P paths are recognized as duplicates.
	 */
	@NonNull
	private String generateId() {
		String input = lat + ":" + lon + ":" + note + ":" + authorId + ":" + timestamp;
		return sha256(input);
	}

	@NonNull
	private static String sha256(@NonNull String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is guaranteed on Android — this should never happen
			throw new RuntimeException("SHA-256 not available", e);
		}
	}

	// --- Expiry check (CoT "stale" equivalent) ---

	/**
	 * Check if this note has expired based on its TTL.
	 * Maps to ATAK CoT's stale time: time + staleDelta.
	 * TTL of 0 means permanent (never expires).
	 */
	public boolean isExpired() {
		if (ttlHours <= 0) return false; // permanent
		long expiryMs = timestamp + ((long) ttlHours * 3600_000L);
		return System.currentTimeMillis() > expiryMs;
	}

	/**
	 * Returns the expiry timestamp in unix ms, or -1 if permanent.
	 */
	public long getExpiryTime() {
		if (ttlHours <= 0) return -1;
		return timestamp + ((long) ttlHours * 3600_000L);
	}

	// --- Getters ---

	@NonNull
	public String getId() { return id; }

	public double getLat() { return lat; }

	public double getLon() { return lon; }

	@NonNull
	public Category getCategory() { return category; }

	@NonNull
	public String getTitle() { return title; }

	@NonNull
	public String getNote() { return note; }

	public long getTimestamp() { return timestamp; }

	@NonNull
	public String getAuthorId() { return authorId; }

	public int getTtlHours() { return ttlHours; }

	public int getConfirmations() { return confirmations; }

	public int getScore() { return score; }

	// --- Crypto signing (Step 5) ---

	@Nullable
	public String getSignature() { return signature; }

	public void setSignature(@Nullable String signature) {
		this.signature = signature;
	}

	@Nullable
	public String getPublicKey() { return publicKey; }

	public void setPublicKey(@Nullable String publicKey) {
		this.publicKey = publicKey;
	}

	// --- Mutable metadata (P2P sync updates these) ---

	public void setConfirmations(int confirmations) {
		this.confirmations = confirmations;
	}

	public void setScore(int score) {
		this.score = score;
	}

	@Override
	@NonNull
	public String toString() {
		return "FieldNote{" + category.getKey() + " '" + title + "' @ "
				+ String.format("%.4f,%.4f", lat, lon) + "}";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		FieldNote that = (FieldNote) o;
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
