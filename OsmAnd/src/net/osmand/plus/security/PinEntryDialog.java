/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.security;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.R;

/**
 * Phase 15: Full-screen PIN entry dialog with 4-digit numeric keypad.
 * Supports multiple modes: setting PINs (with confirm), and authentication.
 */
public class PinEntryDialog extends DialogFragment {

	public static final String TAG = "PinEntryDialog";

	/** Operating mode for the PIN dialog */
	public enum PinMode {
		/** Setting the real (unlock) PIN — requires double-entry */
		SET_REAL_PIN,
		/** Setting the duress PIN — requires double-entry */
		SET_DURESS_PIN,
		/** Authenticating — single entry, returns PinResult */
		AUTHENTICATE
	}

	/** Callback for PIN entry results */
	public interface OnPinEnteredListener {
		/** Called when authentication completes */
		void onPinResult(@NonNull DuressManager.PinResult result);
		/** Called when a new PIN is set successfully */
		default void onPinSet(@NonNull PinMode mode) {}
		/** Called when the dialog is cancelled */
		default void onPinCancelled() {}
	}

	private PinMode mode = PinMode.AUTHENTICATE;
	private StringBuilder currentPin = new StringBuilder();
	private String firstEntry = null; // For confirm step in SET modes
	private boolean isConfirmStep = false;

	// Rate limiting (Phase 16): exponential backoff after failed attempts
	public static final int MAX_ATTEMPTS_BEFORE_LOCKOUT = 3;
	public static final long[] LOCKOUT_DURATIONS_MS = {30_000, 60_000, 300_000, 900_000};
	private int failedAttempts = 0;
	private long lockoutUntil = 0;
	@Nullable
	private CountDownTimer lockoutTimer;

	@Nullable
	private OnPinEnteredListener listener;

	// Views
	private ImageView[] dots = new ImageView[4];
	private TextView titleText;
	private TextView statusText;

	public static PinEntryDialog newInstance(@NonNull PinMode mode) {
		PinEntryDialog dialog = new PinEntryDialog();
		Bundle args = new Bundle();
		args.putString("mode", mode.name());
		dialog.setArguments(args);
		return dialog;
	}

	public void setListener(@Nullable OnPinEnteredListener listener) {
		this.listener = listener;
	}

	public static void showInstance(@NonNull FragmentManager fm, @NonNull PinMode mode,
	                                @Nullable OnPinEnteredListener listener) {
		if (fm.findFragmentByTag(TAG) != null) return;
		PinEntryDialog dialog = newInstance(mode);
		dialog.setListener(listener);
		dialog.setCancelable(false); // Prevent dismissal without PIN
		dialog.show(fm, TAG);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, 0);
		if (getArguments() != null) {
			try {
				mode = PinMode.valueOf(getArguments().getString("mode", "AUTHENTICATE"));
			} catch (IllegalArgumentException e) {
				mode = PinMode.AUTHENTICATE;
			}
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                          @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dialog_pin_entry, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		titleText = view.findViewById(R.id.pin_title);
		statusText = view.findViewById(R.id.pin_status);
		dots[0] = view.findViewById(R.id.pin_dot_1);
		dots[1] = view.findViewById(R.id.pin_dot_2);
		dots[2] = view.findViewById(R.id.pin_dot_3);
		dots[3] = view.findViewById(R.id.pin_dot_4);

		updateTitle();

		// Setup number keys
		int[] keyIds = {R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
				R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9};
		for (int i = 0; i < keyIds.length; i++) {
			final int digit = i;
			Button key = view.findViewById(keyIds[i]);
			if (key != null) {
				key.setOnClickListener(v -> onDigitPressed(digit));
			}
		}

		// Backspace
		ImageButton backspace = view.findViewById(R.id.key_backspace);
		if (backspace != null) {
			backspace.setOnClickListener(v -> onBackspacePressed());
		}

		// Cancel
		Button cancel = view.findViewById(R.id.key_cancel);
		if (cancel != null) {
			cancel.setOnClickListener(v -> {
				if (listener != null) {
					listener.onPinCancelled();
				}
				dismiss();
			});
		}
	}

	private void onDigitPressed(int digit) {
		if (currentPin.length() >= 4) return;

		currentPin.append(digit);
		updateDots();
		clearStatus();

		if (currentPin.length() == 4) {
			// Auto-submit after 4th digit
			View v = getView();
			if (v != null) {
				v.postDelayed(this::handlePinComplete, 150);
			}
		}
	}

	private void onBackspacePressed() {
		if (currentPin.length() > 0) {
			currentPin.deleteCharAt(currentPin.length() - 1);
			updateDots();
			clearStatus();
		}
	}

	private void handlePinComplete() {
		String pin = currentPin.toString();
		currentPin.setLength(0);
		updateDots();

		if (mode == PinMode.AUTHENTICATE) {
			handleAuthentication(pin);
		} else {
			handlePinSetup(pin);
		}
	}

