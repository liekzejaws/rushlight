package net.osmand.plus.security;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.settings.backend.OsmandSettings;

import org.apache.commons.logging.Log;

/**
 * Rushlight: Manages biometric authentication for panel access.
 * When enabled, requires biometric auth before opening any panel tab.
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
	 * If lock is disabled, calls onSuccess immediately.
	 * If biometric is unavailable, bypasses with warning.
	 *
	 * @param activity  The host activity (must be FragmentActivity for BiometricPrompt)
	 * @param onSuccess Called when authentication succeeds or lock is disabled
	 * @param onFailure Called when authentication is denied or cancelled
	 */
	public void authenticate(@NonNull FragmentActivity activity,
	                         @NonNull Runnable onSuccess,
	                         @NonNull Runnable onFailure) {
		if (!isLockEnabled()) {
			onSuccess.run();
			return;
		}

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
