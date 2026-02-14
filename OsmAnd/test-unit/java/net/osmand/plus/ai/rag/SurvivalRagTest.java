package net.osmand.plus.ai.rag;

import net.osmand.plus.ai.rag.QueryClassifier.QueryType;
import net.osmand.plus.guides.GuideCategory;
import net.osmand.plus.guides.GuideEntry;
import net.osmand.plus.guides.GuideSearchIndex;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Phase 14: Tests for survival-aware RAG integration.
 *
 * Tests the full pipeline from query classification → guide search → prompt building.
 * GuideSearchAdapter itself requires OsmandApplication, so we test the components
 * it relies on (GuideSearchIndex, PromptBuilder, QueryClassifier) in isolation.
 */
public class SurvivalRagTest {

	private QueryClassifier classifier;
	private PromptBuilder promptBuilder;
	private GuideSearchIndex searchIndex;

	@Before
	public void setUp() {
		classifier = new QueryClassifier();
		promptBuilder = new PromptBuilder();

		// Build a test search index with sample guides
		searchIndex = new GuideSearchIndex();
		List<GuideEntry> guides = Arrays.asList(
				new GuideEntry.Builder("water-purification", "Water Purification Methods")
						.setCategory(GuideCategory.WATER)
						.setImportance(GuideEntry.Importance.CRITICAL)
						.setTags(new String[]{"water", "purification", "boiling", "filter"})
						.setSummary("Making water safe to drink using available methods")
						.setBody("# Water Purification\n\nBoil water for at least 1 minute at sea level.")
						.build(),
				new GuideEntry.Builder("first-aid-basics", "First Aid Basics")
						.setCategory(GuideCategory.FIRST_AID)
						.setImportance(GuideEntry.Importance.CRITICAL)
						.setTags(new String[]{"first aid", "emergency", "wounds", "CPR"})
						.setSummary("Essential first aid techniques for emergency situations")
						.setBody("# First Aid\n\nCheck airway, breathing, circulation (ABC).")
						.build(),
				new GuideEntry.Builder("fire-starting", "Fire Starting Methods")
						.setCategory(GuideCategory.FIRE)
						.setImportance(GuideEntry.Importance.CRITICAL)
						.setTags(new String[]{"fire", "ignition", "tinder", "ferro rod"})
						.setSummary("Multiple methods for starting fire in any conditions")
						.setBody("# Fire Starting\n\n## Ferro Rod\nStrike at 45 degrees with firm pressure.")
						.build(),
				new GuideEntry.Builder("shelter-basics", "Emergency Shelter Construction")
						.setCategory(GuideCategory.SHELTER)
						.setImportance(GuideEntry.Importance.CRITICAL)
						.setTags(new String[]{"shelter", "lean-to", "debris hut"})
						.setSummary("Building emergency shelters from natural materials")
						.setBody("# Shelter\n\n## Lean-To\nFind a sturdy ridge pole 8-12 feet long.")
						.build()
		);
		searchIndex.buildIndex(guides);
	}

	// ---- Classification → Guide Search pipeline ----

	@Test
	public void testSurvivalQueryFindsGuides() {
		// Classify a survival query
		QueryType type = classifier.classify("How do I purify water?");
		assertEquals(QueryType.SURVIVAL_QUERY, type);
		assertTrue(type.needsGuideSearch());

		// Search guides (as GuideSearchAdapter would)
		List<GuideEntry> results = searchIndex.search("purify water");
		assertFalse("Should find water purification guide", results.isEmpty());
		assertEquals("water-purification", results.get(0).getId());
	}

	@Test
	public void testSurvivalQueryFirstAidFindsGuide() {
		QueryType type = classifier.classify("I have a wound that won't stop bleeding");
		assertEquals(QueryType.SURVIVAL_QUERY, type);

		List<GuideEntry> results = searchIndex.search("wound bleeding");
		assertFalse("Should find first aid guide", results.isEmpty());
		boolean foundFirstAid = false;
		for (GuideEntry e : results) {
			if (e.getId().equals("first-aid-basics")) {
				foundFirstAid = true;
				break;
			}
		}
		assertTrue("First aid guide should be in results", foundFirstAid);
	}

	@Test
	public void testSurvivalQueryFireStartingFindsGuide() {
		QueryType type = classifier.classify("How to start a fire with a ferro rod");
		assertEquals(QueryType.SURVIVAL_QUERY, type);

		List<GuideEntry> results = searchIndex.search("fire ferro rod");
		assertFalse("Should find fire starting guide", results.isEmpty());
		assertEquals("fire-starting", results.get(0).getId());
	}

	@Test
	public void testNonSurvivalQueryDoesNotTriggerGuideSearch() {
		QueryType type = classifier.classify("What is the capital of France?");
		assertNotEquals(QueryType.SURVIVAL_QUERY, type);
		assertFalse(type.needsGuideSearch());
	}

	// ---- Prompt building with survival context ----

	@Test
	public void testBuildSurvivalPromptContainsGuideContext() {
		String guideContext = "--- Guide: Water Purification Methods ---\n" +
				"Category: Water & Hydration | Importance: CRITICAL\n" +
				"Boil water for at least 1 minute.\n\n";
		String wikiContext = "";

		String prompt = promptBuilder.buildSurvivalPrompt(
				"How do I purify water?", guideContext, wikiContext, 1500);

		assertTrue("Prompt should contain guide context",
				prompt.contains("Water Purification Methods"));
		assertTrue("Prompt should contain survival template markers",
				prompt.contains("=== Survival Guides ==="));
		assertTrue("Prompt should contain the question",
				prompt.contains("How do I purify water?"));
		assertTrue("Prompt should contain citation instruction",
				prompt.contains("[Guide: Title]"));
	}

