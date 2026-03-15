/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * LAMPP Morse Code: Static ITU International Morse Code lookup table.
 *
 * Maps characters (A-Z, 0-9, common punctuation) to their dot/dash representations.
 * Pure static data — zero dependencies.
 */
public final class MorseAlphabet {

    public static final char DIT_CHAR = '.';
    public static final char DAH_CHAR = '-';
    public static final String WORD_SPACE_SENTINEL = " ";

    private static final Map<Character, String> CODE_MAP = new HashMap<>();
    private static final Map<String, Character> REVERSE_MAP = new HashMap<>();

    static {
        // Letters A-Z
        CODE_MAP.put('A', ".-");
        CODE_MAP.put('B', "-...");
        CODE_MAP.put('C', "-.-.");
        CODE_MAP.put('D', "-..");
        CODE_MAP.put('E', ".");
        CODE_MAP.put('F', "..-.");
        CODE_MAP.put('G', "--.");
        CODE_MAP.put('H', "....");
        CODE_MAP.put('I', "..");
        CODE_MAP.put('J', ".---");
        CODE_MAP.put('K', "-.-");
        CODE_MAP.put('L', ".-..");
        CODE_MAP.put('M', "--");
        CODE_MAP.put('N', "-.");
        CODE_MAP.put('O', "---");
        CODE_MAP.put('P', ".--.");
        CODE_MAP.put('Q', "--.-");
        CODE_MAP.put('R', ".-.");
        CODE_MAP.put('S', "...");
        CODE_MAP.put('T', "-");
        CODE_MAP.put('U', "..-");
        CODE_MAP.put('V', "...-");
        CODE_MAP.put('W', ".--");
        CODE_MAP.put('X', "-..-");
        CODE_MAP.put('Y', "-.--");
        CODE_MAP.put('Z', "--..");

        // Numbers 0-9
        CODE_MAP.put('0', "-----");
        CODE_MAP.put('1', ".----");
        CODE_MAP.put('2', "..---");
        CODE_MAP.put('3', "...--");
        CODE_MAP.put('4', "....-");
        CODE_MAP.put('5', ".....");
        CODE_MAP.put('6', "-....");
        CODE_MAP.put('7', "--...");
        CODE_MAP.put('8', "---..");
        CODE_MAP.put('9', "----.");

        // Common punctuation
        CODE_MAP.put('.', ".-.-.-");
        CODE_MAP.put(',', "--..--");
        CODE_MAP.put('?', "..--..");
        CODE_MAP.put('\'', ".----.");
        CODE_MAP.put('!', "-.-.--");
        CODE_MAP.put('/', "-..-.");
        CODE_MAP.put('(', "-.--.");
        CODE_MAP.put(')', "-.--.-");
        CODE_MAP.put('&', ".-...");
        CODE_MAP.put(':', "---...");
        CODE_MAP.put(';', "-.-.-.");
        CODE_MAP.put('=', "-...-");
        CODE_MAP.put('+', ".-.-.");
        CODE_MAP.put('-', "-....-");
        CODE_MAP.put('_', "..--.-");
        CODE_MAP.put('"', ".-..-.");
        CODE_MAP.put('$', "...-..-");
        CODE_MAP.put('@', ".--.-.");

        // Space is handled as a word boundary, not a code
        CODE_MAP.put(' ', WORD_SPACE_SENTINEL);

        // Build reverse map for decoding (morse string → character)
        for (Map.Entry<Character, String> entry : CODE_MAP.entrySet()) {
            if (!WORD_SPACE_SENTINEL.equals(entry.getValue())) {
                REVERSE_MAP.put(entry.getValue(), entry.getKey());
            }
        }
    }

    private MorseAlphabet() {
        // Static utility class
    }

    /**
     * Encode a single character to its Morse code representation.
     *
     * @param c the character to encode (case-insensitive)
     * @return dot/dash string, or null if unsupported
     */
    public static String encode(char c) {
        return CODE_MAP.get(Character.toUpperCase(c));
    }

    /**
     * Check if a character has a Morse code representation.
     *
     * @param c the character to check (case-insensitive)
     * @return true if the character can be encoded
     */
    public static boolean isSupported(char c) {
        return CODE_MAP.containsKey(Character.toUpperCase(c));
    }

    /**
     * Check if the character represents a word boundary (space).
     *
     * @param c the character to check
     * @return true if this is a space character
     */
    public static boolean isWordBoundary(char c) {
        return c == ' ';
    }

    /**
     * Decode a Morse code string (dots and dashes) back to a character.
     * This is the reverse of encode().
     *
     * @param morseCode the dot/dash string (e.g., ".-" for 'A')
     * @return the decoded character, or null if the morse code is unknown
     */
    public static Character decode(String morseCode) {
        if (morseCode == null || morseCode.isEmpty()) {
            return null;
        }
        return REVERSE_MAP.get(morseCode);
    }

    /**
     * Get an unmodifiable copy of the reverse lookup map (morse → character).
     * Useful for external validation or debugging.
     *
     * @return unmodifiable map of morse code strings to characters
     */
    public static Map<String, Character> getReverseMap() {
        return Collections.unmodifiableMap(REVERSE_MAP);
    }
}
