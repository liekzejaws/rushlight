package net.osmand.plus.security;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.settings.backend.OsmandSettings;

import org.apache.commons.logging.Log;

/**
 * Rushlight: Manages authentication for panel access.
 * Phase 15: Auth chain is PIN (if set) → Biometric (if enabled) → Unlock.
 *
 * When a real PIN is configured, the PIN dialog appears first. If the duress PIN
 * is entered, data is silently wiped and the app appears to unlock normally.
 */
public class PanelLockManager {

	private static final Log LOG = PlatformUtil.getLog(PanelLockManager.class);

	private final OsmandSettings settings;

	public PanelLockManager(@NonNull OsmandSettings settings) {
		this.settings = settings;
	}

	/**
	 * Check if screen lock is enabled in settings.
	 */
	public boolean isLockEnabled() {
		return settings.LAMPP_SCREEN_LOCK_ENABLED.get();
	}

	/**
	 * Authenticate the user before allowing panel access.
	 * Phase 15 auth chain: PIN → Biometric → Unlock.
	 *
	 * If lock is disabled AND no PIN set, calls onSuccess immediately.
	 * If PIN is set, shows PIN dialog first. On DURESS, silently wipes then succeeds.
	 * After PIN (or if no PIN), proceeds to biometric if enabled.
	 *
	 * @param activity  The host activity (must be FragmentActivity for BiometricPrompt)
	 * @param onSuccess Called when authentication succeeds or lock is disabled
	 * @param onFailure Called when authentication is denied or cancelled
	 */
	public void authenticate(@NonNull FragmentActivity activity,
	                         @NonNull Runnable onSuccess,
	                         @NonNull Runnable onFailure) {
		// Check if PIN is configured
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		DuressManager duressManager = app.getSecurityManager().getDuressManager();

		if (duressManager.isRealPinSet()) {
			// PIN is set — show PIN dialog first
			showPinDialog(activity, duressManager, onSuccess, onFailure);
		} else if (!isLockEnabled()) {
			// No PIN, no lock — proceed immediately
			onSuccess.run();
		} else {
			// No PIN, but biometric lock enabled
			proceedToBiometric(activity, onSuccess, onFailure);
		}
	}

	/**
	 * Show the PIN entry dialog. On success, proceed to biometric (if enabled).
	 */
	private void showPinDialog(@NonNull FragmentActivity activity,
	                           @NonNull DuressManager duressManager,
	                           @NonNull Runnable onSuccess,
	                           @NonNull Runnable onFailure) {
		PinEntryDialog.showInstance(
				activity.getSupportFragmentManager(),
				PinEntryDialog.PinMode.AUTHENTICATE,
				new PinEntryDialog.OnPinEnteredListener() {
					@Override
					public void onPinResult(@NonNull DuressManager.PinResult result) {
						switch (result) {
							case DURESS:
								// Silent wipe + appear to unlock normally
								LOG.warn("DURESS PIN entered — executing silent wipe");
								duressManager.executeDuressWipe();
								onSuccess.run();
								break;
							case REAL:
								// Correct PIN — proceed to biometric if enabled
								if (isLockEnabled()) {
									proceedToBiometric(activity, onSuccess, onFailure);
								} else {
									onSuccess.run();
								}
								break;
							case INVALID:
							default:
								// Invalid — PinEntryDialog handles retry internally
								break;
						}
					}

					@Override
					public void onPinCancelled() {
						onFailure.run();
					}
				}
		);
	}

	/**
	 * Proceed to biometric authentication (existing flow).
	 */
	private void proceedToBiometric(@NonNull FragmentActivity activity,
	                                @NonNull Runnable onSuccess,
	                                @NonNull Runnable onFailure) {
		if (!BiometricHelper.isBiometricAvailable(activity)) {
			LOG.warn("Screen lock enabled but biometric unavailable, bypassing");
			onSuccess.run();
			return;
		}

		BiometricPrompt biometricPrompt = new BiometricPrompt(activity,
				ContextCompat.getMainExecutor(activity),
				new BiometricPrompt.AuthenticationCallback() {
					@Override
					public void onAuthenticationSucceeded(
							@NonNull BiometricPrompt.AuthenticationResult result) {
						LOG.info("Biometric authentication succeeded");
						onSuccess.run();
					}

					@Override
					public void onAuthenticationFailed() {
						LOG.info("Biometric authentication failed");
						// Don't call onFailure here - this fires on each failed attempt.
						// BiometricPrompt handles retry UI internally.
					}

					@Override
					public void onAuthenticationError(int errorCode,
					                                  @NonNull CharSequence errString) {
						LOG.info("Biometric auth error " + errorCode + ": " + errString);
						onFailure.run();
					}
				});

		BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
				.setTitle(activity.getString(R.string.rushlight_unlock_panel))
				.setSubtitle(activity.getString(R.string.rushlight_auth_subtitle))
				.setNegativeButtonText(activity.getString(R.string.shared_string_cancel))
				.build();

		biometricPrompt.authenticate(promptInfo);
	}
}
