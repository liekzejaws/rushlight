package net.osmand.plus.guides;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for GuideSearchIndex.
 */
public class GuideSearchIndexTest {

	private GuideSearchIndex index;

	@Before
	public void setUp() {
		index = new GuideSearchIndex();

		List<GuideEntry> guides = Arrays.asList(
				new GuideEntry.Builder("water-purification", "Water Purification Methods")
						.setCategory(GuideCategory.WATER)
						.setImportance(GuideEntry.Importance.CRITICAL)
						.setTags(new String[]{"water", "purification", "boiling", "filter"})
						.setSummary("Making water safe to drink using available methods")
						.build(),
				new GuideEntry.Builder("first-aid-basics", "First Aid Basics")
						.setCategory(GuideCategory.FIRST_AID)
						.setImportance(GuideEntry.Importance.CRITICAL)
						.setTags(new String[]{"first aid", "emergency", "wounds", "CPR"})
						.setSummary("Essential first aid techniques for emergency situations")
						.build(),
				new GuideEntry.Builder("fire-starting", "Fire Starting Methods")
						.setCategory(GuideCategory.FIRE)
						.setImportance(GuideEntry.Importance.CRITICAL)
						.setTags(new String[]{"fire", "ignition", "tinder", "ferro rod"})
						.setSummary("Multiple methods for starting fire in any conditions")
						.build(),
				new GuideEntry.Builder("water-finding", "Finding Water Sources")
						.setCategory(GuideCategory.WATER)
						.setImportance(GuideEntry.Importance.HIGH)
						.setTags(new String[]{"water", "sources", "collection", "dew"})
						.setSummary("Locating and collecting water in wilderness environments")
						.build(),
				new GuideEntry.Builder("shelter-basics", "Emergency Shelter Construction")
						.setCategory(GuideCategory.SHELTER)
						.setImportance(GuideEntry.Importance.CRITICAL)
						.setTags(new String[]{"shelter", "lean-to", "debris hut"})
						.setSummary("Building emergency shelters from natural materials")
						.build()
		);

		index.buildIndex(guides);
	}

	@Test
	public void testGuideCountAfterBuild() {
		assertEquals(5, index.getGuideCount());
	}

	@Test
	public void testKeywordCountPositive() {
		assertTrue(index.getKeywordCount() > 0);
	}

	@Test
	public void testSearchByExactTag() {
		List<GuideEntry> results = index.search("purification");
		assertFalse(results.isEmpty());
		assertEquals("water-purification", results.get(0).getId());
	}

	@Test
	public void testSearchByTitle() {
		List<GuideEntry> results = index.search("fire starting");
		assertFalse(results.isEmpty());
		boolean foundFire = false;
		for (GuideEntry e : results) {
			if (e.getId().equals("fire-starting")) {
				foundFire = true;
				break;
			}
		}
		assertTrue("Fire starting guide should be in results", foundFire);
	}

	@Test
	public void testSearchByPartialWord() {
		// "purif" should match "purification" via prefix matching
		List<GuideEntry> results = index.search("purif");
		assertFalse(results.isEmpty());
	}

	@Test
	public void testSearchReturnsMultipleMatches() {
		// "water" appears in two guides
		List<GuideEntry> results = index.search("water");
		assertTrue("Should find at least 2 water-related guides", results.size() >= 2);
	}

	@Test
	public void testSearchRanksByCriticalFirst() {
		// Both water guides match, but water-purification is CRITICAL, water-finding is HIGH
		List<GuideEntry> results = index.search("water");
		if (results.size() >= 2) {
			// With equal keyword score, CRITICAL should come first
			GuideEntry first = results.get(0);
			GuideEntry second = results.get(1);
			assertTrue("CRITICAL guide should rank before HIGH",
					first.getImportance().getSortOrder() <= second.getImportance().getSortOrder());
		}
	}

	@Test
	public void testSearchEmptyQuery() {
		List<GuideEntry> results = index.search("");
		assertTrue(results.isEmpty());
	}

