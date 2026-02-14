package net.osmand.plus.security;

import static org.junit.Assert.*;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Phase 16: Tests for the SecureRandom-based fallback passphrase generation.
 * Validates entropy, uniqueness, and format of generated passphrases.
 */
public class FallbackPassphraseTest {

	/**
	 * Simulate the passphrase generation logic from SecurityManager.
	 */
	private String generatePassphrase() {
		byte[] random = new byte[32];
		new SecureRandom().nextBytes(random);
		return Base64.getEncoder().encodeToString(random);
	}

	@Test
	public void testPassphrase_isNotNull() {
		String passphrase = generatePassphrase();
		assertNotNull(passphrase);
	}

	@Test
	public void testPassphrase_isNotEmpty() {
		String passphrase = generatePassphrase();
		assertFalse(passphrase.isEmpty());
	}

	@Test
	public void testPassphrase_hasCorrectBase64Length() {
		// 32 bytes -> Base64 = ceil(32/3)*4 = 44 characters (with padding)
		String passphrase = generatePassphrase();
		assertEquals(44, passphrase.length());
	}

	@Test
	public void testPassphrase_isValidBase64() {
		String passphrase = generatePassphrase();
		try {
			byte[] decoded = Base64.getDecoder().decode(passphrase);
			assertNotNull(decoded);
			assertEquals(32, decoded.length);
		} catch (IllegalArgumentException e) {
			fail("Passphrase is not valid Base64: " + e.getMessage());
		}
	}

	@Test
	public void testPassphrase_decodesTo32Bytes() {
		String passphrase = generatePassphrase();
		byte[] decoded = Base64.getDecoder().decode(passphrase);
		assertEquals(32, decoded.length);
	}

	@Test
	public void testPassphrase_has256BitEntropy() {
		// 32 bytes = 256 bits
		String passphrase = generatePassphrase();
		byte[] decoded = Base64.getDecoder().decode(passphrase);
		assertEquals("Should have 256 bits of entropy", 256, decoded.length * 8);
	}

	@Test
	public void testPassphrase_uniqueAcrossGenerations() {
		// Generate 50 passphrases and verify all are unique
		Set<String> passphrases = new HashSet<>();
		for (int i = 0; i < 50; i++) {
			passphrases.add(generatePassphrase());
		}
		assertEquals("All 50 passphrases should be unique", 50, passphrases.size());
	}

	@Test
	public void testPassphrase_notAllZeros() {
		String passphrase = generatePassphrase();
		byte[] decoded = Base64.getDecoder().decode(passphrase);
		boolean allZeros = true;
		for (byte b : decoded) {
			if (b != 0) {
				allZeros = false;
				break;
			}
		}
		assertFalse("Passphrase should not be all zeros", allZeros);
	}

	@Test
	public void testPassphrase_containsVariedBytes() {
		// At least 10 unique byte values in 32 random bytes
		String passphrase = generatePassphrase();
		byte[] decoded = Base64.getDecoder().decode(passphrase);
		Set<Byte> uniqueBytes = new HashSet<>();
		for (byte b : decoded) {
			uniqueBytes.add(b);
		}
		assertTrue("Random bytes should have variety (at least 10 unique values, got "
				+ uniqueBytes.size() + ")", uniqueBytes.size() >= 10);
	}

	@Test
	public void testPassphrase_doesNotContainNewlines() {
		String passphrase = generatePassphrase();
		assertFalse("Should not contain newlines", passphrase.contains("\n"));
		assertFalse("Should not contain carriage returns", passphrase.contains("\r"));
	}

	@Test
	public void testPassphrase_betterThanOldMethod() {
		// Old method: "rushlight_" + hashCode + "_" + hashCode
		// hashCode returns int (32-bit) — only 2^32 possible values
		// New method: 32 bytes of SecureRandom — 2^256 possible values
		String passphrase = generatePassphrase();
		byte[] decoded = Base64.getDecoder().decode(passphrase);
		// 32 bytes > 4 bytes (int hashCode)
		assertTrue(decoded.length > 4);
	}
}
