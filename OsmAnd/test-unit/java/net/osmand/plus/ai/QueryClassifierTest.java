package net.osmand.plus.ai;

import net.osmand.plus.ai.rag.QueryClassifier;
import net.osmand.plus.ai.rag.QueryClassifier.QueryType;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for QueryClassifier — RAG query routing.
 *
 * Ensures queries are correctly classified for Wikipedia lookup,
 * POI search, navigation, or conversational responses.
 */
public class QueryClassifierTest {

	private QueryClassifier classifier;

	@Before
	public void setUp() {
		classifier = new QueryClassifier();
	}

	// ---- Factual queries → FACTUAL_LOOKUP ----

	@Test
	public void testFactualWhatIs() {
		assertEquals(QueryType.FACTUAL_LOOKUP, classifier.classify("What is photosynthesis?"));
	}

	@Test
	public void testFactualWhoIs() {
		assertEquals(QueryType.FACTUAL_LOOKUP, classifier.classify("Who is Albert Einstein?"));
	}

	@Test
	public void testFactualWhatAre() {
		assertEquals(QueryType.FACTUAL_LOOKUP, classifier.classify("What are mitochondria?"));
	}

	// ---- Definition queries → DEFINITION ----

	@Test
	public void testDefinition() {
		assertEquals(QueryType.DEFINITION, classifier.classify("Define entropy"));
	}

	@Test
	public void testMeaningOf() {
		assertEquals(QueryType.DEFINITION, classifier.classify("What does osmosis mean?"));
	}

	// ---- Explanation queries → EXPLANATION ----

	@Test
	public void testExplain() {
		assertEquals(QueryType.EXPLANATION, classifier.classify("Explain how a combustion engine works"));
	}

	@Test
	public void testHowDoes() {
		assertEquals(QueryType.EXPLANATION, classifier.classify("How does GPS navigation work?"));
	}

	// ---- Information requests → INFORMATION_REQUEST ----

	@Test
	public void testTellMeAbout() {
		assertEquals(QueryType.INFORMATION_REQUEST,
				classifier.classify("Tell me about the French Revolution"));
	}

	// ---- Navigation queries → NAVIGATION ----

	@Test
	public void testNavigationQuery() {
		// "nearest hospital" triggers LOCATION_SEARCH (correct: contains POI + location)
		// Pure navigation without location keywords:
		assertEquals(QueryType.NAVIGATION,
				classifier.classify("Navigate to the city center"));
	}

	@Test
	public void testDirectionsQuery() {
		assertEquals(QueryType.NAVIGATION,
				classifier.classify("Give me directions to downtown"));
	}

	// ---- Location search → LOCATION_SEARCH ----

	@Test
	public void testLocationNearMe() {
		assertEquals(QueryType.LOCATION_SEARCH,
				classifier.classify("Find coffee near me"));
	}

	@Test
	public void testLocationNearby() {
		assertEquals(QueryType.LOCATION_SEARCH,
				classifier.classify("Find restaurants nearby"));
	}

	@Test
	public void testLocationNearest() {
		assertEquals(QueryType.LOCATION_SEARCH,
				classifier.classify("Where is the nearest gas station nearby"));
	}

	// ---- Direction queries → DIRECTION_QUERY ----

	@Test
	public void testDirectionFromHere() {
		assertEquals(QueryType.DIRECTION_QUERY,
				classifier.classify("Which direction is the airport from here"));
	}

	@Test
	public void testHowFar() {
		assertEquals(QueryType.DIRECTION_QUERY,
				classifier.classify("How far is the city center from here"));
	}

	// ---- Conversational → CONVERSATIONAL ----

	@Test
	public void testGreeting() {
		assertEquals(QueryType.CONVERSATIONAL, classifier.classify("hello"));
	}

	@Test
	public void testThanks() {
		assertEquals(QueryType.CONVERSATIONAL, classifier.classify("thank you"));
	}

	@Test
	public void testShortQuery() {
		assertEquals(QueryType.CONVERSATIONAL, classifier.classify("ok"));
	}

	// ---- Real-time → REALTIME ----

	@Test
	public void testWeatherQuery() {
		assertEquals(QueryType.REALTIME, classifier.classify("What's the weather like?"));
	}

	@Test
	public void testTimeQuery() {
		assertEquals(QueryType.REALTIME, classifier.classify("What time is it?"));
	}

	// ---- Technical → TECHNICAL ----

	@Test
	public void testCodeQuery() {
		assertEquals(QueryType.TECHNICAL,
				classifier.classify("I have a syntax error in my program"));
	}

	// ---- Edge cases ----

	@Test
	public void testEmptyQuery() {
		assertEquals(QueryType.CONVERSATIONAL, classifier.classify(""));
	}

	@Test
	public void testWhitespaceOnly() {
		assertEquals(QueryType.CONVERSATIONAL, classifier.classify("   "));
	}

	@Test
	public void testSingleChar() {
		assertEquals(QueryType.CONVERSATIONAL, classifier.classify("a"));
	}

	// ---- QueryType properties ----

	@Test
	public void testFactualLookupNeedsWikipedia() {
		assertTrue(QueryType.FACTUAL_LOOKUP.needsWikipedia());
		assertFalse(QueryType.FACTUAL_LOOKUP.needsPoiSearch());
	}

	@Test
	public void testLocationSearchNeedsPoi() {
		assertFalse(QueryType.LOCATION_SEARCH.needsWikipedia());
		assertTrue(QueryType.LOCATION_SEARCH.needsPoiSearch());
	}

