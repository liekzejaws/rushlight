package net.osmand.plus.morse;

/**
 * LAMPP Morse Code: Goertzel algorithm for efficient single-frequency tone detection.
 *
 * The Goertzel algorithm is computationally more efficient than FFT when detecting
 * a single frequency (like an 800Hz Morse tone). It requires only O(N) multiplications
 * and a constant amount of memory, regardless of the number of frequency bins.
 *
 * Pure math — zero Android dependencies.
 *
 * Reference: MORSE-CODE-SPEC.md section 5.2
 */
public final class GoertzelFilter {

    private GoertzelFilter() {
        // Static utility class
    }

    /**
     * Compute the magnitude of a target frequency in the given audio samples
     * using the Goertzel algorithm.
     *
     * Algorithm:
     *   k = round(N * targetFreq / sampleRate)
     *   w = (2 * PI * k) / N
     *   coeff = 2 * cos(w)
     *   For each sample: s0 = coeff * s1 - s2 + sample; s2 = s1; s1 = s0
     *   magnitude = sqrt(s1^2 + s2^2 - coeff * s1 * s2)
     *
     * @param samples    PCM_16BIT audio samples (short values, range -32768 to 32767)
     * @param numSamples number of samples to process (typically 1024 for good resolution)
     * @param targetFreq target frequency in Hz (e.g., 800)
     * @param sampleRate sample rate in Hz (e.g., 44100)
     * @return magnitude — relative power of the target frequency (higher = stronger).
     *         Returns 0.0 if inputs are invalid.
     */
    public static float computeMagnitude(short[] samples, int numSamples,
                                          int targetFreq, int sampleRate) {
        if (samples == null || numSamples <= 0 || targetFreq <= 0 || sampleRate <= 0) {
            return 0.0f;
        }
        numSamples = Math.min(numSamples, samples.length);

        // Calculate the Goertzel coefficient
        // k = nearest frequency bin index for our target frequency
        int k = (int) (0.5 + ((long) numSamples * targetFreq) / sampleRate);
        double w = (2.0 * Math.PI * k) / numSamples;
        double coeff = 2.0 * Math.cos(w);

        // Process all samples through the Goertzel recurrence
        double s0, s1 = 0.0, s2 = 0.0;
        for (int i = 0; i < numSamples; i++) {
            // Normalize sample to [-1.0, 1.0] range
            double sample = samples[i] / 32768.0;
            s0 = coeff * s1 - s2 + sample;
            s2 = s1;
            s1 = s0;
        }

        // Calculate magnitude from final state
        // power = s1^2 + s2^2 - coeff * s1 * s2
        double power = s1 * s1 + s2 * s2 - coeff * s1 * s2;

        // Return magnitude (sqrt of power), clamped to non-negative
        return (float) Math.sqrt(Math.max(0.0, power));
    }

    /**
     * Compute the magnitude for a specific buffer size that gives good frequency
     * resolution. Processes the most recent `optimalSize` samples from the buffer.
     *
     * For 44100 Hz sample rate, 1024 samples gives ~43 Hz frequency resolution,
     * which is sufficient to isolate an 800 Hz tone from neighboring frequencies.
     *
     * @param samples    full audio buffer
     * @param bufferLen  actual number of valid samples in buffer
     * @param targetFreq target frequency in Hz
     * @param sampleRate sample rate in Hz
     * @param blockSize  number of samples to process (e.g., 1024)
     * @return magnitude of the target frequency
     */
    public static float computeMagnitudeBlock(short[] samples, int bufferLen,
                                               int targetFreq, int sampleRate,
                                               int blockSize) {
        if (bufferLen < blockSize) {
            // Not enough samples — process what we have
            return computeMagnitude(samples, bufferLen, targetFreq, sampleRate);
        }
        // Use the most recent blockSize samples
        int offset = bufferLen - blockSize;
        short[] block = new short[blockSize];
        System.arraycopy(samples, offset, block, 0, blockSize);
        return computeMagnitude(block, blockSize, targetFreq, sampleRate);
    }
}
