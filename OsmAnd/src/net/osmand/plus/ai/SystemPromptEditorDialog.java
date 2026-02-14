package net.osmand.plus.ai;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.security.EncryptedChatStorage;

/**
 * Phase 14: Bottom sheet dialog for editing the AI system prompt.
 * Provides preset prompts for common survival scenarios and a custom text editor.
 */
public class SystemPromptEditorDialog extends BottomSheetDialogFragment {

	public static final String TAG = "SystemPromptEditor";

	// ==================== Preset Prompts ====================

	/** Survival expert — prioritizes safety, practical step-by-step advice */
	public static final String PRESET_SURVIVAL =
			"You are a wilderness survival expert. Prioritize safety above all else. " +
			"Give clear, step-by-step instructions for survival situations. " +
			"Always warn about dangerous techniques. Recommend the most conservative approach " +
			"when multiple options exist. Include specific measurements, times, and quantities. " +
			"Cite survival guides as [Guide: Title] when available.";

	/** Medical first responder — emergency medical focus */
	public static final String PRESET_MEDICAL =
			"You are an emergency medical advisor trained in wilderness first aid. " +
			"Prioritize life-threatening conditions first (airway, breathing, circulation). " +
			"Give clear triage steps and always recommend seeking professional medical help. " +
			"Warn about risks of improvised treatments. Include dosages and timing when relevant. " +
			"Never diagnose — describe symptoms and appropriate first aid responses.";

	/** Navigation guide — offline orientation and wayfinding */
	public static final String PRESET_NAVIGATION =
			"You are a navigation expert specializing in offline orientation. " +
			"Help with compass reading, dead reckoning, celestial navigation, and map interpretation. " +
			"Explain techniques clearly with reference to cardinal directions. " +
			"Account for magnetic declination and terrain. " +
			"When giving directions, be specific about landmarks, angles, and distances.";

	/** Radio operator — communications focus */
	public static final String PRESET_RADIO =
			"You are a communications specialist with expertise in emergency radio operations. " +
			"Help with radio frequencies, protocols, Morse code, and signal procedures. " +
			"Know international distress frequencies and procedures. " +
			"Explain antenna theory, propagation, and improvised radio equipment. " +
			"Use standard phonetic alphabet and radio terminology.";

	/** General assistant — balanced helpful assistant */
	public static final String PRESET_GENERAL =
			"You are a helpful offline assistant. Answer questions clearly and concisely. " +
			"Cite Wikipedia sources as [Source: Title] when available. " +
			"For survival topics, prioritize safety and practical advice. " +
			"Be accurate and acknowledge uncertainty when appropriate.";

	/**
	 * Get all preset names for testing/display.
	 */
	public static String[] getPresetNames() {
		return new String[]{"Survival Expert", "Medical", "Navigation", "Radio Operator", "General"};
	}

	/**
	 * Get all preset prompt texts.
	 */
	public static String[] getPresetPrompts() {
		return new String[]{PRESET_SURVIVAL, PRESET_MEDICAL, PRESET_NAVIGATION, PRESET_RADIO, PRESET_GENERAL};
	}

	// ==================== Dialog State ====================

	private long conversationId;
	private EditText promptInput;
	private ChipGroup chipGroup;

	@Nullable
	private OnPromptSavedListener listener;

	/**
	 * Callback for when the prompt is saved.
	 */
	public interface OnPromptSavedListener {
		void onPromptSaved(@Nullable String prompt);
	}

	public static SystemPromptEditorDialog newInstance(long conversationId) {
		SystemPromptEditorDialog dialog = new SystemPromptEditorDialog();
		Bundle args = new Bundle();
		args.putLong("conversation_id", conversationId);
		dialog.setArguments(args);
		return dialog;
	}

	public void setListener(@Nullable OnPromptSavedListener listener) {
		this.listener = listener;
	}

	public static void showInstance(@NonNull FragmentManager fragmentManager,
	                                long conversationId,
	                                @Nullable OnPromptSavedListener listener) {
		if (fragmentManager.findFragmentByTag(TAG) != null) return;

		SystemPromptEditorDialog dialog = newInstance(conversationId);
		dialog.setListener(listener);
		dialog.show(fragmentManager, TAG);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			conversationId = getArguments().getLong("conversation_id",
					EncryptedChatStorage.DEFAULT_CONVERSATION_ID);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                          @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dialog_system_prompt_editor, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		promptInput = view.findViewById(R.id.prompt_input);
		chipGroup = view.findViewById(R.id.preset_chip_group);
		ImageButton clearButton = view.findViewById(R.id.btn_clear_prompt);
		Button cancelButton = view.findViewById(R.id.btn_cancel);
		Button saveButton = view.findViewById(R.id.btn_save);

		// Load current prompt
		loadCurrentPrompt();

		// Setup preset chip listeners
		setupChipListeners(view);

		// Clear button
		clearButton.setOnClickListener(v -> {
			promptInput.setText("");
			chipGroup.clearCheck();
		});

		// Cancel
		cancelButton.setOnClickListener(v -> dismiss());

		// Save
		saveButton.setOnClickListener(v -> savePrompt());
	}

	private void loadCurrentPrompt() {
		try {
			OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
			EncryptedChatStorage storage = app.getSecurityManager().getChatStorage();
			String currentPrompt = storage.getConversationSystemPrompt(conversationId);
			if (currentPrompt != null && !currentPrompt.isEmpty()) {
				promptInput.setText(currentPrompt);
				// Check if it matches a preset
				selectMatchingChip(currentPrompt);
			}
		} catch (Exception e) {
			// Ignore — fresh dialog
		}
	}

	private void selectMatchingChip(String prompt) {
		if (PRESET_SURVIVAL.equals(prompt)) {
			chipGroup.check(R.id.chip_survival);
		} else if (PRESET_MEDICAL.equals(prompt)) {
			chipGroup.check(R.id.chip_medical);
		} else if (PRESET_NAVIGATION.equals(prompt)) {
			chipGroup.check(R.id.chip_navigation);
		} else if (PRESET_RADIO.equals(prompt)) {
			chipGroup.check(R.id.chip_radio);
		} else if (PRESET_GENERAL.equals(prompt)) {
			chipGroup.check(R.id.chip_general);
		}
	}

	private void setupChipListeners(View view) {
		Chip chipSurvival = view.findViewById(R.id.chip_survival);
		Chip chipMedical = view.findViewById(R.id.chip_medical);
		Chip chipNavigation = view.findViewById(R.id.chip_navigation);
		Chip chipRadio = view.findViewById(R.id.chip_radio);
		Chip chipGeneral = view.findViewById(R.id.chip_general);

		chipSurvival.setOnClickListener(v -> promptInput.setText(PRESET_SURVIVAL));
		chipMedical.setOnClickListener(v -> promptInput.setText(PRESET_MEDICAL));
		chipNavigation.setOnClickListener(v -> promptInput.setText(PRESET_NAVIGATION));
		chipRadio.setOnClickListener(v -> promptInput.setText(PRESET_RADIO));
		chipGeneral.setOnClickListener(v -> promptInput.setText(PRESET_GENERAL));
	}

	private void savePrompt() {
		String prompt = promptInput.getText().toString().trim();
		String promptToSave = prompt.isEmpty() ? null : prompt;

		try {
			OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
			EncryptedChatStorage storage = app.getSecurityManager().getChatStorage();
			storage.setConversationSystemPrompt(conversationId, promptToSave);
		} catch (Exception e) {
			// Log error
		}

		if (listener != null) {
			listener.onPromptSaved(promptToSave);
		}

		dismiss();
	}
}
