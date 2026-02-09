package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * LAMPP Phase 7 & 8: Callback interface for RAG pipeline responses.
 *
 * Provides lifecycle callbacks for Wikipedia search, POI search, streaming generation,
 * and completion/error states.
 */
public interface RagCallback {

    /**
     * Called when starting to search Wikipedia for relevant articles.
     *
     * @param searchTerms The terms being searched
     */
    default void onSearchStarted(@NonNull List<String> searchTerms) {}

    /**
     * Called when Wikipedia search is complete.
     *
     * @param sources The articles found (may be empty)
     * @param timeMs Time taken for search
     */
    default void onSearchComplete(@NonNull List<ArticleSource> sources, long timeMs) {}

    /**
     * Called when starting to search for POIs near the user's location.
     * (Phase 8: Location-aware search)
     *
     * @param searchTerms The POI types being searched (e.g., "coffee", "restaurant")
     * @param location The user's location context
     */
    default void onPoiSearchStarted(@NonNull List<String> searchTerms, @NonNull LocationContext location) {}

    /**
     * Called when POI search is complete.
     * (Phase 8: Location-aware search)
     *
     * @param poiSources The POIs found (may be empty)
     * @param timeMs Time taken for search
     */
    default void onPoiSearchComplete(@NonNull List<PoiSource> poiSources, long timeMs) {}

    /**
     * Called when LLM generation starts.
     *
     * @param usesWikipedia Whether Wikipedia context is being used
     */
    default void onGenerationStarted(boolean usesWikipedia) {}

    /**
     * Called when LLM generation starts with detailed context info.
     * (Phase 8: Extended to include POI context)
     *
     * @param usesWikipedia Whether Wikipedia context is being used
     * @param usesPoiData Whether POI data is being used
     */
    default void onGenerationStarted(boolean usesWikipedia, boolean usesPoiData) {
        // Default: call the simpler version for backwards compatibility
        onGenerationStarted(usesWikipedia || usesPoiData);
    }

    /**
     * Called with each partial token during streaming generation.
     *
     * @param partialAnswer The accumulated answer so far
     */
    void onPartialResult(@NonNull String partialAnswer);

    /**
     * Called when the full response is complete.
     *
     * @param response The complete RAG response with answer and sources
     */
    void onComplete(@NonNull RagResponse response);

    /**
     * Called when an error occurs at any stage.
     *
     * @param error Error description
     */
    void onError(@NonNull String error);

    /**
     * Simple adapter that only requires implementing completion callbacks.
     */
    abstract class SimpleCallback implements RagCallback {
        @Override
        public void onSearchStarted(@NonNull List<String> searchTerms) {}

        @Override
        public void onSearchComplete(@NonNull List<ArticleSource> sources, long timeMs) {}

        @Override
        public void onPoiSearchStarted(@NonNull List<String> searchTerms, @NonNull LocationContext location) {}

        @Override
        public void onPoiSearchComplete(@NonNull List<PoiSource> poiSources, long timeMs) {}

        @Override
        public void onGenerationStarted(boolean usesWikipedia) {}

        @Override
        public void onGenerationStarted(boolean usesWikipedia, boolean usesPoiData) {}
    }
}
