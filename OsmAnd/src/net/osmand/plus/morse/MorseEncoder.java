package net.osmand.plus.morse;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * LAMPP Morse Code: Converts plain text into a sequence of timed Morse events.
 *
 * Pre-computes the entire event sequence before transmission starts.
 * The playback loop is trivial: iterate through events, signal on/off for each duration.
 *
 * Depends on: MorseAlphabet (lookup), MorseTimingManager (timing).
 */
public final class MorseEncoder {

    /**
     * Types of events in a Morse transmission sequence.
     */
    public enum EventType {
        /** Short signal (dot) — signal ON */
        DIT,
        /** Long signal (dash) — signal ON */
        DAH,
        /** Silence between elements within a character */
        ELEMENT_SPACE,
        /** Silence between characters */
        CHAR_SPACE,
        /** Silence between words */
        WORD_SPACE
    }

    /**
     * A single event in a Morse transmission sequence.
     * Each event has a type and a duration in milliseconds.
     */
    public static class MorseEvent {
        private final EventType type;
        private final int durationMs;

        public MorseEvent(@NonNull EventType type, int durationMs) {
            this.type = type;
            this.durationMs = durationMs;
        }

        @NonNull
        public EventType getType() {
            return type;
        }

        public int getDurationMs() {
            return durationMs;
        }

        /**
         * Whether the signal should be ON (flash/tone active) during this event.
         * True for DIT and DAH, false for all space types.
         */
        public boolean isSignalOn() {
            return type == EventType.DIT || type == EventType.DAH;
        }

        /**
         * Display symbol for the Morse preview text.
         *
         * @return "." for DIT, "-" for DAH, " " for CHAR_SPACE, "  " for WORD_SPACE, "" for ELEMENT_SPACE
         */
        @NonNull
        public String displaySymbol() {
            switch (type) {
                case DIT:
                    return ".";
                case DAH:
                    return "-";
                case CHAR_SPACE:
                    return " ";
                case WORD_SPACE:
                    return "  ";
                case ELEMENT_SPACE:
                default:
                    return "";
            }
        }
    }

    private MorseEncoder() {
        // Static utility class
    }

    /**
     * Encode a text string into a flat list of Morse events with timing.
     *
     * The sequence alternates between signal-on events (DIT/DAH) and silence events
     * (ELEMENT_SPACE, CHAR_SPACE, WORD_SPACE). Unsupported characters are skipped.
     *
     * @param text the text to encode
     * @param wpm  words per minute for timing calculations
     * @return ordered list of MorseEvents ready for sequential playback
     */
    @NonNull
    public static List<MorseEvent> encode(@NonNull String text, int wpm) {
        List<MorseEvent> events = new ArrayList<>();
        String normalized = text.trim().toUpperCase();

        if (normalized.isEmpty()) {
            return events;
        }

        wpm = MorseTimingManager.clampWpm(wpm);

        boolean firstChar = true;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);

            // Handle word boundaries (spaces)
            if (MorseAlphabet.isWordBoundary(c)) {
                // Collapse consecutive spaces
                if (!events.isEmpty()) {
                    // Remove trailing char space if present (we'll replace with word space)
                    MorseEvent last = events.get(events.size() - 1);
                    if (last.getType() == EventType.CHAR_SPACE) {
                        events.remove(events.size() - 1);
                    }
                    events.add(new MorseEvent(EventType.WORD_SPACE, MorseTimingManager.wordSpaceMs(wpm)));
                }
                firstChar = true;
                continue;
            }

            // Look up the character's Morse code
            String morseCode = MorseAlphabet.encode(c);
            if (morseCode == null || MorseAlphabet.WORD_SPACE_SENTINEL.equals(morseCode)) {
                // Unsupported character — skip
                continue;
            }

            // Add character space before this character (except the first one)
            if (!firstChar) {
                // Check if last event is already a word space
                if (!events.isEmpty()) {
                    MorseEvent last = events.get(events.size() - 1);
                    if (last.getType() != EventType.WORD_SPACE) {
                        events.add(new MorseEvent(EventType.CHAR_SPACE, MorseTimingManager.charSpaceMs(wpm)));
                    }
                }
            }
            firstChar = false;

            // Encode each element (dit/dah) of the character
            for (int j = 0; j < morseCode.length(); j++) {
                // Add element space between dits/dahs within a character
                if (j > 0) {
                    events.add(new MorseEvent(EventType.ELEMENT_SPACE, MorseTimingManager.elementSpaceMs(wpm)));
                }

                char element = morseCode.charAt(j);
                if (element == MorseAlphabet.DIT_CHAR) {
                    events.add(new MorseEvent(EventType.DIT, MorseTimingManager.ditDurationMs(wpm)));
                } else if (element == MorseAlphabet.DAH_CHAR) {
                    events.add(new MorseEvent(EventType.DAH, MorseTimingManager.dahDurationMs(wpm)));
                }
            }
        }

        // Remove any trailing space events
        while (!events.isEmpty()) {
            MorseEvent last = events.get(events.size() - 1);
            if (!last.isSignalOn()) {
                events.remove(events.size() - 1);
            } else {
                break;
            }
        }

        return events;
    }

    /**
     * Calculate the total duration of a Morse event sequence in milliseconds.
     *
     * @param events the list of events
     * @return total duration in ms
     */
    public static int totalDurationMs(@NonNull List<MorseEvent> events) {
        int total = 0;
        for (MorseEvent event : events) {
            total += event.getDurationMs();
        }
        return total;
    }

    /**
     * Build a display string showing the Morse code pattern.
     * E.g., "SOS" → "... --- ..."
     *
     * @param events the list of events
     * @return human-readable Morse pattern
     */
    @NonNull
    public static String toDisplayString(@NonNull List<MorseEvent> events) {
        StringBuilder sb = new StringBuilder();
        for (MorseEvent event : events) {
            sb.append(event.displaySymbol());
        }
        return sb.toString();
    }
}
