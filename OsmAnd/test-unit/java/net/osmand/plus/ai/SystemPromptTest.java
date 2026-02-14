package net.osmand.plus.ai;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Phase 14 Session 5: Tests for SystemPromptEditorDialog preset prompts.
 * Verifies preset prompt content, naming, and consistency.
 */
public class SystemPromptTest {

	// ==================== Preset Content Tests ====================

	@Test
	public void testAllPresetsNonEmpty() {
		String[] prompts = SystemPromptEditorDialog.getPresetPrompts();
		for (String prompt : prompts) {
			assertNotNull("Preset prompt should not be null", prompt);
			assertFalse("Preset prompt should not be empty", prompt.isEmpty());
		}
	}

	@Test
	public void testAllPresetNamesNonEmpty() {
		String[] names = SystemPromptEditorDialog.getPresetNames();
		for (String name : names) {
			assertNotNull("Preset name should not be null", name);
			assertFalse("Preset name should not be empty", name.isEmpty());
		}
	}

	@Test
	public void testPresetCountsMatch() {
		String[] names = SystemPromptEditorDialog.getPresetNames();
		String[] prompts = SystemPromptEditorDialog.getPresetPrompts();
		assertEquals("Preset names and prompts must have same count",
				names.length, prompts.length);
	}

	@Test
	public void testFivePresetsExist() {
		assertEquals(5, SystemPromptEditorDialog.getPresetNames().length);
		assertEquals(5, SystemPromptEditorDialog.getPresetPrompts().length);
	}

	// ==================== Preset Role Description Tests ====================

	@Test
	public void testSurvivalPresetContainsRoleDescription() {
		String prompt = SystemPromptEditorDialog.PRESET_SURVIVAL;
		assertTrue("Survival preset should mention survival",
				prompt.toLowerCase().contains("survival"));
		assertTrue("Survival preset should mention safety",
				prompt.toLowerCase().contains("safety"));
		assertTrue("Survival preset should mention step-by-step",
				prompt.toLowerCase().contains("step-by-step"));
	}

	@Test
	public void testMedicalPresetContainsRoleDescription() {
		String prompt = SystemPromptEditorDialog.PRESET_MEDICAL;
		assertTrue("Medical preset should mention medical or first aid",
				prompt.toLowerCase().contains("medical") || prompt.toLowerCase().contains("first aid"));
		assertTrue("Medical preset should mention triage or airway",
				prompt.toLowerCase().contains("triage") || prompt.toLowerCase().contains("airway"));
	}

	@Test
	public void testNavigationPresetContainsRoleDescription() {
		String prompt = SystemPromptEditorDialog.PRESET_NAVIGATION;
		assertTrue("Navigation preset should mention navigation or compass",
				prompt.toLowerCase().contains("navigation") || prompt.toLowerCase().contains("compass"));
		assertTrue("Navigation preset should mention offline or orientation",
				prompt.toLowerCase().contains("offline") || prompt.toLowerCase().contains("orientation"));
	}

	@Test
	public void testRadioPresetContainsRoleDescription() {
		String prompt = SystemPromptEditorDialog.PRESET_RADIO;
		assertTrue("Radio preset should mention radio or communications",
				prompt.toLowerCase().contains("radio") || prompt.toLowerCase().contains("communications"));
		assertTrue("Radio preset should mention frequencies or Morse",
				prompt.toLowerCase().contains("frequenc") || prompt.toLowerCase().contains("morse"));
	}

	@Test
	public void testGeneralPresetContainsRoleDescription() {
		String prompt = SystemPromptEditorDialog.PRESET_GENERAL;
		assertTrue("General preset should mention assistant or helpful",
				prompt.toLowerCase().contains("assistant") || prompt.toLowerCase().contains("helpful"));
	}

	// ==================== Uniqueness and Consistency ====================

	@Test
	public void testAllPresetsUnique() {
		String[] prompts = SystemPromptEditorDialog.getPresetPrompts();
		for (int i = 0; i < prompts.length; i++) {
			for (int j = i + 1; j < prompts.length; j++) {
				assertNotEquals("Preset prompts should all be unique: index " + i + " vs " + j,
						prompts[i], prompts[j]);
			}
		}
	}

	@Test
	public void testAllPresetNamesUnique() {
		String[] names = SystemPromptEditorDialog.getPresetNames();
		for (int i = 0; i < names.length; i++) {
			for (int j = i + 1; j < names.length; j++) {
				assertNotEquals("Preset names should all be unique: index " + i + " vs " + j,
						names[i], names[j]);
			}
		}
	}

	@Test
	public void testPresetsHaveMinimumLength() {
		String[] prompts = SystemPromptEditorDialog.getPresetPrompts();
		for (String prompt : prompts) {
			assertTrue("Preset prompts should be substantial (>50 chars), got: " + prompt.length(),
					prompt.length() > 50);
		}
	}

	// ==================== Null/Empty Prompt Handling ====================

	@Test
	public void testNullPromptRepresentsDefault() {
		// A null prompt means "use default" — this is the convention in EncryptedChatStorage
		// and RagManager. Verify the convention is documented in preset access.
		String[] prompts = SystemPromptEditorDialog.getPresetPrompts();
		for (String prompt : prompts) {
			assertNotNull("No preset should be null — null means 'use default/clear'", prompt);
		}
	}
}
