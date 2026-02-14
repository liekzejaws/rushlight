package net.osmand.plus.morse;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for GoertzelFilter — single-frequency magnitude detector.
 *
 * Used for detecting Morse code audio tones. Pure math, zero dependencies.
 */
public class GoertzelFilterTest {

	private static final int SAMPLE_RATE = 44100;
	private static final int TARGET_FREQ = 800;
	private static final int BLOCK_SIZE = 1024;

	/**
	 * Generate a pure sine wave at the given frequency.
	 */
	private short[] generateSineWave(int frequency, int numSamples, double amplitude) {
		short[] samples = new short[numSamples];
		for (int i = 0; i < numSamples; i++) {
			double t = (double) i / SAMPLE_RATE;
			samples[i] = (short) (amplitude * Math.sin(2.0 * Math.PI * frequency * t));
		}
		return samples;
	}

	@Test
	public void testDetectsTargetFrequency() {
		short[] samples = generateSineWave(TARGET_FREQ, BLOCK_SIZE, 16000);
		float magnitude = GoertzelFilter.computeMagnitude(samples, BLOCK_SIZE, TARGET_FREQ, SAMPLE_RATE);

		assertTrue("Should detect strong 800Hz signal, got " + magnitude, magnitude > 100);
	}

	@Test
	public void testRejectsDifferentFrequency() {
		short[] samples = generateSineWave(400, BLOCK_SIZE, 16000);
		float magnitudeAtTarget = GoertzelFilter.computeMagnitude(samples, BLOCK_SIZE, TARGET_FREQ, SAMPLE_RATE);

		// Also measure at actual frequency for comparison
		float magnitudeAtActual = GoertzelFilter.computeMagnitude(samples, BLOCK_SIZE, 400, SAMPLE_RATE);

		assertTrue("Magnitude at wrong frequency should be much lower than at actual frequency",
				magnitudeAtTarget < magnitudeAtActual * 0.3);
	}

	@Test
	public void testDetectsSilence() {
		short[] silence = new short[BLOCK_SIZE]; // All zeros
		float magnitude = GoertzelFilter.computeMagnitude(silence, BLOCK_SIZE, TARGET_FREQ, SAMPLE_RATE);

		assertTrue("Silent signal should have near-zero magnitude, got " + magnitude,
				magnitude < 1.0f);
	}

	@Test
	public void testHandlesInvalidInput() {
		// Null samples
		float mag1 = GoertzelFilter.computeMagnitude(null, BLOCK_SIZE, TARGET_FREQ, SAMPLE_RATE);
		assertEquals("Null samples should return 0", 0.0f, mag1, 0.001f);

		// Zero numSamples
		float mag2 = GoertzelFilter.computeMagnitude(new short[BLOCK_SIZE], 0, TARGET_FREQ, SAMPLE_RATE);
		assertEquals("Zero samples should return 0", 0.0f, mag2, 0.001f);
	}

	@Test
	public void testFrequencyResolution() {
		// Use larger block for better frequency resolution (44100/4096 ≈ 11Hz bin width)
		int bigBlock = 4096;
		short[] samples = generateSineWave(800, bigBlock, 16000);

		float mag800 = GoertzelFilter.computeMagnitude(samples, bigBlock, 800, SAMPLE_RATE);
		float mag600 = GoertzelFilter.computeMagnitude(samples, bigBlock, 600, SAMPLE_RATE);
		float mag1000 = GoertzelFilter.computeMagnitude(samples, bigBlock, 1000, SAMPLE_RATE);

		assertTrue("800Hz target should have higher magnitude than 600Hz, got "
						+ mag800 + " vs " + mag600,
				mag800 > mag600);
		assertTrue("800Hz target should have higher magnitude than 1000Hz, got "
						+ mag800 + " vs " + mag1000,
				mag800 > mag1000);
	}

	@Test
	public void testStrongerSignalHigherMagnitude() {
		short[] weak = generateSineWave(TARGET_FREQ, BLOCK_SIZE, 1000);
		short[] strong = generateSineWave(TARGET_FREQ, BLOCK_SIZE, 16000);

		float magWeak = GoertzelFilter.computeMagnitude(weak, BLOCK_SIZE, TARGET_FREQ, SAMPLE_RATE);
		float magStrong = GoertzelFilter.computeMagnitude(strong, BLOCK_SIZE, TARGET_FREQ, SAMPLE_RATE);

		assertTrue("Stronger signal should have higher magnitude", magStrong > magWeak);
	}
}