	@Test
	public void testConversationalNeedsNothing() {
		assertFalse(QueryType.CONVERSATIONAL.needsWikipedia());
		assertFalse(QueryType.CONVERSATIONAL.needsPoiSearch());
	}

	@Test
	public void testNavigationNeedsNothing() {
		assertFalse(QueryType.NAVIGATION.needsWikipedia());
		assertFalse(QueryType.NAVIGATION.needsPoiSearch());
	}

	// ---- Survival queries → SURVIVAL_QUERY (Phase 14) ----

	@Test
	public void testSurvivalFirstAid() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("How do I perform first aid on a wound?"));
	}

	@Test
	public void testSurvivalCPR() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("How to do CPR on someone"));
	}

	@Test
	public void testSurvivalWaterPurification() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("How do I purify water in the wilderness?"));
	}

	@Test
	public void testSurvivalFireStarting() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("fire starting with a ferro rod"));
	}

	@Test
	public void testSurvivalShelter() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("How to build a lean-to shelter"));
	}

	@Test
	public void testSurvivalStarNavigation() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("How to use star navigation to find north"));
	}

	@Test
	public void testSurvivalSignaling() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("How to signal for rescue with a mirror"));
	}

	@Test
	public void testSurvivalForaging() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("What is the edibility test for wild plants?"));
	}

	@Test
	public void testSurvivalBleeding() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("How to stop heavy bleeding from a wound"));
	}

	@Test
	public void testSurvivalHypothermia() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("Treatment for hypothermia in the field"));
	}

	@Test
	public void testSurvivalGridDown() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("How to prepare for a grid-down scenario"));
	}

	@Test
	public void testSurvivalTourniquet() {
		assertEquals(QueryType.SURVIVAL_QUERY,
				classifier.classify("When should I apply a tourniquet?"));
	}

	// ---- SURVIVAL_QUERY properties ----

	@Test
	public void testSurvivalQueryNeedsWikipedia() {
		assertTrue(QueryType.SURVIVAL_QUERY.needsWikipedia());
	}

	@Test
	public void testSurvivalQueryNeedsGuideSearch() {
		assertTrue(QueryType.SURVIVAL_QUERY.needsGuideSearch());
	}

	@Test
	public void testSurvivalQueryDoesNotNeedPoi() {
		assertFalse(QueryType.SURVIVAL_QUERY.needsPoiSearch());
	}

	@Test
	public void testNonSurvivalDoesNotNeedGuideSearch() {
		assertFalse(QueryType.FACTUAL_LOOKUP.needsGuideSearch());
		assertFalse(QueryType.CONVERSATIONAL.needsGuideSearch());
		assertFalse(QueryType.NAVIGATION.needsGuideSearch());
		assertFalse(QueryType.LOCATION_SEARCH.needsGuideSearch());
		assertFalse(QueryType.DEFINITION.needsGuideSearch());
	}

	// ---- Search term extraction ----

	@Test
	public void testExtractSearchTermsFactual() {
		List<String> terms = classifier.extractSearchTerms("What is the Eiffel Tower?");
		assertFalse("Should extract search terms", terms.isEmpty());
		// Should contain "Eiffel Tower" or similar
		boolean containsEiffel = false;
		for (String term : terms) {
			if (term.toLowerCase().contains("eiffel")) {
				containsEiffel = true;
				break;
			}
		}
		assertTrue("Should extract 'Eiffel' as search term", containsEiffel);
	}

	@Test
	public void testExtractSearchTermsEmpty() {
		List<String> terms = classifier.extractSearchTerms("");
		assertTrue("Empty query should produce no terms", terms.isEmpty());
	}

	// ---- Location context ----

	@Test
	public void testHasLocationContext() {
		assertTrue(classifier.hasLocationContext("Is there anything near me?"));
		assertFalse(classifier.hasLocationContext("What is photosynthesis?"));
	}

	@Test
	public void testContainsPoiKeywords() {
		assertTrue(classifier.containsPoiKeywords("Where can I get coffee?"));
		assertTrue(classifier.containsPoiKeywords("I need a hospital"));
		assertFalse(classifier.containsPoiKeywords("What is quantum physics?"));
	}

	// ---- Search radius extraction ----

	@Test
	public void testExtractSearchRadiusKm() {
		int radius = classifier.extractSearchRadius("Find restaurants within 5 km");
		assertEquals(5000, radius);
	}

	@Test
	public void testExtractSearchRadiusMiles() {
		int radius = classifier.extractSearchRadius("Find hotels within 2 miles");
		assertEquals(3218, radius); // 2 * 1609.34 ≈ 3218
	}

	@Test
	public void testExtractSearchRadiusNotSpecified() {
		int radius = classifier.extractSearchRadius("Find coffee nearby");
		assertEquals(-1, radius);
	}

	// ---- Place name extraction ----

	@Test
	public void testExtractPlaceNameDirection() {
		String place = classifier.extractPlaceName("Which direction is the airport from here");
		assertFalse("Should extract place name", place.isEmpty());
		assertTrue(place.toLowerCase().contains("airport"));
	}

	@Test
	public void testExtractPlaceNameHowFar() {
		String place = classifier.extractPlaceName("How far is Tokyo from here");
		assertFalse(place.isEmpty());
		assertTrue(place.toLowerCase().contains("tokyo"));
	}
}
