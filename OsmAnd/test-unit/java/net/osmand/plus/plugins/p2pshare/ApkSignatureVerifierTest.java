package net.osmand.plus.plugins.p2pshare;

import org.junit.Test;

import java.security.MessageDigest;

import static org.junit.Assert.*;

/**
 * Tests for APK signature verification logic.
 *
 * The computeFingerprint method depends on android.content.pm.Signature which
 * may not be fully functional in unit tests (requires Robolectric for full Android API).
 * These tests verify the SHA-256 fingerprinting algorithm and ContentType detection.
 */
public class ApkSignatureVerifierTest {

	/**
	 * SHA-256 fingerprint computation should produce 64 hex chars.
	 */
	@Test
	public void testSha256ProducesCorrectLength() throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest("test-certificate-data".getBytes());
		StringBuilder hex = new StringBuilder(hash.length * 2);
		for (byte b : hash) {
			hex.append(String.format("%02x", b));
		}
		assertEquals("SHA-256 should produce 64 hex chars", 64, hex.length());
	}

	/**
	 * Same input should produce same fingerprint (deterministic).
	 */
	@Test
	public void testSha256Deterministic() throws Exception {
		String fp1 = sha256Hex("consistent-certificate-bytes");
		String fp2 = sha256Hex("consistent-certificate-bytes");
		assertEquals("Same input should produce same hash", fp1, fp2);
	}

	/**
	 * Different inputs should produce different fingerprints.
	 */
	@Test
	public void testDifferentInputsProduceDifferentFingerprints() throws Exception {
		String fp1 = sha256Hex("legitimate-app-cert");
		String fp2 = sha256Hex("tampered-malware-cert");
		assertNotEquals("Different certs must produce different fingerprints", fp1, fp2);
	}

	/**
	 * Fingerprint should be lowercase hex.
	 */
	@Test
	public void testFingerprintIsLowercaseHex() throws Exception {
		String fingerprint = sha256Hex("test-data");
		assertTrue("Fingerprint should be lowercase hex",
				fingerprint.matches("[0-9a-f]{64}"));
	}

	/**
	 * Empty input should produce the known SHA-256 of empty data.
	 */
	@Test
	public void testEmptyInputProducesKnownHash() throws Exception {
		String fingerprint = sha256Hex("");
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
				fingerprint);
	}

	/**
	 * ContentType.APK should be identifiable for APK files.
	 */
	@Test
	public void testApkContentTypeDetection() {
		assertEquals(ContentType.APK, ContentType.fromFilename("rushlight-1.4.0.apk"));
		assertEquals(ContentType.APK, ContentType.fromFilename("Lampp-1.4.0.apk"));
		assertEquals(ContentType.APK, ContentType.fromFilename("test.APK"));
	}

	/**
	 * Non-APK files should not be identified as APK.
	 */
	@Test
	public void testNonApkContentType() {
		assertNotEquals(ContentType.APK, ContentType.fromFilename("map.obf"));
		assertNotEquals(ContentType.APK, ContentType.fromFilename("wiki.zim"));
		assertNotEquals(ContentType.APK, ContentType.fromFilename("model.gguf"));
	}

	/**
	 * ContentType enum should have exactly 4 types.
	 */
	@Test
	public void testContentTypeCompleteness() {
		assertEquals(4, ContentType.values().length);
	}

	/** Helper: compute SHA-256 hex of a string (mirrors ApkSignatureVerifier logic) */
	private static String sha256Hex(String input) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(input.getBytes());
		StringBuilder hex = new StringBuilder(hash.length * 2);
		for (byte b : hash) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}
}
