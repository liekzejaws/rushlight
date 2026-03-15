/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.lampp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;

import org.apache.commons.logging.Log;

/**
 * Small themed status chip on the map screen showing connectivity and GPS state.
 * Displays:
 *   "OFFLINE · GPS"  — no internet, GPS available (ideal demo state)
 *   "OFFLINE"        — no internet, no GPS
 *   "ONLINE · GPS"   — internet available + GPS
 *   "ONLINE"         — internet, no GPS
 *
 * Themed per active style preset. Positioned at top-left, unobtrusive.
 * Auto-updates via ConnectivityManager.NetworkCallback.
 */
public class ConnectivityStatusOverlay implements DefaultLifecycleObserver {

	private static final Log LOG = PlatformUtil.getLog(ConnectivityStatusOverlay.class);

	private final MapActivity mapActivity;
	private TextView statusView;
	private ConnectivityManager connectivityManager;
	private ConnectivityManager.NetworkCallback networkCallback;
	private boolean hasInternet = false;

	public ConnectivityStatusOverlay(@NonNull MapActivity mapActivity) {
		this.mapActivity = mapActivity;
	}

	/**
	 * Create and attach the overlay to the map activity's root view.
	 */
	public void attach() {
		ViewGroup rootView = mapActivity.findViewById(android.R.id.content);
		if (rootView == null) return;

		// Create status TextView
		statusView = new TextView(mapActivity);
		statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		statusView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
		statusView.setPadding(dp(10), dp(4), dp(10), dp(4));
		statusView.setBackground(createBackground());
		statusView.setGravity(Gravity.CENTER);

		// Position: top-left with margin
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		params.gravity = Gravity.TOP | Gravity.START;
		params.topMargin = dp(42); // Below status bar
		params.leftMargin = dp(8);
		statusView.setLayoutParams(params);
		statusView.setElevation(dp(4));

		rootView.addView(statusView);

		// Register network callback
		connectivityManager = (ConnectivityManager) mapActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager != null) {
			// Check initial state
			hasInternet = isNetworkAvailable();

			networkCallback = new ConnectivityManager.NetworkCallback() {
				@Override
				public void onAvailable(@NonNull Network network) {
					hasInternet = true;
					mapActivity.runOnUiThread(() -> updateStatus());
				}

				@Override
				public void onLost(@NonNull Network network) {
					hasInternet = isNetworkAvailable(); // Re-check in case other networks exist
					mapActivity.runOnUiThread(() -> updateStatus());
				}

				@Override
				public void onCapabilitiesChanged(@NonNull Network network,
				                                  @NonNull NetworkCapabilities capabilities) {
					hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
							&& capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
					mapActivity.runOnUiThread(() -> updateStatus());
				}
			};

			NetworkRequest request = new NetworkRequest.Builder()
					.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
					.build();
			connectivityManager.registerNetworkCallback(request, networkCallback);
		}

		// Register lifecycle
		mapActivity.getLifecycle().addObserver(this);

		// Initial update
		updateStatus();

		LOG.info("Connectivity status overlay attached");
	}

	/**
	 * Update the status chip text and color.
	 */
	private void updateStatus() {
		if (statusView == null) return;

		boolean gpsEnabled = isGpsEnabled();
		String text;
		int textColor;

		if (!hasInternet) {
			text = gpsEnabled ? "OFFLINE · GPS" : "OFFLINE";
			textColor = Color.parseColor("#4CAF50"); // Green — offline is the GOOD state for Rushlight
		} else {
			text = gpsEnabled ? "ONLINE · GPS" : "ONLINE";
			textColor = Color.parseColor("#FFC107"); // Amber — online is less ideal for the narrative
		}

		statusView.setText(text);
		statusView.setTextColor(textColor);
	}

	private boolean isGpsEnabled() {
		try {
			LocationManager lm = (LocationManager) mapActivity.getSystemService(Context.LOCATION_SERVICE);
			return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isNetworkAvailable() {
		if (connectivityManager == null) return false;
		Network network = connectivityManager.getActiveNetwork();
		if (network == null) return false;
		NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
		return caps != null
				&& caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				&& caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
	}

	@NonNull
	private android.graphics.drawable.Drawable createBackground() {
		android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
		bg.setColor(Color.parseColor("#CC1a1a2e")); // Dark navy, semi-transparent
		bg.setCornerRadius(dp(12));
		bg.setStroke(dp(1), Color.parseColor("#334CAF50")); // Subtle green border
		return bg;
	}

	private int dp(int value) {
		return (int) TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP, value,
				mapActivity.getResources().getDisplayMetrics());
	}

	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		updateStatus();
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		if (connectivityManager != null && networkCallback != null) {
			try {
				connectivityManager.unregisterNetworkCallback(networkCallback);
			} catch (Exception e) {
				// Already unregistered
			}
		}
		if (statusView != null) {
			ViewGroup parent = (ViewGroup) statusView.getParent();
			if (parent != null) {
				parent.removeView(statusView);
			}
		}
		LOG.info("Connectivity status overlay destroyed");
	}
}
