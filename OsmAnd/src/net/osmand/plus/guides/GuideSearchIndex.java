package net.osmand.plus.guides;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * In-memory inverted index for searching survival guides by keywords.
 * Built from guide titles, tags, and summaries. Used by both the UI search
 * bar and the RAG pipeline's GuideSearchAdapter.
 */
public class GuideSearchIndex {

	/** Map from lowercase keyword → set of guide IDs containing that keyword */
	private final Map<String, Set<String>> invertedIndex = new HashMap<>();

	/** Map from guide ID → GuideEntry for fast lookup */
	private final Map<String, GuideEntry> guideLookup = new HashMap<>();

	/**
	 * Build or rebuild the index from a list of guides.
	 * Clears any existing index data first.
	 */
	public void buildIndex(@NonNull List<GuideEntry> guides) {
		invertedIndex.clear();
		guideLookup.clear();

		for (GuideEntry guide : guides) {
			guideLookup.put(guide.getId(), guide);
			indexGuide(guide);
		}
	}

	/**
	 * Add a single guide to the index without clearing existing data.
	 */
	public void addGuide(@NonNull GuideEntry guide) {
		guideLookup.put(guide.getId(), guide);
		indexGuide(guide);
	}

	private void indexGuide(@NonNull GuideEntry guide) {
		// Index title words
		indexText(guide.getId(), guide.getTitle());

		// Index tags
		for (String tag : guide.getTags()) {
			indexText(guide.getId(), tag);
		}

		// Index summary
		indexText(guide.getId(), guide.getSummary());

		// Index category name
		indexText(guide.getId(), guide.getCategory().getDisplayName());
	}

	private void indexText(@NonNull String guideId, @NonNull String text) {
		String[] words = text.toLowerCase(Locale.US).split("\\W+");
		for (String word : words) {
			if (word.length() >= 2) {
				invertedIndex.computeIfAbsent(word, k -> new HashSet<>()).add(guideId);
			}
		}
	}

	/**
	 * Search guides by query string. Splits query into words and finds guides
	 * matching ANY word (OR logic), ranked by number of matching words and importance.
	 *
	 * @param query Search query (e.g. "water purification boiling")
	 * @return Ranked list of matching guides, best matches first
	 */
	@NonNull
	public List<GuideEntry> search(@NonNull String query) {
		if (query.trim().isEmpty()) {
			return Collections.emptyList();
		}

		String[] queryWords = query.toLowerCase(Locale.US).split("\\W+");
		Map<String, Integer> scoreMap = new HashMap<>();

		for (String word : queryWords) {
			if (word.length() < 2) continue;

			// Exact match
			Set<String> exactMatches = invertedIndex.get(word);
			if (exactMatches != null) {
				for (String guideId : exactMatches) {
					scoreMap.merge(guideId, 10, Integer::sum);
				}
			}

			// Prefix match (for partial words like "purif" matching "purification")
			for (Map.Entry<String, Set<String>> entry : invertedIndex.entrySet()) {
				if (entry.getKey().startsWith(word) && !entry.getKey().equals(word)) {
					for (String guideId : entry.getValue()) {
						scoreMap.merge(guideId, 5, Integer::sum);
					}
				}
			}
		}

		// Convert scores to ranked list
		List<GuideEntry> results = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : scoreMap.entrySet()) {
			GuideEntry guide = guideLookup.get(entry.getKey());
			if (guide != null) {
				results.add(guide);
			}
		}

		// Sort by: score descending, then importance ascending (CRITICAL first)
		Collections.sort(results, new Comparator<GuideEntry>() {
			@Override
			public int compare(GuideEntry a, GuideEntry b) {
				int scoreA = scoreMap.getOrDefault(a.getId(), 0);
				int scoreB = scoreMap.getOrDefault(b.getId(), 0);
				if (scoreA != scoreB) {
					return Integer.compare(scoreB, scoreA); // Higher score first
				}
				return Integer.compare(
						a.getImportance().getSortOrder(),
						b.getImportance().getSortOrder()); // CRITICAL before HIGH before MEDIUM
			}
		});

		return results;
	}

	/**
	 * Get all guides in a specific category, sorted by importance then title.
	 */
	@NonNull
	public List<GuideEntry> searchByCategory(@NonNull GuideCategory category) {
		List<GuideEntry> results = new ArrayList<>();
		for (GuideEntry guide : guideLookup.values()) {
			if (guide.getCategory() == category) {
				results.add(guide);
			}
		}
		Collections.sort(results, new Comparator<GuideEntry>() {
			@Override
			public int compare(GuideEntry a, GuideEntry b) {
				int cmp = Integer.compare(
						a.getImportance().getSortOrder(),
						b.getImportance().getSortOrder());
				if (cmp != 0) return cmp;
				return a.getTitle().compareToIgnoreCase(b.getTitle());
			}
		});
		return results;
	}

	/**
	 * Get all indexed guides, sorted by category then importance.
	 */
	@NonNull
	public List<GuideEntry> getAllGuides() {
		List<GuideEntry> all = new ArrayList<>(guideLookup.values());
		Collections.sort(all, new Comparator<GuideEntry>() {
			@Override
			public int compare(GuideEntry a, GuideEntry b) {
				int catCmp = Integer.compare(
						a.getCategory().getSortOrder(),
						b.getCategory().getSortOrder());
				if (catCmp != 0) return catCmp;
				return Integer.compare(
						a.getImportance().getSortOrder(),
						b.getImportance().getSortOrder());
			}
		});
		return all;
	}

	/**
	 * Get a specific guide by ID.
	 */
	@NonNull
	public GuideEntry getGuide(@NonNull String id) {
		return guideLookup.get(id);
	}

	/**
	 * @return Total number of indexed guides
	 */
	public int getGuideCount() {
		return guideLookup.size();
	}

	/**
	 * @return Total number of unique indexed keywords
	 */
	public int getKeywordCount() {
		return invertedIndex.size();
	}
}
