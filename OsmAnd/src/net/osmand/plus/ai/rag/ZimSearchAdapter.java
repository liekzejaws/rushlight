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

    // LRU cache for search results
    private static final int CACHE_SIZE = 50;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

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

        // Check cache first
        String cacheKey = String.join("|", searchTerms).toLowerCase();
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOG.debug("Cache hit for: " + cacheKey);
            return cached.results;
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
     * Calculate relevance score for an article.
     *
     * @param title Article title
     * @param text Article text
     * @param searchTerm The search term that found this article
     * @return Relevance score (0-100)
     */
    private int calculateRelevance(@NonNull String title, @NonNull String text,
                                    @NonNull String searchTerm) {
        int score = 0;
        String lowerTitle = title.toLowerCase();
        String lowerTerm = searchTerm.toLowerCase();
        String lowerText = text.toLowerCase();

        // Exact title match (highest value)
        if (lowerTitle.equals(lowerTerm)) {
            score += 50;
        }
        // Title starts with search term
        else if (lowerTitle.startsWith(lowerTerm)) {
            score += 40;
        }
        // Title contains search term
        else if (lowerTitle.contains(lowerTerm)) {
            score += 30;
        }

        // Term frequency in first paragraph (first 500 chars)
        String firstPart = lowerText.substring(0, Math.min(500, lowerText.length()));
        int occurrences = countOccurrences(firstPart, lowerTerm);
        score += Math.min(occurrences * 5, 25);

        // Bonus for longer articles (more content)
        if (text.length() > 5000) {
            score += 10;
        } else if (text.length() > 2000) {
            score += 5;
        }

        // Penalty for very short articles
        if (text.length() < 500) {
            score -= 10;
        }

        return Math.max(0, Math.min(100, score));
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
