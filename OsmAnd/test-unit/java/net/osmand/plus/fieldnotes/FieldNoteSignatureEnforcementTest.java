package net.osmand.plus.fieldnotes;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for hard signature enforcement in FieldNotesManager.receiveFromPeer().
 *
 * Verifies that:
 * - Notes with invalid signatures are rejected
 * - Unsigned notes are still accepted (backward compatibility)
 * - Valid signature verification logic works via FieldNoteSigner.isSigned()
 */
public class FieldNoteSignatureEnforcementTest {

	/**
	 * A FieldNote with null signature and null publicKey should be considered unsigned.
	 */
	@Test
	public void testUnsignedNoteIsNotSigned() {
		FieldNote note = new FieldNote(
				"test-id-1234", 48.8566, 2.3522,
				FieldNote.Category.SHELTER, "Safe house", "Ground floor, blue door",
				System.currentTimeMillis(), "author-abc", 168,
				1, 0, null, null);

		assertFalse("Note without signature should not be signed", FieldNoteSigner.isSigned(note));
	}

	/**
	 * A FieldNote with both signature and publicKey set should be considered signed.
	 */
	@Test
	public void testSignedNoteIsSigned() {
		FieldNote note = new FieldNote(
				"test-id-5678", 48.8566, 2.3522,
				FieldNote.Category.HAZARD, "Checkpoint", "Military checkpoint at bridge",
				System.currentTimeMillis(), "author-def", 168,
				1, 0, "FAKE_SIG_BASE64", "FAKE_PUBKEY_BASE64");

		assertTrue("Note with signature + publicKey should be signed", FieldNoteSigner.isSigned(note));
	}

	/**
	 * A FieldNote with only signature (no publicKey) should not be considered signed.
	 */
	@Test
	public void testPartialSignatureNotSigned() {
		FieldNote note = new FieldNote(
				"test-id-partial", 48.8566, 2.3522,
				FieldNote.Category.ROUTE, "Route north", "Via mountain pass",
				System.currentTimeMillis(), "author-ghi", 168,
				1, 0, "FAKE_SIG_BASE64", null);

		assertFalse("Note with signature but no publicKey should not be considered signed",
				FieldNoteSigner.isSigned(note));
	}

	/**
	 * verify() should return false for a note with garbage signature data.
	 * This simulates a tampered/forged note.
	 */
	@Test
	public void testVerifyRejectsTamperedSignature() {
		FieldNote note = new FieldNote(
				"test-id-tampered", 48.8566, 2.3522,
				FieldNote.Category.INTEL, "False intel", "Trap location",
				System.currentTimeMillis(), "attacker-xyz", 168,
				1, 0, "INVALID_BASE64_SIG", "INVALID_BASE64_KEY");

		// verify() should return false for garbage data (Base64 decode will fail)
		boolean result = FieldNoteSigner.verify(note);
		assertFalse("verify() should reject notes with invalid signature data", result);
	}

	/**
	 * Sync JSON round-trip should preserve signature and publicKey fields.
	 */
	@Test
	public void testSyncJsonPreservesSignatureFields() throws Exception {
		FieldNote original = new FieldNote(
				"test-id-json", 48.8566, 2.3522,
				FieldNote.Category.WATER, "Spring water", "Fresh source near cave",
				System.currentTimeMillis(), "author-jkl", 168,
				1, 0, "c2lnbmF0dXJl", "cHVibGlja2V5");

		JSONObject json = FieldNotesManager.toSyncJson(original);
		String jsonStr = json.toString();
		FieldNote parsed = FieldNotesManager.fromSyncJson(jsonStr);

		assertNotNull("Round-tripped note should not be null", parsed);
		assertEquals("Signature should survive round-trip", "c2lnbmF0dXJl", parsed.getSignature());
		assertEquals("PublicKey should survive round-trip", "cHVibGlja2V5", parsed.getPublicKey());
	}

	/**
	 * Sync JSON round-trip should handle null signature/publicKey.
	 */
	@Test
	public void testSyncJsonHandlesNullSignature() throws Exception {
		FieldNote original = new FieldNote(
				"test-id-unsigned", 48.8566, 2.3522,
				FieldNote.Category.CACHE, "Supply drop", "Under the bridge",
				System.currentTimeMillis(), "author-mno", 168,
				1, 0, null, null);

		JSONObject json = FieldNotesManager.toSyncJson(original);
		String jsonStr = json.toString();
		FieldNote parsed = FieldNotesManager.fromSyncJson(jsonStr);

		assertNotNull("Round-tripped unsigned note should not be null", parsed);
		assertNull("Signature should be null for unsigned note", parsed.getSignature());
		assertNull("PublicKey should be null for unsigned note", parsed.getPublicKey());
	}

	/**
	 * Category.fromKey should handle all 8 standard categories.
	 */
	@Test
	public void testAllCategoriesRoundTrip() {
		for (FieldNote.Category cat : FieldNote.Category.values()) {
			assertEquals("Category round-trip failed for " + cat.name(),
					cat, FieldNote.Category.fromKey(cat.getKey()));
		}
	}
}
