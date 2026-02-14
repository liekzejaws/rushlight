package net.osmand.plus.morse;

import net.osmand.plus.morse.MorseEncoder.EventType;
import net.osmand.plus.morse.MorseEncoder.MorseEvent;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for MorseEncoder — text to timed Morse event conversion.
 *
 * Life-safety critical: incorrect event sequences mean unreadable transmissions.
 */
public class MorseEncoderTest {

	private static final int DEFAULT_WPM = 13;

	@Test
	public void testEncodeSos() {
		List<MorseEvent> events = MorseEncoder.encode("SOS", DEFAULT_WPM);
		assertFalse("SOS should produce events", events.isEmpty());

		String display = MorseEncoder.toDisplayString(events);
		assertEquals("SOS should display as ... --- ...", "... --- ...", display);
	}

	@Test
	public void testEncodeEmptyString() {
		List<MorseEvent> events = MorseEncoder.encode("", DEFAULT_WPM);
		assertTrue("Empty string should produce no events", events.isEmpty());
	}

	@Test
	public void testEncodeWhitespaceOnly() {
		List<MorseEvent> events = MorseEncoder.encode("   ", DEFAULT_WPM);
		assertTrue("Whitespace-only should produce no events", events.isEmpty());
	}

	@Test
	public void testEncodeWithSpaces() {
		List<MorseEvent> events = MorseEncoder.encode("HI MOM", DEFAULT_WPM);
		assertFalse(events.isEmpty());

		// Should contain at least one WORD_SPACE event
		boolean hasWordSpace = false;
		for (MorseEvent event : events) {
			if (event.getType() == EventType.WORD_SPACE) {
				hasWordSpace = true;
				break;
			}
		}
		assertTrue("Words should be separated by WORD_SPACE", hasWordSpace);
	}

	@Test
	public void testEncodeUnsupportedCharsSkipped() {
		// Emoji/unknown chars should be skipped without crashing
		List<MorseEvent> events = MorseEncoder.encode("A\uD83D\uDE00B", DEFAULT_WPM);
		assertFalse(events.isEmpty());

		// Should still encode A and B
		String display = MorseEncoder.toDisplayString(events);
		assertTrue("Should contain A (.-)", display.contains(".-"));
		assertTrue("Should contain B (-...)", display.contains("-..."));
	}

	@Test
	public void testEventTimingsMatchWpm() {
		int wpm = 13;
		int expectedDit = MorseTimingManager.ditDurationMs(wpm);
		int expectedDah = MorseTimingManager.dahDurationMs(wpm);

		List<MorseEvent> events = MorseEncoder.encode("A", wpm);
		// A = .- (dit, element_space, dah)

		assertFalse(events.isEmpty());

		// Find first DIT
		MorseEvent firstDit = null;
		MorseEvent firstDah = null;
		for (MorseEvent e : events) {
			if (e.getType() == EventType.DIT && firstDit == null) firstDit = e;
			if (e.getType() == EventType.DAH && firstDah == null) firstDah = e;
		}

		assertNotNull("A should contain a DIT", firstDit);
		assertNotNull("A should contain a DAH", firstDah);
		assertEquals("DIT duration should match WPM", expectedDit, firstDit.getDurationMs());
		assertEquals("DAH duration should match WPM", expectedDah, firstDah.getDurationMs());
	}

	@Test
	public void testDitDahSequenceForA() {
		// A = .- → DIT, ELEMENT_SPACE, DAH
		List<MorseEvent> events = MorseEncoder.encode("A", DEFAULT_WPM);

		assertEquals("A should have 3 events (dit, space, dah)", 3, events.size());
		assertEquals(EventType.DIT, events.get(0).getType());
		assertEquals(EventType.ELEMENT_SPACE, events.get(1).getType());
		assertEquals(EventType.DAH, events.get(2).getType());
	}

