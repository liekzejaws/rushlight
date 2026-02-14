package net.osmand.plus.morse;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for MorseTimingManager — WPM to timing conversions.
 *
 * ITU standard: "PARIS" = 50 units. At W WPM, 1 unit = 1200/W ms.
 * Pure static math, zero dependencies.
 */
public class MorseTimingManagerTest {

	// ---- Unit duration at known WPM ----

	@Test
	public void testUnitDurationAt13Wpm() {
		// 1200 / 13 = 92 (integer division)
		assertEquals(92, MorseTimingManager.unitDurationMs(13));
	}

	@Test
	public void testUnitDurationAt5Wpm() {
		// 1200 / 5 = 240
		assertEquals(240, MorseTimingManager.unitDurationMs(5));
	}

	@Test
	public void testUnitDurationAt25Wpm() {
		// 1200 / 25 = 48
		assertEquals(48, MorseTimingManager.unitDurationMs(25));
	}

	// ---- Dit = 1 unit ----

	@Test
	public void testDitEqualsOneUnit() {
		for (int wpm = 5; wpm <= 25; wpm++) {
			assertEquals("dit should equal 1 unit at " + wpm + " WPM",
					MorseTimingManager.unitDurationMs(wpm),
					MorseTimingManager.ditDurationMs(wpm));
		}
	}

	// ---- Dah = 3 units ----

	@Test
	public void testDahEqualsThreeUnits() {
		for (int wpm = 5; wpm <= 25; wpm++) {
			assertEquals("dah should equal 3 units at " + wpm + " WPM",
					MorseTimingManager.unitDurationMs(wpm) * 3,
					MorseTimingManager.dahDurationMs(wpm));
		}
	}

	// ---- Element space = 1 unit ----

	@Test
	public void testElementSpaceEqualsOneUnit() {
		assertEquals(MorseTimingManager.unitDurationMs(13),
				MorseTimingManager.elementSpaceMs(13));
	}

	// ---- Character space = 3 units ----

	@Test
	public void testCharSpaceEqualsThreeUnits() {
		for (int wpm = 5; wpm <= 25; wpm++) {
			assertEquals("char space should equal 3 units at " + wpm + " WPM",
					MorseTimingManager.unitDurationMs(wpm) * 3,
					MorseTimingManager.charSpaceMs(wpm));
		}
	}

	// ---- Word space = 7 units ----

	@Test
	public void testWordSpaceEqualsSevenUnits() {
		for (int wpm = 5; wpm <= 25; wpm++) {
			assertEquals("word space should equal 7 units at " + wpm + " WPM",
					MorseTimingManager.unitDurationMs(wpm) * 7,
					MorseTimingManager.wordSpaceMs(wpm));
		}
	}

	// ---- Clamp: below minimum ----

	@Test
	public void testClampBelowMinimum() {
		assertEquals(MorseTimingManager.MIN_WPM, MorseTimingManager.clampWpm(3));
		assertEquals(MorseTimingManager.MIN_WPM, MorseTimingManager.clampWpm(0));
		assertEquals(MorseTimingManager.MIN_WPM, MorseTimingManager.clampWpm(-5));
	}

	// ---- Clamp: above maximum ----

	@Test
	public void testClampAboveMaximum() {
		assertEquals(MorseTimingManager.MAX_WPM, MorseTimingManager.clampWpm(30));
		assertEquals(MorseTimingManager.MAX_WPM, MorseTimingManager.clampWpm(100));
	}

	// ---- Clamp: within range ----

	@Test
	public void testClampWithinRange() {
		assertEquals(15, MorseTimingManager.clampWpm(15));
		assertEquals(5, MorseTimingManager.clampWpm(5));
		assertEquals(25, MorseTimingManager.clampWpm(25));
	}

	// ---- Zero/Negative WPM uses default ----

	@Test
	public void testZeroWpmUsesDefault() {
		assertEquals(MorseTimingManager.unitDurationMs(MorseTimingManager.DEFAULT_WPM),
				MorseTimingManager.unitDurationMs(0));
	}

	@Test
	public void testNegativeWpmUsesDefault() {
		assertEquals(MorseTimingManager.unitDurationMs(MorseTimingManager.DEFAULT_WPM),
				MorseTimingManager.unitDurationMs(-5));
	}

	// ---- Timing relationships at boundary WPM values ----

	@Test
	public void testTimingRelationships() {
		// At any WPM: dit < dah, charSpace < wordSpace
		for (int wpm = MorseTimingManager.MIN_WPM; wpm <= MorseTimingManager.MAX_WPM; wpm++) {
			assertTrue("dit < dah at " + wpm + " WPM",
					MorseTimingManager.ditDurationMs(wpm) < MorseTimingManager.dahDurationMs(wpm));
			assertTrue("charSpace < wordSpace at " + wpm + " WPM",
					MorseTimingManager.charSpaceMs(wpm) < MorseTimingManager.wordSpaceMs(wpm));
			assertTrue("all durations positive at " + wpm + " WPM",
					MorseTimingManager.ditDurationMs(wpm) > 0);
		}
	}

	// ---- Constants ----

	@Test
	public void testDefaultWpmIs13() {
		assertEquals(13, MorseTimingManager.DEFAULT_WPM);
	}

	@Test
	public void testMinWpmIs5() {
		assertEquals(5, MorseTimingManager.MIN_WPM);
	}

	@Test
	public void testMaxWpmIs25() {
		assertEquals(25, MorseTimingManager.MAX_WPM);
	}
}
