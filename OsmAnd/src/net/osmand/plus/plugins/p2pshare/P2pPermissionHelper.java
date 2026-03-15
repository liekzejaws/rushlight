/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.plugins.p2pshare.ui.P2pPermissionRationaleDialog;
import net.osmand.plus.utils.AndroidUtils;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized permission helper for P2P sharing.
 * Handles API-level-aware checks, rationale dialogs, and graceful denial fallbacks.
 *
 * Required permissions by API level:
 * - API 31+ (Android 12+): BLUETOOTH_SCAN, BLUETOOTH_CONNECT, NEARBY_WIFI_DEVICES (API 33+)
 * - API 30 and below: BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION
 */
public class P2pPermissionHelper {

	private static final Log LOG = PlatformUtil.getLog(P2pPermissionHelper.class);

	public static final int P2P_PERMISSION_REQUEST_CODE = 7001;

	/**
	 * Callback for permission request results.
	 */
	public interface PermissionCallback {
		void onPermissionsGranted();
		void onPermissionsDenied(@NonNull String message);
	}

	/**
	 * Check if all required P2P permissions are granted.
	 */
	public static boolean hasAllPermissions(@NonNull Context context) {
		return hasBlePermissions(context) && hasWifiDirectPermissions(context);
	}

	/**
	 * Check if BLE permissions are granted.
	 */
	public static boolean hasBlePermissions(@NonNull Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			return AndroidUtils.hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
					&& AndroidUtils.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT);
		} else {
			return AndroidUtils.hasPermission(context, Manifest.permission.BLUETOOTH)
					&& AndroidUtils.hasPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
					&& AndroidUtils.hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
		}
	}

	/**
	 * Check if WiFi Direct permissions are granted.
	 */
	public static boolean hasWifiDirectPermissions(@NonNull Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return AndroidUtils.hasPermission(context, "android.permission.NEARBY_WIFI_DEVICES");
		}
		return AndroidUtils.hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
	}

	/**
	 * Get a list of all missing permissions.
	 */
	@NonNull
	public static List<String> getMissingPermissions(@NonNull Context context) {
		List<String> missing = new ArrayList<>();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			if (!AndroidUtils.hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)) {
				missing.add(Manifest.permission.BLUETOOTH_SCAN);
			}
			if (!AndroidUtils.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)) {
				missing.add(Manifest.permission.BLUETOOTH_CONNECT);
			}
		} else {
			if (!AndroidUtils.hasPermission(context, Manifest.permission.BLUETOOTH)) {
				missing.add(Manifest.permission.BLUETOOTH);
			}
			if (!AndroidUtils.hasPermission(context, Manifest.permission.BLUETOOTH_ADMIN)) {
				missing.add(Manifest.permission.BLUETOOTH_ADMIN);
			}
			if (!AndroidUtils.hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
				missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (!AndroidUtils.hasPermission(context, "android.permission.NEARBY_WIFI_DEVICES")) {
				missing.add("android.permission.NEARBY_WIFI_DEVICES");
			}
		}

		return missing;
	}

	/**
	 * Request P2P permissions with rationale dialog if needed.
	 * Shows a rationale dialog explaining why permissions are needed before requesting.
	 *
	 * @param activity Activity to request from
	 * @param fragmentManager For showing the rationale dialog
	 * @param callback Called with the result
	 */
	public static void requestPermissionsWithRationale(
			@NonNull Activity activity,
			@NonNull FragmentManager fragmentManager,
			@NonNull PermissionCallback callback) {

		List<String> missing = getMissingPermissions(activity);

		if (missing.isEmpty()) {
			callback.onPermissionsGranted();
			return;
		}

		// Check if any permission was previously denied (should show rationale)
		boolean shouldShowRationale = false;
		boolean permanentlyDenied = false;

		for (String permission : missing) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
				shouldShowRationale = true;
			}
			// If permission is missing but rationale is false, it might be "Don't ask again"
			// or first time asking. We check first-time-ask via shared prefs.
			if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
					&& wasPermissionPreviouslyRequested(activity, permission)) {
				permanentlyDenied = true;
			}
		}

		if (permanentlyDenied) {
			// User selected "Don't ask again" — direct to Settings
			handlePermanentDenial(activity, callback);
			return;
		}

		if (shouldShowRationale) {
			// Show rationale dialog, then request on OK
			P2pPermissionRationaleDialog.showInstance(fragmentManager, () -> {
				markPermissionsAsRequested(activity, missing);
				requestPermissions(activity, missing);
			});
		} else {
			// First time requesting — show rationale dialog anyway for a better UX
			P2pPermissionRationaleDialog.showInstance(fragmentManager, () -> {
				markPermissionsAsRequested(activity, missing);
				requestPermissions(activity, missing);
			});
		}
	}

	/**
	 * Directly request permissions without rationale dialog.
	 * Use this when the rationale dialog was already shown.
	 */
	public static void requestPermissions(@NonNull Activity activity,
	                                        @NonNull List<String> permissions) {
		if (permissions.isEmpty()) return;

		ActivityCompat.requestPermissions(
				activity,
				permissions.toArray(new String[0]),
				P2P_PERMISSION_REQUEST_CODE);
	}

	/**
	 * Handle the result from onRequestPermissionsResult.
	 *
	 * @return true if all requested permissions were granted
	 */
	public static boolean handlePermissionResult(@NonNull Activity activity,
	                                               int requestCode,
	                                               @NonNull String[] permissions,
	                                               @NonNull int[] grantResults) {
		if (requestCode != P2P_PERMISSION_REQUEST_CODE) {
			return false;
		}

		boolean allGranted = true;
		for (int result : grantResults) {
			if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
				allGranted = false;
				break;
			}
		}

		if (!allGranted) {
			// Check for permanent denial
			boolean anyPermanentlyDenied = false;
			for (String permission : permissions) {
				if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
						&& !AndroidUtils.hasPermission(activity, permission)) {
					anyPermanentlyDenied = true;
					break;
				}
			}

			if (anyPermanentlyDenied) {
				Toast.makeText(activity,
						R.string.p2p_share_permissions_settings_required,
						Toast.LENGTH_LONG).show();
			}
		}

		return allGranted;
	}

	/**
	 * Handle the case where user selected "Don't ask again".
	 * Shows a toast and optionally opens app settings.
	 */
	private static void handlePermanentDenial(
			@NonNull Activity activity,
			@NonNull PermissionCallback callback) {
		Toast.makeText(activity,
				R.string.p2p_share_permissions_settings_required,
				Toast.LENGTH_LONG).show();

		callback.onPermissionsDenied(
				activity.getString(R.string.p2p_share_permissions_settings_required));
	}

	/**
	 * Open the app's system settings page for manual permission granting.
	 */
	public static void openAppSettings(@NonNull Context context) {
		Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		intent.setData(Uri.fromParts("package", context.getPackageName(), null));
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);
	}

	/**
	 * Get a user-friendly description of what permissions are needed.
	 */
	@NonNull
	public static String getPermissionExplanation(@NonNull Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			return context.getString(R.string.p2p_share_permission_rationale_api31);
		} else {
			return context.getString(R.string.p2p_share_permission_rationale_legacy);
		}
	}

	// Shared preferences tracking for "Don't ask again" detection

	private static boolean wasPermissionPreviouslyRequested(
			@NonNull Context context, @NonNull String permission) {
		return context.getSharedPreferences("p2p_permissions", Context.MODE_PRIVATE)
				.getBoolean("requested_" + permission, false);
	}

	private static void markPermissionsAsRequested(
			@NonNull Context context, @NonNull List<String> permissions) {
		android.content.SharedPreferences.Editor editor =
				context.getSharedPreferences("p2p_permissions", Context.MODE_PRIVATE).edit();
		for (String permission : permissions) {
			editor.putBoolean("requested_" + permission, true);
		}
		editor.apply();
	}
}
