package net.osmand.plus.morse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

/**
 * LAMPP Morse Code: Timing-based decode engine with adaptive WPM detection.
 *
 * The reverse of MorseEncoder. Receives raw signal timing events (ON/OFF durations)
 * from MicMorseProcessor or CameraMorseProcessor and classifies them into
 * Morse characters using the ITU timing standard and a reverse lookup table.
 *
 * Core constraint: LLM is NOT in the decode path. Pure DSP + lookup tables only.
 *
 * Adaptive speed detection: Tracks running average of shortest ON durations to
 * estimate the dit length, from which WPM = 1200 / ditLengthMs.
 *
 * Reference: MORSE-CODE-SPEC.md sections 5, 6.
 */
public class MorseDecoder {

    private static final Log LOG = PlatformUtil.getLog(MorseDecoder.class);

    /**
     * Callback interface for decoded Morse output.
     * All callbacks are invoked on the processor's thread — caller must marshal to UI thread.
     */
    public interface MorseDecoderListener {
        /** A complete character has been decoded from the accumulated dit/dah buffer. */
        void onCharDecoded(char c);
        /** A word space has been detected (emit a space character). */
        void onWordSpace();
        /** The dit/dah accumulation buffer has changed (for live preview). */
        void onBufferUpdate(@NonNull String dotsDashes);
        /** Adaptive WPM estimate has been updated. */
        void onWpmEstimate(int estimatedWpm);
    }

    // Adaptive WPM estimation
    private static final float ADAPTIVE_ALPHA = 0.3f; // Exponential moving average weight
    private static final int MIN_DIT_SAMPLES = 3;      // Min samples before trusting adaptive WPM

    // Timing tolerance: ±40% around expected durations
    // Dit/Dah boundary: 2× unit (midpoint between dit=1u and dah=3u)
    // Char/Word boundary: 5× unit (midpoint between char=3u and word=7u)
    private static final float DIT_DAH_BOUNDARY = 2.0f;
    private static final float ELEMENT_CHAR_BOUNDARY = 2.0f;
    private static final float CHAR_WORD_BOUNDARY = 5.0f;

    @Nullable
    private final MorseDecoderListener listener;

    // Current dit/dah accumulation buffer for the current character
    private final StringBuilder charBuffer = new StringBuilder();

    // Adaptive timing state
    private float adaptiveUnitMs;       // Current estimated unit duration in ms
    private float ditAvgMs;             // Running average of dit-length ON durations
    private int ditSampleCount;         // Number of dit-length samples collected
    private boolean useFixedWpm;        // If true, ignore adaptive and use fixed WPM
    private int fixedWpm;

    // Decoded text accumulator (for self-test comparison and history)
    private final StringBuilder decodedText = new StringBuilder();

    public MorseDecoder(@Nullable MorseDecoderListener listener) {
        this.listener = listener;
        // Initialize with default WPM timing
        this.adaptiveUnitMs = MorseTimingManager.unitDurationMs(MorseTimingManager.DEFAULT_WPM);
        this.ditAvgMs = adaptiveUnitMs;
        this.ditSampleCount = 0;
        this.useFixedWpm = false;
    }

    /**
     * Signal has turned ON (tone detected / light detected).
     * Called by the signal processor when a state transition OFF→ON occurs.
     */
    public void onSignalStart() {
        // No action needed at signal start — we act on signal end (when we know the duration)
    }

    /**
     * Signal has turned OFF after being ON for the given duration.
     * Classifies the duration as DIT or DAH and appends to the character buffer.
     *
     * @param durationMs how long the signal was ON in milliseconds
     */
    public void onSignalEnd(long durationMs) {
        if (durationMs <= 0) return;

        float unit = getCurrentUnitMs();

        // Classify: dit if < 2×unit, dah if >= 2×unit
        if (durationMs < unit * DIT_DAH_BOUNDARY) {
            charBuffer.append(MorseAlphabet.DIT_CHAR);
            // Update adaptive dit estimate
            updateDitEstimate(durationMs);
        } else {
            charBuffer.append(MorseAlphabet.DAH_CHAR);
            // Dah should be ~3× dit; can refine unit estimate from dah too
            // dah / 3 ≈ dit, but less reliable — only update if no dit samples yet
            if (ditSampleCount < MIN_DIT_SAMPLES) {
                updateDitEstimate(durationMs / 3.0f);
            }
        }

        if (listener != null) {
            listener.onBufferUpdate(charBuffer.toString());
        }
    }

