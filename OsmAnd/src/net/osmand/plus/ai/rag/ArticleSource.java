/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * LAMPP Phase 7: Represents a Wikipedia article used as a source for RAG.
 *
 * Contains the article title, extracted text content, and metadata for citations.
 */
public class ArticleSource {

    private final String title;
    private final String text;
    private final int relevanceScore;
    private final String snippet; // Short preview for display

    public ArticleSource(@NonNull String title, @NonNull String text, int relevanceScore) {
        this.title = title;
        this.text = text;
        this.relevanceScore = relevanceScore;
        this.snippet = generateSnippet(text);
    }

    /**
     * Create an ArticleSource with custom snippet.
     */
    public ArticleSource(@NonNull String title, @NonNull String text,
                         int relevanceScore, @Nullable String snippet) {
        this.title = title;
        this.text = text;
        this.relevanceScore = relevanceScore;
        this.snippet = snippet != null ? snippet : generateSnippet(text);
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getText() {
        return text;
    }

    /**
     * Get text truncated to a maximum number of characters.
     */
    @NonNull
    public String getText(int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        // Truncate at sentence boundary if possible
        int lastPeriod = text.lastIndexOf(". ", maxChars);
        if (lastPeriod > maxChars / 2) {
            return text.substring(0, lastPeriod + 1);
        }
        return text.substring(0, maxChars);
    }

    /**
     * Get relevance score (higher = more relevant).
     * Score is based on title match quality and content relevance.
     */
    public int getRelevanceScore() {
        return relevanceScore;
    }

    /**
     * Get a short snippet for display in search results or citations.
     */
    @NonNull
    public String getSnippet() {
        return snippet;
    }

    /**
     * Estimate the number of tokens in the text.
     * Rough estimate: ~4 characters per token for English.
     */
    public int estimateTokens() {
        return text.length() / 4;
    }

    /**
     * Generate citation text for including in prompts.
     */
    @NonNull
    public String toCitation() {
        return "[Source: " + title + "]";
    }

    private static String generateSnippet(@NonNull String text) {
        int snippetLength = 150;
        if (text.length() <= snippetLength) {
            return text;
        }
        // Try to end at a word boundary
        int endIndex = text.lastIndexOf(' ', snippetLength);
        if (endIndex < snippetLength / 2) {
            endIndex = snippetLength;
        }
        return text.substring(0, endIndex) + "...";
    }

    @Override
    public String toString() {
        return "ArticleSource{" +
                "title='" + title + '\'' +
                ", textLength=" + text.length() +
                ", relevance=" + relevanceScore +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArticleSource that = (ArticleSource) o;
        return title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return title.hashCode();
    }
}
