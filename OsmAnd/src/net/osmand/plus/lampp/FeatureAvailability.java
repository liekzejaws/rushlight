package net.osmand.plus.lampp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.ai.DeviceCapabilityDetector;
import net.osmand.plus.ai.LlmManager;

import org.apache.commons.logging.Log;

/**
 * Centralized feature availability checks for Rushlight.
 *
 * Android fragmentation means different devices support different features.
 * This class provides a single point of truth for what's available,
 * with human-readable explanations for users when features are unavailable.
 *
 * Features checked:
 * - AI Chat: API 30+, sufficient RAM
 * - P2P BLE Discovery: API 26+, BLE hardware
 * - P2P WiFi Direct: WiFi Direct hardware
 * - Morse Flashlight: Camera flash hardware
 * - Morse Camera Receive: Camera hardware
 * - Stealth Dialer Code: Telephony capability
 */
public class FeatureAvailability {

	private static final Log LOG = PlatformUtil.getLog(FeatureAvailability.class);

	/**
	 * Availability result with reason explanation.
	 */
	public static class AvailabilityResult {
		public final boolean available;
		@NonNull public final String reason;
		@Nullable public final String workaround;

		public AvailabilityResult(boolean available, @NonNull String reason,
		                          @Nullable String workaround) {
			this.available = available;
			this.reason = reason;
			this.workaround = workaround;
		}

		public static AvailabilityResult available() {
			return new AvailabilityResult(true, "Available", null);
		}

		public static AvailabilityResult unavailable(@NonNull String reason,
		                                              @Nullable String workaround) {
			return new AvailabilityResult(false, reason, workaround);
		}
	}

	private final OsmandApplication app;
	private final DeviceCapabilityDetector deviceDetector;

	public FeatureAvailability(@NonNull OsmandApplication app) {
		this.app = app;
		this.deviceDetector = new DeviceCapabilityDetector(app);
	}

	// ---- Individual Feature Checks ----

	/**
	 * Check if AI Chat (LLM inference) is available.
	 * Requires: API 30+, ≥3GB available RAM
	 */
	@NonNull
	public AvailabilityResult checkAiChat() {
		if (Build.VERSION.SDK_INT < LlmManager.MIN_SDK_FOR_AI) {
			return AvailabilityResult.unavailable(
					"AI requires Android 11 (API 30) or higher. "
							+ "Your device runs Android " + Build.VERSION.SDK_INT + ".",
					"Use the Wikipedia tab for offline reference instead.");
		}

		long ramMb = deviceDetector.getTotalRamMb();
		if (ramMb < 3000) {
			return AvailabilityResult.unavailable(
					"AI requires at least 3 GB RAM. Your device has " + ramMb + " MB.",
					"Use smaller models like TinyLlama 1.1B, or use Wikipedia tab.");
		}

		return AvailabilityResult.available();
	}

	/**
	 * Check if BLE peer discovery is available.
	 * Requires: API 26+, BLE hardware
	 */
	@NonNull
	public AvailabilityResult checkBlePeerDiscovery() {
		if (!app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			return AvailabilityResult.unavailable(
					"Bluetooth Low Energy hardware not available on this device.",
					"Use WiFi Direct for P2P sharing instead (if available).");
		}

		BluetoothManager btManager = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
		if (btManager == null || btManager.getAdapter() == null) {
			return AvailabilityResult.unavailable(
					"Bluetooth adapter not available.",
					null);
		}

		if (!btManager.getAdapter().isEnabled()) {
			return AvailabilityResult.unavailable(
					"Bluetooth is disabled. Enable it in system settings.",
					"Turn on Bluetooth from the notification shade or Settings.");
		}

		return AvailabilityResult.available();
	}

	/**
	 * Check if WiFi Direct is available for P2P file transfer.
	 */
	@NonNull
	public AvailabilityResult checkWifiDirect() {
		WifiP2pManager p2pManager = (WifiP2pManager)
				app.getSystemService(Context.WIFI_P2P_SERVICE);
		if (p2pManager == null) {
			return AvailabilityResult.unavailable(
					"WiFi Direct not supported on this device.",
					"Use Bluetooth for P2P sharing (slower but compatible).");
		}

		return AvailabilityResult.available();
	}

	/**
	 * Check if Morse code flashlight transmission is available.
	 */
	@NonNull
	public AvailabilityResult checkMorseFlashlight() {
		if (!app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
			return AvailabilityResult.unavailable(
					"Camera flash not available on this device.",
					"Use audio mode instead — Morse via speaker.");
		}

		return AvailabilityResult.available();
	}

	/**
	 * Check if Morse code camera receive is available.
	 */
	@NonNull
	public AvailabilityResult checkMorseCameraReceive() {
		if (!app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
			return AvailabilityResult.unavailable(
					"Camera not available on this device.",
					"Use microphone mode to receive Morse via audio.");
		}

		return AvailabilityResult.available();
	}

	/**
	 * Check if stealth dialer code is available.
	 * Requires telephony/dialer on device.
	 */
	@NonNull
	public AvailabilityResult checkStealthDialerCode() {
		TelephonyManager tm = (TelephonyManager) app.getSystemService(Context.TELEPHONY_SERVICE);
		if (tm == null || !app.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
			return AvailabilityResult.unavailable(
					"Telephony not available (WiFi-only tablet?).",
					"Use PIN lock instead of stealth dialer code for app security.");
		}

		return AvailabilityResult.available();
	}

	// ---- Convenience Methods ----

	/**
	 * Check if a specific LAMPP tab's primary feature is available.
	 */
	@NonNull
	public AvailabilityResult checkTab(@NonNull LamppTab tab) {
		switch (tab) {
			case AI_CHAT:
				return checkAiChat();
			case P2P:
				// P2P needs at least one transport
				AvailabilityResult ble = checkBlePeerDiscovery();
				AvailabilityResult wifi = checkWifiDirect();
				if (ble.available || wifi.available) {
					return AvailabilityResult.available();
				}
				return AvailabilityResult.unavailable(
						"Neither Bluetooth nor WiFi Direct is available.",
						"P2P sharing requires BLE or WiFi Direct hardware.");
			case MORSE:
				// Morse needs at least one send/receive mode
				return AvailabilityResult.available(); // Audio mode is always available
			default:
				return AvailabilityResult.available();
		}
	}

	/**
	 * Get a summary of all feature availability for diagnostics.
	 */
	@NonNull
	public String getAvailabilitySummary() {
		StringBuilder sb = new StringBuilder("Feature Availability:\n");
		sb.append("  AI Chat: ").append(formatResult(checkAiChat())).append("\n");
		sb.append("  BLE Discovery: ").append(formatResult(checkBlePeerDiscovery())).append("\n");
		sb.append("  WiFi Direct: ").append(formatResult(checkWifiDirect())).append("\n");
		sb.append("  Morse Flash: ").append(formatResult(checkMorseFlashlight())).append("\n");
		sb.append("  Morse Camera: ").append(formatResult(checkMorseCameraReceive())).append("\n");
		sb.append("  Stealth Dialer: ").append(formatResult(checkStealthDialerCode())).append("\n");
		sb.append("  Device: ").append(deviceDetector.getDeviceSummary());
		return sb.toString();
	}

	@NonNull
	private String formatResult(@NonNull AvailabilityResult result) {
		return result.available ? "OK" : "NO (" + result.reason + ")";
	}
}
