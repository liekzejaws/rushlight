package net.osmand.plus.lampp;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.card.MaterialCardView;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.security.DuressManager;
import net.osmand.plus.security.PinEntryDialog;
import net.osmand.plus.security.SecurityManager;
import net.osmand.plus.security.StealthManager;
import net.osmand.plus.security.StealthSettingsDialog;
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

		// Panic wipe button
		View panicWipeButton = contentView.findViewById(R.id.panic_wipe_button);
		if (panicWipeButton != null) {
			panicWipeButton.setOnClickListener(v -> showPanicWipeConfirmation());
		}

		// Phase 15: PIN setup buttons
		setupPinSection(contentView);

		// Phase 15 Session 2: Stealth mode section
		setupStealthSection(contentView);

		// Show Setup Guide button
		View showGuideButton = contentView.findViewById(R.id.show_setup_guide_button);
		if (showGuideButton != null) {
			showGuideButton.setOnClickListener(v -> {
				MapActivity mapActivity = getMapActivity();
				if (mapActivity != null) {
					mapActivity.getLamppPanelManager().closeActivePanel(false);
					OnboardingOverlay.showFromTools(mapActivity);
				}
			});
		}

		// Prepare Demo button
		View prepareDemoButton = contentView.findViewById(R.id.prepare_demo_button);
		if (prepareDemoButton != null) {
			prepareDemoButton.setOnClickListener(v -> showPrepareDemoConfirmation());
		}

		// Show current selection
		updateSelection();
	}

	// ==================== Phase 15: Duress PIN UI ====================

	private void setupPinSection(@NonNull View contentView) {
		View setRealPinBtn = contentView.findViewById(R.id.set_real_pin_button);
		View setDuressPinBtn = contentView.findViewById(R.id.set_duress_pin_button);
		View clearPinsBtn = contentView.findViewById(R.id.clear_pins_button);
		TextView wipeScopeText = contentView.findViewById(R.id.wipe_scope_text);

		if (setRealPinBtn != null) {
			setRealPinBtn.setOnClickListener(v -> showPinSetup(PinEntryDialog.PinMode.SET_REAL_PIN));
		}
		if (setDuressPinBtn != null) {
			setDuressPinBtn.setOnClickListener(v -> showPinSetup(PinEntryDialog.PinMode.SET_DURESS_PIN));
			// Only show duress PIN button if real PIN is set
			updateDuressButtonVisibility(setDuressPinBtn);
		}
		if (clearPinsBtn != null) {
			clearPinsBtn.setOnClickListener(v -> showClearPinsConfirmation());
			// Only show clear button if real PIN is set
			updateClearButtonVisibility(clearPinsBtn);
		}
		if (wipeScopeText != null) {
			updateWipeScopeText(wipeScopeText);
			wipeScopeText.setOnClickListener(v -> cycleWipeScope(wipeScopeText));
		}
	}

	private void showPinSetup(@NonNull PinEntryDialog.PinMode mode) {
		FragmentActivity activity = getActivity();
		if (activity == null) return;

		PinEntryDialog.showInstance(
				activity.getSupportFragmentManager(),
				mode,
				new PinEntryDialog.OnPinEnteredListener() {
					@Override
					public void onPinResult(@NonNull DuressManager.PinResult result) {}

					@Override
					public void onPinSet(@NonNull PinEntryDialog.PinMode setMode) {
						OsmandApplication app = getMyApplication();
						if (app != null) {
							app.showShortToastMessage(R.string.pin_set_success);
						}
						// Refresh button visibility
						View contentView = getView();
						if (contentView != null) {
							View duressPinBtn = contentView.findViewById(R.id.set_duress_pin_button);
							View clearPinsBtn = contentView.findViewById(R.id.clear_pins_button);
							if (duressPinBtn != null) updateDuressButtonVisibility(duressPinBtn);
							if (clearPinsBtn != null) updateClearButtonVisibility(clearPinsBtn);
						}
					}

					@Override
					public void onPinCancelled() {}
				}
		);
	}

	private void showClearPinsConfirmation() {
		if (getContext() == null) return;
		new AlertDialog.Builder(getContext())
				.setTitle(R.string.clear_pins)
				.setMessage(R.string.clear_pins_confirm)
				.setPositiveButton(R.string.clear_pins, (dialog, which) -> {
					OsmandApplication app = getMyApplication();
					if (app != null) {
						app.getSecurityManager().getDuressManager().clearPins();
						app.showShortToastMessage(R.string.pins_cleared);
						// Refresh button visibility
						View contentView = getView();
						if (contentView != null) {
							View duressPinBtn = contentView.findViewById(R.id.set_duress_pin_button);
							View clearPinsBtn = contentView.findViewById(R.id.clear_pins_button);
							if (duressPinBtn != null) updateDuressButtonVisibility(duressPinBtn);
							if (clearPinsBtn != null) updateClearButtonVisibility(clearPinsBtn);
						}
					}
				})
				.setNegativeButton(R.string.shared_string_cancel, null)
				.show();
	}

	private void updateDuressButtonVisibility(@NonNull View button) {
		OsmandApplication app = getMyApplication();
		if (app != null) {
			boolean realPinSet = app.getSecurityManager().getDuressManager().isRealPinSet();
			button.setVisibility(realPinSet ? View.VISIBLE : View.GONE);
		}
	}

	private void updateClearButtonVisibility(@NonNull View button) {
		OsmandApplication app = getMyApplication();
		if (app != null) {
			boolean realPinSet = app.getSecurityManager().getDuressManager().isRealPinSet();
			button.setVisibility(realPinSet ? View.VISIBLE : View.GONE);
		}
	}

	private void updateWipeScopeText(@NonNull TextView textView) {
		OsmandApplication app = getMyApplication();
		if (app == null) return;
		DuressManager.DuressWipeScope scope = app.getSecurityManager().getDuressManager().getWipeScope();
		switch (scope) {
			case CHAT_ONLY:
				textView.setText(R.string.wipe_chat_only);
				break;
			case CHAT_AND_MODELS:
				textView.setText(R.string.wipe_chat_and_models);
				break;
			case EVERYTHING:
				textView.setText(R.string.wipe_everything);
				break;
		}
	}

	private void cycleWipeScope(@NonNull TextView textView) {
		OsmandApplication app = getMyApplication();
		if (app == null) return;
		DuressManager dm = app.getSecurityManager().getDuressManager();
		DuressManager.DuressWipeScope current = dm.getWipeScope();
		DuressManager.DuressWipeScope next;
		switch (current) {
			case CHAT_ONLY:
				next = DuressManager.DuressWipeScope.CHAT_AND_MODELS;
				break;
			case CHAT_AND_MODELS:
				next = DuressManager.DuressWipeScope.EVERYTHING;
				break;
			default:
				next = DuressManager.DuressWipeScope.CHAT_ONLY;
				break;
		}
		dm.setWipeScope(next);
		updateWipeScopeText(textView);
	}

	// ==================== Phase 15 Session 2: Stealth Mode UI ====================

	private void setupStealthSection(@NonNull View contentView) {
		View stealthSettingsBtn = contentView.findViewById(R.id.stealth_settings_button);
		TextView stealthStatusText = contentView.findViewById(R.id.stealth_status_indicator);

		if (stealthStatusText != null) {
			updateStealthStatus(stealthStatusText);
		}

		if (stealthSettingsBtn != null) {
			stealthSettingsBtn.setOnClickListener(v -> {
				FragmentActivity activity = getActivity();
				if (activity == null) return;
				StealthSettingsDialog.showInstance(
						activity.getSupportFragmentManager(),
						enabled -> {
							// Refresh status on stealth state change
							View cv = getView();
							if (cv != null) {
								TextView statusText = cv.findViewById(R.id.stealth_status_indicator);
								if (statusText != null) updateStealthStatus(statusText);
							}
						}
				);
			});
		}
	}

	private void updateStealthStatus(@NonNull TextView textView) {
		OsmandApplication app = getMyApplication();
		if (app == null) return;
		boolean enabled = app.getSecurityManager().getStealthManager().isStealthEnabled();
		textView.setText(enabled ? R.string.stealth_active : R.string.stealth_inactive);
	}

	private void showPanicWipeConfirmation() {
		if (getContext() == null) return;
		new AlertDialog.Builder(getContext())
				.setTitle(R.string.rushlight_panic_wipe_confirm_title)
				.setMessage(R.string.rushlight_panic_wipe_confirm_message)
				.setPositiveButton(R.string.rushlight_panic_wipe, (dialog, which) -> {
					OsmandApplication app = getMyApplication();
					if (app != null) {
						SecurityManager secMgr = app.getSecurityManager();
						secMgr.executePanicWipe();
						app.showShortToastMessage(R.string.rushlight_panic_wipe_success);
					}
				})
				.setNegativeButton(R.string.shared_string_cancel, null)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.show();
	}

	private void showPrepareDemoConfirmation() {
		FragmentActivity activity = getActivity();
		if (activity == null) return;
		DemoPreflightDialog.showInstance(activity.getSupportFragmentManager());
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