	@Test
	public void testSearchWhitespaceOnly() {
		List<GuideEntry> results = index.search("   ");
		assertTrue(results.isEmpty());
	}

	@Test
	public void testSearchNoResults() {
		List<GuideEntry> results = index.search("xyznonexistent");
		assertTrue(results.isEmpty());
	}

	@Test
	public void testSearchBySummaryKeyword() {
		// "techniques" appears only in first-aid-basics summary
		List<GuideEntry> results = index.search("techniques");
		assertFalse(results.isEmpty());
		assertEquals("first-aid-basics", results.get(0).getId());
	}

	@Test
	public void testSearchByCategoryName() {
		// "shelter" is both a category name and a tag
		List<GuideEntry> results = index.search("shelter");
		assertFalse(results.isEmpty());
		boolean foundShelter = false;
		for (GuideEntry e : results) {
			if (e.getId().equals("shelter-basics")) {
				foundShelter = true;
				break;
			}
		}
		assertTrue("Shelter guide should be in results", foundShelter);
	}

	@Test
	public void testSearchByCategory() {
		List<GuideEntry> waterGuides = index.searchByCategory(GuideCategory.WATER);
		assertEquals(2, waterGuides.size());
		// CRITICAL before HIGH
		assertEquals(GuideEntry.Importance.CRITICAL, waterGuides.get(0).getImportance());
		assertEquals(GuideEntry.Importance.HIGH, waterGuides.get(1).getImportance());
	}

	@Test
	public void testSearchByCategoryEmpty() {
		List<GuideEntry> results = index.searchByCategory(GuideCategory.FOOD);
		assertTrue(results.isEmpty());
	}

	@Test
	public void testGetAllGuides() {
		List<GuideEntry> all = index.getAllGuides();
		assertEquals(5, all.size());
		// Should be sorted by category sort order
		assertTrue(all.get(0).getCategory().getSortOrder() <=
				all.get(all.size() - 1).getCategory().getSortOrder());
	}

	@Test
	public void testGetGuideById() {
		GuideEntry guide = index.getGuide("first-aid-basics");
		assertNotNull(guide);
		assertEquals("First Aid Basics", guide.getTitle());
	}

	@Test
	public void testGetGuideByIdNotFound() {
		GuideEntry guide = index.getGuide("nonexistent");
		assertNull(guide);
	}

	@Test
	public void testAddGuideIncrementally() {
		int countBefore = index.getGuideCount();
		GuideEntry newGuide = new GuideEntry.Builder("nav-basics", "Navigation Basics")
				.setCategory(GuideCategory.NAVIGATION)
				.setTags(new String[]{"compass", "navigation"})
				.setSummary("Basic navigation skills")
				.build();
		index.addGuide(newGuide);

		assertEquals(countBefore + 1, index.getGuideCount());
		List<GuideEntry> results = index.search("compass");
		assertFalse(results.isEmpty());
	}

	@Test
	public void testRebuildClearsOldData() {
		assertEquals(5, index.getGuideCount());

		// Rebuild with only 1 guide
		List<GuideEntry> newGuides = Arrays.asList(
				new GuideEntry.Builder("only-one", "Only One Guide")
						.setSummary("The only guide")
						.build()
		);
		index.buildIndex(newGuides);

		assertEquals(1, index.getGuideCount());
		assertNull(index.getGuide("water-purification")); // old guide gone
		assertNotNull(index.getGuide("only-one")); // new guide present
	}

	@Test
	public void testMultiWordSearchScoresHigher() {
		// "water purification" should rank water-purification higher than water-finding
		List<GuideEntry> results = index.search("water purification");
		assertFalse(results.isEmpty());
		assertEquals("water-purification", results.get(0).getId());
	}

	@Test
	public void testSingleCharWordsIgnored() {
		// Single-char words (< 2 chars) should be ignored in indexing
		List<GuideEntry> results = index.search("a");
		assertTrue(results.isEmpty());
	}
}
