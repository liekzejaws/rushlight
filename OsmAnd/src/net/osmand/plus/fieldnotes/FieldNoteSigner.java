package net.osmand.plus.fieldnotes;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Manages a persistent ECDSA P-256 keypair for signing FieldNotes.
 *
 * Provides:
 * - Keypair generation on first use (stored in Android Keystore)
 * - Deterministic author ID derived from public key hash
 * - Sign: SHA256withECDSA over canonical note fields → Base64 signature
 * - Verify: given a note with signature + public key, verify integrity
 * - Clear keypair (for panic wipe integration)
 *
 * Uses the same EC P-256 curve as CryptoHandshake.java (P2P transport),
 * but with a persistent (not ephemeral) keypair.
 */
public class FieldNoteSigner {

	private static final Log LOG = PlatformUtil.getLog(FieldNoteSigner.class);

	/** Android Keystore alias for the FieldNote signing key */
	private static final String KEYSTORE_ALIAS = "rushlight_fieldnote_signing_key";
	private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
	private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

	private final OsmandApplication app;

	/** Cached author ID derived from public key hash */
	@Nullable
	private String cachedAuthorId;

	/** Cached Base64-encoded public key */
	@Nullable
	private String cachedPublicKeyBase64;

	public FieldNoteSigner(@NonNull OsmandApplication app) {
		this.app = app;
	}

	// --- Keypair Lifecycle ---

