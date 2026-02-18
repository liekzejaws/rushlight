package net.osmand.plus.security;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.settings.backend.OsmandSettings;

import org.apache.commons.logging.Log;

/**
 * Phase 15: Manages stealth mode — hides the app from the launcher.
 *
 * When stealth mode is enabled, the launcher activity-alias is disabled via
 * PackageManager, making the app invisible in the app drawer. The only way
 * to launch the app is via a secret dialer code (default: *#73784# = *#RUSH#).
 *
 * StealthDialerReceiver intercepts outgoing calls matching the code,
 * aborts the call, and launches MapActivity.
 */
public class StealthManager {

	private static final Log LOG = PlatformUtil.getLog(StealthManager.class);

	/** Default dialer code: *#RUSH# on a phone keypad (R=7, U=8, S=7, H=4) */
	public static final String DEFAULT_DIALER_CODE = "*#73784#";

	/** Min/max length for custom dialer codes */
	public static final int MIN_CODE_LENGTH = 6;
	public static final int MAX_CODE_LENGTH = 12;

	/**
	 * Component name for the launcher activity-alias defined in AndroidManifest.xml.
	 * This is the alias that gets enabled/disabled to show/hide from launcher.
	 */
	private static final String LAUNCHER_ALIAS_CLASS = "net.osmand.plus.activities.LauncherAlias";

	private final OsmandApplication app;
	private final OsmandSettings settings;

	public StealthManager(@NonNull OsmandApplication app) {
		this.app = app;
		this.settings = app.getSettings();
	}

	// ==================== Stealth Mode Control ====================

	/**
	 * Enable stealth mode — hide the app from the launcher.
	 * The launcher activity-alias is disabled, making the app invisible in the app drawer.
	 */
	public void enableStealthMode() {
		try {
			setLauncherAliasEnabled(false);
			settings.LAMPP_STEALTH_ENABLED.set(true);
			LOG.info("Stealth mode ENABLED — app hidden from launcher");
		} catch (Exception e) {
			LOG.error("Failed to enable stealth mode: " + e.getMessage());
		}
	}

	/**
	 * Disable stealth mode — restore the app in the launcher.
	 * Re-enables the launcher activity-alias.
	 */
	public void disableStealthMode() {
		try {
			setLauncherAliasEnabled(true);
			settings.LAMPP_STEALTH_ENABLED.set(false);
			LOG.info("Stealth mode DISABLED — app visible in launcher");
		} catch (Exception e) {
			LOG.error("Failed to disable stealth mode: " + e.getMessage());
		}
	}

	/**
	 * Check if stealth mode is currently enabled.
	 */
	public boolean isStealthEnabled() {
		return settings.LAMPP_STEALTH_ENABLED.get();
	}

	// ==================== Dialer Code ====================

	/**
	 * Get the current dialer code used to launch the app in stealth mode.
	 */
	@NonNull
	public String getDialerCode() {
		String code = settings.LAMPP_STEALTH_DIALER_CODE.get();
		return code.isEmpty() ? DEFAULT_DIALER_CODE : code;
	}

	/**
	 * Set a custom dialer code.
	 * @return true if the code is valid and was saved, false otherwise
	 */
	public boolean setDialerCode(@NonNull String code) {
		if (!isValidDialerCode(code)) {
			return false;
		}
		settings.LAMPP_STEALTH_DIALER_CODE.set(code);
		return true;
	}

	/**
	 * Check if a dialed number matches the configured stealth code.
	 * @param dialedNumber the number being dialed (from NEW_OUTGOING_CALL broadcast)
	 * @return true if the dialed number matches the stealth code
	 */
	public boolean matchesDialerCode(@Nullable String dialedNumber) {
		if (dialedNumber == null || dialedNumber.isEmpty()) {
			return false;
		}
		return dialedNumber.equals(getDialerCode());
	}

	/**
	 * Validate a dialer code format.
	 * Must start with *#, end with #, be 6-12 characters, and contain only digits, *, and #.
	 */
	public static boolean isValidDialerCode(@Nullable String code) {
		if (code == null || code.length() < MIN_CODE_LENGTH || code.length() > MAX_CODE_LENGTH) {
			return false;
		}
		if (!code.startsWith("*#") || !code.endsWith("#")) {
			return false;
		}
		// Middle section (between *# and trailing #) must be digits only
		String middle = code.substring(2, code.length() - 1);
		for (int i = 0; i < middle.length(); i++) {
			if (!Character.isDigit(middle.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	// ==================== Component Management ====================

	/**
	 * Enable or disable the launcher activity-alias.
	 * When disabled, the app disappears from the launcher.
	 * Uses DONT_KILL_APP to avoid terminating the running app.
	 */
	private void setLauncherAliasEnabled(boolean enabled) {
		try {
			PackageManager pm = app.getPackageManager();
			ComponentName alias = new ComponentName(app, LAUNCHER_ALIAS_CLASS);
			int newState = enabled
					? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
					: PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
			pm.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP);
		} catch (Exception e) {
			LOG.error("Failed to toggle launcher alias", e);
		}
	}
}
