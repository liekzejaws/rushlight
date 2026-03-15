/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility for sanitizing sensitive information from P2P log messages.
 *
 * Prevents MAC addresses, device names, and other identifying information
 * from leaking into logcat — which could be captured by forensic tools
 * or malicious apps with READ_LOGS permission.
 */
public final class P2pLogSanitizer {

	private P2pLogSanitizer() {
		// Utility class
	}

	/**
	 * Redact a Bluetooth MAC address, keeping only the last 2 octets for debugging.
	 * Example: "AA:BB:CC:DD:EE:FF" → "XX:XX:XX:XX:EE:FF"
	 *
	 * @param mac The full MAC address
	 * @return Redacted MAC address, or "null" if input is null
	 */
	@NonNull
	public static String redactMac(@Nullable String mac) {
		if (mac == null || mac.isEmpty()) {
			return "null";
		}

		// Standard MAC format: XX:XX:XX:XX:XX:XX (17 chars)
		String[] parts = mac.split(":");
		if (parts.length == 6) {
			return "XX:XX:XX:XX:" + parts[4] + ":" + parts[5];
		}

		// Non-standard format — redact all but last 4 chars
		if (mac.length() > 4) {
			return "REDACTED..." + mac.substring(mac.length() - 4);
		}

		return "REDACTED";
	}

	/**
	 * Redact a device name for logging. Shows only first character and length.
	 * Example: "John's Pixel" → "J****** (12 chars)"
	 *
	 * @param name The device name
	 * @return Redacted device name
	 */
	@NonNull
	public static String redactDeviceName(@Nullable String name) {
		if (name == null || name.isEmpty()) {
			return "unnamed";
		}

		if (name.length() <= 2) {
			return "** (" + name.length() + " chars)";
		}

		return name.charAt(0) + "****** (" + name.length() + " chars)";
	}

	/**
	 * Create a safe peer identifier for logging from an ephemeral ID.
	 * Example: "a1b2c3d4e5f6" → "Peer-a1b2"
	 *
	 * @param ephemeralId The ephemeral peer ID (hex string)
	 * @return Short peer alias safe for logging
	 */
	@NonNull
	public static String peerAlias(@Nullable String ephemeralId) {
		if (ephemeralId == null || ephemeralId.length() < 4) {
			return "Peer-????";
		}
		return "Peer-" + ephemeralId.substring(0, 4);
	}
}
