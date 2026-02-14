package net.osmand.plus.morse;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for MorseAlphabet — ITU Morse code lookup table.
 *
 * Life-safety critical: incorrect encoding could garble SOS or emergency coordinates.
 */
public class MorseAlphabetTest {

	// ---- Encode: Letters ----

	@Test
	public void testEncodeA() {
		assertEquals(".-", MorseAlphabet.encode('A'));
	}

	@Test
	public void testEncodeS() {
		assertEquals("...", MorseAlphabet.encode('S'));
	}

	@Test
	public void testEncodeO() {
		assertEquals("---", MorseAlphabet.encode('O'));
	}

	@Test
	public void testEncodeE() {
		assertEquals(".", MorseAlphabet.encode('E'));
	}

	@Test
	public void testEncodeT() {
		assertEquals("-", MorseAlphabet.encode('T'));
	}

	// ---- Encode: Digits ----

	@Test
	public void testEncodeDigit0() {
		assertEquals("-----", MorseAlphabet.encode('0'));
	}

	@Test
	public void testEncodeDigit1() {
		assertEquals(".----", MorseAlphabet.encode('1'));
	}

	@Test
	public void testEncodeDigit5() {
		assertEquals(".....", MorseAlphabet.encode('5'));
	}

	@Test
	public void testEncodeDigit9() {
		assertEquals("----.", MorseAlphabet.encode('9'));
	}

	// ---- Encode: Case insensitivity ----

	@Test
	public void testEncodeLowercaseA() {
		assertEquals(".-", MorseAlphabet.encode('a'));
	}

	@Test
	public void testEncodeLowercaseMatchesUppercase() {
		assertEquals(MorseAlphabet.encode('S'), MorseAlphabet.encode('s'));
		assertEquals(MorseAlphabet.encode('O'), MorseAlphabet.encode('o'));
		assertEquals(MorseAlphabet.encode('Z'), MorseAlphabet.encode('z'));
	}

	// ---- Encode: Unsupported chars ----

	@Test
	public void testEncodeUnsupportedReturnsNull() {
		assertNull("Emoji should not be encodeable", MorseAlphabet.encode('\uD83D'));
		assertNull("Null char should return null", MorseAlphabet.encode('\0'));
	}

	// ---- SOS encoding (life-safety critical) ----

	@Test
	public void testSosEncoding() {
		assertEquals("S = ...", "...", MorseAlphabet.encode('S'));
		assertEquals("O = ---", "---", MorseAlphabet.encode('O'));
		// Full SOS: ... --- ...
		String sos = MorseAlphabet.encode('S') + " " +
				MorseAlphabet.encode('O') + " " +
				MorseAlphabet.encode('S');
		assertEquals("... --- ...", sos);
	}

	// ---- Decode ----

	@Test
	public void testDecodeValidMorse() {
		assertEquals(Character.valueOf('A'), MorseAlphabet.decode(".-"));
		assertEquals(Character.valueOf('S'), MorseAlphabet.decode("..."));
		assertEquals(Character.valueOf('O'), MorseAlphabet.decode("---"));
	}

	@Test
	public void testDecodeInvalidMorseReturnsNull() {
		assertNull("Unknown pattern should return null", MorseAlphabet.decode("........"));
		assertNull("Empty string should return null", MorseAlphabet.decode(""));
		assertNull("Null should return null", MorseAlphabet.decode(null));
	}

	// ---- Round-trip ----

	@Test
	public void testRoundTripAllLetters() {
		for (char c = 'A'; c <= 'Z'; c++) {
			String morse = MorseAlphabet.encode(c);
			assertNotNull("Letter " + c + " should encode", morse);
			Character decoded = MorseAlphabet.decode(morse);
			assertNotNull("Morse '" + morse + "' should decode", decoded);
			assertEquals("Round-trip failed for " + c, c, decoded.charValue());
		}
	}

	@Test
	public void testRoundTripAllDigits() {
		for (char c = '0'; c <= '9'; c++) {
			String morse = MorseAlphabet.encode(c);
			assertNotNull("Digit " + c + " should encode", morse);
			Character decoded = MorseAlphabet.decode(morse);
			assertNotNull("Morse '" + morse + "' should decode", decoded);
			assertEquals("Round-trip failed for " + c, c, decoded.charValue());
		}
	}

	// ---- isSupported ----

	@Test
	public void testIsSupportedLetters() {
		for (char c = 'A'; c <= 'Z'; c++) {
			assertTrue("Letter " + c + " should be supported", MorseAlphabet.isSupported(c));
		}
	}

	@Test
	public void testIsSupportedDigits() {
		for (char c = '0'; c <= '9'; c++) {
			assertTrue("Digit " + c + " should be supported", MorseAlphabet.isSupported(c));
		}
	}

	@Test
	public void testIsSupportedSpace() {
		assertTrue("Space should be supported", MorseAlphabet.isSupported(' '));
	}

	@Test
	public void testIsNotSupportedEmoji() {
		assertFalse(MorseAlphabet.isSupported('\uD83D'));
	}

	// ---- isWordBoundary ----

	@Test
	public void testIsWordBoundarySpace() {
		assertTrue(MorseAlphabet.isWordBoundary(' '));
	}

	@Test
	public void testIsNotWordBoundaryLetter() {
		assertFalse(MorseAlphabet.isWordBoundary('A'));
		assertFalse(MorseAlphabet.isWordBoundary('.'));
	}

	// ---- getReverseMap ----

	@Test
	public void testReverseMapIsUnmodifiable() {
		Map<String, Character> reverseMap = MorseAlphabet.getReverseMap();
		try {
			reverseMap.put("test", 'X');
			fail("Reverse map should be unmodifiable");
		} catch (UnsupportedOperationException e) {
			// Expected
		}
	}

	@Test
	public void testReverseMapContainsAllLetters() {
		Map<String, Character> reverseMap = MorseAlphabet.getReverseMap();
		assertTrue(reverseMap.size() >= 36); // 26 letters + 10 digits
	}

	// ---- Punctuation ----

	@Test
	public void testEncodePunctuation() {
		assertEquals(".-.-.-", MorseAlphabet.encode('.'));
		assertEquals("--..--", MorseAlphabet.encode(','));
		assertEquals("..--..", MorseAlphabet.encode('?'));
	}
}
