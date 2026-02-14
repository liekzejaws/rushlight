package net.osmand.plus.guides;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for GuideJsonParser.
 */
public class GuideJsonParserTest {

	private static final String VALID_GUIDE_JSON = "{\n" +
			"  \"id\": \"test-guide\",\n" +
			"  \"title\": \"Test Guide\",\n" +
			"  \"category\": \"WATER\",\n" +
			"  \"tags\": [\"water\", \"purification\", \"survival\"],\n" +
			"  \"importance\": \"CRITICAL\",\n" +
			"  \"summary\": \"How to purify water in the wild\",\n" +
			"  \"body\": \"# Water Purification\\n\\n## Boiling\\nBring water to a rolling boil.\",\n" +
			"  \"lastUpdated\": 1707868800000\n" +
			"}";

	private static final String MINIMAL_GUIDE_JSON = "{\n" +
			"  \"id\": \"minimal\",\n" +
			"  \"title\": \"Minimal Guide\"\n" +
			"}";

	@Test
	public void testParseValidGuide() {
		GuideEntry entry = GuideJsonParser.parseGuide(VALID_GUIDE_JSON, true);

		assertNotNull(entry);
		assertEquals("test-guide", entry.getId());
		assertEquals("Test Guide", entry.getTitle());
		assertEquals(GuideCategory.WATER, entry.getCategory());
		assertEquals(GuideEntry.Importance.CRITICAL, entry.getImportance());
		assertEquals("How to purify water in the wild", entry.getSummary());
		assertTrue(entry.getBody().contains("# Water Purification"));
		assertEquals(3, entry.getTags().size());
		assertEquals("water", entry.getTags().get(0));
		assertEquals(1707868800000L, entry.getLastUpdated());
		assertTrue(entry.isBundled());
	}

	@Test
	public void testParseMinimalGuide() {
		GuideEntry entry = GuideJsonParser.parseGuide(MINIMAL_GUIDE_JSON, false);

		assertNotNull(entry);
		assertEquals("minimal", entry.getId());
		assertEquals("Minimal Guide", entry.getTitle());
		assertEquals(GuideCategory.FIRST_AID, entry.getCategory()); // default
		assertEquals(GuideEntry.Importance.MEDIUM, entry.getImportance()); // default
		assertFalse(entry.isBundled());
	}

	@Test
	public void testParseInvalidJson() {
		GuideEntry entry = GuideJsonParser.parseGuide("not valid json {{{", true);
		assertNull(entry);
	}

	@Test
	public void testParseMissingRequiredId() {
		String json = "{\"title\": \"No ID\"}";
		GuideEntry entry = GuideJsonParser.parseGuide(json, true);
		assertNull(entry);
	}

	@Test
	public void testParseMissingRequiredTitle() {
		String json = "{\"id\": \"no-title\"}";
		GuideEntry entry = GuideJsonParser.parseGuide(json, true);
		assertNull(entry);
	}

	@Test
	public void testParseEmptyId() {
		String json = "{\"id\": \"\", \"title\": \"Empty ID\"}";
		GuideEntry entry = GuideJsonParser.parseGuide(json, true);
		assertNull(entry);
	}

	@Test
	public void testParseEmptyTitle() {
		String json = "{\"id\": \"empty-title\", \"title\": \"\"}";
		GuideEntry entry = GuideJsonParser.parseGuide(json, true);
		assertNull(entry);
	}

	@Test
	public void testParseGuideList() {
		String json = "[" + VALID_GUIDE_JSON + "," + MINIMAL_GUIDE_JSON + "]";
		List<GuideEntry> guides = GuideJsonParser.parseGuideList(json, true, true);

		assertEquals(2, guides.size());
		assertEquals("test-guide", guides.get(0).getId());
		assertEquals("minimal", guides.get(1).getId());
	}

	@Test
	public void testParseGuideListSkipsInvalid() {
		String json = "[" +
				VALID_GUIDE_JSON + "," +
				"{\"id\": \"\", \"title\": \"Bad\"}" + "," +
				MINIMAL_GUIDE_JSON +
				"]";
		List<GuideEntry> guides = GuideJsonParser.parseGuideList(json, true, true);

		assertEquals(2, guides.size());
	}

	@Test
	public void testParseGuideListInvalidJson() {
		List<GuideEntry> guides = GuideJsonParser.parseGuideList("not an array", true, true);
		assertTrue(guides.isEmpty());
	}

	@Test
	public void testParseGuideListEmptyArray() {
		List<GuideEntry> guides = GuideJsonParser.parseGuideList("[]", true, true);
		assertTrue(guides.isEmpty());
	}

	@Test
	public void testParseGuideListWithoutBody() {
		String json = "[" + VALID_GUIDE_JSON + "]";
		List<GuideEntry> guides = GuideJsonParser.parseGuideList(json, true, false);

		assertEquals(1, guides.size());
		assertNull(guides.get(0).getBody()); // body excluded
		assertEquals("How to purify water in the wild", guides.get(0).getSummary()); // summary included
	}

	@Test
	public void testParseUnknownImportance() {
		String json = "{\"id\": \"test\", \"title\": \"Test\", \"importance\": \"ULTRA\"}";
		GuideEntry entry = GuideJsonParser.parseGuide(json, true);

		assertNotNull(entry);
		assertEquals(GuideEntry.Importance.MEDIUM, entry.getImportance()); // default
	}

	@Test
	public void testParseUnknownCategory() {
		String json = "{\"id\": \"test\", \"title\": \"Test\", \"category\": \"MAGIC\"}";
		GuideEntry entry = GuideJsonParser.parseGuide(json, true);

		assertNotNull(entry);
		assertEquals(GuideCategory.FIRST_AID, entry.getCategory()); // default
	}

	@Test
	public void testParseBodyWithSpecialCharacters() {
		String json = "{\"id\": \"special\", \"title\": \"Special Chars\"," +
				"\"body\": \"Line 1\\nLine 2\\n## Header\\n- Bullet with \\\"quotes\\\"\"}";
		GuideEntry entry = GuideJsonParser.parseGuide(json, true);

		assertNotNull(entry);
		assertTrue(entry.getBody().contains("Line 1"));
		assertTrue(entry.getBody().contains("## Header"));
	}
}
