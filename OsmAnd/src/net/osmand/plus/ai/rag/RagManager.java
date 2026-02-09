package net.osmand.plus.ai.rag;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.ai.LlmManager;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LAMPP Phase 7: Orchestrates the RAG (Retrieval-Augmented Generation) pipeline.
 *
 * Coordinates between:
 * 1. QueryClassifier - determines if Wikipedia lookup is needed
 * 2. ZimSearchAdapter - searches Wikipedia ZIM files
 * 3. PromptBuilder - constructs context-augmented prompts
 * 4. LlmManager - generates responses with streaming
 *
 * The pipeline:
 * User Query → Classify → (Optional: Search Wikipedia) → Build Prompt → Generate Response
 */
public class RagManager {

    private static final Log LOG = PlatformUtil.getLog(RagManager.class);

    // Default maximum articles to include as context
    private static final int DEFAULT_MAX_SOURCES = 3;

    // Default token budget for context
    private static final int DEFAULT_CONTEXT_TOKENS = 3000;

    private final OsmandApplication app;
    private final LlmManager llmManager;
    private final ZimSearchAdapter zimAdapter;
    private final QueryClassifier classifier;
    private final PromptBuilder promptBuilder;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // Configuration
    private int maxSources = DEFAULT_MAX_SOURCES;
    private int contextTokenBudget = DEFAULT_CONTEXT_TOKENS;
    private boolean wikipediaEnabled = true;

    public RagManager(@NonNull OsmandApplication app, @NonNull LlmManager llmManager) {
        this.app = app;
        this.llmManager = llmManager;
        this.zimAdapter = new ZimSearchAdapter(app);
        this.classifier = new QueryClassifier();
        this.promptBuilder = new PromptBuilder();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        LOG.info("RagManager initialized");
    }

    /**
     * Process a user query with RAG pipeline.
     *
     * @param query User's question
     * @param callback Callback for streaming results and completion
     */
    public void queryAsync(@NonNull String query, @NonNull RagCallback callback) {
        executor.execute(() -> {
            try {
                processQuery(query, callback);
            } catch (Exception e) {
                LOG.error("Error in RAG pipeline", e);
                mainHandler.post(() -> callback.onError("RAG error: " + e.getMessage()));
            }
        });
    }

