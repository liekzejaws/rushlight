/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare.transport;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Phase 12: ECDH key exchange for P2P transport encryption.
 *
 * Uses standard Java crypto APIs (available on all Android versions):
 * - ECDH with P-256 (secp256r1) for key agreement
 * - HMAC-SHA256 based HKDF for key derivation
 *
 * Protocol:
 * 1. Both sides generate ephemeral EC P-256 keypairs
 * 2. Exchange public keys (prefixed with 4-byte magic "RLKE" + 2-byte key length)
 * 3. Derive shared secret via ECDH agreement
 * 4. HKDF-SHA256 to derive separate encrypt/decrypt keys (32 bytes each)
 *
 * The handshake is symmetrical — both sides run the same code.
 * Key ordering is determined by comparing public key encodings lexicographically:
 * the "lower" key holder uses keyMaterial[0:32] for sending, [32:64] for receiving,
 * and the "higher" key holder uses the opposite.
 */
public class CryptoHandshake {

	private static final Log LOG = PlatformUtil.getLog(CryptoHandshake.class);

	/** Magic bytes "RLKE" (Rushlight Key Exchange) to identify handshake packets */
	private static final byte[] HANDSHAKE_MAGIC = {0x52, 0x4C, 0x4B, 0x45};

	/** HKDF info string for key derivation */
	private static final byte[] HKDF_INFO = "rushlight-p2p-v1".getBytes();

	/** HKDF salt — fixed value for deterministic derivation */
	private static final byte[] HKDF_SALT = "rushlight-p2p-salt-2026".getBytes();

	/** Total key material: 32 bytes send key + 32 bytes receive key */
	private static final int KEY_MATERIAL_LENGTH = 64;

	/** Timeout for reading peer's public key (10 seconds) */
	private static final int HANDSHAKE_TIMEOUT_MS = 10000;

	/**
	 * Result of a successful handshake containing directional encryption keys.
	 */
	public static class HandshakeResult {
		@NonNull public final byte[] sendKey;
		@NonNull public final byte[] receiveKey;
		@NonNull public final String fingerprint;

		HandshakeResult(@NonNull byte[] sendKey, @NonNull byte[] receiveKey, @NonNull String fingerprint) {
			this.sendKey = sendKey;
			this.receiveKey = receiveKey;
			this.fingerprint = fingerprint;
		}
	}

	/**
	 * Perform the ECDH key exchange over the given raw streams.
	 * Must be called before wrapping with Data*Stream or EncryptedStreamPair.
	 *
	 * @param rawIn  Raw socket InputStream
	 * @param rawOut Raw socket OutputStream
	 * @return HandshakeResult with directional keys, or throws on failure
	 */
	@NonNull
	public static HandshakeResult perform(@NonNull InputStream rawIn,
	                                       @NonNull OutputStream rawOut) throws IOException {
		try {
			// 1. Generate ephemeral ECDH P-256 keypair
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
			keyGen.initialize(new ECGenParameterSpec("secp256r1"));
			KeyPair keyPair = keyGen.generateKeyPair();

			byte[] ourPublicBytes = keyPair.getPublic().getEncoded();

			LOG.info("CryptoHandshake: generated ephemeral EC P-256 keypair ("
					+ ourPublicBytes.length + " bytes public key)");

			// 2. Send our public key: MAGIC (4) + key length (2) + public key (N bytes)
			rawOut.write(HANDSHAKE_MAGIC);
			rawOut.write((ourPublicBytes.length >> 8) & 0xFF);
			rawOut.write(ourPublicBytes.length & 0xFF);
			rawOut.write(ourPublicBytes);
			rawOut.flush();

			// 3. Read peer's public key: MAGIC (4) + key length (2) + public key (N bytes)
			byte[] peerMagic = new byte[4];
			readFully(rawIn, peerMagic, HANDSHAKE_TIMEOUT_MS);

			if (!Arrays.equals(peerMagic, HANDSHAKE_MAGIC)) {
				throw new IOException("CryptoHandshake: invalid magic bytes from peer. "
						+ "Peer may not support encryption.");
			}

			byte[] peerLenBytes = new byte[2];
			readFully(rawIn, peerLenBytes, HANDSHAKE_TIMEOUT_MS);
			int peerKeyLen = ((peerLenBytes[0] & 0xFF) << 8) | (peerLenBytes[1] & 0xFF);

			if (peerKeyLen <= 0 || peerKeyLen > 256) {
				throw new IOException("CryptoHandshake: invalid peer key length: " + peerKeyLen);
			}

			byte[] peerPublicBytes = new byte[peerKeyLen];
			readFully(rawIn, peerPublicBytes, HANDSHAKE_TIMEOUT_MS);

			LOG.info("CryptoHandshake: received peer public key (" + peerKeyLen + " bytes)");

			// 4. Reconstruct peer's public key and perform ECDH agreement
			KeyFactory keyFactory = KeyFactory.getInstance("EC");
			PublicKey peerPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(peerPublicBytes));

			KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
			agreement.init(keyPair.getPrivate());
			agreement.doPhase(peerPublicKey, true);

			byte[] sharedSecret = agreement.generateSecret();

			// 5. HKDF-SHA256 to derive 64 bytes of key material
			byte[] keyMaterial = hkdfExpand(
					hkdfExtract(HKDF_SALT, sharedSecret),
					HKDF_INFO,
					KEY_MATERIAL_LENGTH);

			// 6. Determine key direction by comparing public keys lexicographically
			// The side with the "lower" public key uses first half for sending
			int cmp = compareBytes(ourPublicBytes, peerPublicBytes);
			if (cmp == 0) {
				throw new IOException("CryptoHandshake: identical public keys (extremely unlikely)");
			}

			byte[] sendKey = new byte[32];
			byte[] receiveKey = new byte[32];

			if (cmp < 0) {
				// Our key is "lower" — use first 32 bytes for sending, second for receiving
				System.arraycopy(keyMaterial, 0, sendKey, 0, 32);
				System.arraycopy(keyMaterial, 32, receiveKey, 0, 32);
			} else {
				// Our key is "higher" — use second 32 bytes for sending, first for receiving
				System.arraycopy(keyMaterial, 32, sendKey, 0, 32);
				System.arraycopy(keyMaterial, 0, receiveKey, 0, 32);
			}

			// 7. Generate fingerprint for logging (first 8 hex chars of shared secret hash)
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] fingerprintHash = sha256.digest(sharedSecret);
			String fingerprint = bytesToHex(fingerprintHash).substring(0, 8).toUpperCase();