	@Test
	public void testCharacterSpacingBetweenLetters() {
		// AB → A (dit,space,dah) + CHAR_SPACE + B (dah,space,dit,space,dit,space,dit)
		List<MorseEvent> events = MorseEncoder.encode("AB", DEFAULT_WPM);

		int charSpaceCount = 0;
		for (MorseEvent event : events) {
			if (event.getType() == EventType.CHAR_SPACE) {
				charSpaceCount++;
				assertEquals("Char space should be 3 units",
						MorseTimingManager.charSpaceMs(DEFAULT_WPM),
						event.getDurationMs());
			}
		}
		assertEquals("Should have exactly 1 char space between A and B", 1, charSpaceCount);
	}

	@Test
	public void testWordSpacingDuration() {
		List<MorseEvent> events = MorseEncoder.encode("A B", DEFAULT_WPM);

		for (MorseEvent event : events) {
			if (event.getType() == EventType.WORD_SPACE) {
				assertEquals("Word space should be 7 units",
						MorseTimingManager.wordSpaceMs(DEFAULT_WPM),
						event.getDurationMs());
				return;
			}
		}
		fail("Should contain a WORD_SPACE event");
	}

	@Test
	public void testEncodeAtMinWpm() {
		List<MorseEvent> events = MorseEncoder.encode("SOS", MorseTimingManager.MIN_WPM);
		assertFalse("Should encode at min WPM", events.isEmpty());

		// Timings should use MIN_WPM
		for (MorseEvent event : events) {
			if (event.getType() == EventType.DIT) {
				assertEquals(MorseTimingManager.ditDurationMs(MorseTimingManager.MIN_WPM),
						event.getDurationMs());
				break;
			}
		}
	}

	@Test
	public void testEncodeAtMaxWpm() {
		List<MorseEvent> events = MorseEncoder.encode("SOS", MorseTimingManager.MAX_WPM);
		assertFalse("Should encode at max WPM", events.isEmpty());

		for (MorseEvent event : events) {
			if (event.getType() == EventType.DIT) {
				assertEquals(MorseTimingManager.ditDurationMs(MorseTimingManager.MAX_WPM),
						event.getDurationMs());
				break;
			}
		}
	}

	@Test
	public void testEncodeClampsBelowMinWpm() {
		// WPM = 1 should be clamped to MIN_WPM
		List<MorseEvent> events = MorseEncoder.encode("E", 1);
		assertFalse(events.isEmpty());
		assertEquals(EventType.DIT, events.get(0).getType());
		assertEquals(MorseTimingManager.ditDurationMs(MorseTimingManager.MIN_WPM),
				events.get(0).getDurationMs());
	}

	@Test
	public void testTotalDurationMs() {
		List<MorseEvent> events = MorseEncoder.encode("E", DEFAULT_WPM);
		// E = single dit
		assertEquals(1, events.size());
		assertEquals(MorseTimingManager.ditDurationMs(DEFAULT_WPM),
				MorseEncoder.totalDurationMs(events));
	}

	@Test
	public void testSignalOnForDitAndDah() {
		List<MorseEvent> events = MorseEncoder.encode("A", DEFAULT_WPM);
		// A = dit, space, dah
		assertTrue("DIT should be signal ON", events.get(0).isSignalOn());
		assertFalse("ELEMENT_SPACE should be signal OFF", events.get(1).isSignalOn());
		assertTrue("DAH should be signal ON", events.get(2).isSignalOn());
	}

	@Test
	public void testNoTrailingSpaceEvents() {
		List<MorseEvent> events = MorseEncoder.encode("A ", DEFAULT_WPM);
		if (!events.isEmpty()) {
			MorseEvent last = events.get(events.size() - 1);
			assertTrue("Last event should be signal ON (no trailing spaces)",
					last.isSignalOn());
		}
	}

	@Test
	public void testCaseInsensitive() {
		List<MorseEvent> upper = MorseEncoder.encode("SOS", DEFAULT_WPM);
		List<MorseEvent> lower = MorseEncoder.encode("sos", DEFAULT_WPM);
		assertEquals("Case should not affect event count", upper.size(), lower.size());
		assertEquals("Case should not affect display",
				MorseEncoder.toDisplayString(upper),
				MorseEncoder.toDisplayString(lower));
	}
}
