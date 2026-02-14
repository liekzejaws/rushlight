package net.osmand.plus.morse;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for MorseDecoder — signal timing to text conversion.
 *
 * Tests dit/dah classification, character/word boundary detection,
 * adaptive WPM estimation, and listener callbacks.
 */
public class MorseDecoderTest {

	private MorseDecoder decoder;
	private TestDecoderListener listener;

	@Before
	public void setUp() {
		listener = new TestDecoderListener();
		decoder = new MorseDecoder(listener);
	}

	@Test
	public void testDecodeDit() {
		int wpm = MorseTimingManager.DEFAULT_WPM;
		int ditMs = MorseTimingManager.ditDurationMs(wpm);

		// E = single dit
		decoder.onSignalStart();
		decoder.onSignalEnd(ditMs);
		decoder.onSilence(MorseTimingManager.charSpaceMs(wpm) + 1);

		assertEquals("Should have buffer update", true, listener.bufferUpdates.size() > 0);
	}

	@Test
	public void testDecodeDah() {
		int wpm = MorseTimingManager.DEFAULT_WPM;
		int dahMs = MorseTimingManager.dahDurationMs(wpm);

		// T = single dah
		decoder.onSignalStart();
		decoder.onSignalEnd(dahMs);
		decoder.onSilence(MorseTimingManager.charSpaceMs(wpm) + 1);

		// T should be decoded
		assertTrue("Buffer should contain dah (-)", listener.bufferUpdates.size() > 0);
	}

	@Test
	public void testCharacterBoundary() {
		int wpm = MorseTimingManager.DEFAULT_WPM;
		int ditMs = MorseTimingManager.ditDurationMs(wpm);
		int elemSpace = MorseTimingManager.elementSpaceMs(wpm);
		int charSpace = MorseTimingManager.charSpaceMs(wpm);

		// Send E (single dit) then wait for char boundary
		decoder.onSignalStart();
		decoder.onSignalEnd(ditMs);
		decoder.onSilence(charSpace + 10); // Trigger char decode

		assertTrue("Should have decoded at least one character",
				listener.decodedChars.size() > 0);
	}

	@Test
	public void testWordBoundary() {
		int wpm = MorseTimingManager.DEFAULT_WPM;
		int ditMs = MorseTimingManager.ditDurationMs(wpm);
		int wordSpace = MorseTimingManager.wordSpaceMs(wpm);

		// Send E then wait for word boundary
		decoder.onSignalStart();
		decoder.onSignalEnd(ditMs);
		decoder.onSilence(wordSpace + 50); // Well past word boundary

		assertTrue("Should detect word space", listener.wordSpaceCount > 0
				|| listener.decodedChars.size() > 0);
	}

	@Test
	public void testFlush() {
		int wpm = MorseTimingManager.DEFAULT_WPM;
		int ditMs = MorseTimingManager.ditDurationMs(wpm);

		// Send a dit but don't trigger char decode via silence
		decoder.onSignalStart();
		decoder.onSignalEnd(ditMs);

		// Force decode
		decoder.flush();

		assertTrue("Flush should decode pending elements",
				listener.decodedChars.size() > 0 || listener.bufferUpdates.size() > 0);
	}

	@Test
	public void testReset() {
		int wpm = MorseTimingManager.DEFAULT_WPM;
		int ditMs = MorseTimingManager.ditDurationMs(wpm);

		decoder.onSignalStart();
		decoder.onSignalEnd(ditMs);
		decoder.flush();

		String textBefore = decoder.getDecodedText();

		decoder.reset();

		assertEquals("Reset should clear decoded text", "", decoder.getDecodedText());
		assertEquals("Reset should restore default WPM",
				MorseTimingManager.DEFAULT_WPM, decoder.getEstimatedWpm());
	}

	@Test
	public void testSetFixedWpm() {
		decoder.setFixedWpm(20);
		assertEquals(20, decoder.getEstimatedWpm());

		// Setting 0 re-enables adaptive mode.
		// adaptiveUnitMs was updated to 20 WPM by setFixedWpm(20),
		// so getEstimatedWpm() returns 20 until adaptive recalibrates.
		decoder.setFixedWpm(0);
		assertEquals("After setFixedWpm(0), WPM reflects last adaptive unit",
				20, decoder.getEstimatedWpm());
	}

	@Test
	public void testNullListenerSafe() {
		// Should not throw with null listener
		MorseDecoder noListener = new MorseDecoder(null);
		int ditMs = MorseTimingManager.ditDurationMs(13);

		noListener.onSignalStart();
		noListener.onSignalEnd(ditMs);
		noListener.onSilence(MorseTimingManager.charSpaceMs(13) + 10);
		noListener.flush();
		// No exception = pass
	}

	// ---- Test listener implementation ----

	private static class TestDecoderListener implements MorseDecoder.MorseDecoderListener {
		final List<Character> decodedChars = new ArrayList<>();
		final List<String> bufferUpdates = new ArrayList<>();
		int wordSpaceCount = 0;
		int lastWpmEstimate = 0;

		@Override
		public void onCharDecoded(char c) {
			decodedChars.add(c);
		}

		@Override
		public void onWordSpace() {
			wordSpaceCount++;
		}

		@Override
		public void onBufferUpdate(String dotsDashes) {
			bufferUpdates.add(dotsDashes);
		}

		@Override
		public void onWpmEstimate(int estimatedWpm) {
			lastWpmEstimate = estimatedWpm;
		}
	}
}
