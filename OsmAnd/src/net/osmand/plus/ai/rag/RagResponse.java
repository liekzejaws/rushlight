package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LAMPP Phase 7 & 8: Response from the RAG pipeline.
 *
 * Contains the generated answer text along with source citations
 * (Wikipedia articles and/or POIs) and timing/performance metadata.
 */
public class RagResponse {

    private final String answer;
    private final List<ArticleSource> sources;       // Wikipedia sources
    private final List<PoiSource> poiSources;         // POI sources (Phase 8)
    private final long searchTimeMs;                  // Wikipedia search time
    private final long poiSearchTimeMs;               // POI search time (Phase 8)
    private final long generationTimeMs;
    private final boolean usedWikipedia;
    private final boolean usedPoiData;                // Phase 8
    private final long totalPipelineTimeMs;            // v0.8: end-to-end pipeline time
    private final String error;

    private RagResponse(@NonNull String answer, @NonNull List<ArticleSource> sources,
                        @NonNull List<PoiSource> poiSources,
                        long searchTimeMs, long poiSearchTimeMs, long generationTimeMs,
                        boolean usedWikipedia, boolean usedPoiData, long totalPipelineTimeMs,
                        @Nullable String error) {
        this.answer = answer;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.poiSources = Collections.unmodifiableList(new ArrayList<>(poiSources));
        this.searchTimeMs = searchTimeMs;
        this.poiSearchTimeMs = poiSearchTimeMs;
        this.generationTimeMs = generationTimeMs;
        this.usedWikipedia = usedWikipedia;
        this.usedPoiData = usedPoiData;
        this.totalPipelineTimeMs = totalPipelineTimeMs;
        this.error = error;
    }

    /**
     * Create a successful response with Wikipedia sources.
     */
    @NonNull
    public static RagResponse success(@NonNull String answer, @NonNull List<ArticleSource> sources,
                                       long searchTimeMs, long generationTimeMs) {
        return new RagResponse(answer, sources, Collections.emptyList(),
                searchTimeMs, 0, generationTimeMs, !sources.isEmpty(), false, 0, null);
    }

    /**
     * Create a successful response with POI sources (Phase 8).
     */
    @NonNull
    public static RagResponse successWithPoi(@NonNull String answer, @NonNull List<PoiSource> poiSources,
                                              long poiSearchTimeMs, long generationTimeMs) {
        return new RagResponse(answer, Collections.emptyList(), poiSources,
                0, poiSearchTimeMs, generationTimeMs, false, !poiSources.isEmpty(), 0, null);
    }

    /**
     * Create a successful response with both Wikipedia and POI sources (Phase 8: Hybrid).
     */
    @NonNull
    public static RagResponse successHybrid(@NonNull String answer,
                                             @NonNull List<ArticleSource> wikiSources,
                                             @NonNull List<PoiSource> poiSources,
                                             long wikiSearchTimeMs, long poiSearchTimeMs,
                                             long generationTimeMs) {
        return new RagResponse(answer, wikiSources, poiSources,
                wikiSearchTimeMs, poiSearchTimeMs, generationTimeMs,
                !wikiSources.isEmpty(), !poiSources.isEmpty(), 0, null);
    }

    /**
     * Create a successful response without Wikipedia (direct LLM response).
     */
    @NonNull
    public static RagResponse successWithoutWikipedia(@NonNull String answer, long generationTimeMs) {
        return new RagResponse(answer, Collections.emptyList(), Collections.emptyList(),
                0, 0, generationTimeMs, false, false, 0, null);
    }

    /**
     * Create an error response.
     */
    @NonNull
    public static RagResponse error(@NonNull String error) {
        return new RagResponse("", Collections.emptyList(), Collections.emptyList(),
                0, 0, 0, false, false, 0, error);
    }

    /**
     * Get the generated answer text.
     */
    @NonNull
    public String getAnswer() {
        return answer;
    }

    /**
     * Get the list of Wikipedia sources used.
     */
    @NonNull
    public List<ArticleSource> getSources() {
        return sources;
    }

    /**
     * Check if any Wikipedia sources were used.
     */
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    /**
     * Get the list of POI sources (Phase 8).
     */
    @NonNull
    public List<PoiSource> getPoiSources() {
        return poiSources;
    }

    /**
     * Check if any POI sources were used (Phase 8).
     */
    public boolean hasPoiSources() {
        return !poiSources.isEmpty();
    }

    /**
     * Check if any sources (Wikipedia or POI) were used.
     */
    public boolean hasAnySources() {
        return hasSources() || hasPoiSources();
    }

    /**
     * Get time spent searching Wikipedia.
     */
    public long getSearchTimeMs() {
        return searchTimeMs;
    }

    /**
     * Get time spent searching POIs (Phase 8).
     */
    public long getPoiSearchTimeMs() {
        return poiSearchTimeMs;
    }

    /**
     * Get time spent generating the response.
     */
    public long getGenerationTimeMs() {
        return generationTimeMs;
    }

    /**
     * Get total time for the RAG pipeline (sum of component times).
     */
    public long getTotalTimeMs() {
        return searchTimeMs + poiSearchTimeMs + generationTimeMs;
    }

