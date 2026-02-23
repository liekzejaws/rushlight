package net.osmand.plus.lampp;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.card.MaterialCardView;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.ai.BenchmarkResult;
import net.osmand.plus.ai.DeviceCapabilityDetector;
import net.osmand.plus.ai.LlmManager;
import net.osmand.plus.ai.PerformanceBenchmark;
import net.osmand.plus.ai.rag.RagManager;
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

		// v0.8: Run Benchmark button
		View benchmarkButton = contentView.findViewById(R.id.run_benchmark_button);
		if (benchmarkButton != null) {
			benchmarkButton.setOnClickListener(v -> showBenchmarkConfirmation());
		}

		// v1.4: Device Report button
		View deviceReportButton = contentView.findViewById(R.id.device_report_button);
		if (deviceReportButton != null) {
			deviceReportButton.setOnClickListener(v -> showDeviceReport());
		}

		// Prepare Demo button
		View prepareDemoButton = contentView.findViewById(R.id.prepare_demo_button);
		if (prepareDemoButton != null) {
			prepareDemoButton.setOnClickListener(v -> showPrepareDemoConfirmation());
		}

		// v1.0: FieldNotes section
		setupFieldNotesSection(contentView);

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

	// ==================== v0.8: Performance Benchmark ====================

	private void showBenchmarkConfirmation() {
		OsmandApplication app = getMyApplication();
		if (app == null) return;

		// Check if AI features are available
		if (!LlmManager.isAiAvailable()) {
			Toast.makeText(getContext(), "AI features require Android 11 or higher", Toast.LENGTH_SHORT).show();
			return;
		}

		// Create a temporary LlmManager to check model status
		LlmManager tempLlm = new LlmManager(app);
		if (!tempLlm.hasDownloadedModels()) {
			Toast.makeText(getContext(), "Download an AI model first", Toast.LENGTH_SHORT).show();
			tempLlm.close();
			return;
		}

		new AlertDialog.Builder(getContext())
				.setTitle("Run Benchmark")
				.setMessage("This will send 4 test queries to the AI model and measure performance against all 4 OTF grant targets:\n\n• AI query time (<3s)\n• Peak RAM (<500MB)\n• Battery drain (<15%/hr)\n• Cold start time (<5s)\n\nThis takes approximately 30-60 seconds. Continue?")
				.setPositiveButton("Run", (dialog, which) -> runBenchmark(app))
				.setNegativeButton(R.string.shared_string_cancel, null)
				.show();
	}

	private void runBenchmark(@NonNull OsmandApplication app) {
		ProgressDialog progressDialog = new ProgressDialog(getContext());
		progressDialog.setTitle("Running Benchmark");
		progressDialog.setMessage("Loading model...");
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setMax(4);
		progressDialog.setProgress(0);
		progressDialog.setCancelable(false);
		progressDialog.show();

		LlmManager llmManager = new LlmManager(app);
		RagManager ragManager = new RagManager(app, llmManager);

		// Load the first available model, then run benchmark
		java.io.File[] models = llmManager.getDownloadedModels();
		llmManager.loadModel(models[0], new LlmManager.ModelLoadCallback() {
			@Override
			public void onLoadStarted() {
				progressDialog.setMessage("Loading model: " + models[0].getName() + "...");
			}

			@Override
			public void onLoadComplete(String modelName) {
				progressDialog.setMessage("Running queries...");
				PerformanceBenchmark benchmark = new PerformanceBenchmark(app, ragManager, llmManager);
				benchmark.runAsync(new PerformanceBenchmark.BenchmarkCallback() {
					@Override
					public void onProgress(int current, int total, String queryText) {
						progressDialog.setProgress(current);
						progressDialog.setMessage("Query " + current + "/" + total + ":\n" + queryText);
					}

					@Override
					public void onComplete(java.util.List<BenchmarkResult> results, String summary) {
						progressDialog.dismiss();
						llmManager.close();
						showBenchmarkResults(summary);
					}

					@Override
					public void onError(String error) {
						progressDialog.dismiss();
						llmManager.close();
						Toast.makeText(getContext(), "Benchmark error: " + error, Toast.LENGTH_LONG).show();
					}
				});
			}

			@Override
			public void onLoadError(String error) {
				progressDialog.dismiss();
				llmManager.close();
				Toast.makeText(getContext(), "Failed to load model: " + error, Toast.LENGTH_LONG).show();
			}
		});
	}

	private void showBenchmarkResults(@NonNull String summary) {
		if (getContext() == null) return;
		OsmandApplication app = getMyApplication();
		new AlertDialog.Builder(getContext())
				.setTitle("Benchmark Results")
				.setMessage(summary)
				.setPositiveButton(R.string.shared_string_ok, null)
				.setNeutralButton("Share Report", (dialog, which) -> {
					// Generate a markdown report for grant documentation
					String markdownReport;
					if (app != null) {
						DeviceCapabilityDetector detector = new DeviceCapabilityDetector(app);
						markdownReport = PerformanceBenchmark.exportMarkdownReport(summary, detector);
					} else {
						markdownReport = summary;
					}
					Intent shareIntent = new Intent(Intent.ACTION_SEND);
					shareIntent.setType("text/plain");
					shareIntent.putExtra(Intent.EXTRA_TEXT, markdownReport);
					shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Rushlight Benchmark Report");
					startActivity(Intent.createChooser(shareIntent, "Share Report"));
				})
				.show();
	}

	private void showDeviceReport() {
		FragmentActivity activity = getActivity();
		if (activity == null) return;
		DeviceTestReportDialog.showInstance(activity.getSupportFragmentManager());
	}

	private void showPrepareDemoConfirmation() {
		FragmentActivity activity = getActivity();
		if (activity == null) return;
		DemoPreflightDialog.showInstance(activity.getSupportFragmentManager());
	}

	// ==================== v1.0: FieldNotes ====================

	private void setupFieldNotesSection(@NonNull View contentView) {
		OsmandApplication app = getMyApplication();
		if (app == null) return;

		// Update note count
		TextView countText = contentView.findViewById(R.id.fieldnotes_count_text);
		if (countText != null) {
			int count = app.getFieldNotesManager().getNoteCount();
			countText.setText(count == 0 ? "No notes" : count + " active note" + (count == 1 ? "" : "s"));
		}

		// View All button — show a simple list dialog of all FieldNotes
		View listButton = contentView.findViewById(R.id.fieldnotes_list_button);
		if (listButton != null) {
			listButton.setOnClickListener(v -> showFieldNotesList());
		}

		// Cleanup button
		View cleanupButton = contentView.findViewById(R.id.fieldnotes_cleanup_button);
		if (cleanupButton != null) {
			cleanupButton.setOnClickListener(v -> {
				if (app == null) return;
				int cleaned = app.getFieldNotesManager().cleanupExpired();
				Toast.makeText(getContext(),
						cleaned == 0 ? "No expired notes" : "Cleaned " + cleaned + " expired note(s)",
						Toast.LENGTH_SHORT).show();
				// Refresh count
				if (countText != null) {
					int count = app.getFieldNotesManager().getNoteCount();
					countText.setText(count == 0 ? "No notes" : count + " active note" + (count == 1 ? "" : "s"));
				}
			});
		}
	}

	private void showFieldNotesList() {
		OsmandApplication app = getMyApplication();
		if (app == null || getContext() == null) return;

		java.util.List<net.osmand.plus.fieldnotes.FieldNote> notes = app.getFieldNotesManager().getAllNotes();
		if (notes.isEmpty()) {
			Toast.makeText(getContext(), "No FieldNotes yet. Long-press the map to add one.", Toast.LENGTH_SHORT).show();
			return;
		}

		String[] items = new String[notes.size()];
		for (int i = 0; i < notes.size(); i++) {
			net.osmand.plus.fieldnotes.FieldNote note = notes.get(i);
			items[i] = note.getCategory().getDisplayName() + ": " + note.getTitle();
		}

		new AlertDialog.Builder(getContext())
				.setTitle("FieldNotes (" + notes.size() + ")")
				.setItems(items, (dialog, which) -> {
					// Show the selected note in the view dialog
					net.osmand.plus.fieldnotes.FieldNote selectedNote = notes.get(which);
					FragmentActivity activity = getActivity();
					if (activity != null) {
						net.osmand.plus.fieldnotes.ViewFieldNoteDialog viewDialog =
								net.osmand.plus.fieldnotes.ViewFieldNoteDialog.newInstance(selectedNote.getId());
						viewDialog.show(activity.getSupportFragmentManager(),
								net.osmand.plus.fieldnotes.ViewFieldNoteDialog.TAG);
					}
				})
				.setNegativeButton(R.string.shared_string_cancel, null)
				.show();
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
