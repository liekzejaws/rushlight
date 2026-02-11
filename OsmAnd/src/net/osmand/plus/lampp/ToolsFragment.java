package net.osmand.plus.lampp;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.settings.fragments.SettingsScreenType;

/**
 * LAMPP: Tools panel with theme preset selection.
 * Shows 3 preset cards (Pip-Boy, Modern, Classic OsmAnd) and a "More settings..." button.
 */
public class ToolsFragment extends LamppPanelFragment {

	public static final String TAG = "ToolsFragment";

	private MaterialCardView cardPipBoy;
	private MaterialCardView cardModern;
	private MaterialCardView cardClassic;
	private ImageView checkPipBoy;
	private ImageView checkModern;
	private ImageView checkClassic;

	@Override
	protected int getPanelLayoutId() {
		return R.layout.lampp_tools_panel;
	}

	@NonNull
	@Override
	public String getPanelTag() {
		return TAG;
	}

	@Override
	protected void onPanelViewCreated(@NonNull View contentView, @Nullable Bundle savedInstanceState) {
		// Find preset cards
		cardPipBoy = contentView.findViewById(R.id.preset_pip_boy);
		cardModern = contentView.findViewById(R.id.preset_modern);
		cardClassic = contentView.findViewById(R.id.preset_classic);

		// Find checkmarks
		checkPipBoy = contentView.findViewById(R.id.check_pip_boy);
		checkModern = contentView.findViewById(R.id.check_modern);
		checkClassic = contentView.findViewById(R.id.check_classic);

		// Set click listeners
		cardPipBoy.setOnClickListener(v -> applyPreset(LamppStylePreset.PIP_BOY));
		cardModern.setOnClickListener(v -> applyPreset(LamppStylePreset.MODERN));
		cardClassic.setOnClickListener(v -> applyPreset(LamppStylePreset.CLASSIC));

		// More settings button
		View moreSettings = contentView.findViewById(R.id.more_settings_button);
		if (moreSettings != null) {
			moreSettings.setOnClickListener(v -> {
				MapActivity mapActivity = getMapActivity();
				if (mapActivity != null) {
					// Close the panel first, then open settings
					mapActivity.getLamppPanelManager().closeActivePanel(false);
					BaseSettingsFragment.showInstance(mapActivity, SettingsScreenType.LAMPP_SETTINGS);
				}
			});
		}

		// Show current selection
		updateSelection();
	}

	private void applyPreset(@NonNull LamppStylePreset preset) {
		OsmandApplication app = getMyApplication();
		if (app == null) return;

		// Save preference
		app.getSettings().LAMPP_STYLE_PRESET.set(preset);

		// Update checkmarks
		updateSelection();

		// Refresh tab bar colors
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getLamppPanelManager().refreshTheme();
		}
	}

	private void updateSelection() {
		OsmandApplication app = getMyApplication();
		if (app == null) return;

		LamppStylePreset current = LamppThemeUtils.getActivePreset(app);

		// Show/hide checkmarks
		setCheckVisible(checkPipBoy, current == LamppStylePreset.PIP_BOY);
		setCheckVisible(checkModern, current == LamppStylePreset.MODERN);
		setCheckVisible(checkClassic, current == LamppStylePreset.CLASSIC);

		// Update card stroke widths to emphasize selection
		setCardSelected(cardPipBoy, current == LamppStylePreset.PIP_BOY);
		setCardSelected(cardModern, current == LamppStylePreset.MODERN);
		setCardSelected(cardClassic, current == LamppStylePreset.CLASSIC);
	}

	private void setCheckVisible(@Nullable ImageView check, boolean visible) {
		if (check == null) return;
		if (visible && check.getVisibility() != View.VISIBLE) {
			check.setAlpha(0f);
			check.setVisibility(View.VISIBLE);
			check.animate().alpha(1f).setDuration(150).start();
		} else if (!visible && check.getVisibility() == View.VISIBLE) {
			check.animate().alpha(0f).setDuration(150)
					.withEndAction(() -> check.setVisibility(View.GONE))
					.start();
		} else if (visible) {
			check.setVisibility(View.VISIBLE);
			check.setAlpha(1f);
		} else {
			check.setVisibility(View.GONE);
		}
	}

	private void setCardSelected(@Nullable MaterialCardView card, boolean selected) {
		if (card == null) return;
		int currentWidth = card.getStrokeWidth();
		int targetWidth = selected
				? (int) (3 * getResources().getDisplayMetrics().density)
				: (int) (1 * getResources().getDisplayMetrics().density);
		if (currentWidth != targetWidth) {
			ValueAnimator anim = ValueAnimator.ofInt(currentWidth, targetWidth);
			anim.setDuration(150);
			anim.addUpdateListener(a -> card.setStrokeWidth((int) a.getAnimatedValue()));
			anim.start();
		}
	}

	@Nullable
	private OsmandApplication getMyApplication() {
		return getActivity() != null ? (OsmandApplication) getActivity().getApplication() : null;
	}
}
