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
 * LAMPP Phase 7 & 8: Orchestrates the RAG (Retrieval-Augmented Generation) pipeline.
 *
 * Coordinates between:
 * 1. QueryClassifier - determines if Wikipedia or POI lookup is needed
 * 2. ZimSearchAdapter - searches Wikipedia ZIM files
 * 3. MapDataAdapter - searches POI database (Phase 8)
 * 4. PromptBuilder - constructs context-augmented prompts
 * 5. LlmManager - generates responses with streaming
 *
 * The pipeline:
 * User Query → Classify → (Optional: Search Wikipedia/POI) → Build Prompt → Generate Response
 */
public class RagManager {

    private static final Log LOG = PlatformUtil.getLog(RagManager.class);

    // Default maximum articles/POIs to include as context
    // Reduced from 3 to 2 to fit in smaller model context windows
    private static final int DEFAULT_MAX_SOURCES = 2;
    private static final int DEFAULT_MAX_POIS = 5;

    // Default token budget for context
    // Reduced from 3000 to 1500 for TinyLlama and other small models (2K-4K context)
    private static final int DEFAULT_CONTEXT_TOKENS = 1500;

    // Default search radius for POI in meters
    private static final int DEFAULT_POI_RADIUS = 1000;

    // Maximum search radius for place/direction queries (in meters)
    private static final int MAX_SEARCH_RADIUS = 500000;  // 500 km for city searches

    private final OsmandApplication app;
    private final LlmManager llmManager;
    private final ZimSearchAdapter zimAdapter;
    private final MapDataAdapter mapAdapter;    // Phase 8
    private final QueryClassifier classifier;
    private final PromptBuilder promptBuilder;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // Configuration
    private int maxSources = DEFAULT_MAX_SOURCES;
    private int maxPois = DEFAULT_MAX_POIS;
    private int contextTokenBudget = DEFAULT_CONTEXT_TOKENS;
    private int poiSearchRadius = DEFAULT_POI_RADIUS;
    private boolean wikipediaEnabled = true;
    private boolean poiSearchEnabled = true;     // Phase 8