    /**
     * Process a query synchronously (called from executor thread).
     */
    private void processQuery(@NonNull String query, @NonNull RagCallback callback) {
        LOG.info("Processing query: " + query.substring(0, Math.min(50, query.length())) + "...");

        // Check if LLM is ready
        if (!llmManager.isModelLoaded()) {
            mainHandler.post(() -> callback.onError("No LLM model loaded. Please load a model first."));
            return;
        }

        // Classify the query
        QueryClassifier.QueryType queryType = classifier.classify(query);
        LOG.debug("Query classified as: " + queryType);

        // Determine if we should search Wikipedia
        boolean shouldSearchWikipedia = wikipediaEnabled
                && queryType.needsWikipedia()
                && zimAdapter.isAvailable();

        List<ArticleSource> sources = new ArrayList<>();
        long searchTimeMs = 0;

        if (shouldSearchWikipedia) {
            // Extract search terms
            List<String> searchTerms = classifier.extractSearchTerms(query);
            LOG.info("Search terms: " + searchTerms);

            // Notify callback
            mainHandler.post(() -> callback.onSearchStarted(searchTerms));

            // Search Wikipedia
            long searchStart = System.currentTimeMillis();
            sources = zimAdapter.searchRelevant(searchTerms, maxSources);
            searchTimeMs = System.currentTimeMillis() - searchStart;

            // Fit to token budget
            if (!sources.isEmpty()) {
                sources = zimAdapter.fitToBudget(sources, contextTokenBudget);
            }

            // Notify callback
            final List<ArticleSource> finalSources = sources;
            final long finalSearchTime = searchTimeMs;
            mainHandler.post(() -> callback.onSearchComplete(finalSources, finalSearchTime));

            LOG.info("Wikipedia search: " + sources.size() + " sources in " + searchTimeMs + "ms");
        }

        // Build prompt
        String prompt;
        if (!sources.isEmpty()) {
            prompt = promptBuilder.buildPrompt(query, sources, contextTokenBudget);
        } else if (shouldSearchWikipedia) {
            // Searched but found nothing
            prompt = promptBuilder.buildNoContextPrompt(query);
        } else {
            // Didn't search (conversational, navigation, etc.)
            prompt = promptBuilder.buildSimplePrompt(query);
        }

        LOG.debug("Built prompt, length: " + prompt.length() + " chars");

        // Notify callback that generation is starting
        final boolean usesWikipedia = !sources.isEmpty();
        mainHandler.post(() -> callback.onGenerationStarted(usesWikipedia));

        // Create response builder
        RagResponse.Builder responseBuilder = new RagResponse.Builder();
        responseBuilder.setSources(sources);
        responseBuilder.setSearchTimeMs(searchTimeMs);

        // Generate response with streaming
        llmManager.generateResponseAsync(prompt, new LlmManager.LlmCallback() {
            @Override
            public void onPartialResult(String partialText) {
                responseBuilder.appendAnswer("");
                mainHandler.post(() -> callback.onPartialResult(partialText));
            }

            @Override
            public void onComplete(String fullResponse) {
                // Build final response
                RagResponse response = RagResponse.success(
                        fullResponse,
                        responseBuilder.build().getSources(),
                        responseBuilder.build().getSearchTimeMs(),
                        responseBuilder.build().getGenerationTimeMs()
                );

                LOG.info("RAG complete: " + response);
                mainHandler.post(() -> callback.onComplete(response));
            }

            @Override
            public void onError(String error) {
                LOG.error("LLM generation error: " + error);
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    /**
     * Check if Wikipedia search is available.
     */
    public boolean isWikipediaAvailable() {
        return zimAdapter.isAvailable();
    }

    /**
     * Get the title of the currently loaded Wikipedia ZIM.
     */
    @Nullable
    public String getWikipediaTitle() {
        return zimAdapter.getZimTitle();
    }

    /**
     * Check if the LLM is ready for queries.
     */
    public boolean isReady() {
        return llmManager.isModelLoaded();
    }

    /**
     * Check if currently generating a response.
     */
    public boolean isGenerating() {
        return llmManager.isGenerating();
    }

    /**
     * Stop the current generation.
     */
    public void stopGeneration() {
        llmManager.stopGeneration();
    }

    /**
     * Enable or disable Wikipedia integration.
     */
    public void setWikipediaEnabled(boolean enabled) {
        this.wikipediaEnabled = enabled;
    }

    /**
     * Check if Wikipedia integration is enabled.
     */
    public boolean isWikipediaEnabled() {
        return wikipediaEnabled;
    }

    /**
     * Set the maximum number of Wikipedia sources to use.
     */
    public void setMaxSources(int maxSources) {
        this.maxSources = Math.max(1, Math.min(5, maxSources));
    }

    /**
     * Get the maximum number of sources.
     */
    public int getMaxSources() {
        return maxSources;
    }

    /**
     * Set the token budget for context.
     */
    public void setContextTokenBudget(int budget) {
        this.contextTokenBudget = Math.max(500, Math.min(6000, budget));
    }

    /**
     * Get the context token budget.
     */
    public int getContextTokenBudget() {
        return contextTokenBudget;
    }

    /**
     * Get status summary for UI display.
     */
    @NonNull
    public String getStatusSummary() {
        StringBuilder sb = new StringBuilder();

        sb.append("LLM: ");
        if (llmManager.isModelLoaded()) {
            sb.append(llmManager.getCurrentModelName());
        } else {
            sb.append("Not loaded");
        }

        sb.append("\nWikipedia: ");
        if (zimAdapter.isAvailable()) {
            sb.append(zimAdapter.getZimTitle());
            if (!wikipediaEnabled) {
                sb.append(" (disabled)");
            }
        } else {
            sb.append("Not loaded");
        }

        return sb.toString();
    }

    /**
     * Clear the Wikipedia search cache.
     */
    public void clearCache() {
        zimAdapter.clearCache();
    }

    /**
     * Shutdown the manager and release resources.
     */
    public void shutdown() {
        executor.shutdown();
        clearCache();
    }
}
