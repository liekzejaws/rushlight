package net.osmand.plus.morse;

/**
 * LAMPP Morse Code: WPM to millisecond timing conversions.
 *
 * ITU standard: The word "PARIS" = 50 units.
 * At W words per minute, 1 unit = 1200 / W milliseconds.
 *
 * Timing breakdown:
 * - Dit (dot):           1 unit
 * - Dah (dash):          3 units
 * - Element space:       1 unit  (between dits/dahs within a character)
 * - Character space:     3 units (between characters)
 * - Word space:          7 units (between words)
 *
 * Example at 13 WPM: dit=92ms, dah=277ms, char-space=277ms, word-space=646ms
 *
 * Pure static math — zero dependencies.
 */
public final class MorseTimingManager {

    /** Default words per minute */
    public static final int DEFAULT_WPM = 13;

    /** Minimum WPM supported */
    public static final int MIN_WPM = 5;

    /** Maximum WPM supported */
    public static final int MAX_WPM = 25;

    private MorseTimingManager() {
        // Static utility class
    }

    /**
     * Calculate the duration of one timing unit in milliseconds.
     *
     * @param wpm words per minute (based on "PARIS" = 50 units)
     * @return duration of one unit in ms
     */
    public static int unitDurationMs(int wpm) {
        if (wpm <= 0) wpm = DEFAULT_WPM;
        return 1200 / wpm;
    }

    /**
     * Duration of a dit (dot) = 1 unit.
     */
    public static int ditDurationMs(int wpm) {
        return unitDurationMs(wpm);
    }

    /**
     * Duration of a dah (dash) = 3 units.
     */
    public static int dahDurationMs(int wpm) {
        return unitDurationMs(wpm) * 3;
    }

    /**
     * Duration of space between elements (dits/dahs) within a character = 1 unit.
     */
    public static int elementSpaceMs(int wpm) {
        return unitDurationMs(wpm);
    }

    /**
     * Duration of space between characters = 3 units.
     */
    public static int charSpaceMs(int wpm) {
        return unitDurationMs(wpm) * 3;
    }

    /**
     * Duration of space between words = 7 units.
     */
    public static int wordSpaceMs(int wpm) {
        return unitDurationMs(wpm) * 7;
    }

    /**
     * Clamp a WPM value to the supported range [MIN_WPM, MAX_WPM].
     */
    public static int clampWpm(int wpm) {
        return Math.max(MIN_WPM, Math.min(MAX_WPM, wpm));
    }
}
