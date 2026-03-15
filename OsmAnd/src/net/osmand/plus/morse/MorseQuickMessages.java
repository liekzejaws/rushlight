/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.ai.rag.LocationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LAMPP Morse Code: Predefined emergency quick messages for one-tap sending.
 *
 * Provides a set of common survival/emergency messages that can be quickly
 * selected and transmitted. The SEND LOCATION message auto-appends GPS
 * coordinates in a compact Morse-friendly format.
 *
 * Reference: MORSE-CODE-SPEC.md section 8.
 */
public final class MorseQuickMessages {

	/**
	 * A predefined quick message for the chip bar.
	 */
	public static class QuickMessage {
		public final String id;
		public final String displayText;
		public final String morseText;
		public final boolean appendsGps;

		public QuickMessage(@NonNull String id, @NonNull String displayText,
		                    @NonNull String morseText, boolean appendsGps) {
			this.id = id;
			this.displayText = displayText;
			this.morseText = morseText;
			this.appendsGps = appendsGps;
		}
	}

	private MorseQuickMessages() {
		// Static utility class
	}

	/**
	 * Returns the list of predefined quick messages for the chip bar.
	 * The "Custom" entry has empty morseText (caller should focus the input field).
	 */
	@NonNull
	public static List<QuickMessage> getDefaultMessages() {
		List<QuickMessage> list = new ArrayList<>();
		list.add(new QuickMessage("sos", "SOS", "SOS", false));
		list.add(new QuickMessage("need_water", "WATER", "NEED WATER", false));
		list.add(new QuickMessage("need_medical", "MEDICAL", "NEED MEDICAL", false));
		list.add(new QuickMessage("safe_ok", "SAFE OK", "SAFE ALL OK", false));
		list.add(new QuickMessage("send_loc", "SEND LOC", "SEND LOCATION", true));
		list.add(new QuickMessage("custom", "Custom", "", false));
		return list;
	}

	/**
	 * Formats GPS coordinates into compact Morse-friendly format.
	 * Example: "LOC 41.23N 81.45W"
	 *
	 * Uses 2 decimal places (~1.1km accuracy), which is appropriate
	 * for Morse transmission efficiency.
	 *
	 * @param location the current location context, or null if unavailable
	 * @return formatted GPS string, or empty string if location is null
	 */
	@NonNull
	public static String formatGpsForMorse(@Nullable LocationContext location) {
		if (location == null) return "";
		double lat = location.getLatitude();
		double lon = location.getLongitude();
		String latDir = lat >= 0 ? "N" : "S";
		String lonDir = lon >= 0 ? "E" : "W";
		return String.format(Locale.US, "LOC %.2f%s %.2f%s",
				Math.abs(lat), latDir, Math.abs(lon), lonDir);
	}

	/**
	 * Resolves a QuickMessage's text, optionally appending GPS coordinates.
	 *
	 * @param msg       the quick message to resolve
	 * @param appendGps whether to append GPS (caller reads from settings)
	 * @param app       the application context for location access
	 * @return the resolved message text ready for the input field
	 */
	@NonNull
	public static String resolveMessageText(@NonNull QuickMessage msg,
	                                        boolean appendGps,
	                                        @Nullable OsmandApplication app) {
		if (!msg.appendsGps || !appendGps || app == null) {
			return msg.morseText;
		}
		LocationContext loc = LocationContext.fromOsmAnd(app);
		String gps = formatGpsForMorse(loc);
		if (gps.isEmpty()) {
			return msg.morseText;
		}
		return msg.morseText + " " + gps;
	}
}