    /**
     * Silence has been detected for the given duration.
     * Classifies the silence to determine if we've reached a character or word boundary.
     *
     * @param durationMs how long the silence lasted in milliseconds
     */
    public void onSilence(long durationMs) {
        if (durationMs <= 0) return;

        float unit = getCurrentUnitMs();

        if (durationMs < unit * ELEMENT_CHAR_BOUNDARY) {
            // Element space (within character) — do nothing, wait for more dits/dahs
            return;
        }

        if (durationMs < unit * CHAR_WORD_BOUNDARY) {
            // Character space — decode the accumulated buffer
            decodeBuffer();
        } else {
            // Word space — decode buffer + emit space
            decodeBuffer();
            if (listener != null) {
                listener.onWordSpace();
            }
            decodedText.append(' ');
        }
    }

    /**
     * Force-decode whatever is currently in the dit/dah buffer.
     * Call this when reception stops to process any remaining partial character.
     */
    public void flush() {
        decodeBuffer();
    }

    /**
     * Reset all decoder state. Clears buffers, resets adaptive WPM to default.
     */
    public void reset() {
        charBuffer.setLength(0);
        decodedText.setLength(0);
        adaptiveUnitMs = MorseTimingManager.unitDurationMs(MorseTimingManager.DEFAULT_WPM);
        ditAvgMs = adaptiveUnitMs;
        ditSampleCount = 0;
        if (listener != null) {
            listener.onBufferUpdate("");
        }
    }

    /**
     * Use a fixed WPM for timing classification instead of adaptive detection.
     *
     * @param wpm the fixed WPM to use (0 to re-enable adaptive)
     */
    public void setFixedWpm(int wpm) {
        if (wpm <= 0) {
            useFixedWpm = false;
        } else {
            useFixedWpm = true;
            fixedWpm = MorseTimingManager.clampWpm(wpm);
            adaptiveUnitMs = MorseTimingManager.unitDurationMs(fixedWpm);
        }
    }

    /**
     * Get the current estimated WPM (adaptive or fixed).
     */
    public int getEstimatedWpm() {
        float unit = getCurrentUnitMs();
        if (unit <= 0) return MorseTimingManager.DEFAULT_WPM;
        int wpm = (int) (1200.0f / unit);
        return MorseTimingManager.clampWpm(wpm);
    }

    /**
     * Get all decoded text accumulated since last reset.
     */
    @NonNull
    public String getDecodedText() {
        return decodedText.toString();
    }

    // ==================== Internal ====================

    private float getCurrentUnitMs() {
        if (useFixedWpm) {
            return MorseTimingManager.unitDurationMs(fixedWpm);
        }
        return adaptiveUnitMs;
    }

    private void updateDitEstimate(float durationMs) {
        if (useFixedWpm) return;

        if (ditSampleCount == 0) {
            ditAvgMs = durationMs;
        } else {
            // Exponential moving average
            ditAvgMs = ADAPTIVE_ALPHA * durationMs + (1.0f - ADAPTIVE_ALPHA) * ditAvgMs;
        }
        ditSampleCount++;

        // Update unit duration from dit average (dit = 1 unit)
        adaptiveUnitMs = ditAvgMs;

        // Notify listener of WPM update periodically (every 5 samples to avoid spam)
        if (listener != null && ditSampleCount % 5 == 0) {
            listener.onWpmEstimate(getEstimatedWpm());
        }
    }

    private void decodeBuffer() {
        if (charBuffer.length() == 0) return;

        String morseCode = charBuffer.toString();
        Character decoded = MorseAlphabet.decode(morseCode);

        if (decoded != null) {
            if (listener != null) {
                listener.onCharDecoded(decoded);
            }
            decodedText.append(decoded);
            LOG.debug("Decoded: " + morseCode + " → " + decoded);
        } else {
            // Unknown Morse sequence — emit '?' as placeholder
            if (listener != null) {
                listener.onCharDecoded('?');
            }
            decodedText.append('?');
            LOG.warn("Unknown morse code: " + morseCode);
        }

        charBuffer.setLength(0);
        if (listener != null) {
            listener.onBufferUpdate("");
        }
    }
}
