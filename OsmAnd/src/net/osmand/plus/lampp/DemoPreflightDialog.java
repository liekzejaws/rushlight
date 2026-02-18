package net.osmand.plus.lampp;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.ai.LlmManager;
import net.osmand.plus.lampp.DemoPreflightChecker.CheckResult;
import net.osmand.plus.lampp.DemoPreflightChecker.CheckStatus;
import net.osmand.plus.lampp.DemoPreflightChecker.PreflightReport;
import net.osmand.plus.security.SecurityManager;

import org.apache.commons.logging.Log;

/**
 * Dialog that displays demo pre-flight readiness checklist.
 * Shows status of all Rushlight subsystems with fix actions where available.
 * When "Start Demo" is tapped, executes full demo preparation sequence.
 */
public class DemoPreflightDialog extends DialogFragment {

	private static final Log LOG = PlatformUtil.getLog(DemoPreflightDialog.class);
	private static final String TAG = "DemoPreflightDialog";

	private LinearLayout checksContainer;
	private TextView summaryText;
	private MaterialButton startDemoButton;
	private MaterialButton recheckButton;

	private final DemoPreflightChecker checker = new DemoPreflightChecker();
	private PreflightReport currentReport;

	public static void showInstance(@NonNull FragmentManager fm) {
		if (fm.findFragmentByTag(TAG) != null) return;
		new DemoPreflightDialog().show(fm, TAG);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(STYLE_NO_TITLE, R.style.OsmandDarkTheme);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.dialog_demo_preflight, container, false);

		checksContainer = view.findViewById(R.id.checks_container);
		summaryText = view.findViewById(R.id.preflight_summary);
		startDemoButton = view.findViewById(R.id.start_demo_button);
		recheckButton = view.findViewById(R.id.recheck_button);

		recheckButton.setOnClickListener(v -> runChecks());
		startDemoButton.setOnClickListener(v -> executeDemoPrep());

