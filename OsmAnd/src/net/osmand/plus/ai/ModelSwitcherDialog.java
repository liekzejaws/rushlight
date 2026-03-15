/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.lampp.LamppStylePreset;
import net.osmand.plus.lampp.LamppThemeUtils;
import net.osmand.plus.settings.enums.ThemeUsageContext;

import org.apache.commons.logging.Log;

import java.io.File;

/**
 * v1.4: Quick model switcher dialog for the AI chat screen.
 * Lists all downloaded GGUF models with file sizes and suitability badges.
 * Allows one-tap switching between models without navigating to the full
 * model management screen.
 */
public class ModelSwitcherDialog extends DialogFragment {

	public static final String TAG = "ModelSwitcherDialog";
	private static final Log LOG = PlatformUtil.getLog(ModelSwitcherDialog.class);
	private static final String KEY_CURRENT_MODEL = "current_model_name";

	public interface ModelSwitchListener {
		void onModelSelected(@NonNull File modelFile);
	}

	@Nullable
	private ModelSwitchListener listener;

	public void setModelSwitchListener(@Nullable ModelSwitchListener listener) {
		this.listener = listener;
	}

	/**
	 * Show the model switcher dialog.
	 *
	 * @param fragmentManager The fragment manager to show the dialog in
	 * @param currentModelName The name of the currently loaded model (without .gguf), or null
	 * @param listener Callback when a model is selected
	 */
	public static void show(@NonNull FragmentManager fragmentManager,
	                         @Nullable String currentModelName,
	                         @Nullable ModelSwitchListener listener) {
		ModelSwitcherDialog dialog = new ModelSwitcherDialog();
		Bundle args = new Bundle();
		if (currentModelName != null) {
			args.putString(KEY_CURRENT_MODEL, currentModelName);
		}
		dialog.setArguments(args);
		dialog.setModelSwitchListener(listener);
		dialog.show(fragmentManager, TAG);
	}

	@NonNull
	@Override
	public AlertDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		FragmentActivity activity = requireActivity();
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		DeviceCapabilityDetector detector = new DeviceCapabilityDetector(app);

		// List model files from the models directory
		File modelsDir = new File(app.getAppPath(null), LlmManager.MODELS_DIR);
		File[] models;
		if (modelsDir.exists()) {
			File[] files = modelsDir.listFiles((d, name) -> name.endsWith(".gguf"));
			models = files != null ? files : new File[0];
		} else {
			models = new File[0];
		}

		String currentModelName = null;
		Bundle args = getArguments();
		if (args != null) {
			currentModelName = args.getString(KEY_CURRENT_MODEL);
		}

		LamppStylePreset preset = LamppThemeUtils.getActivePreset(app);
		boolean nightMode = app.getDaynightHelper().isNightMode(ThemeUsageContext.OVER_MAP);

		// Build custom view
		LinearLayout container = new LinearLayout(activity);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setPadding(dp(16), dp(8), dp(16), dp(8));

		if (models.length == 0) {
			TextView empty = new TextView(activity);
			empty.setText("No models downloaded yet.\nUse the AI Chat to download a model.");
			empty.setPadding(dp(8), dp(16), dp(8), dp(16));
			container.addView(empty);
		} else if (models.length == 1) {
			TextView single = new TextView(activity);
			String name = models[0].getName().replace(".gguf", "");
			long sizeMb = models[0].length() / (1024 * 1024);
			single.setText("Only one model available: " + name + " (" + sizeMb + " MB)");
			single.setPadding(dp(8), dp(16), dp(8), dp(16));
			container.addView(single);
		} else {
			for (File modelFile : models) {
				View row = buildModelRow(activity, modelFile, detector,
						currentModelName, preset, nightMode);
				container.addView(row);
			}
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setTitle("Switch Model")
				.setView(container)
				.setNegativeButton("Close", null);

		return builder.create();
	}

	private View buildModelRow(@NonNull Context ctx, @NonNull File modelFile,
	                           @NonNull DeviceCapabilityDetector detector,
	                           @Nullable String currentModelName,
	                           @NonNull LamppStylePreset preset, boolean nightMode) {
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setPadding(dp(8), dp(12), dp(8), dp(12));
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		row.setClickable(true);
		row.setFocusable(true);
		row.setBackgroundResource(android.R.drawable.list_selector_background);

		String displayName = modelFile.getName().replace(".gguf", "");
		long sizeMb = modelFile.length() / (1024 * 1024);
		boolean isActive = displayName.equals(currentModelName);
		DeviceCapabilityDetector.ModelSuitability suitability = detector.getModelSuitability(sizeMb);

		// Active indicator (green checkmark or empty space)
		ImageView indicator = new ImageView(ctx);
		LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(20), dp(20));
		indicatorParams.setMarginEnd(dp(8));
		indicatorParams.gravity = android.view.Gravity.CENTER_VERTICAL;
		indicator.setLayoutParams(indicatorParams);
		if (isActive) {
			indicator.setImageResource(R.drawable.ic_action_done);
			indicator.setColorFilter(0xFF4CAF50); // green
		}
		row.addView(indicator);

		// Text container (name + size + suitability)
		LinearLayout textContainer = new LinearLayout(ctx);
		textContainer.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
		textContainer.setLayoutParams(textParams);

		// Model name
		TextView nameText = new TextView(ctx);
		nameText.setText(displayName);
		nameText.setTextSize(15);
		if (isActive) {
			nameText.setTextColor(0xFF4CAF50);
			nameText.setTypeface(null, android.graphics.Typeface.BOLD);
		} else {
			nameText.setTextColor(preset.getTextPrimaryColor(ctx, nightMode));
		}
		textContainer.addView(nameText);

		// Size + suitability line
		TextView detailText = new TextView(ctx);
		String suitText;
		int suitColor;
		switch (suitability) {
			case RECOMMENDED:
				suitText = "Recommended";
				suitColor = 0xFF4CAF50;
				break;
			case POSSIBLE:
				suitText = "May be slow";
				suitColor = 0xFFFF9800;
				break;
			case NOT_RECOMMENDED:
			default:
				suitText = "Too large for device";
				suitColor = 0xFFFF4444;
				break;
		}
		String activeLabel = isActive ? " · Active" : "";
		detailText.setText(sizeMb + " MB · " + suitText + activeLabel);
		detailText.setTextSize(12);
		detailText.setTextColor(suitColor);
		textContainer.addView(detailText);

		row.addView(textContainer);

		// Click handler
		if (!isActive) {
			row.setOnClickListener(v -> {
				if (listener != null) {
					listener.onModelSelected(modelFile);
				}
				dismiss();
			});
		} else {
			row.setOnClickListener(v -> dismiss());
		}

		return row;
	}

	private int dp(int value) {
		float density = requireContext().getResources().getDisplayMetrics().density;
		return (int) (value * density + 0.5f);
	}
}
