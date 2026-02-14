package net.osmand.plus.guides;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for GuideEntry data model.
 */
public class GuideEntryTest {

	@Test
	public void testBuilderCreatesValidEntry() {
		GuideEntry entry = new GuideEntry.Builder("test-id", "Test Guide")
				.setCategory(GuideCategory.WATER)
				.setImportance(GuideEntry.Importance.HIGH)
				.setSummary("Test summary")
				.setBody("# Test Body")
				.setTags(new String[]{"water", "purification"})
				.setLastUpdated(1707868800000L)
				.setBundled(true)
				.build();

		assertEquals("test-id", entry.getId());
		assertEquals("Test Guide", entry.getTitle());
		assertEquals(GuideCategory.WATER, entry.getCategory());
		assertEquals(GuideEntry.Importance.HIGH, entry.getImportance());
		assertEquals("Test summary", entry.getSummary());
		assertEquals("# Test Body", entry.getBody());
		assertEquals(2, entry.getTags().size());
		assertEquals("water", entry.getTags().get(0));
		assertEquals("purification", entry.getTags().get(1));
		assertEquals(1707868800000L, entry.getLastUpdated());
		assertTrue(entry.isBundled());
		assertTrue(entry.hasBody());
	}

	@Test
	public void testBuilderDefaults() {
		GuideEntry entry = new GuideEntry.Builder("min-id", "Minimal Guide").build();

		assertEquals("min-id", entry.getId());
		assertEquals("Minimal Guide", entry.getTitle());
		assertEquals(GuideCategory.FIRST_AID, entry.getCategory());
		assertEquals(GuideEntry.Importance.MEDIUM, entry.getImportance());
		assertEquals("", entry.getSummary());
		assertNull(entry.getBody());
		assertTrue(entry.getTags().isEmpty());
		assertEquals(0L, entry.getLastUpdated());
		assertTrue(entry.isBundled());
		assertFalse(entry.hasBody());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testBuilderRejectsNullId() {
		new GuideEntry.Builder(null, "Title").build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testBuilderRejectsEmptyId() {
		new GuideEntry.Builder("", "Title").build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testBuilderRejectsNullTitle() {
		new GuideEntry.Builder("id", null).build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testBuilderRejectsEmptyTitle() {
		new GuideEntry.Builder("id", "").build();
	}

	@Test
	public void testTagsAreImmutable() {
		String[] tags = {"a", "b"};
		GuideEntry entry = new GuideEntry.Builder("id", "Title")
				.setTags(tags)
				.build();

		// Modifying original array shouldn't affect entry
		tags[0] = "modified";
		assertEquals("a", entry.getTags().get(0));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testTagsListIsUnmodifiable() {
		GuideEntry entry = new GuideEntry.Builder("id", "Title")
				.setTags(new String[]{"a", "b"})
				.build();

		entry.getTags().add("c");
	}

	@Test
	public void testNullTagsProducesEmptyList() {
		GuideEntry entry = new GuideEntry.Builder("id", "Title")
				.setTags(null)
				.build();

		assertNotNull(entry.getTags());
		assertTrue(entry.getTags().isEmpty());
	}

	@Test
	public void testHasBodyWithEmptyString() {
		GuideEntry entry = new GuideEntry.Builder("id", "Title")
				.setBody("")
				.build();
		assertFalse(entry.hasBody());
	}

	@Test
	public void testImportanceSortOrder() {
		assertEquals(0, GuideEntry.Importance.CRITICAL.getSortOrder());
		assertEquals(1, GuideEntry.Importance.HIGH.getSortOrder());
		assertEquals(2, GuideEntry.Importance.MEDIUM.getSortOrder());
	}

	@Test
	public void testImportanceFromString() {
		assertEquals(GuideEntry.Importance.CRITICAL, GuideEntry.Importance.fromString("CRITICAL"));
		assertEquals(GuideEntry.Importance.CRITICAL, GuideEntry.Importance.fromString("critical"));
		assertEquals(GuideEntry.Importance.HIGH, GuideEntry.Importance.fromString("High"));
		assertEquals(GuideEntry.Importance.MEDIUM, GuideEntry.Importance.fromString("MEDIUM"));
		assertEquals(GuideEntry.Importance.MEDIUM, GuideEntry.Importance.fromString(null));
		assertEquals(GuideEntry.Importance.MEDIUM, GuideEntry.Importance.fromString("invalid"));
	}

	@Test
	public void testNotBundled() {
		GuideEntry entry = new GuideEntry.Builder("id", "User Guide")
				.setBundled(false)
				.build();
		assertFalse(entry.isBundled());
	}
}