		runChecks();
		return view;
	}

	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null && dialog.getWindow() != null) {
			Window window = dialog.getWindow();
			window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			window.setGravity(Gravity.CENTER);
		}
	}

	private void runChecks() {
		OsmandApplication app = getOsmandApp();
		if (app == null) return;

		checksContainer.removeAllViews();
		currentReport = checker.runChecks(app);

		for (CheckResult check : currentReport.checks) {
			addCheckRow(check);
		}

		updateSummary();
	}

	private void addCheckRow(@NonNull CheckResult check) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(0, dpToPx(6), 0, dpToPx(6));

		// Status icon
		ImageView icon = new ImageView(requireContext());
		int iconSize = dpToPx(20);
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
		iconParams.setMarginEnd(dpToPx(10));
		icon.setLayoutParams(iconParams);

		switch (check.status) {
			case PASS:
				icon.setImageResource(R.drawable.ic_action_done);
				icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.lampp_green_primary));
				break;
			case WARN:
				icon.setImageResource(R.drawable.ic_action_info_outlined);
				icon.setColorFilter(Color.parseColor("#FFA000"));
				break;
			case FAIL:
				icon.setImageResource(R.drawable.ic_action_alert);
				icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.lampp_accent_red));
				break;
		}
		row.addView(icon);

		// Text container
		LinearLayout textContainer = new LinearLayout(requireContext());
		textContainer.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
				0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textContainer.setLayoutParams(textParams);

		// Check name
		TextView nameView = new TextView(requireContext());
		nameView.setText(check.name);
		nameView.setTextSize(14);
		nameView.setTextColor(ContextCompat.getColor(requireContext(), R.color.lampp_green_primary));
		textContainer.addView(nameView);

		// Check message
		TextView msgView = new TextView(requireContext());
		msgView.setText(check.message);
		msgView.setTextSize(12);
		msgView.setTextColor(ContextCompat.getColor(requireContext(), R.color.lampp_text_secondary));
		textContainer.addView(msgView);

		row.addView(textContainer);

		// Fix button if available
		if (check.fixAction != null) {
			MaterialButton fixBtn = new MaterialButton(requireContext(),
					null, com.google.android.material.R.attr.borderlessButtonStyle);
			fixBtn.setText(R.string.demo_preflight_fix);
			fixBtn.setTextSize(12);
			fixBtn.setAllCaps(false);
			fixBtn.setMinWidth(0);
			fixBtn.setMinimumWidth(0);
			fixBtn.setMinHeight(0);
			fixBtn.setMinimumHeight(0);
			fixBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
			fixBtn.setOnClickListener(v -> {
				check.fixAction.run();
				// Re-run checks after fix
				runChecks();
			});
			row.addView(fixBtn);
		}

		// Fix button for model loading (special case — needs callback)
		if (check.name.equals(getString(R.string.demo_preflight_check_model))
				&& check.status == CheckStatus.FAIL
				&& check.fixAction == null) {
			OsmandApplication app = getOsmandApp();
			if (app != null) {
				LlmManager llm = new LlmManager(app);
				if (llm.hasDownloadedModels()) {
					MaterialButton fixBtn = new MaterialButton(requireContext(),
							null, com.google.android.material.R.attr.borderlessButtonStyle);
					fixBtn.setText(R.string.lampp_model_load);
					fixBtn.setTextSize(12);
					fixBtn.setAllCaps(false);
					fixBtn.setMinWidth(0);
					fixBtn.setMinimumWidth(0);
					fixBtn.setMinHeight(0);
					fixBtn.setMinimumHeight(0);
					fixBtn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
					fixBtn.setOnClickListener(v -> {
						fixBtn.setEnabled(false);
						fixBtn.setText(R.string.lampp_model_status_loading);
						llm.preWarmIfNeeded(new LlmManager.ModelLoadCallback() {
							@Override
							public void onLoadStarted() {
								// Already showing loading text
							}

							@Override
							public void onLoadComplete(String modelName) {
								if (isAdded()) {
									runChecks();
								}
							}

							@Override
							public void onLoadError(String error) {
								if (isAdded()) {
									runChecks();
								}
							}
						});
					});
					row.addView(fixBtn);
				}
			}
		}

		checksContainer.addView(row);
	}

	private void updateSummary() {
		if (currentReport == null) return;

		if (currentReport.allCriticalPassed) {
			summaryText.setText(R.string.demo_preflight_all_passed);
			summaryText.setTextColor(ContextCompat.getColor(requireContext(), R.color.lampp_green_primary));
			startDemoButton.setEnabled(true);
		} else {
			summaryText.setText(R.string.demo_preflight_critical_failed);
			summaryText.setTextColor(ContextCompat.getColor(requireContext(), R.color.lampp_accent_red));
			startDemoButton.setEnabled(false);
		}
	}

	private void executeDemoPrep() {
		OsmandApplication app = getOsmandApp();
		MapActivity mapActivity = getMapActivity();
		if (app == null) return;

		// 1. Clear chat history
		try {
			SecurityManager secMgr = app.getSecurityManager();
			net.osmand.plus.security.EncryptedChatStorage chatStorage = secMgr.getChatStorage();
			if (chatStorage != null) {
				chatStorage.clearMessages();
			}
		} catch (Exception e) {
			LOG.warn("Failed to clear chat: " + e.getMessage());
		}

		// 2. Reset onboarding shown flag
		app.getSettings().LAMPP_ONBOARDING_SHOWN.set(false);

		// 3. Set Pip-Boy theme
		app.getSettings().LAMPP_STYLE_PRESET.set(LamppStylePreset.PIP_BOY);
		if (mapActivity != null) {
			mapActivity.getLamppPanelManager().refreshTheme();
		}

		// 4. Close active panel
		if (mapActivity != null) {
			mapActivity.getLamppPanelManager().closeActivePanel(false);
		}

		// 5. Pre-warm model if available
		LlmManager llm = new LlmManager(app);
		llm.preWarmIfNeeded(null);

		// 6. Show success toast
		Toast.makeText(requireContext(),
				R.string.demo_ready_toast, Toast.LENGTH_SHORT).show();

		dismiss();
	}

	@Nullable
	private OsmandApplication getOsmandApp() {
		if (getActivity() != null && getActivity().getApplication() instanceof OsmandApplication) {
			return (OsmandApplication) getActivity().getApplication();
		}
		return null;
	}

	@Nullable
	private MapActivity getMapActivity() {
		if (getActivity() instanceof MapActivity) {
			return (MapActivity) getActivity();
		}
		return null;
	}

	private int dpToPx(int dp) {
		return (int) (dp * requireContext().getResources().getDisplayMetrics().density);
	}
}
