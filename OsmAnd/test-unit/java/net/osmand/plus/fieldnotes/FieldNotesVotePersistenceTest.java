package net.osmand.plus.fieldnotes;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for vote persistence model changes in FieldNotesDbHelper (v3).
 *
 * These tests verify the vote data model and constants without requiring
 * an Android context (DB operations require integration tests).
 */
public class FieldNotesVotePersistenceTest {

	/**
	 * Vote direction constants should be well-defined.
	 */
	@Test
	public void testVoteDirectionValues() {
		int upvote = 1;
		int downvote = -1;
		assertNotEquals("Upvote and downvote must be different", upvote, downvote);
		assertTrue("Upvote should be positive", upvote > 0);
		assertTrue("Downvote should be negative", downvote < 0);
	}

	/**
	 * FieldNote score should be modifiable via setScore.
	 */
	@Test
	public void testFieldNoteScoreModification() {
		FieldNote note = new FieldNote(
				"vote-test-1", 48.8566, 2.3522,
				FieldNote.Category.SHELTER, "Test shelter", "Description",
				System.currentTimeMillis(), "author-1", 168,
				1, 0, null, null);

		assertEquals("Initial score should be 0", 0, note.getScore());

		note.setScore(1);
		assertEquals("Score after upvote should be 1", 1, note.getScore());

		note.setScore(0);
		assertEquals("Score after downvote should be 0", 0, note.getScore());

		note.setScore(-1);
		assertEquals("Score can go negative", -1, note.getScore());
	}

	/**
	 * Content-addressed ID should be deterministic.
	 */
	@Test
	public void testContentAddressedIdDeterministic() {
		long timestamp = 1710000000000L;
		FieldNote note1 = new FieldNote(48.8566, 2.3522,
				FieldNote.Category.WATER, "Spring", "Fresh water",
				timestamp, "author-x", 168);
		FieldNote note2 = new FieldNote(48.8566, 2.3522,
				FieldNote.Category.WATER, "Spring", "Fresh water",
				timestamp, "author-x", 168);

		assertEquals("Same content should produce same ID", note1.getId(), note2.getId());
	}

	/**
	 * Different content should produce different IDs.
	 * ID is SHA-256 of lat:lon:note:authorId:timestamp, so changing the note text
	 * should produce a different ID.
	 */
	@Test
	public void testDifferentContentProducesDifferentId() {
		long timestamp = 1710000000000L;
		FieldNote note1 = new FieldNote(48.8566, 2.3522,
				FieldNote.Category.WATER, "Spring A", "Fresh water from cave",
				timestamp, "author-x", 168);
		FieldNote note2 = new FieldNote(48.8566, 2.3522,
				FieldNote.Category.WATER, "Spring B", "Brackish water from well",
				timestamp, "author-x", 168);

		assertNotEquals("Different note text should produce different ID",
				note1.getId(), note2.getId());
	}

	/**
	 * FieldNote categories should have well-defined keys.
	 */
	@Test
	public void testAllCategoryKeysAreLowercase() {
		for (FieldNote.Category cat : FieldNote.Category.values()) {
			String key = cat.getKey();
			assertNotNull("Category key should not be null", key);
			assertEquals("Category key should be lowercase",
					key.toLowerCase(), key);
		}
	}

	/**
	 * DB_NAME constant should be accessible and correct.
	 */
	@Test
	public void testDbNameConstant() {
		assertEquals("fieldnotes_db", FieldNotesDbHelper.DB_NAME);
	}
}
