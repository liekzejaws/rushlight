package net.osmand.plus.security;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Phase 15 Session 1: Tests for DuressManager PIN hashing, validation, and wipe scopes.
 * Tests the core crypto logic without requiring Android context.
 */
public class DuressManagerTest {

	// Use a fixed test salt for deterministic tests
	private static final String TEST_SALT = "dGVzdHNhbHQxMjM0NTY3OA=="; // "testsalt12345678" in Base64

	// ==================== PIN Format Validation ====================

	@Test
	public void testValidPin4Digits() {
		assertTrue(DuressManager.isValidPinFormat("1234"));
		assertTrue(DuressManager.isValidPinFormat("0000"));
		assertTrue(DuressManager.isValidPinFormat("9999"));
	}

	@Test
	public void testInvalidPinTooShort() {
		assertFalse(DuressManager.isValidPinFormat("123"));
		assertFalse(DuressManager.isValidPinFormat("12"));
		assertFalse(DuressManager.isValidPinFormat("1"));
		assertFalse(DuressManager.isValidPinFormat(""));
	}

	@Test
	public void testInvalidPinTooLong() {
		assertFalse(DuressManager.isValidPinFormat("12345"));
		assertFalse(DuressManager.isValidPinFormat("123456"));
	}

	@Test
	public void testInvalidPinNonNumeric() {
		assertFalse(DuressManager.isValidPinFormat("abcd"));
		assertFalse(DuressManager.isValidPinFormat("12a4"));
		assertFalse(DuressManager.isValidPinFormat("12 4"));
		assertFalse(DuressManager.isValidPinFormat("12-4"));
	}

	@Test
	public void testInvalidPinNull() {
		assertFalse(DuressManager.isValidPinFormat(null));
	}

	// ==================== PIN Hashing ====================

	@Test
	public void testHashPinDeterministic() {
		// Same PIN + same salt = same hash
		String hash1 = DuressManager.hashPin("1234", TEST_SALT);
		String hash2 = DuressManager.hashPin("1234", TEST_SALT);
		assertEquals(hash1, hash2);
	}

	@Test
	public void testHashPinDifferentPins() {
		// Different PINs = different hashes
		String hash1 = DuressManager.hashPin("1234", TEST_SALT);
		String hash2 = DuressManager.hashPin("0000", TEST_SALT);
		assertNotEquals(hash1, hash2);
	}

	@Test
	public void testHashPinDifferentSalts() {
		// Same PIN + different salt = different hash
		String salt2 = "YW5vdGhlcnNhbHQ5ODc2NTQ="; // different Base64
		String hash1 = DuressManager.hashPin("1234", TEST_SALT);
		String hash2 = DuressManager.hashPin("1234", salt2);
		assertNotEquals(hash1, hash2);
	}

	@Test
	public void testHashPinNotEmpty() {
		String hash = DuressManager.hashPin("1234", TEST_SALT);
		assertNotNull(hash);
		assertFalse(hash.isEmpty());
	}

	@Test
	public void testHashPinReasonableLength() {
		// PBKDF2 with 32-byte output in Base64 should be ~44 chars
		String hash = DuressManager.hashPin("1234", TEST_SALT);
		assertTrue("Hash should be Base64 encoded, got length: " + hash.length(),
				hash.length() >= 20 && hash.length() <= 64);
	}

	// ==================== Constant-Time Comparison ====================

	@Test
	public void testConstantTimeEqualsMatching() {
		assertTrue(DuressManager.constantTimeEquals("abc", "abc"));
		assertTrue(DuressManager.constantTimeEquals("", ""));
	}

	@Test
	public void testConstantTimeEqualsNonMatching() {
		assertFalse(DuressManager.constantTimeEquals("abc", "abd"));
		assertFalse(DuressManager.constantTimeEquals("abc", "ab"));
		assertFalse(DuressManager.constantTimeEquals("abc", "abcd"));
	}

	@Test
	public void testConstantTimeEqualsDifferentLengths() {
		assertFalse(DuressManager.constantTimeEquals("short", "longer_string"));
	}

	// ==================== PinResult Enum ====================