    /**
     * Get the end-to-end pipeline time including classification and prompt building overhead.
     * Returns 0 if not measured.
     */
    public long getTotalPipelineTimeMs() {
        return totalPipelineTimeMs > 0 ? totalPipelineTimeMs : getTotalTimeMs();
    }

    /**
     * Check if Wikipedia was used for this response.
     */
    public boolean usedWikipedia() {
        return usedWikipedia;
    }

    /**
     * Check if POI data was used for this response (Phase 8).
     */
    public boolean usedPoiData() {
        return usedPoiData;
    }

    /**
     * Check if this is an error response.
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * Get the error message, if any.
     */
    @Nullable
    public String getError() {
        return error;
    }

    /**
     * Get a formatted citation string for Wikipedia sources.
     * Example: "Sources: Brooklyn Bridge, East River"
     */
    @NonNull
    public String getFormattedSources() {
        if (sources.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("Sources: ");
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sources.get(i).getTitle());
        }
        return sb.toString();
    }

    /**
     * Get a formatted string for POI sources (Phase 8).
     * Example: "Nearby: Starbucks (150m NE), Blue Bottle (320m N)"
     */
    @NonNull
    public String getFormattedPoiSources() {
        if (poiSources.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("Nearby: ");
        for (int i = 0; i < poiSources.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            PoiSource poi = poiSources.get(i);
            sb.append(poi.getName());
            sb.append(" (").append(poi.getDistanceDirection()).append(")");
        }
        return sb.toString();
    }

    /**
     * Get a combined formatted string for all sources.
     */
    @NonNull
    public String getFormattedAllSources() {
        StringBuilder sb = new StringBuilder();
        if (!sources.isEmpty()) {
            sb.append(getFormattedSources());
        }
        if (!poiSources.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(getFormattedPoiSources());
        }
        return sb.toString();
    }

    /**
     * Get performance summary string.
     */
    @NonNull
    public String getPerformanceSummary() {
        StringBuilder sb = new StringBuilder();
        if (usedWikipedia) {
            sb.append("Wiki: ").append(searchTimeMs).append("ms");
        }
        if (usedPoiData) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("POI: ").append(poiSearchTimeMs).append("ms");
        }
        if (sb.length() > 0) sb.append(", ");
        sb.append("Gen: ").append(generationTimeMs).append("ms");
        sb.append(", Total: ").append(getTotalTimeMs()).append("ms");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "RagResponse{" +
                "answerLength=" + answer.length() +
                ", wikiSources=" + sources.size() +
                ", poiSources=" + poiSources.size() +
                ", usedWikipedia=" + usedWikipedia +
                ", usedPoiData=" + usedPoiData +
                ", totalTimeMs=" + getTotalTimeMs() +
                (error != null ? ", error=" + error : "") +
                '}';
    }

    /**
     * Builder for constructing partial responses during streaming.
     */
    public static class Builder {
        private StringBuilder answer = new StringBuilder();
        private List<ArticleSource> sources = new ArrayList<>();
        private List<PoiSource> poiSources = new ArrayList<>();
        private long searchTimeMs = 0;
        private long poiSearchTimeMs = 0;
        private long generationStartTime = 0;
        private boolean usedWikipedia = false;
        private boolean usedPoiData = false;
        private long totalPipelineTimeMs = 0;
        private String error = null;

        public Builder() {
            generationStartTime = System.currentTimeMillis();
        }

        public Builder setSources(@NonNull List<ArticleSource> sources) {
            this.sources = new ArrayList<>(sources);
            this.usedWikipedia = !sources.isEmpty();
            return this;
        }

        public Builder setPoiSources(@NonNull List<PoiSource> poiSources) {
            this.poiSources = new ArrayList<>(poiSources);
            this.usedPoiData = !poiSources.isEmpty();
            return this;
        }

        public Builder setSearchTimeMs(long timeMs) {
            this.searchTimeMs = timeMs;
            return this;
        }

        public Builder setPoiSearchTimeMs(long timeMs) {
            this.poiSearchTimeMs = timeMs;
            return this;
        }

        public Builder setTotalPipelineTimeMs(long timeMs) {
            this.totalPipelineTimeMs = timeMs;
            return this;
        }

        public Builder appendAnswer(@NonNull String token) {
            this.answer.append(token);
            return this;
        }

        public Builder setError(@NonNull String error) {
            this.error = error;
            return this;
        }

        @NonNull
        public String getCurrentAnswer() {
            return answer.toString();
        }

        @NonNull
        public List<ArticleSource> getSources() {
            return sources;
        }

        @NonNull
        public List<PoiSource> getPoiSources() {
            return poiSources;
        }

        public long getSearchTimeMs() {
            return searchTimeMs;
        }

        public long getPoiSearchTimeMs() {
            return poiSearchTimeMs;
        }

        public long getGenerationTimeMs() {
            return System.currentTimeMillis() - generationStartTime;
        }

        @NonNull
        public RagResponse build() {
            long generationTimeMs = System.currentTimeMillis() - generationStartTime;
            if (error != null) {
                return RagResponse.error(error);
            }
            return new RagResponse(answer.toString(), sources, poiSources,
                    searchTimeMs, poiSearchTimeMs, generationTimeMs,
                    usedWikipedia, usedPoiData, totalPipelineTimeMs, null);
        }
    }
}
