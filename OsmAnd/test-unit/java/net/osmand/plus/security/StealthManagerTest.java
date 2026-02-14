package net.osmand.plus.security;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Phase 15 Session 2: Tests for StealthManager dialer code validation and matching.
 * Tests the core logic without requiring Android context.
 */
public class StealthManagerTest {

	// ==================== Default Code ====================

	@Test
	public void testDefaultDialerCode() {
		assertEquals("*#73784#", StealthManager.DEFAULT_DIALER_CODE);
	}

	@Test
	public void testDefaultCodeIsValid() {
		assertTrue(StealthManager.isValidDialerCode(StealthManager.DEFAULT_DIALER_CODE));
	}

	// ==================== Code Validation ====================

	@Test
	public void testValidCodeStandard() {
		assertTrue(StealthManager.isValidDialerCode("*#12345#"));
	}

	@Test
	public void testValidCodeMinLength() {
		// Minimum 6 chars: *# + at least 2 digits + #
		assertTrue(StealthManager.isValidDialerCode("*#123#")); // 6 chars
	}

	@Test
	public void testValidCodeMaxLength() {
		// Maximum 12 chars: *# + up to 9 digits + #
		assertTrue(StealthManager.isValidDialerCode("*#123456789#")); // 12 chars
	}

	@Test
	public void testInvalidCodeNull() {
		assertFalse(StealthManager.isValidDialerCode(null));
	}

	@Test
	public void testInvalidCodeEmpty() {
		assertFalse(StealthManager.isValidDialerCode(""));
	}

	@Test
	public void testInvalidCodeTooShort() {
		assertFalse(StealthManager.isValidDialerCode("*#1#")); // 4 chars, needs 6+
		assertFalse(StealthManager.isValidDialerCode("*#12#")); // 5 chars
	}

	@Test
	public void testInvalidCodeTooLong() {
		assertFalse(StealthManager.isValidDialerCode("*#1234567890#")); // 13 chars
	}

	@Test
	public void testInvalidCodeNoStarHash() {
		assertFalse(StealthManager.isValidDialerCode("123456#"));
		assertFalse(StealthManager.isValidDialerCode("#12345#"));
	}

	@Test
	public void testInvalidCodeNoTrailingHash() {
		assertFalse(StealthManager.isValidDialerCode("*#12345"));
	}

	@Test
	public void testInvalidCodeLettersInMiddle() {
		assertFalse(StealthManager.isValidDialerCode("*#abc12#"));
		assertFalse(StealthManager.isValidDialerCode("*#12ab3#"));
	}

	@Test
	public void testInvalidCodeSpecialCharsInMiddle() {
		assertFalse(StealthManager.isValidDialerCode("*#12*45#"));
		assertFalse(StealthManager.isValidDialerCode("*#12#45#"));
	}

	// ==================== Code Length Constants ====================

	@Test
	public void testMinCodeLength() {
		assertEquals(6, StealthManager.MIN_CODE_LENGTH);
	}

	@Test
	public void testMaxCodeLength() {
		assertEquals(12, StealthManager.MAX_CODE_LENGTH);
	}
}
