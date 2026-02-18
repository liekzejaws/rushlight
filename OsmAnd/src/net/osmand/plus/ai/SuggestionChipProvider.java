package net.osmand.plus.ai;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.ai.rag.RagManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 17: Provides context-aware suggestion chips for the AI chat empty state.
 *
 * Generates 4-6 tappable suggestions based on the current app context:
 * - GPS availability → location-based queries
 * - ZIM files loaded → Wikipedia research queries
 * - Always → core survival knowledge queries
 */
public class SuggestionChipProvider {

	/**
	 * A single suggestion chip with text and optional icon.
	 */
	public static class Suggestion {
		@NonNull public final String text;
		@DrawableRes public final int iconRes;

		public Suggestion(@NonNull String text, @DrawableRes int iconRes) {
			this.text = text;
			this.iconRes = iconRes;
		}
	}

	/**
	 * Build a list of contextual suggestions based on the current state.
	 */
	@NonNull
	public static List<Suggestion> getSuggestions(@NonNull OsmandApplication app,
	                                               @NonNull RagManager ragManager) {
		List<Suggestion> suggestions = new ArrayList<>();

		// Core survival queries (always shown)
		suggestions.add(new Suggestion("How to purify water?",
				R.drawable.ic_action_sensor_heart_rate_outlined)); // droplet-like icon
		suggestions.add(new Suggestion("First aid for burns",
				R.drawable.ic_action_help));

		// Location-aware queries (if GPS available)
		if (ragManager.isMapDataAvailable()) {
			suggestions.add(new Suggestion("What's around me?",
					R.drawable.ic_action_explore));
		}

		// Wikipedia queries (if ZIM loaded)
		if (ragManager.isWikipediaAvailable()) {
			suggestions.add(new Suggestion("Explain how a compass works",
					R.drawable.ic_action_compass));
		}

		// Navigation / practical
		suggestions.add(new Suggestion("How to navigate without GPS?",
				R.drawable.ic_action_start_navigation));

		// Emergency signals
		suggestions.add(new Suggestion("International distress signals",
				R.drawable.ic_action_signal));

		// Limit to 6 max
		if (suggestions.size() > 6) {
			suggestions = suggestions.subList(0, 6);
		}

		return suggestions;
	}
}
