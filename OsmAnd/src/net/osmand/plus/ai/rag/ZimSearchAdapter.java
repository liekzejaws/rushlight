package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.wikipedia.WikipediaPlugin;
import net.osmand.plus.wikipedia.ZimFileManager;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LAMPP Phase 7: Adapter for searching Wikipedia ZIM files.
 *
 * Bridges the RAG pipeline to ZimFileManager, providing search with text extraction
 * and relevance scoring for Wikipedia articles.
 */
public class ZimSearchAdapter {

    private static final Log LOG = PlatformUtil.getLog(ZimSearchAdapter.class);

    // Maximum articles to consider per search term
    private static final int MAX_RESULTS_PER_TERM = 5;

    // Default token budget for article context
    private static final int DEFAULT_MAX_TOKENS = 3000;

    // LRU cache for search results (expanded for offline use — content doesn't change)
    private static final int CACHE_SIZE = 200;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes

    // BM25 parameters (standard values, tuned for short document titles + snippets)
    private static final float BM25_K1 = 1.2f;
    private static final float BM25_B = 0.75f;
    // Estimated average document length in tokens (title + first 500 chars)
    private static final float AVG_DOC_LENGTH = 120.0f;

    // Performance: max time for BM25 scoring before falling back to heuristic
    private static final long BM25_TIMEOUT_MS = 100;

    private final OsmandApplication app;
    private final HtmlTextExtractor textExtractor;
    private final Map<String, CacheEntry> searchCache;

