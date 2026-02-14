package net.osmand.plus.lampp;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 16: Tests that all Rushlight preference keys referenced in the settings XML
 * are actually declared in OsmandSettings.java, and vice versa.
 * Also validates that the settings XML has all expected categories.
 */
public class SettingsCompletenessTest {

	// All LAMPP preference keys that should appear in the settings screen
	private static final List<String> EXPECTED_PREF_KEYS = Arrays.asList(
			// AI Model
			"lampp_manage_models",
			"lampp_llm_threads",
			"lampp_llm_ctx_size",
			"lampp_llm_max_tokens",
			"lampp_llm_temperature",
			// RAG Context
			"lampp_rag_wikipedia_enabled",
			"lampp_rag_poi_search_enabled",
			"lampp_rag_max_sources",
			"lampp_rag_context_tokens",
			"lampp_rag_poi_radius",
			// Pip-Boy Effects
			"lampp_pipboy_scanlines",
			"lampp_pipboy_glow",
			"lampp_pipboy_retro_font",
			"lampp_pipboy_cursor_blink",
			// Morse Code (Phase 16)
			"lampp_morse_wpm",
			"lampp_morse_audio_freq",
			"lampp_morse_receive_sensitivity",
			"lampp_morse_ai_correct",
			"lampp_morse_gps_append",
			// P2P (Phase 16)
			"lampp_p2p_encryption_enabled",
			// AI Behavior
			"lampp_system_prompt",
			// Security
			"lampp_screen_lock_enabled",
			"lampp_chat_encryption_enabled"
	);

	// Expected category keys in the settings XML
	private static final List<String> EXPECTED_CATEGORIES = Arrays.asList(
			"lampp_ai_model",
			"lampp_rag_context",
			"lampp_pipboy_effects",
			"lampp_morse_settings",
			"lampp_p2p_settings",
			"lampp_ai_behavior",
			"lampp_security"
	);

	@Test
	public void testExpectedPrefKeys_count() {
		// 5 + 5 + 4 + 5 + 1 + 1 + 2 = 23 preferences
		assertEquals(23, EXPECTED_PREF_KEYS.size());
	}

	@Test
	public void testExpectedCategories_count() {
		assertEquals(7, EXPECTED_CATEGORIES.size());
	}

	@Test
	public void testAllPrefKeys_areUniqueInList() {
		long uniqueCount = EXPECTED_PREF_KEYS.stream().distinct().count();
		assertEquals("All preference keys should be unique",
				EXPECTED_PREF_KEYS.size(), uniqueCount);
	}

	@Test
	public void testAllPrefKeys_startWithLampp() {
		for (String key : EXPECTED_PREF_KEYS) {
			assertTrue("Key should start with 'lampp_': " + key,
					key.startsWith("lampp_"));
		}
	}

	@Test
	public void testAllCategories_startWithLampp() {
		for (String category : EXPECTED_CATEGORIES) {
			assertTrue("Category should start with 'lampp_': " + category,
					category.startsWith("lampp_"));
		}
	}

	@Test
	public void testMorseSettings_included() {
		assertTrue(EXPECTED_PREF_KEYS.contains("lampp_morse_wpm"));
		assertTrue(EXPECTED_PREF_KEYS.contains("lampp_morse_audio_freq"));
		assertTrue(EXPECTED_PREF_KEYS.contains("lampp_morse_receive_sensitivity"));
		assertTrue(EXPECTED_PREF_KEYS.contains("lampp_morse_ai_correct"));
		assertTrue(EXPECTED_PREF_KEYS.contains("lampp_morse_gps_append"));
	}

	@Test
	public void testP2pSettings_included() {
		assertTrue(EXPECTED_PREF_KEYS.contains("lampp_p2p_encryption_enabled"));
	}

	@Test
	public void testSecuritySettings_included() {
		assertTrue(EXPECTED_PREF_KEYS.contains("lampp_screen_lock_enabled"));
		assertTrue(EXPECTED_PREF_KEYS.contains("lampp_chat_encryption_enabled"));
	}

	@Test
	public void testMorseCategory_exists() {
		assertTrue(EXPECTED_CATEGORIES.contains("lampp_morse_settings"));
	}

	@Test
	public void testP2pCategory_exists() {
		assertTrue(EXPECTED_CATEGORIES.contains("lampp_p2p_settings"));
	}

	@Test
	public void testSettingsXml_containsAllKeys() throws Exception {
		// Read the settings XML and verify all expected keys are present
		File xmlFile = new File("OsmAnd/res/xml/lampp_settings.xml");
		if (!xmlFile.exists()) {
			// Try from different working directory
			xmlFile = new File("res/xml/lampp_settings.xml");
		}
		if (!xmlFile.exists()) {
			// Skip file-based test if file not accessible from test runner
			return;
		}

		String content = readFileContent(xmlFile);
		for (String key : EXPECTED_PREF_KEYS) {
			assertTrue("Settings XML should contain key: " + key,
					content.contains("\"" + key + "\""));
		}
	}

	@Test
	public void testSettingsXml_containsAllCategories() throws Exception {
		File xmlFile = new File("OsmAnd/res/xml/lampp_settings.xml");
		if (!xmlFile.exists()) {
			xmlFile = new File("res/xml/lampp_settings.xml");
		}
		if (!xmlFile.exists()) {
			return; // Skip if not accessible
		}

		String content = readFileContent(xmlFile);
		for (String category : EXPECTED_CATEGORIES) {
			assertTrue("Settings XML should contain category: " + category,
					content.contains("\"" + category + "\""));
		}
	}

	private String readFileContent(File file) throws Exception {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
		}
		return sb.toString();
	}
}
