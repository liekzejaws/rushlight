package net.osmand.plus.morse;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

/**
 * LAMPP Morse Code: Microphone audio capture with Goertzel tone detection.
 *
 * Captures audio via AudioRecord (44100Hz, MONO, PCM_16BIT) and uses the
 * Goertzel algorithm to detect the presence/absence of a target frequency
 * (default 800Hz). State transitions (tone on/off) are reported to a
 * MorseDecoder for character classification.
 *
 * Requires RECORD_AUDIO permission (runtime check done by MorseFragment).
 *
 * Reference: MORSE-CODE-SPEC.md section 5.2
 */
public class MicMorseProcessor {

    private static final Log LOG = PlatformUtil.getLog(MicMorseProcessor.class);

    // Audio config — matches MorseAudioGenerator exactly
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // Goertzel processing parameters
    private static final int GOERTZEL_BLOCK_SIZE = 1024; // ~23ms at 44100Hz, ~43Hz resolution
    private static final int CALIBRATION_FRAMES = 22;     // ~500ms of calibration at 23ms/frame
    private static final int MIN_TRANSITION_MS = 20;       // Debounce: ignore transitions < 20ms
    private static final float DEFAULT_THRESHOLD_MULTIPLIER = 3.0f;

    private final int targetFrequency;
    private final int sampleRate;

    private AudioRecord audioRecord;
    @Nullable
    private MorseDecoder decoder;
    private Thread captureThread;
    private volatile boolean listening;

    // Tone detection state
    private boolean tonePresent;
    private long lastTransitionTime;

    // Noise calibration
    private float noiseFloor;
    private float threshold;
    private float sensitivity = 0.5f; // 0.0 to 1.0

    // Calibration tracking
    private int calibrationCount;
    private float calibrationSum;
    private boolean calibrated;

    /**
     * Create a microphone Morse processor.
     *
     * @param targetFrequency the tone frequency to detect (Hz, e.g., 800)
     * @param sampleRate      audio sample rate (Hz, e.g., 44100)
     */
    public MicMorseProcessor(int targetFrequency, int sampleRate) {
        this.targetFrequency = targetFrequency;
        this.sampleRate = sampleRate;
    }

    /**
     * Set the decoder that will receive timing events.
     */
    public void setDecoder(@NonNull MorseDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * Initialize the AudioRecord. Must be called before start().
     *
     * @return true if AudioRecord was created successfully
     */
    public boolean init() {
        try {
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                LOG.error("Failed to get min buffer size for AudioRecord");
                return false;
            }

            // Use a buffer at least 4× the Goertzel block size for smooth reading
            int bufferSize = Math.max(minBufferSize, GOERTZEL_BLOCK_SIZE * 4 * 2); // *2 for bytes per sample

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                LOG.error("AudioRecord failed to initialize");
                audioRecord.release();
                audioRecord = null;
                return false;
            }

            return true;
        } catch (SecurityException e) {
            LOG.error("RECORD_AUDIO permission not granted", e);
            return false;
        } catch (Exception e) {
            LOG.error("Failed to initialize AudioRecord", e);
            return false;
        }
    }

    /**
     * Start capturing audio and detecting tones.
     * Spawns a background thread for the capture loop.
     */
    public void start() {
        if (audioRecord == null || listening) return;

        // Reset state
        tonePresent = false;
        calibrated = false;
        calibrationCount = 0;
        calibrationSum = 0;
        noiseFloor = 0;
        lastTransitionTime = System.currentTimeMillis();

        listening = true;
        audioRecord.startRecording();

        captureThread = new Thread(this::captureLoop, "MicMorseThread");
        captureThread.start();
        LOG.info("MicMorseProcessor started (freq=" + targetFrequency + "Hz)");
    }

    /**
     * Stop capturing and release the capture thread.
     */
    public void stop() {
        listening = false;
        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(1000);
            } catch (InterruptedException ignored) {
            }
            captureThread = null;
        }
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
        }
        LOG.info("MicMorseProcessor stopped");
    }

    /**
     * Release all resources.
     */
    public void release() {
        stop();
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    /**
     * Check if currently listening.
     */
    public boolean isListening() {
        return listening;
    }

    /**
     * Set detection sensitivity.
     *
     * @param sensitivity 0.0 (least sensitive, needs strong signal) to 1.0 (most sensitive)
     */
    public void setSensitivity(float sensitivity) {
        this.sensitivity = Math.max(0.0f, Math.min(1.0f, sensitivity));
        recalculateThreshold();
    }

    // ==================== Capture Loop ====================

    private void captureLoop() {
        short[] buffer = new short[GOERTZEL_BLOCK_SIZE];

        while (listening) {
            int read = audioRecord.read(buffer, 0, GOERTZEL_BLOCK_SIZE);
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                    LOG.error("AudioRecord read error: INVALID_OPERATION");
                    break;
                }
                continue;
            }

            // Compute magnitude of the target frequency
            float magnitude = GoertzelFilter.computeMagnitude(
                    buffer, read, targetFrequency, sampleRate);

            // Calibration phase: collect noise floor samples
            if (!calibrated) {
                calibrationSum += magnitude;
                calibrationCount++;
                if (calibrationCount >= CALIBRATION_FRAMES) {
                    noiseFloor = calibrationSum / calibrationCount;
                    recalculateThreshold();
                    calibrated = true;
                    LOG.info("Noise calibrated: floor=" + noiseFloor + ", threshold=" + threshold);
                }
                continue; // Don't process during calibration
            }

            // Detect tone presence
            long now = System.currentTimeMillis();
            boolean toneDetected = magnitude > threshold;

            if (toneDetected && !tonePresent) {
                // Transition: OFF → ON
                long silenceDuration = now - lastTransitionTime;

                // Debounce check
                if (silenceDuration < MIN_TRANSITION_MS) continue;

                if (decoder != null && lastTransitionTime > 0) {
                    decoder.onSilence(silenceDuration);
                    decoder.onSignalStart();
                }
                tonePresent = true;
                lastTransitionTime = now;

            } else if (!toneDetected && tonePresent) {
                // Transition: ON → OFF
                long toneDuration = now - lastTransitionTime;

                // Debounce check
                if (toneDuration < MIN_TRANSITION_MS) continue;

                if (decoder != null) {
                    decoder.onSignalEnd(toneDuration);
                }
                tonePresent = false;
                lastTransitionTime = now;
            }
        }

        // If we stopped while tone was present, report the final signal end
        if (tonePresent && decoder != null) {
            long toneDuration = System.currentTimeMillis() - lastTransitionTime;
            decoder.onSignalEnd(toneDuration);
            tonePresent = false;
        }
    }

    private void recalculateThreshold() {
        // Sensitivity maps: 0.0 → 5× noise floor (hard to trigger), 1.0 → 1.5× noise floor (easy)
        float multiplier = DEFAULT_THRESHOLD_MULTIPLIER + (0.5f - sensitivity) * 4.0f;
        multiplier = Math.max(1.2f, multiplier); // Never below 1.2× noise
        threshold = noiseFloor * multiplier;

        // Ensure a minimum threshold even if noise floor is very low
        threshold = Math.max(threshold, 0.01f);
    }
}
