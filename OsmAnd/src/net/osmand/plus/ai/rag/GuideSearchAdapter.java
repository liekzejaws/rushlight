package net.osmand.plus.ai.rag;

import android.util.Log;

import androidx.annotation.NonNull;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.guides.GuideEntry;
import net.osmand.plus.guides.GuideManager;

import java.util.List;

/**
 * LAMPP Phase 14: RAG adapter for survival guide search.
 * Searches the offline survival guide database and formats results
 * as context for the LLM prompt.
 *
 * Follows the same adapter pattern as ZimSearchAdapter.
 */
public class GuideSearchAdapter {

	private static final String TAG = "GuideSearchAdapter";
	private static final int APPROX_CHARS_PER_TOKEN = 4;

	private final OsmandApplication app;

	public GuideSearchAdapter(@NonNull OsmandApplication app) {
		this.app = app;
	}

	/**
	 * Search survival guides and format results within the token budget.
	 *
	 * @param query       The user's query
	 * @param tokenBudget Maximum tokens for guide context
	 * @return Formatted guide context string, or empty string if no matches
	 */
	@NonNull
	public String search(@NonNull String query, int tokenBudget) {
		GuideManager guideManager = app.getGuideManager();
		if (!guideManager.isLoaded()) {
			guideManager.loadGuidesSync();
		}

		List<GuideEntry> results = guideManager.search(query);
		if (results.isEmpty()) {
			return "";
		}

		int charBudget = tokenBudget * APPROX_CHARS_PER_TOKEN;
		StringBuilder context = new StringBuilder();
		int guidesIncluded = 0;

		for (GuideEntry guide : results) {
			String formatted = formatGuide(guide);
			if (context.length() + formatted.length() > charBudget) {
				// Try to include at least a summary if full body doesn't fit
				String summaryOnly = formatGuideSummaryOnly(guide);
				if (context.length() + summaryOnly.length() <= charBudget) {
					context.append(summaryOnly);
					guidesIncluded++;
				}
				break; // Budget exhausted
			}
			context.append(formatted);
			guidesIncluded++;
		}

		Log.d(TAG, "Guide search for '" + query + "': " + results.size() +
				" matches, " + guidesIncluded + " included in context");

		return context.toString();
	}

	/**
	 * Format a single guide entry for inclusion in the LLM prompt context.
	 */
	@NonNull
	private String formatGuide(@NonNull GuideEntry guide) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- Guide: ").append(guide.getTitle()).append(" ---\n");
		sb.append("Category: ").append(guide.getCategory().getDisplayName());
		sb.append(" | Importance: ").append(guide.getImportance().name()).append("\n");

		if (guide.hasBody()) {
			sb.append(guide.getBody()).append("\n");
		} else {
			sb.append(guide.getSummary()).append("\n");
		}
		sb.append("\n");
		return sb.toString();
	}

	/**
	 * Format only the summary of a guide (for when body doesn't fit in budget).
	 */
	@NonNull
	private String formatGuideSummaryOnly(@NonNull GuideEntry guide) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- Guide: ").append(guide.getTitle()).append(" (summary) ---\n");
		sb.append("Category: ").append(guide.getCategory().getDisplayName());
		sb.append(" | Importance: ").append(guide.getImportance().name()).append("\n");
		sb.append(guide.getSummary()).append("\n\n");
		return sb.toString();
	}
}
