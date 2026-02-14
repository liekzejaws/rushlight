package net.osmand.plus.security;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Phase 15 Session 3: Comprehensive PIN entry validation tests.
 * Verifies all edge cases for PIN format validation and PinEntryDialog modes.
 */
public class PinEntryValidationTest {

	// ==================== Valid PIN Formats ====================

	@Test
	public void testValidPin1234() {
		assertTrue(DuressManager.isValidPinFormat("1234"));
	}

	@Test
	public void testValidPin0000() {
		assertTrue(DuressManager.isValidPinFormat("0000"));
	}

	@Test
	public void testValidPin9999() {
		assertTrue(DuressManager.isValidPinFormat("9999"));
	}

	@Test
	public void testValidPin5678() {
		assertTrue(DuressManager.isValidPinFormat("5678"));
	}

	// ==================== Invalid PIN Formats ====================

	@Test
	public void testInvalidPinEmpty() {
		assertFalse(DuressManager.isValidPinFormat(""));
	}

	@Test
	public void testInvalidPinNull() {
		assertFalse(DuressManager.isValidPinFormat(null));
	}

	@Test
	public void testInvalidPin1Digit() {
		assertFalse(DuressManager.isValidPinFormat("1"));
	}

	@Test
	public void testInvalidPin2Digits() {
		assertFalse(DuressManager.isValidPinFormat("12"));
	}

	@Test
	public void testInvalidPin3Digits() {
		assertFalse(DuressManager.isValidPinFormat("123"));
	}

	@Test
	public void testInvalidPin5Digits() {
		assertFalse(DuressManager.isValidPinFormat("12345"));
	}

	@Test
	public void testInvalidPin6Digits() {
		assertFalse(DuressManager.isValidPinFormat("123456"));
	}

	@Test
	public void testInvalidPinLetters() {
		assertFalse(DuressManager.isValidPinFormat("abcd"));
	}

	@Test
	public void testInvalidPinMixedAlphanumeric() {
		assertFalse(DuressManager.isValidPinFormat("12a4"));
	}

	@Test
	public void testInvalidPinSpaces() {
		assertFalse(DuressManager.isValidPinFormat("1 34"));
	}

	@Test
	public void testInvalidPinDashes() {
		assertFalse(DuressManager.isValidPinFormat("12-4"));
	}

	@Test
	public void testInvalidPinDots() {
		assertFalse(DuressManager.isValidPinFormat("1.34"));
	}

	@Test
	public void testInvalidPinSpecialChars() {
		assertFalse(DuressManager.isValidPinFormat("*#12"));
	}

	// ==================== PinMode Enum ====================

	@Test
	public void testPinModeSetRealExists() {
		assertNotNull(PinEntryDialog.PinMode.SET_REAL_PIN);
	}

	@Test
	public void testPinModeSetDuressExists() {
		assertNotNull(PinEntryDialog.PinMode.SET_DURESS_PIN);
	}

	@Test
	public void testPinModeAuthenticateExists() {
		assertNotNull(PinEntryDialog.PinMode.AUTHENTICATE);
	}

	@Test
	public void testPinModeCount() {
		assertEquals(3, PinEntryDialog.PinMode.values().length);
	}

	@Test
	public void testPinModeValueOf() {
		assertEquals(PinEntryDialog.PinMode.SET_REAL_PIN,
				PinEntryDialog.PinMode.valueOf("SET_REAL_PIN"));
		assertEquals(PinEntryDialog.PinMode.SET_DURESS_PIN,
				PinEntryDialog.PinMode.valueOf("SET_DURESS_PIN"));
		assertEquals(PinEntryDialog.PinMode.AUTHENTICATE,
				PinEntryDialog.PinMode.valueOf("AUTHENTICATE"));
	}

	// ==================== Hash Confirm Match Simulation ====================

	@Test
	public void testConfirmMatchSimulation() {
		// Simulate: user enters PIN twice → both entries hash to the same value
		String salt = "dGVzdHNhbHQxMjM0NTY3OA==";
		String firstEntry = DuressManager.hashPin("4321", salt);
		String confirmEntry = DuressManager.hashPin("4321", salt);
		assertTrue("Confirm entry should match first entry",
				DuressManager.constantTimeEquals(firstEntry, confirmEntry));
	}

	@Test
	public void testConfirmMismatchSimulation() {
		// Simulate: user enters different PINs → hashes differ
		String salt = "dGVzdHNhbHQxMjM0NTY3OA==";
		String firstEntry = DuressManager.hashPin("4321", salt);
		String confirmEntry = DuressManager.hashPin("4320", salt);
		assertFalse("Mismatched confirm entry should not match first entry",
				DuressManager.constantTimeEquals(firstEntry, confirmEntry));
	}
}