			// Clear sensitive data
			Arrays.fill(sharedSecret, (byte) 0);
			Arrays.fill(keyMaterial, (byte) 0);

			LOG.info("CryptoHandshake: key exchange complete, fingerprint=" + fingerprint);

			return new HandshakeResult(sendKey, receiveKey, fingerprint);

		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("CryptoHandshake failed: " + e.getMessage(), e);
		}
	}

	// ---- HKDF-SHA256 implementation (RFC 5869) ----

	/**
	 * HKDF-Extract: PRK = HMAC-SHA256(salt, IKM)
	 * Package-private for unit testing.
	 */
	static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(salt, "HmacSHA256"));
		return mac.doFinal(ikm);
	}

	/**
	 * HKDF-Expand: OKM = T(1) || T(2) || ... where T(i) = HMAC-SHA256(PRK, T(i-1) || info || i)
	 * Package-private for unit testing.
	 */
	static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(prk, "HmacSHA256"));

		int hashLen = 32; // SHA-256 output
		int iterations = (int) Math.ceil((double) length / hashLen);
		byte[] okm = new byte[length];
		byte[] t = new byte[0];

		for (int i = 1; i <= iterations; i++) {
			mac.reset();
			mac.update(t);
			mac.update(info);
			mac.update((byte) i);
			t = mac.doFinal();

			int copyLen = Math.min(hashLen, length - (i - 1) * hashLen);
			System.arraycopy(t, 0, okm, (i - 1) * hashLen, copyLen);
		}

		return okm;
	}

	// ---- Utility methods ----

	/**
	 * Read exactly `buffer.length` bytes from the stream, with a timeout.
	 */
	private static void readFully(@NonNull InputStream in, @NonNull byte[] buffer,
	                                int timeoutMs) throws IOException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		int offset = 0;
		while (offset < buffer.length) {
			if (System.currentTimeMillis() > deadline) {
				throw new IOException("CryptoHandshake: timeout reading peer data");
			}
			int available = in.available();
			if (available <= 0) {
				// Brief sleep to avoid busy-waiting
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("CryptoHandshake: interrupted", e);
				}
				continue;
			}
			int toRead = Math.min(available, buffer.length - offset);
			int read = in.read(buffer, offset, toRead);
			if (read < 0) {
				throw new IOException("CryptoHandshake: unexpected end of stream");
			}
			offset += read;
		}
	}

	/**
	 * Lexicographic comparison of two byte arrays.
	 */
	private static int compareBytes(@NonNull byte[] a, @NonNull byte[] b) {
		int len = Math.min(a.length, b.length);
		for (int i = 0; i < len; i++) {
			int cmp = (a[i] & 0xFF) - (b[i] & 0xFF);
			if (cmp != 0) return cmp;
		}
		return Integer.compare(a.length, b.length);
	}

	/**
	 * Convert byte array to hex string.
	 */
	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b & 0xFF));
		}
		return sb.toString();
	}
}