	private void handleAuthentication(@NonNull String pin) {
		// Phase 16: Check lockout before validating
		if (isLockedOut()) {
			long remainingSec = (lockoutUntil - System.currentTimeMillis()) / 1000;
			showStatus(getString(R.string.pin_lockout, remainingSec));
			return;
		}

		if (listener != null) {
			if (getActivity() != null) {
				net.osmand.plus.OsmandApplication app =
						(net.osmand.plus.OsmandApplication) getActivity().getApplication();
				DuressManager duressManager = app.getSecurityManager().getDuressManager();
				DuressManager.PinResult result = duressManager.validatePin(pin);

				if (result == DuressManager.PinResult.INVALID) {
					failedAttempts++;
					if (failedAttempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT
							&& failedAttempts % MAX_ATTEMPTS_BEFORE_LOCKOUT == 0) {
						applyLockout();
					} else {
						showStatus(getString(R.string.pin_invalid));
					}
					return; // Don't dismiss — let user retry
				}

				// Successful auth — reset failed attempts
				failedAttempts = 0;
				cancelLockoutTimer();
				listener.onPinResult(result);
				dismiss();
			}
		}
	}

	/**
	 * Phase 16: Check if the dialog is currently in lockout state.
	 */
	private boolean isLockedOut() {
		return System.currentTimeMillis() < lockoutUntil;
	}

	/**
	 * Phase 16: Apply exponential backoff lockout after repeated failures.
	 * Lockout duration increases every MAX_ATTEMPTS_BEFORE_LOCKOUT failures.
	 */
	private void applyLockout() {
		int lockoutIndex = (failedAttempts / MAX_ATTEMPTS_BEFORE_LOCKOUT) - 1;
		lockoutIndex = Math.min(lockoutIndex, LOCKOUT_DURATIONS_MS.length - 1);
		long duration = LOCKOUT_DURATIONS_MS[lockoutIndex];
		lockoutUntil = System.currentTimeMillis() + duration;

		// Start countdown timer to update status display
		cancelLockoutTimer();
		lockoutTimer = new CountDownTimer(duration, 1000) {
			@Override
			public void onTick(long millisUntilFinished) {
				long sec = millisUntilFinished / 1000;
				showStatus(getString(R.string.pin_lockout, sec));
			}

			@Override
			public void onFinish() {
				clearStatus();
			}
		}.start();
	}

	/**
	 * Phase 16: Cancel any active lockout countdown timer.
	 */
	private void cancelLockoutTimer() {
		if (lockoutTimer != null) {
			lockoutTimer.cancel();
			lockoutTimer = null;
		}
	}

	private void handlePinSetup(@NonNull String pin) {
		if (!isConfirmStep) {
			// First entry — ask for confirmation
			firstEntry = pin;
			isConfirmStep = true;
			updateTitle();
			return;
		}

		// Confirm step — check match
		if (!pin.equals(firstEntry)) {
			showStatus(getString(R.string.pins_dont_match));
			isConfirmStep = false;
			firstEntry = null;
			updateTitle();
			return;
		}

		// PINs match — save
		if (getActivity() != null) {
			net.osmand.plus.OsmandApplication app =
					(net.osmand.plus.OsmandApplication) getActivity().getApplication();
			DuressManager duressManager = app.getSecurityManager().getDuressManager();

			boolean success;
			if (mode == PinMode.SET_REAL_PIN) {
				success = duressManager.setRealPin(pin);
			} else {
				success = duressManager.setDuressPin(pin);
				if (!success) {
					showStatus(getString(R.string.duress_pin_same_as_real));
					isConfirmStep = false;
					firstEntry = null;
					updateTitle();
					return;
				}
			}

			if (success && listener != null) {
				listener.onPinSet(mode);
			}
			dismiss();
		}
	}

	private void updateTitle() {
		if (titleText == null) return;
		if (isConfirmStep) {
			titleText.setText(R.string.confirm_pin);
		} else {
			switch (mode) {
				case SET_REAL_PIN:
					titleText.setText(R.string.set_unlock_pin);
					break;
				case SET_DURESS_PIN:
					titleText.setText(R.string.set_duress_pin);
					break;
				case AUTHENTICATE:
				default:
					titleText.setText(R.string.enter_pin);
					break;
			}
		}
	}

	private void updateDots() {
		if (getContext() == null) return;
		int filled = currentPin.length();
		int activeColor = ContextCompat.getColor(getContext(), R.color.lampp_green_primary);
		int inactiveColor = ContextCompat.getColor(getContext(), R.color.lampp_text_secondary);
		for (int i = 0; i < 4; i++) {
			if (dots[i] != null) {
				dots[i].setColorFilter(i < filled ? activeColor : inactiveColor);
			}
		}
	}

	private void showStatus(@NonNull String message) {
		if (statusText != null) {
			statusText.setText(message);
			statusText.setVisibility(View.VISIBLE);
		}
	}

	private void clearStatus() {
		if (statusText != null) {
			statusText.setVisibility(View.GONE);
		}
	}

	@NonNull
	private String getStringRes(int resId) {
		return getContext() != null ? getContext().getString(resId) : "";
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		cancelLockoutTimer();
	}
}
