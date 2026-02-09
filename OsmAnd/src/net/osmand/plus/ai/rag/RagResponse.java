package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LAMPP Phase 7: Response from the RAG pipeline.
 *
 * Contains the generated answer text along with source citations
 * and timing/performance metadata.
 */
public class RagResponse {

    private final String answer;
    private final List<ArticleSource> sources;
    private final long searchTimeMs;
    private final long generationTimeMs;
    private final boolean usedWikipedia;
    private final String error;

    private RagResponse(@NonNull String answer, @NonNull List<ArticleSource> sources,
                        long searchTimeMs, long generationTimeMs,
                        boolean usedWikipedia, @Nullable String error) {
        this.answer = answer;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.searchTimeMs = searchTimeMs;
        this.generationTimeMs = generationTimeMs;
        this.usedWikipedia = usedWikipedia;
        this.error = error;
    }

    /**
     * Create a successful response with sources.
     */
    @NonNull
    public static RagResponse success(@NonNull String answer, @NonNull List<ArticleSource> sources,
                                       long searchTimeMs, long generationTimeMs) {
        return new RagResponse(answer, sources, searchTimeMs, generationTimeMs, true, null);
    }

    /**
     * Create a successful response without Wikipedia (direct LLM response).
     */
    @NonNull
    public static RagResponse successWithoutWikipedia(@NonNull String answer, long generationTimeMs) {
        return new RagResponse(answer, Collections.emptyList(), 0, generationTimeMs, false, null);
    }

    /**
     * Create an error response.
     */
    @NonNull
    public static RagResponse error(@NonNull String error) {
        return new RagResponse("", Collections.emptyList(), 0, 0, false, error);
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
     * Check if any sources were used.
     */
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    /**
     * Get time spent searching Wikipedia.
     */
    public long getSearchTimeMs() {
        return searchTimeMs;
    }

    /**
     * Get time spent generating the response.
     */
    public long getGenerationTimeMs() {
        return generationTimeMs;
    }

    /**
     * Get total time for the RAG pipeline.
     */
    public long getTotalTimeMs() {
        return searchTimeMs + generationTimeMs;
    }

    /**
     * Check if Wikipedia was used for this response.
     */
    public boolean usedWikipedia() {
        return usedWikipedia;
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
     * Get a formatted citation string for all sources.
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
     * Get performance summary string.
     */
    @NonNull
    public String getPerformanceSummary() {
        if (usedWikipedia) {
            return String.format("Search: %dms, Generation: %dms, Total: %dms",
                    searchTimeMs, generationTimeMs, getTotalTimeMs());
        } else {
            return String.format("Generation: %dms", generationTimeMs);
        }
    }

    @Override
    public String toString() {
        return "RagResponse{" +
                "answerLength=" + answer.length() +
                ", sources=" + sources.size() +
                ", usedWikipedia=" + usedWikipedia +
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
        private long searchTimeMs = 0;
        private long generationStartTime = 0;
        private boolean usedWikipedia = false;
        private String error = null;

        public Builder() {
            generationStartTime = System.currentTimeMillis();
        }

        public Builder setSources(@NonNull List<ArticleSource> sources) {
            this.sources = new ArrayList<>(sources);
            this.usedWikipedia = !sources.isEmpty();
            return this;
        }

        public Builder setSearchTimeMs(long timeMs) {
            this.searchTimeMs = timeMs;
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
        public RagResponse build() {
            long generationTimeMs = System.currentTimeMillis() - generationStartTime;
            if (error != null) {
                return RagResponse.error(error);
            }
            return new RagResponse(answer.toString(), sources,
                    searchTimeMs, generationTimeMs, usedWikipedia, null);
        }
    }
}
