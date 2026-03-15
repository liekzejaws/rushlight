/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.lampp;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import net.osmand.plus.R;
import net.osmand.plus.ai.LlmModelsFragment;
import net.osmand.plus.security.BiometricHelper;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.settings.preferences.ListPreferenceEx;
import net.osmand.plus.settings.preferences.SwitchPreferenceEx;

/**
 * LAMPP: Dedicated settings screen for AI inference and RAG configuration.
 * Accessible from the Tools panel's "More settings..." button.
 */
public class LamppSettingsFragment extends BaseSettingsFragment {

	private static final String MANAGE_MODELS_KEY = "lampp_manage_models";

	@Override
	protected void setupPreferences() {
		// AI Model category
		setupManageModelsPref();
		setupListPref(settings.LAMPP_LLM_THREADS.getId(),
				R.string.lampp_llm_threads_desc,
				new String[]{"1", "2", "4", "6", "8"},
				new Integer[]{1, 2, 4, 6, 8});
		setupListPref(settings.LAMPP_LLM_CTX_SIZE.getId(),
				R.string.lampp_llm_ctx_size_desc,
				new String[]{"2048", "4096", "8192"},
				new Integer[]{2048, 4096, 8192});
		setupListPref(settings.LAMPP_LLM_MAX_TOKENS.getId(),
				R.string.lampp_llm_max_tokens_desc,
				new String[]{"256", "512", "1024", "2048", "4096"},
				new Integer[]{256, 512, 1024, 2048, 4096});
		setupListPref(settings.LAMPP_LLM_TEMPERATURE.getId(),
				R.string.lampp_llm_temperature_desc,
				new String[]{"0.0", "0.1", "0.3", "0.5", "0.7", "1.0", "1.2", "1.5"},
				new Integer[]{0, 1, 3, 5, 7, 10, 12, 15});

		// RAG Context Sources category
		setupSwitchPref(settings.LAMPP_RAG_WIKIPEDIA_ENABLED.getId(),
				R.string.lampp_rag_wikipedia_desc);
		setupSwitchPref(settings.LAMPP_RAG_POI_SEARCH_ENABLED.getId(),
				R.string.lampp_rag_poi_search_desc);
		setupListPref(settings.LAMPP_RAG_MAX_SOURCES.getId(),
				R.string.lampp_rag_max_sources_desc,
				new String[]{"1", "2", "3", "4", "5"},
				new Integer[]{1, 2, 3, 4, 5});
		setupListPref(settings.LAMPP_RAG_CONTEXT_TOKENS.getId(),
				R.string.lampp_rag_context_tokens_desc,
				new String[]{"500", "1000", "1500", "2000", "3000", "4000", "6000"},
				new Integer[]{500, 1000, 1500, 2000, 3000, 4000, 6000});
		setupListPref(settings.LAMPP_RAG_POI_RADIUS.getId(),
				R.string.lampp_rag_poi_radius_desc,
				new String[]{"100 m", "250 m", "500 m", "1 km", "2 km", "5 km", "10 km"},
				new Integer[]{100, 250, 500, 1000, 2000, 5000, 10000});

		// Pip-Boy Effects
		setupSwitchPref(settings.LAMPP_PIPBOY_SCANLINES.getId(),
				R.string.lampp_pipboy_scanlines_desc);
		setupSwitchPref(settings.LAMPP_PIPBOY_GLOW.getId(),
				R.string.lampp_pipboy_glow_desc);
		setupSwitchPref(settings.LAMPP_PIPBOY_RETRO_FONT.getId(),
				R.string.lampp_pipboy_retro_font_desc);
		setupSwitchPref(settings.LAMPP_PIPBOY_CURSOR_BLINK.getId(),
				R.string.lampp_pipboy_cursor_blink_desc);

		// Morse Code (Phase 16)
		setupListPref(settings.LAMPP_MORSE_WPM.getId(),
				R.string.lampp_morse_wpm_desc,
				new String[]{"5", "8", "10", "13", "15", "18", "20", "25"},
				new Integer[]{5, 8, 10, 13, 15, 18, 20, 25});
		setupListPref(settings.LAMPP_MORSE_AUDIO_FREQ.getId(),
				R.string.lampp_morse_audio_freq_desc,
				new String[]{"400 Hz", "550 Hz", "700 Hz", "800 Hz", "1000 Hz", "1200 Hz"},
				new Integer[]{400, 550, 700, 800, 1000, 1200});
		setupListPref(settings.LAMPP_MORSE_RECEIVE_SENSITIVITY.getId(),
				R.string.lampp_morse_sensitivity_desc,
				new String[]{"10", "25", "50", "75", "90"},
				new Integer[]{10, 25, 50, 75, 90});
		setupSwitchPref(settings.LAMPP_MORSE_AI_CORRECT.getId(),
				R.string.lampp_morse_ai_correct_desc);
		setupSwitchPref(settings.LAMPP_MORSE_GPS_APPEND.getId(),
				R.string.lampp_morse_gps_append_desc);

		// P2P Sharing (Phase 16)
		setupSwitchPref(settings.LAMPP_P2P_ENCRYPTION_ENABLED.getId(),
				R.string.lampp_p2p_encryption_desc);

		// AI Behavior
		setupSystemPromptPref();

		// Security
		setupScreenLockPref();
		setupSwitchPref(settings.LAMPP_CHAT_ENCRYPTION_ENABLED.getId(),
				R.string.rushlight_chat_encryption_desc);
	}

