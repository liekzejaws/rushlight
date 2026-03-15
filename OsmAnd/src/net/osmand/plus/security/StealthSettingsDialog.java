/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.security;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;

/**
 * Phase 15: Settings dialog for stealth mode configuration.
 * Allows enabling/disabling stealth mode and configuring the dialer code.
 */
public class StealthSettingsDialog extends DialogFragment {

	public static final String TAG = "StealthSettingsDialog";

	/** Callback for stealth state changes */
	public interface OnStealthChangedListener {
		void onStealthStateChanged(boolean enabled);
	}

	@Nullable
	private OnStealthChangedListener listener;

	public void setListener(@Nullable OnStealthChangedListener listener) {
		this.listener = listener;
	}

	public static void showInstance(@NonNull FragmentManager fm,
	                                @Nullable OnStealthChangedListener listener) {
		if (fm.findFragmentByTag(TAG) != null) return;
		StealthSettingsDialog dialog = new StealthSettingsDialog();
		dialog.setListener(listener);
		dialog.show(fm, TAG);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, 0);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                          @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dialog_stealth_settings, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		OsmandApplication app = getApp();
		if (app == null) {
			dismiss();
			return;
		}

		StealthManager stealthManager = app.getSecurityManager().getStealthManager();

		// Views
		Button toggleButton = view.findViewById(R.id.stealth_toggle_button);
		EditText codeInput = view.findViewById(R.id.stealth_code_input);
		Button saveCodeButton = view.findViewById(R.id.stealth_save_code_button);
		Button testCodeButton = view.findViewById(R.id.stealth_test_code_button);
		TextView statusText = view.findViewById(R.id.stealth_status_text);
		TextView warningText = view.findViewById(R.id.stealth_warning_text);
		Button closeButton = view.findViewById(R.id.stealth_close_button);

		// Initialize state
		boolean isEnabled = stealthManager.isStealthEnabled();
		updateToggleButton(toggleButton, isEnabled);
		updateStatus(statusText, isEnabled);
		codeInput.setText(stealthManager.getDialerCode());

		// Toggle stealth mode
		if (toggleButton != null) {
			toggleButton.setOnClickListener(v -> {
				boolean currentlyEnabled = stealthManager.isStealthEnabled();
				if (currentlyEnabled) {
					// Disable — straightforward
					stealthManager.disableStealthMode();
					updateToggleButton(toggleButton, false);
					updateStatus(statusText, false);
					if (listener != null) {
						listener.onStealthStateChanged(false);
					}
				} else {
					// Enable — show warning first
					showEnableWarning(stealthManager, toggleButton, statusText);
				}
			});
		}

		// Save custom code
		if (saveCodeButton != null) {
			saveCodeButton.setOnClickListener(v -> {
				String code = codeInput != null ? codeInput.getText().toString().trim() : "";
				if (stealthManager.setDialerCode(code)) {
					if (warningText != null) {
						warningText.setVisibility(View.GONE);
					}
					app.showShortToastMessage(R.string.stealth_code_saved);
				} else {
					if (warningText != null) {
						warningText.setText(R.string.stealth_code_invalid);
						warningText.setVisibility(View.VISIBLE);
					}
				}
			});
		}

		// Test code — open phone dialer
		if (testCodeButton != null) {
			testCodeButton.setOnClickListener(v -> {
				String code = stealthManager.getDialerCode();
				Intent dialIntent = new Intent(Intent.ACTION_DIAL);
				dialIntent.setData(Uri.parse("tel:" + Uri.encode(code)));
				try {
					startActivity(dialIntent);
				} catch (Exception e) {
					app.showShortToastMessage(R.string.stealth_no_dialer);
				}
			});
		}

		// Close button
		if (closeButton != null) {
			closeButton.setOnClickListener(v -> dismiss());
		}
	}

	private void showEnableWarning(@NonNull StealthManager stealthManager,
	                               @NonNull Button toggleButton,
	                               @Nullable TextView statusText) {
		if (getContext() == null) return;
		String code = stealthManager.getDialerCode();
		new AlertDialog.Builder(getContext())
				.setTitle(R.string.stealth_mode)
				.setMessage(getString(R.string.stealth_enable_warning, code))
				.setPositiveButton(R.string.stealth_enable, (dialog, which) -> {
					stealthManager.enableStealthMode();
					updateToggleButton(toggleButton, true);
					updateStatus(statusText, true);
					if (listener != null) {
						listener.onStealthStateChanged(true);
					}
				})
				.setNegativeButton(R.string.shared_string_cancel, null)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.show();
	}

	private void updateToggleButton(@Nullable Button button, boolean isEnabled) {
		if (button == null) return;
		button.setText(isEnabled ? R.string.stealth_deactivate : R.string.stealth_enable);
	}

	private void updateStatus(@Nullable TextView text, boolean isEnabled) {
		if (text == null) return;
		text.setText(isEnabled ? R.string.stealth_active : R.string.stealth_inactive);
	}

	@Nullable
	private OsmandApplication getApp() {
		return getActivity() != null
				? (OsmandApplication) getActivity().getApplication()
				: null;
	}
}