    public RagManager(@NonNull OsmandApplication app, @NonNull LlmManager llmManager) {
        this.app = app;
        this.llmManager = llmManager;
        this.zimAdapter = new ZimSearchAdapter(app);
        this.mapAdapter = new MapDataAdapter(app);   // Phase 8
        this.classifier = new QueryClassifier();
        this.promptBuilder = new PromptBuilder();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        LOG.info("RagManager initialized (Phase 8: POI search enabled)");
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

        // Determine if we should search POIs (Phase 8)
        boolean shouldSearchPoi = poiSearchEnabled
                && queryType.needsPoiSearch()
                && mapAdapter.isAvailable();

        // Check for direction queries about places (Phase 8.2)
        boolean isDirectionQuery = queryType == QueryClassifier.QueryType.DIRECTION_QUERY
                && mapAdapter.isAvailable();

        // Also check for hybrid queries (location context in factual query)
        boolean isHybridQuery = shouldSearchWikipedia
                && classifier.hasLocationContext(query)
                && mapAdapter.isAvailable()
                && poiSearchEnabled;

        List<ArticleSource> wikiSources = new ArrayList<>();
        List<PoiSource> poiSources = new ArrayList<>();
        List<PlaceResult> placeResults = new ArrayList<>();  // Phase 8.2: Direction queries
        long wikiSearchTimeMs = 0;
        long poiSearchTimeMs = 0;
        long placeSearchTimeMs = 0;

        // Search Wikipedia if needed
        if (shouldSearchWikipedia) {
            // Extract search terms
            List<String> searchTerms = classifier.extractSearchTerms(query);
            LOG.info("Wikipedia search terms: " + searchTerms);

            // Notify callback
            mainHandler.post(() -> callback.onSearchStarted(searchTerms));

            // Search Wikipedia
            long searchStart = System.currentTimeMillis();
            wikiSources = zimAdapter.searchRelevant(searchTerms, maxSources);
            wikiSearchTimeMs = System.currentTimeMillis() - searchStart;

            // Fit to token budget
            if (!wikiSources.isEmpty()) {
                int wikiBudget = isHybridQuery ? (int)(contextTokenBudget * 0.6) : contextTokenBudget;
                wikiSources = zimAdapter.fitToBudget(wikiSources, wikiBudget);
            }

            // Notify callback
            final List<ArticleSource> finalWikiSources = wikiSources;
            final long finalWikiSearchTime = wikiSearchTimeMs;
            mainHandler.post(() -> callback.onSearchComplete(finalWikiSources, finalWikiSearchTime));

            LOG.info("Wikipedia search: " + wikiSources.size() + " sources in " + wikiSearchTimeMs + "ms");
        }

        // Search POIs if needed (Phase 8)
        if (shouldSearchPoi || isHybridQuery) {
            // Get user location
            int radius = classifier.extractSearchRadius(query);
            if (radius <= 0) {
                radius = poiSearchRadius;
            }
            LocationContext location = mapAdapter.getCurrentLocation(radius);

            if (location != null) {
                List<String> poiTerms = classifier.extractPoiSearchTerms(query);
                LOG.info("POI search terms: " + poiTerms + " at " + location);

                // Notify callback
                final LocationContext finalLocation = location;
                mainHandler.post(() -> callback.onPoiSearchStarted(poiTerms, finalLocation));

                // Search POIs
                long poiSearchStart = System.currentTimeMillis();
                poiSources = mapAdapter.searchNearby(location, query, maxPois);
                poiSearchTimeMs = System.currentTimeMillis() - poiSearchStart;

                // Notify callback
                final List<PoiSource> finalPoiSources = poiSources;
                final long finalPoiSearchTime = poiSearchTimeMs;
                mainHandler.post(() -> callback.onPoiSearchComplete(finalPoiSources, finalPoiSearchTime));

                LOG.info("POI search: " + poiSources.size() + " results in " + poiSearchTimeMs + "ms");
            } else {
                LOG.warn("Location unavailable for POI search");
                mainHandler.post(() -> callback.onError("Location unavailable. Please enable GPS."));
                // Continue without POI data if this is a hybrid query
                if (!isHybridQuery && shouldSearchPoi) {
                    return;
                }
            }
        }

        // Search for places if this is a direction query (Phase 8.2)
        if (isDirectionQuery) {
            // Extract place name from the query
            String placeName = classifier.extractPlaceName(query);
            LOG.info("Direction query - searching for place: " + placeName);

            if (!placeName.isEmpty()) {
                LocationContext location = mapAdapter.getCurrentLocation(MAX_SEARCH_RADIUS);

                if (location != null) {
                    // Notify callback (reusing POI callback for place search)
                    List<String> searchTerms = new ArrayList<>();
                    searchTerms.add(placeName);
                    final LocationContext finalLocation = location;
                    mainHandler.post(() -> callback.onPoiSearchStarted(searchTerms, finalLocation));

                    // Search for the place
                    long placeSearchStart = System.currentTimeMillis();
                    placeResults = mapAdapter.searchPlace(placeName, location, 5);
                    placeSearchTimeMs = System.currentTimeMillis() - placeSearchStart;

                    LOG.info("Place search found " + placeResults.size() + " results in " + placeSearchTimeMs + "ms");

                    // Notify callback (convert to PoiSource for compatibility)
                    final long finalPlaceSearchTime = placeSearchTimeMs;
                    mainHandler.post(() -> callback.onPoiSearchComplete(new ArrayList<>(), finalPlaceSearchTime));
                } else {
                    LOG.warn("Location unavailable for direction query");
                    mainHandler.post(() -> callback.onError("Location unavailable. Please enable GPS for direction queries."));
                    return;
                }
            }
        }

        // Build prompt based on what data we have
        String prompt;
        if (!placeResults.isEmpty()) {
            // Direction query about a place (Phase 8.2)
            LocationContext location = mapAdapter.getCurrentLocation(MAX_SEARCH_RADIUS);
            prompt = promptBuilder.buildDirectionPrompt(query, placeResults, location, contextTokenBudget);
        } else if (!wikiSources.isEmpty() && !poiSources.isEmpty()) {
            // Hybrid: both Wikipedia and POI
            prompt = promptBuilder.buildHybridPrompt(query, wikiSources, poiSources, contextTokenBudget);
        } else if (!poiSources.isEmpty()) {
            // POI only
            LocationContext location = mapAdapter.getCurrentLocation(poiSearchRadius);
            if (location != null) {
                prompt = promptBuilder.buildLocationPrompt(query, poiSources, location, contextTokenBudget);
            } else {
                prompt = promptBuilder.buildSimplePrompt(query);
            }
        } else if (!wikiSources.isEmpty()) {
            // Wikipedia only
            prompt = promptBuilder.buildPrompt(query, wikiSources, contextTokenBudget);
        } else if (shouldSearchWikipedia) {
            // Searched Wikipedia but found nothing
            prompt = promptBuilder.buildNoContextPrompt(query);
        } else if (shouldSearchPoi || isDirectionQuery) {
            // Searched POI/place but found nothing
            prompt = promptBuilder.buildNoPoiPrompt(query);
        } else {
            // Didn't search (conversational, etc.)
            prompt = promptBuilder.buildSimplePrompt(query);
        }

        LOG.debug("Built prompt, length: " + prompt.length() + " chars");

        // Notify callback that generation is starting
        final boolean usesWikipedia = !wikiSources.isEmpty();
        final boolean usesPoiData = !poiSources.isEmpty();
        mainHandler.post(() -> callback.onGenerationStarted(usesWikipedia, usesPoiData));

        // Create response builder
        RagResponse.Builder responseBuilder = new RagResponse.Builder();
        responseBuilder.setSources(wikiSources);
        responseBuilder.setPoiSources(poiSources);
        responseBuilder.setSearchTimeMs(wikiSearchTimeMs);
        responseBuilder.setPoiSearchTimeMs(poiSearchTimeMs);

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
                RagResponse response = responseBuilder.build();
                // Create the actual response with the full answer
                RagResponse finalResponse;
                if (!responseBuilder.getSources().isEmpty() || !responseBuilder.getPoiSources().isEmpty()) {
                    finalResponse = RagResponse.successHybrid(
                            fullResponse,
                            responseBuilder.getSources(),
                            responseBuilder.getPoiSources(),
                            responseBuilder.getSearchTimeMs(),
                            responseBuilder.getPoiSearchTimeMs(),
                            responseBuilder.getGenerationTimeMs()
                    );
                } else {
                    finalResponse = RagResponse.successWithoutWikipedia(
                            fullResponse,
                            responseBuilder.getGenerationTimeMs()
                    );
                }

                LOG.info("RAG complete: " + finalResponse);
                mainHandler.post(() -> callback.onComplete(finalResponse));
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
     * Enable or disable POI search (Phase 8).
     */
    public void setPoiSearchEnabled(boolean enabled) {
        this.poiSearchEnabled = enabled;
    }

    /**
     * Check if POI search is enabled (Phase 8).
     */
    public boolean isPoiSearchEnabled() {
        return poiSearchEnabled;
    }

    /**
     * Set the POI search radius in meters (Phase 8).
     */
    public void setPoiSearchRadius(int radiusMeters) {
        this.poiSearchRadius = Math.max(100, Math.min(10000, radiusMeters));
    }

    /**
     * Get the POI search radius (Phase 8).
     */
    public int getPoiSearchRadius() {
        return poiSearchRadius;
    }

    /**
     * Set maximum number of POI results (Phase 8).
     */
    public void setMaxPois(int maxPois) {
        this.maxPois = Math.max(1, Math.min(20, maxPois));
    }

    /**
     * Get maximum POI results (Phase 8).
     */
    public int getMaxPois() {
        return maxPois;
    }

    /**
     * Check if POI/Map data is available (Phase 8).
     */
    public boolean isMapDataAvailable() {
        return mapAdapter.isAvailable();
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

        sb.append("\nMap/POI: ");
        if (mapAdapter.isAvailable()) {
            sb.append("Available");
            if (!poiSearchEnabled) {
                sb.append(" (disabled)");
            }
        } else {
            sb.append("No map data");
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
