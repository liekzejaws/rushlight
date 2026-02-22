package net.osmand.plus.fieldnotes;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;

/**
 * Dialog for creating a new FieldNote at a specific location.
 * Shown after long-pressing the map and selecting "Add FieldNote".
 *
 * Features:
 * - Category picker with color-coded chips
 * - Title and note text inputs with character limits
 * - TTL (expiry) picker with presets
 * - Validates input before enabling save button
 */
public class CreateFieldNoteDialog extends DialogFragment {

	public static final String TAG = "CreateFieldNoteDialog";

	private static final String ARG_LAT = "lat";
	private static final String ARG_LON = "lon";

	// TTL presets: label → hours
	private static final String[] TTL_LABELS = {
			"1 hour", "6 hours", "24 hours", "3 days",
			"1 week", "1 month", "Permanent"
	};
	private static final int[] TTL_VALUES = {
			1, 6, 24, 72,
			168, 720, 0  // 0 = permanent
	};
	private static final int DEFAULT_TTL_INDEX = 4; // 1 week

	private double lat;
	private double lon;
	private FieldNote.Category selectedCategory = null;

	private TextInputEditText titleInput;
	private TextInputEditText noteInput;
	private Spinner ttlSpinner;
	private Button saveButton;

	/**
	 * Create a new dialog for a specific lat/lon.
	 */
	public static CreateFieldNoteDialog newInstance(double lat, double lon) {
		CreateFieldNoteDialog dialog = new CreateFieldNoteDialog();
		Bundle args = new Bundle();
		args.putDouble(ARG_LAT, lat);
		args.putDouble(ARG_LON, lon);
		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			lat = getArguments().getDouble(ARG_LAT);
			lon = getArguments().getDouble(ARG_LON);
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		FragmentActivity activity = requireActivity();
		View view = LayoutInflater.from(activity).inflate(
				R.layout.dialog_create_fieldnote, null);

		setupLocationDisplay(view);
		setupCategoryChips(view);
		setupInputFields(view);
		setupTtlSpinner(view);
		setupButtons(view);

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setView(view);
		return builder.create();
	}

	private void setupLocationDisplay(@NonNull View view) {
		TextView locationText = view.findViewById(R.id.fieldnote_location);
		locationText.setText(String.format("%.5f, %.5f", lat, lon));
	}

	private void setupCategoryChips(@NonNull View view) {
		ChipGroup chipGroup = view.findViewById(R.id.category_chip_group);

		for (FieldNote.Category category : FieldNote.Category.values()) {
			Chip chip = new Chip(requireContext());
			chip.setText(category.getDisplayName());
			chip.setCheckable(true);
			chip.setChipIconResource(category.getIconRes());
			chip.setChipIconTint(android.content.res.ColorStateList.valueOf(category.getColor()));
			chip.setChipIconVisible(true);

			// Use category color for checked state
			chip.setCheckedIconVisible(false);
			chip.setTag(category);

			chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
				if (isChecked) {
					selectedCategory = (FieldNote.Category) buttonView.getTag();
					// Set chip background tint to category color when checked
					chip.setChipStrokeColorResource(android.R.color.transparent);
					chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
							(category.getColor() & 0x00FFFFFF) | 0x33000000)); // 20% alpha
				} else {
					chip.setChipBackgroundColor(null);
				}
				validateForm();
			});

			chipGroup.addView(chip);
		}
	}

	private void setupInputFields(@NonNull View view) {
		titleInput = view.findViewById(R.id.fieldnote_title_input);
		noteInput = view.findViewById(R.id.fieldnote_note_input);

		TextWatcher watcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				validateForm();
			}
		};

		titleInput.addTextChangedListener(watcher);
		noteInput.addTextChangedListener(watcher);
	}

	private void setupTtlSpinner(@NonNull View view) {
		ttlSpinner = view.findViewById(R.id.fieldnote_ttl_spinner);
		ArrayAdapter<String> adapter = new ArrayAdapter<>(
				requireContext(),
				android.R.layout.simple_spinner_item,
				TTL_LABELS);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		ttlSpinner.setAdapter(adapter);
		ttlSpinner.setSelection(DEFAULT_TTL_INDEX);
	}

	private void setupButtons(@NonNull View view) {
		saveButton = view.findViewById(R.id.fieldnote_save_button);
		Button cancelButton = view.findViewById(R.id.fieldnote_cancel_button);

		saveButton.setOnClickListener(v -> saveFieldNote());
		cancelButton.setOnClickListener(v -> dismiss());
	}

	private void validateForm() {
		boolean valid = selectedCategory != null
				&& titleInput != null
				&& titleInput.getText() != null
				&& titleInput.getText().toString().trim().length() > 0;

		if (saveButton != null) {
			saveButton.setEnabled(valid);
		}
	}

	private void saveFieldNote() {
		if (selectedCategory == null || titleInput == null || noteInput == null) {
			return;
		}

		String title = titleInput.getText() != null
				? titleInput.getText().toString().trim() : "";
		String note = noteInput.getText() != null
				? noteInput.getText().toString().trim() : "";

		if (title.isEmpty()) {
			titleInput.setError("Title is required");
			return;
		}

		int ttlIndex = ttlSpinner.getSelectedItemPosition();
		int ttlHours = (ttlIndex >= 0 && ttlIndex < TTL_VALUES.length)
				? TTL_VALUES[ttlIndex] : FieldNote.DEFAULT_TTL_HOURS;

		OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
		FieldNotesManager manager = app.getFieldNotesManager();

		FieldNote fieldNote = manager.createNote(lat, lon, selectedCategory,
				title, note, ttlHours);

		if (fieldNote != null) {
			Toast.makeText(requireContext(),
					"FieldNote saved: " + selectedCategory.getDisplayName(),
					Toast.LENGTH_SHORT).show();
			dismiss();
		} else {
			Toast.makeText(requireContext(),
					"Failed to save FieldNote",
					Toast.LENGTH_SHORT).show();
		}
	}
}