    public ZimSearchAdapter(@NonNull OsmandApplication app) {
        this.app = app;
        this.textExtractor = new HtmlTextExtractor();
        this.searchCache = new LinkedHashMap<String, CacheEntry>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > CACHE_SIZE;
            }
        };
    }

    /**
     * Check if Wikipedia ZIM search is available.
     */
    public boolean isAvailable() {
        ZimFileManager zim = getZimFileManager();
        return zim != null && zim.isOpen();
    }

    /**
     * Get the title of the currently open ZIM file.
     */
    @Nullable
    public String getZimTitle() {
        ZimFileManager zim = getZimFileManager();
        return zim != null ? zim.getZimTitle() : null;
    }

    /**
     * Search for relevant articles matching the given terms.
     *
     * @param searchTerms List of search terms to try
     * @param maxResults Maximum number of results to return
     * @return List of ArticleSource objects, ranked by relevance
     */
    @NonNull
    public List<ArticleSource> searchRelevant(@NonNull List<String> searchTerms, int maxResults) {
        if (searchTerms.isEmpty() || !isAvailable()) {
            return new ArrayList<>();
        }

        // Check cache first (exact match)
        String cacheKey = String.join("|", searchTerms).toLowerCase();
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOG.debug("Cache hit (exact) for: " + cacheKey);
            return cached.results;
        }

        // Check prefix-match cache: if "dehydr" was searched and we have "dehydration" cached
        for (Map.Entry<String, CacheEntry> entry : searchCache.entrySet()) {
            if (!entry.getValue().isExpired() && entry.getKey().startsWith(cacheKey)) {
                LOG.debug("Cache hit (prefix) for: " + cacheKey + " → " + entry.getKey());
                return entry.getValue().results;
            }
        }

        LOG.info("Searching ZIM for terms: " + searchTerms);
        long startTime = System.currentTimeMillis();

        Set<String> seenTitles = new HashSet<>();
        List<ArticleSource> allResults = new ArrayList<>();

        ZimFileManager zim = getZimFileManager();
        if (zim == null) {
            return allResults;
        }

        // Search for each term
        for (String term : searchTerms) {
            List<String> titles = zim.searchArticles(term, MAX_RESULTS_PER_TERM);

            for (String title : titles) {
                if (seenTitles.contains(title.toLowerCase())) {
                    continue;
                }
                seenTitles.add(title.toLowerCase());

                // Get article content
                String html = zim.getArticleHtml(title);
                if (html == null || html.isEmpty()) {
                    continue;
                }

                // Extract text
                String text = textExtractor.extractText(html);
                if (text.isEmpty()) {
                    continue;
                }

                // Calculate relevance score
                int relevance = calculateRelevance(title, text, term);

                // Create article source
                ArticleSource source = new ArticleSource(title, text, relevance);
                allResults.add(source);
            }
        }

        // Sort by relevance (descending)
        allResults.sort((a, b) -> Integer.compare(b.getRelevanceScore(), a.getRelevanceScore()));

        // Limit results
        if (allResults.size() > maxResults) {
            allResults = allResults.subList(0, maxResults);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOG.info("ZIM search found " + allResults.size() + " articles in " + elapsed + "ms");

        // Cache results
        searchCache.put(cacheKey, new CacheEntry(allResults));

        return allResults;
    }

    /**
     * Get the plain text content of an article, truncated to token limit.
     *
     * @param title Article title
     * @param maxTokens Maximum tokens to return
     * @return Plain text content, or null if not found
     */
    @Nullable
    public String getArticlePlainText(@NonNull String title, int maxTokens) {
        ZimFileManager zim = getZimFileManager();
        if (zim == null) {
            return null;
        }

        String html = zim.getArticleHtml(title);
        if (html == null) {
            return null;
        }

        return textExtractor.extractText(html, maxTokens);
    }

    /**
     * Get articles with text truncated to fit within a total token budget.
     *
     * @param sources List of article sources to process
     * @param totalTokenBudget Maximum total tokens across all articles
     * @return List with truncated text
     */
    @NonNull
    public List<ArticleSource> fitToBudget(@NonNull List<ArticleSource> sources, int totalTokenBudget) {
        if (sources.isEmpty()) {
            return sources;
        }

        // Distribute tokens among articles, giving more to higher relevance
        int tokensPerArticle = totalTokenBudget / sources.size();
        int remainingTokens = totalTokenBudget;

        List<ArticleSource> result = new ArrayList<>();

        for (int i = 0; i < sources.size(); i++) {
            ArticleSource source = sources.get(i);

            // Give more tokens to earlier (higher relevance) articles
            int budget = (i == 0) ? (int)(tokensPerArticle * 1.5) : tokensPerArticle;
            budget = Math.min(budget, remainingTokens);

            if (budget <= 0) {
                break;
            }

            String truncatedText = textExtractor.truncateToTokens(source.getText(), budget);
            int actualTokens = textExtractor.estimateTokens(truncatedText);
            remainingTokens -= actualTokens;

            result.add(new ArticleSource(
                    source.getTitle(),
                    truncatedText,
                    source.getRelevanceScore(),
                    source.getSnippet()
            ));
        }

        return result;
    }

    /**
     * Clear the search cache.
     */
    public void clearCache() {
        searchCache.clear();
    }

    /**
     * Get the ZimFileManager from WikipediaPlugin.
     */
    @Nullable
    private ZimFileManager getZimFileManager() {
        WikipediaPlugin plugin = PluginsHelper.getPlugin(WikipediaPlugin.class);
        if (plugin != null) {
            return plugin.getZimFileManager();
        }
        return null;
    }

    /**
     * Calculate relevance score using BM25 algorithm with title boost.
     *
     * BM25 is a probabilistic relevance function that accounts for:
     * - Term frequency (how often the term appears)
     * - Document length normalization (avoids bias toward longer docs)
     * - Inverse document frequency (rare terms score higher)
     *
     * Plus heuristic title-match boosts for better precision.
     *
     * @param title Article title
     * @param text Article text
     * @param searchTerm The search term that found this article
     * @return Relevance score (0-100)
     */
    private int calculateRelevance(@NonNull String title, @NonNull String text,
                                    @NonNull String searchTerm) {
        String lowerTitle = title.toLowerCase();
        String lowerTerm = searchTerm.toLowerCase();
        String lowerText = text.toLowerCase();

        // Create the "document" for BM25: title (weighted 3x) + first 500 chars of body
        String firstPart = lowerText.substring(0, Math.min(500, lowerText.length()));
        String document = lowerTitle + " " + lowerTitle + " " + lowerTitle + " " + firstPart;

        // Split search term into individual words for multi-term BM25
        String[] queryTerms = lowerTerm.split("\\s+");
        String[] docWords = document.split("\\s+");
        int docLength = docWords.length;

        float bm25Score = 0.0f;

        // Approximate IDF: since we don't have corpus stats, use a heuristic
        // Rare terms (longer) get higher IDF
        for (String term : queryTerms) {
            if (term.isEmpty()) continue;

            // Count term frequency in document
            int tf = 0;
            for (String word : docWords) {
                if (word.contains(term)) {
                    tf++;
                }
            }

            // Heuristic IDF based on term length (longer = more specific = higher IDF)
            float idf = (float) Math.log(1.0 + (10.0 / (1.0 + Math.max(0, 5 - term.length()))));

            // BM25 TF component
            float tfNorm = (tf * (BM25_K1 + 1.0f))
                    / (tf + BM25_K1 * (1.0f - BM25_B + BM25_B * (docLength / AVG_DOC_LENGTH)));

            bm25Score += idf * tfNorm;
        }

        // Normalize BM25 score to 0-60 range
        float normalizedBm25 = Math.min(60.0f, bm25Score * 10.0f);

        // Title match bonuses (0-40 range)
        float titleBonus = 0;
        if (lowerTitle.equals(lowerTerm)) {
            titleBonus = 40; // Exact title match
        } else if (lowerTitle.startsWith(lowerTerm)) {
            titleBonus = 30; // Title starts with term
        } else if (lowerTitle.contains(lowerTerm)) {
            titleBonus = 20; // Title contains term
        } else {
            // Check if all query words appear in title
            boolean allInTitle = true;
            for (String term : queryTerms) {
                if (!lowerTitle.contains(term)) {
                    allInTitle = false;
                    break;
                }
            }
            if (allInTitle && queryTerms.length > 1) {
                titleBonus = 15;
            }
        }

        // Article quality bonus (max 10)
        float qualityBonus = 0;
        if (text.length() > 5000) {
            qualityBonus = 5;
        } else if (text.length() < 300) {
            qualityBonus = -5; // Penalty for stubs
        }

        int finalScore = Math.round(normalizedBm25 + titleBonus + qualityBonus);
        return Math.max(0, Math.min(100, finalScore));
    }

    /**
     * Count occurrences of a substring in text.
     */
    private int countOccurrences(@NonNull String text, @NonNull String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /**
     * Cache entry with expiration.
     */
    private static class CacheEntry {
        final List<ArticleSource> results;
        final long timestamp;

        CacheEntry(@NonNull List<ArticleSource> results) {
            this.results = new ArrayList<>(results);
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
