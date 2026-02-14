package net.osmand.plus.security;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Phase 16: Tests for PIN rate-limiting constants and logic in PinEntryDialog.
 * Validates exponential backoff configuration and lockout index calculation.
 */
public class PinRateLimitTest {

	// --- Configuration validation ---

	@Test
	public void testMaxAttemptsBeforeLockout_isThree() {
		assertEquals(3, PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT);
	}

	@Test
	public void testLockoutDurations_hasFourLevels() {
		assertEquals(4, PinEntryDialog.LOCKOUT_DURATIONS_MS.length);
	}

	@Test
	public void testLockoutDuration_level1_is30Seconds() {
		assertEquals(30_000L, PinEntryDialog.LOCKOUT_DURATIONS_MS[0]);
	}

	@Test
	public void testLockoutDuration_level2_is60Seconds() {
		assertEquals(60_000L, PinEntryDialog.LOCKOUT_DURATIONS_MS[1]);
	}

	@Test
	public void testLockoutDuration_level3_is5Minutes() {
		assertEquals(300_000L, PinEntryDialog.LOCKOUT_DURATIONS_MS[2]);
	}

	@Test
	public void testLockoutDuration_level4_is15Minutes() {
		assertEquals(900_000L, PinEntryDialog.LOCKOUT_DURATIONS_MS[3]);
	}

	@Test
	public void testLockoutDurations_areIncreasing() {
		for (int i = 1; i < PinEntryDialog.LOCKOUT_DURATIONS_MS.length; i++) {
			assertTrue("Duration at index " + i + " should be greater than previous",
					PinEntryDialog.LOCKOUT_DURATIONS_MS[i] > PinEntryDialog.LOCKOUT_DURATIONS_MS[i - 1]);
		}
	}

	@Test
	public void testLockoutDurations_allPositive() {
		for (long duration : PinEntryDialog.LOCKOUT_DURATIONS_MS) {
			assertTrue("Duration should be positive", duration > 0);
		}
	}

	// --- Lockout index calculation ---

	@Test
	public void testLockoutIndex_firstLockout_isZero() {
		// After 3 failures (first lockout trigger)
		int failedAttempts = 3;
		int lockoutIndex = (failedAttempts / PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT) - 1;
		assertEquals(0, lockoutIndex);
	}

	@Test
	public void testLockoutIndex_secondLockout_isOne() {
		int failedAttempts = 6;
		int lockoutIndex = (failedAttempts / PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT) - 1;
		assertEquals(1, lockoutIndex);
	}

	@Test
	public void testLockoutIndex_thirdLockout_isTwo() {
		int failedAttempts = 9;
		int lockoutIndex = (failedAttempts / PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT) - 1;
		assertEquals(2, lockoutIndex);
	}

	@Test
	public void testLockoutIndex_fourthLockout_isThree() {
		int failedAttempts = 12;
		int lockoutIndex = (failedAttempts / PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT) - 1;
		assertEquals(3, lockoutIndex);
	}

	@Test
	public void testLockoutIndex_clampsToMaxLevel() {
		// After 15+ failures, should clamp to last level
		int failedAttempts = 15;
		int lockoutIndex = (failedAttempts / PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT) - 1;
		lockoutIndex = Math.min(lockoutIndex, PinEntryDialog.LOCKOUT_DURATIONS_MS.length - 1);
		assertEquals(3, lockoutIndex); // Max index
	}

	@Test
	public void testLockoutIndex_veryHighFailures_clamps() {
		int failedAttempts = 99;
		int lockoutIndex = (failedAttempts / PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT) - 1;
		lockoutIndex = Math.min(lockoutIndex, PinEntryDialog.LOCKOUT_DURATIONS_MS.length - 1);
		assertEquals(3, lockoutIndex);
	}

	// --- Lockout trigger condition ---

	@Test
	public void testLockoutTrigger_atExactThreshold() {
		int failedAttempts = 3;
		boolean shouldLock = failedAttempts >= PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT
				&& failedAttempts % PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT == 0;
		assertTrue(shouldLock);
	}

	@Test
	public void testLockoutTrigger_belowThreshold_noLock() {
		int failedAttempts = 2;
		boolean shouldLock = failedAttempts >= PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT
				&& failedAttempts % PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT == 0;
		assertFalse(shouldLock);
	}

	@Test
	public void testLockoutTrigger_betweenThresholds_noLock() {
		int failedAttempts = 4;
		boolean shouldLock = failedAttempts >= PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT
				&& failedAttempts % PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT == 0;
		assertFalse(shouldLock);
	}

	@Test
	public void testLockoutTrigger_atSecondThreshold() {
		int failedAttempts = 6;
		boolean shouldLock = failedAttempts >= PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT
				&& failedAttempts % PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT == 0;
		assertTrue(shouldLock);
	}

	@Test
	public void testLockoutTrigger_atThirdThreshold() {
		int failedAttempts = 9;
		boolean shouldLock = failedAttempts >= PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT
				&& failedAttempts % PinEntryDialog.MAX_ATTEMPTS_BEFORE_LOCKOUT == 0;
		assertTrue(shouldLock);
	}

	// --- Total lockout duration sequence ---

	@Test
	public void testTotalLockoutTime_first4Lockouts() {
		long total = 0;
		for (long d : PinEntryDialog.LOCKOUT_DURATIONS_MS) {
			total += d;
		}
		// 30s + 60s + 300s + 900s = 1290s = 21.5 minutes
		assertEquals(1_290_000L, total);
	}
}
