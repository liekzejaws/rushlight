/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Rushlight: Central security coordinator.
 * Manages encryption passphrase via Android Keystore and panic wipe operations.
 */
public class SecurityManager {

	private static final Log LOG = PlatformUtil.getLog(SecurityManager.class);
	private static final String KEYSTORE_ALIAS = "rushlight_chat_key";
	private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

	private final OsmandApplication app;
	@Nullable
	private EncryptedChatStorage chatStorage;
	@Nullable
	private String cachedPassphrase;
	@Nullable
	private DuressManager duressManager;
	@Nullable
	private StealthManager stealthManager;

	public SecurityManager(@NonNull OsmandApplication app) {
		this.app = app;
	}

	/**
	 * Get the duress PIN manager (Phase 15).
	 */
	@NonNull
	public DuressManager getDuressManager() {
		if (duressManager == null) {
			duressManager = new DuressManager(app);
		}
		return duressManager;
	}

	/**
	 * Get the stealth mode manager (Phase 15).
	 */
	@NonNull
	public StealthManager getStealthManager() {
		if (stealthManager == null) {
			stealthManager = new StealthManager(app);
		}
		return stealthManager;
	}

	/**
	 * Get or create the encryption passphrase from Android Keystore.
	 * Falls back to a deterministic app-derived key if Keystore is unavailable.
	 */
	@NonNull
	public String getChatPassphrase() {
		if (cachedPassphrase != null) {
			return cachedPassphrase;
		}

		try {
			cachedPassphrase = getOrCreateKeystoreKey();
		} catch (Exception e) {
			LOG.error("Keystore unavailable, using fallback passphrase: " + e.getMessage());
			// Fallback: SecureRandom 32-byte key persisted in preferences
			cachedPassphrase = getOrCreateFallbackPassphrase();
		}

		return cachedPassphrase;
	}

	@NonNull
	private String getOrCreateKeystoreKey() throws Exception {
		KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
		keyStore.load(null);

		if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
			KeyGenerator keyGenerator = KeyGenerator.getInstance(
					KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
			keyGenerator.init(new KeyGenParameterSpec.Builder(
					KEYSTORE_ALIAS,
					KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
					.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
					.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
					.setKeySize(256)
					.build());
			keyGenerator.generateKey();
		}

		SecretKey key = (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
		if (key == null) {
			throw new RuntimeException("Failed to retrieve key from keystore");
		}
		return Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
	}

	/**
	 * Get or create a SecureRandom-based fallback passphrase for devices
	 * without hardware keystore. Stored in SharedPreferences.
	 * Provides 256-bit entropy vs. the old Build.FINGERPRINT.hashCode() (32-bit).
	 */
	@NonNull
	private String getOrCreateFallbackPassphrase() {
		String existing = app.getSettings().LAMPP_FALLBACK_PASSPHRASE.get();
		if (existing != null && !existing.isEmpty()) {
			return existing;
		}
		byte[] random = new byte[32];
		new SecureRandom().nextBytes(random);
		String passphrase = Base64.encodeToString(random, Base64.NO_WRAP);
		app.getSettings().LAMPP_FALLBACK_PASSPHRASE.set(passphrase);
		return passphrase;
	}

	/**
	 * Get or create the encrypted chat storage instance.
	 * Returns null if SQLCipher is not available on this device.
	 */
	@Nullable
	public EncryptedChatStorage getChatStorage() {
		if (chatStorage == null) {
			if (!EncryptedChatStorage.isSqlCipherAvailable()) {
				LOG.warn("SQLCipher not available, encrypted chat storage disabled");
				return null;
			}
			try {
				chatStorage = new EncryptedChatStorage(app, getChatPassphrase());
			} catch (Exception e) {
				LOG.error("Failed to create encrypted chat storage", e);
				return null;
			}
		}
		return chatStorage;
	}

	/**
	 * Execute emergency panic wipe of all sensitive data.
	 * Destroys chat database and clears encryption key.
	 */
	public void executePanicWipe() {
		executePanicWipe(DuressManager.DuressWipeScope.EVERYTHING);
	}

	/**
	 * Execute panic wipe with a specific scope (Phase 15).
	 * CHAT_ONLY: Wipe chat/conversations only.
	 * CHAT_AND_MODELS: Chat + delete GGUF model files.
	 * EVERYTHING: All data, PINs, keystore key.
	 */
	public void executePanicWipe(@NonNull DuressManager.DuressWipeScope scope) {
		LOG.warn("PANIC WIPE: Executing with scope " + scope);

		// Always wipe chat database (all scopes)
		if (chatStorage != null) {
			chatStorage.wipeAll();
			chatStorage = null;
		} else {
			EncryptedChatStorage tempStorage = new EncryptedChatStorage(app, getChatPassphrase());
			tempStorage.wipeAll();
		}

		if (scope == DuressManager.DuressWipeScope.CHAT_AND_MODELS
				|| scope == DuressManager.DuressWipeScope.EVERYTHING) {
			// Delete GGUF model files from the models directory
			deleteModelFiles();

			// Wipe FieldNotes — they contain sensitive geo-pinned data
			// (safe houses, border crossings, route intel)
			try {
				app.getFieldNotesManager().getDbHelper().wipeAll();
			} catch (Exception e) {
				LOG.error("Failed to wipe FieldNotes: " + e.getMessage());
			}
		}

		if (scope == DuressManager.DuressWipeScope.EVERYTHING) {
			// Remove Keystore key
			try {
				KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
				keyStore.load(null);
				if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
					keyStore.deleteEntry(KEYSTORE_ALIAS);
				}
			} catch (Exception e) {
				LOG.error("Failed to delete keystore entry: " + e.getMessage());
			}

			// Clear FieldNote signing keypair (Step 5 — device gets new identity)
			try {
				app.getFieldNotesManager().getSigner().clearKeypair();
			} catch (Exception e) {
				LOG.error("Failed to clear FieldNote signing key: " + e.getMessage());
			}

			// Clear PINs
			if (duressManager != null) {
				duressManager.clearPins();
			}

			// Disable stealth mode — restore launcher icon so user isn't locked out
			if (stealthManager != null && stealthManager.isStealthEnabled()) {
				stealthManager.disableStealthMode();
			}

			// Clear cached passphrase
			cachedPassphrase = null;
		}

		LOG.warn("PANIC WIPE: Complete (scope=" + scope + ")");
	}

	/**
	 * Delete GGUF model files from the app's files directory.
	 */
	private void deleteModelFiles() {
		try {
			java.io.File modelsDir = new java.io.File(app.getFilesDir(), "models");
			if (modelsDir.exists() && modelsDir.isDirectory()) {
				java.io.File[] files = modelsDir.listFiles();
				if (files != null) {
					for (java.io.File file : files) {
						if (file.getName().endsWith(".gguf")) {
							if (file.delete()) {
								LOG.info("Deleted model file: " + file.getName());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			LOG.error("Failed to delete model files: " + e.getMessage());
		}
	}

	/**
	 * Listener interface for panic wipe events.
	 * UI components should implement this to clear in-memory data.
	 */
	public interface PanicWipeListener {
		void onPanicWipe();
	}
}
