package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * LAMPP Phase 7: Builds prompts for RAG-augmented LLM generation.
 *
 * Constructs prompts that include Wikipedia context and citation instructions.
 */
public class PromptBuilder {

    /**
     * Template for RAG prompt with Wikipedia context.
     *
     * The template instructs the LLM to:
     * 1. Use the provided Wikipedia excerpts as context
     * 2. Cite sources using [Source: Title] format
     * 3. Acknowledge if the excerpts don't contain relevant information
     */
    private static final String RAG_TEMPLATE =
            "Use the following Wikipedia excerpts to answer the question.\n" +
            "Cite sources as [Source: Article Title] when using information from them.\n" +
            "If the excerpts don't contain relevant information, acknowledge this and answer based on your knowledge.\n\n" +
            "=== Wikipedia Context ===\n" +
            "%s" +
            "=== End Context ===\n\n" +
            "Question: %s\n\n" +
            "Answer:";

    /**
     * Template for a single article in the context.
     */
    private static final String ARTICLE_TEMPLATE =
            "[%s]\n%s\n\n";

    /**
     * Template for fallback when no Wikipedia context is available.
     */
    private static final String NO_CONTEXT_TEMPLATE =
            "Note: No Wikipedia articles were found for this topic.\n\n" +
            "Question: %s\n\n" +
            "Answer:";

    /**
     * System prompt prefix for the assistant.
     */
    private static final String SYSTEM_PREFIX =
            "You are a helpful assistant with access to offline Wikipedia. " +
            "When citing Wikipedia, use the format [Source: Article Title]. " +
            "Be concise and accurate.\n\n";

    /**
     * Maximum context tokens to use (leaving room for response).
     * Reduced from 3000 to 1500 for TinyLlama and other small models.
     */
    private static final int DEFAULT_CONTEXT_BUDGET = 1500;

    /**
     * Build a RAG prompt with Wikipedia context.
     *
     * @param query User's question
     * @param sources Wikipedia articles to include as context
     * @return Complete prompt for LLM
     */
    @NonNull
    public String buildPrompt(@NonNull String query, @NonNull List<ArticleSource> sources) {
        if (sources.isEmpty()) {
            return buildNoContextPrompt(query);
        }

        StringBuilder contextBuilder = new StringBuilder();

        for (ArticleSource source : sources) {
            contextBuilder.append(String.format(ARTICLE_TEMPLATE,
                    source.getTitle(),
                    source.getText()));
        }

        return String.format(RAG_TEMPLATE, contextBuilder.toString(), query);
    }

    /**
     * Build a RAG prompt with Wikipedia context, respecting token budget.
     *
     * @param query User's question
     * @param sources Wikipedia articles to include as context
     * @param maxContextTokens Maximum tokens for context (excludes query)
     * @return Complete prompt for LLM
     */
    @NonNull
    public String buildPrompt(@NonNull String query, @NonNull List<ArticleSource> sources,
                               int maxContextTokens) {
        if (sources.isEmpty()) {
            return buildNoContextPrompt(query);
        }

        StringBuilder contextBuilder = new StringBuilder();
        int remainingTokens = maxContextTokens;

        for (ArticleSource source : sources) {
            // Calculate space needed for this article
            String articleHeader = "[" + source.getTitle() + "]\n";
            int headerTokens = estimateTokens(articleHeader);

            if (remainingTokens - headerTokens <= 100) {
                // Not enough space for meaningful content
                break;
            }

            // Calculate how much text we can include
            int textBudget = remainingTokens - headerTokens - 10; // 10 tokens buffer
            String text = truncateToTokens(source.getText(), textBudget);

            contextBuilder.append(articleHeader);
            contextBuilder.append(text);
            contextBuilder.append("\n\n");

            remainingTokens -= (headerTokens + estimateTokens(text) + 2);

            if (remainingTokens <= 100) {
                break;
            }
        }

        return String.format(RAG_TEMPLATE, contextBuilder.toString(), query);
    }

    /**
     * Build a prompt when no Wikipedia context is available.
     *
     * @param query User's question
     * @return Prompt for LLM without context
     */
    @NonNull
    public String buildNoContextPrompt(@NonNull String query) {
        return String.format(NO_CONTEXT_TEMPLATE, query);
    }

    /**
     * Build a simple prompt without any RAG augmentation.
     *
     * @param query User's question
     * @return Simple prompt
     */
    @NonNull
    public String buildSimplePrompt(@NonNull String query) {
        return query;
    }

    /**
     * Get the system prompt prefix.
     */
    @NonNull
    public String getSystemPrefix() {
        return SYSTEM_PREFIX;
    }

    /**
     * Get the default context token budget.
     */
    public int getDefaultContextBudget() {
        return DEFAULT_CONTEXT_BUDGET;
    }

    /**
     * Estimate token count for text.
     */
    private int estimateTokens(@NonNull String text) {
        return text.length() / 4;
    }

    /**
     * Truncate text to approximately the specified number of tokens.
     */
    @NonNull
    private String truncateToTokens(@NonNull String text, int maxTokens) {
        int maxChars = maxTokens * 4;

        if (text.length() <= maxChars) {
            return text;
        }

        // Find a sentence boundary before the limit
        int lastPeriod = text.lastIndexOf(". ", maxChars);
        if (lastPeriod > maxChars / 2) {
            return text.substring(0, lastPeriod + 1);
        }

        // Find a paragraph boundary
        int lastParagraph = text.lastIndexOf("\n\n", maxChars);
        if (lastParagraph > maxChars / 2) {
            return text.substring(0, lastParagraph);
        }

        // Truncate at word boundary
        int lastSpace = text.lastIndexOf(' ', maxChars);
        if (lastSpace > maxChars / 2) {
            return text.substring(0, lastSpace) + "...";
        }

        return text.substring(0, maxChars) + "...";
    }

    /**
     * Format citation text for display in chat.
     *
     * @param sources List of sources used
     * @return Formatted citation line
     */
    @NonNull
    public String formatCitations(@NonNull List<ArticleSource> sources) {
        if (sources.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n\nSources: ");
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sources.get(i).getTitle());
        }
        return sb.toString();
    }
}
