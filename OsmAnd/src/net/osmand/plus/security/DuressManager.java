package net.osmand.plus.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.settings.backend.OsmandSettings;

import org.apache.commons.logging.Log;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Phase 15: Manages duress PIN and real PIN for covert data protection.
 *
 * When a user is coerced into unlocking the app (e.g., border crossing, detention),
 * they can enter the duress PIN instead of their real PIN. The app appears to unlock
 * normally, but sensitive data is silently destroyed in the background.
 *
 * PINs are stored as PBKDF2 hashes with a per-installation random salt.
 * Validation uses constant-time comparison to prevent timing attacks.
 */
public class DuressManager {

	private static final Log LOG = PlatformUtil.getLog(DuressManager.class);

	/** PBKDF2 iteration count — balance between security and speed on mobile */
	public static final int PBKDF2_ITERATIONS = 10000;
	private static final int SALT_BYTES = 16;
	private static final int HASH_BYTES = 32;
	private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

	/** Result of PIN validation */
	public enum PinResult {
		REAL,
		DURESS,
		INVALID
	}

	/** Scope of data destruction when duress PIN is entered */
	public enum DuressWipeScope {
		/** Wipe chat history and AI conversations only */
		CHAT_ONLY,
		/** Wipe chat + delete downloaded GGUF model files */
		CHAT_AND_MODELS,
		/** Wipe everything: chat, models, transfer history, guides cache, PINs */
		EVERYTHING
	}

	private final OsmandApplication app;
	private final OsmandSettings settings;

	public DuressManager(@NonNull OsmandApplication app) {
		this.app = app;
		this.settings = app.getSettings();
	}

	// ==================== PIN Management ====================

	/**
	 * Validate a 4-digit numeric PIN format.
	 * @return true if the PIN is exactly 4 numeric digits
	 */
	public static boolean isValidPinFormat(@Nullable String pin) {
		if (pin == null || pin.length() != 4) {
			return false;
		}
		for (int i = 0; i < 4; i++) {
			if (!Character.isDigit(pin.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Set the real (legitimate) PIN.
	 * @return true if set successfully, false if format invalid
	 */
	public boolean setRealPin(@NonNull String pin) {
		if (!isValidPinFormat(pin)) {
			return false;
		}
		ensureSaltExists();
		String hash = hashPin(pin, getSalt());
		settings.LAMPP_REAL_PIN_HASH.set(hash);
		return true;
	}

	/**
	 * Set the duress PIN.
	 * @return true if set successfully, false if format invalid or same as real PIN
	 */
	public boolean setDuressPin(@NonNull String pin) {
		if (!isValidPinFormat(pin)) {
			return false;
		}
		// Reject if same as real PIN
		if (isRealPinSet()) {
			String realHash = settings.LAMPP_REAL_PIN_HASH.get();
			String candidateHash = hashPin(pin, getSalt());
			if (constantTimeEquals(realHash, candidateHash)) {
				return false;
			}
		}
		ensureSaltExists();
		String hash = hashPin(pin, getSalt());
		settings.LAMPP_DURESS_PIN_HASH.set(hash);
		return true;
	}

	/**
	 * Validate a PIN entry.
	 * Uses constant-time comparison to prevent timing attacks.
	 */
	@NonNull
	public PinResult validatePin(@NonNull String pin) {
		if (!isValidPinFormat(pin)) {
			return PinResult.INVALID;
		}

		String salt = getSalt();
		if (salt.isEmpty()) {
			return PinResult.INVALID;
		}

		String inputHash = hashPin(pin, salt);
		String realHash = settings.LAMPP_REAL_PIN_HASH.get();
		String duressHash = settings.LAMPP_DURESS_PIN_HASH.get();

		// Always compare both to maintain constant time
		boolean matchesReal = !realHash.isEmpty() && constantTimeEquals(inputHash, realHash);
		boolean matchesDuress = !duressHash.isEmpty() && constantTimeEquals(inputHash, duressHash);

		if (matchesReal) {
			return PinResult.REAL;
		} else if (matchesDuress) {
			return PinResult.DURESS;
		} else {
			return PinResult.INVALID;
		}
	}

	/** Check if a real PIN has been set */
	public boolean isRealPinSet() {
		return !settings.LAMPP_REAL_PIN_HASH.get().isEmpty();
	}

	/** Check if duress mode is fully configured (both PINs set) */
	public boolean isDuressEnabled() {
		return isRealPinSet() && !settings.LAMPP_DURESS_PIN_HASH.get().isEmpty();
	}

	/** Clear both PINs and the salt */
	public void clearPins() {
		settings.LAMPP_REAL_PIN_HASH.set("");
		settings.LAMPP_DURESS_PIN_HASH.set("");
		settings.LAMPP_PIN_SALT.set("");
	}

	// ==================== Wipe Scope ====================

	/** Get the configured wipe scope */
	@NonNull
	public DuressWipeScope getWipeScope() {
		String scope = settings.LAMPP_DURESS_WIPE_SCOPE.get();
		try {
			return DuressWipeScope.valueOf(scope);
		} catch (IllegalArgumentException e) {
			return DuressWipeScope.CHAT_ONLY;
		}
	}

	/** Set the wipe scope */
	public void setWipeScope(@NonNull DuressWipeScope scope) {
		settings.LAMPP_DURESS_WIPE_SCOPE.set(scope.name());
	}

	// ==================== Duress Wipe Execution ====================

	/**
	 * Execute the duress wipe with the configured scope.
	 * Called silently when the duress PIN is entered.
	 */
	public void executeDuressWipe() {
		DuressWipeScope scope = getWipeScope();
		LOG.warn("DURESS WIPE: Executing with scope " + scope);
		app.getSecurityManager().executePanicWipe(scope);
	}

	// ==================== Crypto Utilities ====================

	/**
	 * Hash a PIN using PBKDF2 with the installation-specific salt.
	 * Returns a Base64-encoded hash string.
	 */
	@NonNull
	public static String hashPin(@NonNull String pin, @NonNull String saltBase64) {
		try {
			byte[] salt = Base64.getDecoder().decode(saltBase64);
			PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BYTES * 8);
			SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
			byte[] hash = factory.generateSecret(spec).getEncoded();
			return Base64.getEncoder().encodeToString(hash);
		} catch (Exception e) {
			LOG.error("PBKDF2 hashing failed: " + e.getMessage());
			// Fallback to SHA-256 if PBKDF2 unavailable
			return sha256Fallback(pin + saltBase64);
		}
	}

	/**
	 * Constant-time string comparison to prevent timing attacks.
	 */
	public static boolean constantTimeEquals(@NonNull String a, @NonNull String b) {
		if (a.length() != b.length()) {
			return false;
		}
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}

	@NonNull
	private String getSalt() {
		return settings.LAMPP_PIN_SALT.get();
	}

	private void ensureSaltExists() {
		if (settings.LAMPP_PIN_SALT.get().isEmpty()) {
			byte[] salt = new byte[SALT_BYTES];
			new SecureRandom().nextBytes(salt);
			settings.LAMPP_PIN_SALT.set(Base64.getEncoder().encodeToString(salt));
		}
	}

	@NonNull
	private static String sha256Fallback(@NonNull String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes("UTF-8"));
			return Base64.getEncoder().encodeToString(hash);
		} catch (Exception e) {
			return "";
		}
	}
}