	/**
	 * Ensure the signing keypair exists. Generates one if needed.
	 * Called lazily on first sign/getAuthorId.
	 */
	private void ensureKeypair() {
		try {
			KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
			keyStore.load(null);
			if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
				generateKeypair();
			}
		} catch (Exception e) {
			LOG.error("Failed to check/init signing keypair: " + e.getMessage());
		}
	}

	/**
	 * Generate a new ECDSA P-256 keypair in Android Keystore.
	 */
	private void generateKeypair() {
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance(
					KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE);

			KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
					KEYSTORE_ALIAS,
					KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
					.setDigests(KeyProperties.DIGEST_SHA256)
					.setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
					.build();

			keyGen.initialize(spec);
			KeyPair keyPair = keyGen.generateKeyPair();

			// Clear cache so it's recomputed from new key
			cachedAuthorId = null;
			cachedPublicKeyBase64 = null;

			LOG.info("FieldNoteSigner: generated new ECDSA P-256 signing keypair");
		} catch (Exception e) {
			LOG.error("Failed to generate signing keypair: " + e.getMessage());
		}
	}

	/**
	 * Clear the signing keypair from Android Keystore.
	 * Called during panic wipe. A new keypair will be generated on next use,
	 * giving the device a new identity.
	 */
	public void clearKeypair() {
		try {
			KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
			keyStore.load(null);
			if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
				keyStore.deleteEntry(KEYSTORE_ALIAS);
				LOG.info("FieldNoteSigner: signing keypair cleared (panic wipe)");
			}
			cachedAuthorId = null;
			cachedPublicKeyBase64 = null;
		} catch (Exception e) {
			LOG.error("Failed to clear signing keypair: " + e.getMessage());
		}
	}

	// --- Author ID ---

	/**
	 * Get the author ID derived from the signing public key.
	 * SHA-256 of the X509-encoded public key, truncated to 16 hex chars.
	 *
	 * This is more privacy-preserving than Android ID and is verifiable:
	 * anyone with the public key can confirm the author ID matches.
	 */
	@NonNull
	public String getAuthorId() {
		if (cachedAuthorId != null) {
			return cachedAuthorId;
		}

		ensureKeypair();

		try {
			KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
			keyStore.load(null);
			PublicKey publicKey = keyStore.getCertificate(KEYSTORE_ALIAS).getPublicKey();

			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(publicKey.getEncoded());

			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++) { // 8 bytes = 16 hex chars
				hex.append(String.format("%02x", hash[i]));
			}
			cachedAuthorId = hex.toString();
			return cachedAuthorId;

		} catch (Exception e) {
			LOG.error("Failed to derive author ID from public key: " + e.getMessage());
			return "unknown";
		}
	}

	// --- Public Key Export ---

	/**
	 * Get the Base64-encoded X509 public key for inclusion in sync packets.
	 */
	@Nullable
	public String getPublicKeyBase64() {
		if (cachedPublicKeyBase64 != null) {
			return cachedPublicKeyBase64;
		}

		ensureKeypair();

		try {
			KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
			keyStore.load(null);
			PublicKey publicKey = keyStore.getCertificate(KEYSTORE_ALIAS).getPublicKey();
			cachedPublicKeyBase64 = android.util.Base64.encodeToString(
					publicKey.getEncoded(), android.util.Base64.NO_WRAP);
			return cachedPublicKeyBase64;
		} catch (Exception e) {
			LOG.error("Failed to export public key: " + e.getMessage());
			return null;
		}
	}

	// --- Signing ---

	/**
	 * Sign a FieldNote, setting its signature and publicKey fields.
	 *
	 * Signs the canonical representation of the note's immutable fields:
	 * id:lat:lon:category:title:note:timestamp:authorId
	 *
	 * Score and confirmations are excluded (they change via P2P).
	 */
	public void sign(@NonNull FieldNote note) {
		ensureKeypair();

		try {
			KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
			keyStore.load(null);

			PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEYSTORE_ALIAS, null);
			if (privateKey == null) {
				LOG.error("FieldNoteSigner: private key not found in Keystore");
				return;
			}

			String canonical = getCanonicalString(note);
			Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
			sig.initSign(privateKey);
			sig.update(canonical.getBytes(StandardCharsets.UTF_8));
			byte[] signatureBytes = sig.sign();

			String signatureBase64 = android.util.Base64.encodeToString(
					signatureBytes, android.util.Base64.NO_WRAP);

			note.setSignature(signatureBase64);
			note.setPublicKey(getPublicKeyBase64());

			LOG.info("FieldNote signed: " + note.getId().substring(0, 8));
		} catch (Exception e) {
			LOG.error("Failed to sign FieldNote: " + e.getMessage());
		}
	}

	// --- Verification ---

	/**
	 * Verify a FieldNote's signature against its embedded public key.
	 *
	 * @return true if signature is valid, false if invalid or missing/malformed
	 */
	public static boolean verify(@NonNull FieldNote note) {
		String sig = note.getSignature();
		String pubKeyStr = note.getPublicKey();

		if (sig == null || pubKeyStr == null) {
			return false; // unsigned note
		}

		try {
			// Decode the public key
			byte[] pubKeyBytes = android.util.Base64.decode(pubKeyStr, android.util.Base64.NO_WRAP);
			KeyFactory keyFactory = KeyFactory.getInstance("EC");
			PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubKeyBytes));

			// Verify the signature
			String canonical = getCanonicalString(note);
			Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
			verifier.initVerify(publicKey);
			verifier.update(canonical.getBytes(StandardCharsets.UTF_8));

			byte[] signatureBytes = android.util.Base64.decode(sig, android.util.Base64.NO_WRAP);
			return verifier.verify(signatureBytes);

		} catch (Exception e) {
			LOG.error("Signature verification failed: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Check if a FieldNote has a signature (regardless of validity).
	 */
	public static boolean isSigned(@NonNull FieldNote note) {
		return note.getSignature() != null && note.getPublicKey() != null;
	}

	// --- Canonical String ---

	/**
	 * Build the canonical string representation of a FieldNote for signing.
	 * Includes all immutable content fields. Excludes score and confirmations
	 * (which change via P2P voting/gossip).
	 */
	@NonNull
	private static String getCanonicalString(@NonNull FieldNote note) {
		return note.getId() + ":"
				+ note.getLat() + ":"
				+ note.getLon() + ":"
				+ note.getCategory().getKey() + ":"
				+ note.getTitle() + ":"
				+ note.getNote() + ":"
				+ note.getTimestamp() + ":"
				+ note.getAuthorId();
	}
}