	@Test
	public void testBuildSurvivalPromptWithWikiContext() {
		String guideContext = "--- Guide: First Aid Basics ---\n" +
				"Category: First Aid | Importance: CRITICAL\n" +
				"Check ABC: Airway, Breathing, Circulation.\n\n";
		String wikiContext = "[First aid]\nFirst aid is the first and immediate assistance.\n\n";

		String prompt = promptBuilder.buildSurvivalPrompt(
				"How to treat a wound?", guideContext, wikiContext, 1500);

		assertTrue("Prompt should contain guide context",
				prompt.contains("First Aid Basics"));
		assertTrue("Prompt should contain wiki context",
				prompt.contains("=== Wikipedia Context ==="));
		assertTrue("Prompt should contain wiki article",
				prompt.contains("First aid"));
	}

	@Test
	public void testBuildSurvivalPromptWithoutWikiContext() {
		String guideContext = "--- Guide: Fire Starting Methods ---\n" +
				"Ferro rod technique.\n\n";
		String wikiContext = "";

		String prompt = promptBuilder.buildSurvivalPrompt(
				"How to start a fire?", guideContext, wikiContext, 1500);

		assertTrue("Prompt should contain guide context",
				prompt.contains("Fire Starting Methods"));
		assertFalse("Prompt should NOT contain wiki section when empty",
				prompt.contains("=== Wikipedia Context ==="));
	}

	@Test
	public void testSurvivalPromptContainsSafetyInstruction() {
		String guideContext = "Some guide context";
		String prompt = promptBuilder.buildSurvivalPrompt(
				"test query", guideContext, "", 1500);

		assertTrue("Prompt should mention safety priority",
				prompt.contains("Prioritize safety"));
		assertTrue("Prompt should mention step-by-step",
				prompt.contains("step-by-step"));
	}

	// ---- Format wiki sources ----

	@Test
	public void testFormatWikiSourcesEmpty() {
		String formatted = promptBuilder.formatWikiSources(new ArrayList<>());
		assertEquals("", formatted);
	}

	@Test
	public void testFormatWikiSourcesContainsArticle() {
		List<ArticleSource> sources = new ArrayList<>();
		sources.add(new ArticleSource("Hypothermia", "Hypothermia occurs when body temperature drops.", 100));

		String formatted = promptBuilder.formatWikiSources(sources);
		assertTrue(formatted.contains("Hypothermia"));
		assertTrue(formatted.contains("body temperature drops"));
	}

	// ---- Guide context formatting (simulating GuideSearchAdapter.formatGuide) ----

	@Test
	public void testGuideContextFormattingWithBody() {
		GuideEntry guide = searchIndex.getGuide("water-purification");
		assertNotNull(guide);

		// Simulate the formatting that GuideSearchAdapter does
		String formatted = formatGuide(guide);
		assertTrue(formatted.contains("--- Guide: Water Purification Methods ---"));
		assertTrue(formatted.contains("Category: Water"));
		assertTrue(formatted.contains("Importance: CRITICAL"));
		assertTrue(formatted.contains("Boil water for at least 1 minute"));
	}

	@Test
	public void testGuideContextFormattingSummaryOnly() {
		// Build a guide without body
		GuideEntry noBody = new GuideEntry.Builder("test", "Test Guide")
				.setCategory(GuideCategory.WATER)
				.setImportance(GuideEntry.Importance.HIGH)
				.setSummary("This is just a summary")
				.build();

		String formatted = formatGuide(noBody);
		assertTrue(formatted.contains("--- Guide: Test Guide ---"));
		assertTrue(formatted.contains("This is just a summary"));
	}

	@Test
	public void testTokenBudgetRespected() {
		// Simulate the token budget logic from GuideSearchAdapter
		int tokenBudget = 50; // Very small budget = 200 chars
		int charBudget = tokenBudget * 4;

		StringBuilder context = new StringBuilder();
		List<GuideEntry> results = searchIndex.search("water");

		for (GuideEntry guide : results) {
			String formatted = formatGuide(guide);
			if (context.length() + formatted.length() > charBudget) {
				// Would exceed budget - try summary only
				String summaryOnly = formatGuideSummaryOnly(guide);
				if (context.length() + summaryOnly.length() <= charBudget) {
					context.append(summaryOnly);
				}
				break;
			}
			context.append(formatted);
		}

		assertTrue("Context should respect char budget",
				context.length() <= charBudget);
	}

	// ---- Helper methods (mirror GuideSearchAdapter's private methods) ----

	private String formatGuide(GuideEntry guide) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- Guide: ").append(guide.getTitle()).append(" ---\n");
		sb.append("Category: ").append(guide.getCategory().getDisplayName());
		sb.append(" | Importance: ").append(guide.getImportance().name()).append("\n");

		if (guide.hasBody()) {
			sb.append(guide.getBody()).append("\n");
		} else {
			sb.append(guide.getSummary()).append("\n");
		}
		sb.append("\n");
		return sb.toString();
	}

	private String formatGuideSummaryOnly(GuideEntry guide) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- Guide: ").append(guide.getTitle()).append(" (summary) ---\n");
		sb.append("Category: ").append(guide.getCategory().getDisplayName());
		sb.append(" | Importance: ").append(guide.getImportance().name()).append("\n");
		sb.append(guide.getSummary()).append("\n\n");
		return sb.toString();
	}
}
