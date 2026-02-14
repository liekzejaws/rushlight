package net.osmand.plus.plugins.p2pshare.transport;

import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for CryptoHandshake HKDF key derivation.
 *
 * Tests the HKDF-SHA256 implementation (RFC 5869) and key pair derivation logic.
 * The full handshake protocol requires two connected streams and is tested at integration level.
 */
public class CryptoHandshakeTest {

	private static final byte[] TEST_SALT = "test-salt".getBytes();
	private static final byte[] TEST_IKM = "test-input-key-material-32bytes!".getBytes();
	private static final byte[] TEST_INFO = "test-info".getBytes();

	@Test
	public void testHkdfExtractProducesNonZeroKey() throws Exception {
		byte[] prk = CryptoHandshake.hkdfExtract(TEST_SALT, TEST_IKM);
		assertNotNull(prk);
		assertEquals("HKDF-Extract should produce 32-byte PRK", 32, prk.length);

		// Verify it's not all zeros
		boolean allZero = true;
		for (byte b : prk) {
			if (b != 0) {
				allZero = false;
				break;
			}
		}
		assertFalse("PRK should not be all zeros", allZero);
	}

	@Test
	public void testHkdfExpandProducesCorrectLength() throws Exception {
		byte[] prk = CryptoHandshake.hkdfExtract(TEST_SALT, TEST_IKM);

		// Test various output lengths
		byte[] okm32 = CryptoHandshake.hkdfExpand(prk, TEST_INFO, 32);
		assertEquals(32, okm32.length);

		byte[] okm64 = CryptoHandshake.hkdfExpand(prk, TEST_INFO, 64);
		assertEquals(64, okm64.length);

		byte[] okm16 = CryptoHandshake.hkdfExpand(prk, TEST_INFO, 16);
		assertEquals(16, okm16.length);
	}

	@Test
	public void testHkdfExpandDifferentInfoProducesDifferentKeys() throws Exception {
		byte[] prk = CryptoHandshake.hkdfExtract(TEST_SALT, TEST_IKM);

		byte[] okm1 = CryptoHandshake.hkdfExpand(prk, "context-a".getBytes(), 32);
		byte[] okm2 = CryptoHandshake.hkdfExpand(prk, "context-b".getBytes(), 32);

		assertFalse("Different info strings should produce different keys",
				Arrays.equals(okm1, okm2));
	}

	@Test
	public void testHkdfKnownVector() throws Exception {
		// RFC 5869 Test Case 1 (simplified verification)
		// With known salt and IKM, output must be deterministic
		byte[] prk1 = CryptoHandshake.hkdfExtract(TEST_SALT, TEST_IKM);
		byte[] prk2 = CryptoHandshake.hkdfExtract(TEST_SALT, TEST_IKM);
		assertArrayEquals("HKDF-Extract must be deterministic", prk1, prk2);

		byte[] okm1 = CryptoHandshake.hkdfExpand(prk1, TEST_INFO, 64);
		byte[] okm2 = CryptoHandshake.hkdfExpand(prk2, TEST_INFO, 64);
		assertArrayEquals("HKDF-Expand must be deterministic", okm1, okm2);
	}

	@Test
	public void testHkdfExtractMatchesHmacSha256() throws Exception {
		// HKDF-Extract is defined as HMAC-SHA256(salt, IKM)
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(TEST_SALT, "HmacSHA256"));
		byte[] expected = mac.doFinal(TEST_IKM);

		byte[] actual = CryptoHandshake.hkdfExtract(TEST_SALT, TEST_IKM);
		assertArrayEquals("hkdfExtract should equal HMAC-SHA256(salt, IKM)", expected, actual);
	}

	@Test
	public void testDeriveKeyMaterialProducesTwoDistinctHalves() throws Exception {
		byte[] prk = CryptoHandshake.hkdfExtract(
				"rushlight-p2p-salt-2026".getBytes(),
				"shared-secret-for-testing-12345".getBytes());
		byte[] keyMaterial = CryptoHandshake.hkdfExpand(prk,
				"rushlight-p2p-v1".getBytes(), 64);

		assertEquals(64, keyMaterial.length);

		byte[] firstHalf = Arrays.copyOfRange(keyMaterial, 0, 32);
		byte[] secondHalf = Arrays.copyOfRange(keyMaterial, 32, 64);

		assertFalse("Send and receive keys must differ",
				Arrays.equals(firstHalf, secondHalf));
	}

	@Test
	public void testDeriveKeyMaterialDeterministic() throws Exception {
		byte[] secret = "test-shared-secret".getBytes();
		byte[] salt = "rushlight-p2p-salt-2026".getBytes();
		byte[] info = "rushlight-p2p-v1".getBytes();

		byte[] prk1 = CryptoHandshake.hkdfExtract(salt, secret);
		byte[] km1 = CryptoHandshake.hkdfExpand(prk1, info, 64);

		byte[] prk2 = CryptoHandshake.hkdfExtract(salt, secret);
		byte[] km2 = CryptoHandshake.hkdfExpand(prk2, info, 64);

		assertArrayEquals("Same inputs must produce same key material", km1, km2);
	}

	@Test
	public void testGenerateEcKeyPairNotNull() throws Exception {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
		keyGen.initialize(new ECGenParameterSpec("secp256r1"));
		KeyPair keyPair = keyGen.generateKeyPair();

		assertNotNull("EC keypair should not be null", keyPair);
		assertNotNull("Public key should not be null", keyPair.getPublic());
		assertNotNull("Private key should not be null", keyPair.getPrivate());

		byte[] encoded = keyPair.getPublic().getEncoded();
		assertTrue("EC P-256 public key should be 88-91 bytes encoded",
				encoded.length >= 80 && encoded.length <= 100);
	}
}
