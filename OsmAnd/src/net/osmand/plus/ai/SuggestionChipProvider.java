/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.ai.rag.RagManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Phase 17 + v0.2: Context-sensitive suggestion chips for AI chat empty state.
 *
 * Generates tappable suggestions based on device context:
 * - Model/ZIM availability → setup chips when not configured
 * - GPS availability → location-based queries
 * - Battery level → power-saving tips
 * - Time of day → morning vs night survival tips
 * - Always → core survival knowledge queries
 */
public class SuggestionChipProvider {

	/**
	 * A single suggestion chip with text, optional icon, and optional action.
	 */
	public static class Suggestion {
		@NonNull public final String text;
		@DrawableRes public final int iconRes;
		@NonNull public final SuggestionAction action;

		public Suggestion(@NonNull String text, @DrawableRes int iconRes) {
			this(text, iconRes, SuggestionAction.SEND_MESSAGE);
		}

		public Suggestion(@NonNull String text, @DrawableRes int iconRes,
		                  @NonNull SuggestionAction action) {
			this.text = text;
			this.iconRes = iconRes;
			this.action = action;
		}
	}

	/**
	 * Action type for a suggestion chip.
	 */
	public enum SuggestionAction {
		SEND_MESSAGE,       // Default: send text as AI chat message
		OPEN_MODEL_MANAGER, // Navigate to AI model download
		OPEN_WIKI_DOWNLOAD, // Navigate to ZIM file download
		OPEN_TOOLS          // Navigate to tools tab
	}

	/**
	 * Build a list of contextual suggestions based on the current device state.
	 */
	@NonNull
	public static List<Suggestion> getSuggestions(@NonNull OsmandApplication app,
	                                               @NonNull RagManager ragManager) {
		List<Suggestion> suggestions = new ArrayList<>();

		// Priority 1: Setup chips when features aren't configured yet
		LlmManager llmManager = new LlmManager(app);
		boolean hasModel = llmManager.hasDownloadedModels();
		boolean hasWiki = ragManager.isWikipediaAvailable();
		boolean hasMap = ragManager.isMapDataAvailable();

		if (!hasModel) {
			suggestions.add(new Suggestion("Download AI Model",
					R.drawable.ic_action_help, SuggestionAction.OPEN_MODEL_MANAGER));
		}

		if (!hasWiki) {
			suggestions.add(new Suggestion("Get Wikipedia Offline",
					R.drawable.ic_action_read_from_file, SuggestionAction.OPEN_WIKI_DOWNLOAD));
		}

		// Priority 2: Battery-aware chip
		DeviceCapabilityDetector detector = new DeviceCapabilityDetector(app);
		int batteryLevel = detector.getBatteryLevel();
		if (batteryLevel < 20 && !detector.isCharging()) {
			suggestions.add(new Suggestion("Battery saving tips",
					R.drawable.ic_action_sensor_heart_rate_outlined));
		}

		// Priority 3: Location-aware queries (if GPS available)
		if (hasMap) {
			suggestions.add(new Suggestion("What's around me?",
					R.drawable.ic_action_explore));
			suggestions.add(new Suggestion("Navigate to safety",
					R.drawable.ic_action_start_navigation));
		}

		// Priority 4: Time-of-day awareness
		int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
		if (hour >= 18 || hour < 6) {
			// Night time suggestions
			suggestions.add(new Suggestion("How to build a shelter?",
					R.drawable.ic_action_help));
			suggestions.add(new Suggestion("Night navigation techniques",
					R.drawable.ic_action_compass));
		} else {
			// Daytime suggestions
			suggestions.add(new Suggestion("How to purify water?",
					R.drawable.ic_action_sensor_heart_rate_outlined));
			suggestions.add(new Suggestion("First aid for burns",
					R.drawable.ic_action_help));
		}

		// Priority 5: Wikipedia-powered queries
		if (hasWiki) {
			suggestions.add(new Suggestion("Explain how a compass works",
					R.drawable.ic_action_compass));
		}

		// Priority 6: Always-available general queries
		suggestions.add(new Suggestion("International distress signals",
				R.drawable.ic_action_signal));

		// Limit to 6 max
		if (suggestions.size() > 6) {
			suggestions = suggestions.subList(0, 6);
		}

		return suggestions;
	}
}
