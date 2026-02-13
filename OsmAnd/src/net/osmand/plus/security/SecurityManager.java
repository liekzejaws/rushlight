package net.osmand.plus.security;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

import java.security.KeyStore;

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

	public SecurityManager(@NonNull OsmandApplication app) {
		this.app = app;
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
			// Fallback: derive from package name + installation ID
			// Less secure than hardware keystore but functional
			cachedPassphrase = "rushlight_" + app.getPackageName().hashCode()
					+ "_" + Build.FINGERPRINT.hashCode();
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
	 * Get or create the encrypted chat storage instance.
	 */
	@NonNull
	public EncryptedChatStorage getChatStorage() {
		if (chatStorage == null) {
			chatStorage = new EncryptedChatStorage(app, getChatPassphrase());
		}
		return chatStorage;
	}

	/**
	 * Execute emergency panic wipe of all sensitive data.
	 * Destroys chat database and clears encryption key.
	 */
	public void executePanicWipe() {
		LOG.warn("PANIC WIPE: Executing emergency data wipe");

		// Wipe chat database
		if (chatStorage != null) {
			chatStorage.wipeAll();
			chatStorage = null;
		} else {
			// Create temporary instance just to wipe
			EncryptedChatStorage tempStorage = new EncryptedChatStorage(app, getChatPassphrase());
			tempStorage.wipeAll();
		}

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

		// Clear cached passphrase
		cachedPassphrase = null;

		LOG.warn("PANIC WIPE: Complete");
	}

	/**
	 * Listener interface for panic wipe events.
	 * UI components should implement this to clear in-memory data.
	 */
	public interface PanicWipeListener {
		void onPanicWipe();
	}
}
