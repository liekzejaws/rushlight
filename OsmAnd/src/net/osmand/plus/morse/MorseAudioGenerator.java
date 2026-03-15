/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

/**
 * LAMPP Morse Code: Generates audio tones via AudioTrack for Morse transmission.
 *
 * Uses AudioTrack in streaming mode with PCM_16BIT, MONO, 44100 Hz.
 * AudioTrack.write() naturally blocks, providing precise timing for tone playback.
 *
 * No permissions required for audio output.
 */
public class MorseAudioGenerator {

    private static final Log LOG = PlatformUtil.getLog(MorseAudioGenerator.class);

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    /** Default tone frequency in Hz */
    public static final int DEFAULT_FREQUENCY = 800;

    private final int frequency;
    private AudioTrack audioTrack;
    private boolean initialized;

    /**
     * Create an audio generator with the specified tone frequency.
     *
     * @param frequency tone frequency in Hz (typically 600-1000)
     */
    public MorseAudioGenerator(int frequency) {
        this.frequency = Math.max(200, Math.min(2000, frequency));
    }

    /**
     * Create an audio generator with the default frequency (800 Hz).
     */
    public MorseAudioGenerator() {
        this(DEFAULT_FREQUENCY);
    }

    /**
     * Initialize the AudioTrack. Must be called before playTone/playSilence.
     *
     * @return true if initialization succeeded
     */
    public boolean init() {
        try {
            int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                LOG.error("Failed to get min buffer size for AudioTrack");
                return false;
            }

            // Use a buffer large enough for smooth playback
            int bufferSize = Math.max(minBufferSize, SAMPLE_RATE * 2); // At least 1 second buffer

            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();

            initialized = true;
            return true;
        } catch (Exception e) {
            LOG.error("Failed to initialize AudioTrack", e);
            return false;
        }
    }

    /**
     * Start the AudioTrack playback stream.
     */
    public void start() {
        if (audioTrack != null && initialized) {
            try {
                audioTrack.play();
            } catch (IllegalStateException e) {
                LOG.error("Failed to start AudioTrack", e);
            }
        }
    }

    /**
     * Stop the AudioTrack playback.
     */
    public void stop() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
            } catch (IllegalStateException e) {
                LOG.error("Failed to stop AudioTrack", e);
            }
        }
    }

    /**
     * Release AudioTrack resources. Must be called when done.
     */
    public void release() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
            } catch (IllegalStateException ignored) {
            }
            audioTrack.release();
            audioTrack = null;
        }
        initialized = false;
    }

    /**
     * Play a sine wave tone for the specified duration.
     * This method BLOCKS for the duration of the tone.
     *
     * @param durationMs tone duration in milliseconds
     */
    public void playTone(int durationMs) {
        if (audioTrack == null || !initialized) return;

        int numSamples = (int) ((long) SAMPLE_RATE * durationMs / 1000);
        short[] samples = new short[numSamples];

        double twoPiF = 2.0 * Math.PI * frequency;
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / SAMPLE_RATE;
            // Apply short fade-in/out to avoid clicks (5ms ramps)
            double envelope = 1.0;
            int rampSamples = (int) (SAMPLE_RATE * 0.005); // 5ms
            if (i < rampSamples) {
                envelope = (double) i / rampSamples;
            } else if (i > numSamples - rampSamples) {
                envelope = (double) (numSamples - i) / rampSamples;
            }
            samples[i] = (short) (Short.MAX_VALUE * 0.8 * envelope * Math.sin(twoPiF * t));
        }

        try {
            audioTrack.write(samples, 0, samples.length);
        } catch (Exception e) {
            LOG.error("Error writing tone to AudioTrack", e);
        }
    }

    /**
     * Play silence for the specified duration.
     * This method BLOCKS for the duration of the silence.
     *
     * @param durationMs silence duration in milliseconds
     */
    public void playSilence(int durationMs) {
        if (audioTrack == null || !initialized) return;

        int numSamples = (int) ((long) SAMPLE_RATE * durationMs / 1000);
        short[] silence = new short[numSamples];
        // Array is zero-initialized by default

        try {
            audioTrack.write(silence, 0, silence.length);
        } catch (Exception e) {
            LOG.error("Error writing silence to AudioTrack", e);
        }
    }

    /**
     * Check if the audio generator is initialized and ready.
     */
    public boolean isReady() {
        return initialized && audioTrack != null;
    }
}