	@Test
	public void testPinResultValues() {
		assertEquals(3, DuressManager.PinResult.values().length);
		assertNotNull(DuressManager.PinResult.REAL);
		assertNotNull(DuressManager.PinResult.DURESS);
		assertNotNull(DuressManager.PinResult.INVALID);
	}

	// ==================== DuressWipeScope Enum ====================

	@Test
	public void testWipeScopeValues() {
		assertEquals(3, DuressManager.DuressWipeScope.values().length);
		assertNotNull(DuressManager.DuressWipeScope.CHAT_ONLY);
		assertNotNull(DuressManager.DuressWipeScope.CHAT_AND_MODELS);
		assertNotNull(DuressManager.DuressWipeScope.EVERYTHING);
	}

	@Test
	public void testWipeScopeValueOf() {
		assertEquals(DuressManager.DuressWipeScope.CHAT_ONLY,
				DuressManager.DuressWipeScope.valueOf("CHAT_ONLY"));
		assertEquals(DuressManager.DuressWipeScope.CHAT_AND_MODELS,
				DuressManager.DuressWipeScope.valueOf("CHAT_AND_MODELS"));
		assertEquals(DuressManager.DuressWipeScope.EVERYTHING,
				DuressManager.DuressWipeScope.valueOf("EVERYTHING"));
	}

	// ==================== PBKDF2 Properties ====================

	@Test
	public void testPbkdf2IterationCountMinimum() {
		// PBKDF2 should use at least 10,000 iterations for security
		assertTrue("PBKDF2 iterations must be >= 10000, got: " + DuressManager.PBKDF2_ITERATIONS,
				DuressManager.PBKDF2_ITERATIONS >= 10000);
	}

	@Test
	public void testHashPinSimilarPinsProduceDifferentHashes() {
		// Adjacent PINs should produce completely different hashes
		String hash1 = DuressManager.hashPin("1234", TEST_SALT);
		String hash2 = DuressManager.hashPin("1235", TEST_SALT);
		assertNotEquals("Adjacent PINs must produce different hashes", hash1, hash2);
	}

	@Test
	public void testHashPinAllZeros() {
		String hash = DuressManager.hashPin("0000", TEST_SALT);
		assertNotNull(hash);
		assertFalse(hash.isEmpty());
	}

	@Test
	public void testHashPinAllNines() {
		String hash = DuressManager.hashPin("9999", TEST_SALT);
		assertNotNull(hash);
		assertFalse(hash.isEmpty());
		// And different from all-zeros
		String hashZeros = DuressManager.hashPin("0000", TEST_SALT);
		assertNotEquals(hash, hashZeros);
	}

	// ==================== Validation Integration ====================

	@Test
	public void testHashAndCompareRoundTrip() {
		// Simulate what happens during setPin + validatePin
		String pin = "4567";
		String storedHash = DuressManager.hashPin(pin, TEST_SALT);
		String inputHash = DuressManager.hashPin(pin, TEST_SALT);
		assertTrue("Same PIN should produce matching hash",
				DuressManager.constantTimeEquals(storedHash, inputHash));
	}

	@Test
	public void testHashAndCompareWrongPin() {
		String realPin = "1234";
		String wrongPin = "5678";
		String storedHash = DuressManager.hashPin(realPin, TEST_SALT);
		String inputHash = DuressManager.hashPin(wrongPin, TEST_SALT);
		assertFalse("Wrong PIN should not match",
				DuressManager.constantTimeEquals(storedHash, inputHash));
	}

	@Test
	public void testHashAndCompareDuressScenario() {
		// Simulate: real=1234, duress=0000, input=0000 should match duress
		String realHash = DuressManager.hashPin("1234", TEST_SALT);
		String duressHash = DuressManager.hashPin("0000", TEST_SALT);
		String inputHash = DuressManager.hashPin("0000", TEST_SALT);

		assertFalse("Input should NOT match real",
				DuressManager.constantTimeEquals(inputHash, realHash));
		assertTrue("Input SHOULD match duress",
				DuressManager.constantTimeEquals(inputHash, duressHash));
	}
}