	private void setupSystemPromptPref() {
		EditTextPreference pref = findPreference(settings.LAMPP_SYSTEM_PROMPT.getId());
		if (pref != null) {
			pref.setIconSpaceReserved(false);
			pref.setSummary(R.string.rushlight_system_prompt_desc);
			// Show current value as dialog default
			String currentPrompt = settings.LAMPP_SYSTEM_PROMPT.get();
			pref.setText(currentPrompt);
			pref.setOnPreferenceChangeListener((preference, newValue) -> {
				String value = (String) newValue;
				settings.LAMPP_SYSTEM_PROMPT.set(value);
				return true;
			});
		}
	}

	private void setupScreenLockPref() {
		SwitchPreferenceEx pref = findPreference(settings.LAMPP_SCREEN_LOCK_ENABLED.getId());
		if (pref != null) {
			pref.setIconSpaceReserved(false);
			pref.setDescription(R.string.rushlight_screen_lock_desc);
			pref.setOnPreferenceChangeListener((preference, newValue) -> {
				boolean enabled = (Boolean) newValue;
				if (enabled && getContext() != null
						&& !BiometricHelper.isBiometricAvailable(getContext())) {
					app.showShortToastMessage(R.string.rushlight_biometric_unavailable);
					return false;
				}
				return true;
			});
		}
	}

	private void setupManageModelsPref() {
		Preference pref = findPreference(MANAGE_MODELS_KEY);
		if (pref != null) {
			pref.setIconSpaceReserved(false);
			pref.setSummary(R.string.lampp_manage_models_desc);
		}
	}

	private void setupListPref(String key, int descResId,
	                           String[] entries, Integer[] values) {
		ListPreferenceEx pref = findPreference(key);
		if (pref != null) {
			pref.setIconSpaceReserved(false);
			pref.setDescription(descResId);
			pref.setEntries(entries);
			pref.setEntryValues(values);
		}
	}

	private void setupSwitchPref(String key, int descResId) {
		SwitchPreferenceEx pref = findPreference(key);
		if (pref != null) {
			pref.setIconSpaceReserved(false);
			pref.setDescription(descResId);
		}
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (MANAGE_MODELS_KEY.equals(preference.getKey())) {
			if (getActivity() != null) {
				LlmModelsFragment.showInstance(
						getActivity().getSupportFragmentManager());
			}
			return true;
		}
		return super.onPreferenceClick(preference);
	}
}
