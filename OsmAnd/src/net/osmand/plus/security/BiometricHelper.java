/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.security;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;

/**
 * Rushlight: Helper to check biometric authentication availability.
 */
public class BiometricHelper {

	/**
	 * Check if any form of biometric authentication is available and enrolled.
	 */
	public static boolean isBiometricAvailable(@NonNull Context context) {
		BiometricManager biometricManager = BiometricManager.from(context);
		int result = biometricManager.canAuthenticate(
				BiometricManager.Authenticators.BIOMETRIC_WEAK);
		return result == BiometricManager.BIOMETRIC_SUCCESS;
	}

	/**
	 * Get a user-readable message about biometric availability.
	 */
	@NonNull
	public static String getAvailabilityMessage(@NonNull Context context) {
		BiometricManager biometricManager = BiometricManager.from(context);
		int result = biometricManager.canAuthenticate(
				BiometricManager.Authenticators.BIOMETRIC_WEAK);
		switch (result) {
			case BiometricManager.BIOMETRIC_SUCCESS:
				return "Biometric authentication available";
			case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
				return "No biometric hardware available";
			case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
				return "Biometric hardware unavailable";
			case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
				return "No biometric credentials enrolled";
			default:
				return "Biometric status unknown";
		}
	}
}
