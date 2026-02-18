package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * LAMPP Phase 7 & 8: Builds prompts for RAG-augmented LLM generation.
 *
 * Constructs prompts that include Wikipedia context, POI data, and citation instructions.
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
            "Use the following knowledge base excerpts to answer the question.\n" +
            "Cite sources as [Source: Article Title] when using information from them.\n" +
            "If the excerpts don't fully cover the topic, supplement with your own knowledge " +
            "and note what comes from the knowledge base vs. general knowledge.\n\n" +
            "=== Knowledge Base ===\n" +
            "%s" +
            "=== End Knowledge Base ===\n\n" +
            "Question: %s\n\n" +
            "Answer:";

    /**
     * Template for location-aware POI search prompt (Phase 8).
     */
    private static final String LOCATION_TEMPLATE =
            "You are helping a user find places nearby.\n" +
            "The user is at: %s\n\n" +
            "=== Nearby Places ===\n" +
            "%s" +
            "=== End Places ===\n\n" +
            "Question: %s\n\n" +
            "Provide helpful information about the nearby places. " +
            "Mention distance and direction when relevant. Be concise.\n\n" +
            "Answer:";

    /**
     * Template for direction/distance queries about places (Phase 8.2).
     */
    private static final String DIRECTION_TEMPLATE =
            "You are helping a user with direction and distance information.\n" +
            "The user is at: %s\n\n" +
            "=== Places Found ===\n" +
            "%s" +
            "=== End Places ===\n\n" +
            "Question: %s\n\n" +
            "Provide clear direction and distance information based on the found places. " +
            "Be specific about cardinal directions (N, NE, E, SE, S, SW, W, NW). " +
            "If multiple places with the same name exist, mention them all.\n\n" +
            "Answer:";

    /**
     * Template for hybrid Wikipedia + POI prompt (Phase 8).
     */
    private static final String HYBRID_TEMPLATE =
            "Use the following information to answer the question.\n\n" +
            "=== Wikipedia Context ===\n" +
            "%s" +
            "=== End Wikipedia ===\n\n" +
            "=== Nearby Places ===\n" +
            "%s" +
            "=== End Places ===\n\n" +
            "Question: %s\n\n" +
            "Cite Wikipedia sources as [Source: Title]. " +
            "Include distance/direction for nearby places.\n\n" +
            "Answer:";

    /**
     * Template for survival guide queries (Phase 14).
     * Prioritizes safety and actionable step-by-step instructions.
     */
    private static final String SURVIVAL_TEMPLATE =
            "Use the following survival and field reference materials to provide accurate, actionable advice.\n" +
            "Cross-reference guide content with Wikipedia context when both are available.\n" +
            "Prioritize safety — when in doubt, recommend the most conservative approach.\n\n" +
            "=== Field Guides ===\n" +
            "%s" +
            "=== End Guides ===\n\n" +
            "%s" +
            "Question: %s\n\n" +
            "Provide clear, step-by-step instructions. " +
            "Cite guides as [Guide: Title] and Wikipedia as [Source: Title]. " +
            "Be specific about quantities, times, measurements, and safety warnings.\n\n" +
            "Answer:";

    /**
     * Template for practical/engineering queries (Phase v0.3).
     * Routes through guide database for hands-on repair and construction knowledge.
     */
    private static final String PRACTICAL_TEMPLATE =
            "Use the following reference materials to provide accurate, hands-on guidance.\n" +
            "Focus on what can be done with available materials and common tools.\n\n" +
            "=== Reference Materials ===\n" +
            "%s" +
            "=== End References ===\n\n" +
            "%s" +
            "Question: %s\n\n" +
            "Provide specific, actionable steps. Include material alternatives when the ideal isn't available. " +
            "Cite references as [Guide: Title] or [Source: Title]. " +
            "Warn about safety hazards clearly.\n\n" +
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
            "Note: No matching references were found in the local knowledge base for this topic.\n" +
            "Answer based on your training knowledge.\n\n" +
            "Question: %s\n\n" +
            "Answer:";

    /**
     * Default system prompt prefix for the assistant.
     * Establishes Rushlight's identity as a purpose-built offline field intelligence system.
     * Can be overridden via LAMPP_SYSTEM_PROMPT setting.
     */
    private static final String DEFAULT_SYSTEM_PREFIX =
            "You are Rushlight, an offline field intelligence assistant built for survival, " +
            "navigation, and practical problem-solving in austere environments. You have " +
            "access to an integrated knowledge base of survival guides, practical engineering " +
            "references, Wikipedia articles, and local map data — all stored on-device with " +
            "no internet required.\n\n" +
            "When answering questions:\n" +
            "- Draw from your knowledge base first, citing sources as [Guide: Title] or [Source: Article Title]\n" +
            "- Give actionable, step-by-step instructions with specific measurements and quantities\n" +
            "- Prioritize safety — recommend the most conservative approach when lives are at stake\n" +
            "- Think like a field engineer: improvise with available materials when ideal tools aren't available\n" +
            "- Be concise and direct — you're being consulted in the field, not in a classroom";

    // ---- System Prompt Presets (selectable from chat UI) ----

    /** Field engineering mode: mechanical, structural, automotive, electrical repair */
    public static final String PRESET_FIELD_ENGINEER =
            "You are Rushlight in field engineering mode. You specialize in practical repairs, " +
            "improvised solutions, and mechanical problem-solving with limited tools and materials. " +
            "Think like a combat engineer or expedition mechanic. Give specific measurements, " +
            "torque values, material properties, and step-by-step procedures. When the ideal " +
            "part or tool isn't available, suggest the best field-expedient alternative. " +
            "Always warn about safety hazards — especially electrical, structural, and chemical risks.";

    /** Field medic mode: first aid, trauma, environmental injuries */
    public static final String PRESET_MEDIC =
            "You are Rushlight in field medic mode. You provide first aid and emergency medical " +
            "guidance for austere environments where professional medical care is unavailable. " +
            "Prioritize life-threatening conditions first (airway, breathing, circulation). " +
            "Give specific dosages, timing, and technique descriptions. Always recommend " +
            "seeking professional medical care when possible. Clearly distinguish between " +
            "what a trained medic should do vs. what an untrained person can safely attempt.";

    /** Navigation mode: maps, compass, celestial, terrain */
    public static final String PRESET_NAVIGATOR =
            "You are Rushlight in navigation mode. You help users navigate using offline maps, " +
            "compass, celestial observation, terrain association, and dead reckoning. Give " +
            "specific bearings, distances, and landmark-based instructions. When GPS is " +
            "unavailable, guide users through alternative position-fixing methods. Reference " +
            "map data and nearby places when available.";

    /** Survival mode: wilderness, emergency preparedness, grid-down scenarios */
    public static final String PRESET_SURVIVAL =
            "You are Rushlight in survival mode. You provide expert wilderness survival and " +
            "emergency preparedness guidance. Cover the priorities: shelter, water, fire, " +
            "food, signaling, and security. Give specific techniques with quantities, timing, " +
            "and environmental considerations. Adapt advice to the user's apparent situation " +
            "and available resources. Safety is paramount — when in doubt, recommend the " +
            "most conservative approach.";

    /** General mode: default Rushlight personality */
    public static final String PRESET_GENERAL = DEFAULT_SYSTEM_PREFIX;

    /**
     * Custom system prompt, set from user settings.
     * If null, DEFAULT_SYSTEM_PREFIX is used.
     */
    @Nullable
    private String customSystemPrompt;

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

        return getSystemPrefix() + String.format(RAG_TEMPLATE, contextBuilder.toString(), query);
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

        return getSystemPrefix() + String.format(RAG_TEMPLATE, contextBuilder.toString(), query);
    }

    /**
     * Build a prompt when no Wikipedia context is available.
     *
     * @param query User's question
     * @return Prompt for LLM without context
     */
    @NonNull
    public String buildNoContextPrompt(@NonNull String query) {
        return getSystemPrefix() + String.format(NO_CONTEXT_TEMPLATE, query);
    }

    /**
     * Build a simple prompt without any RAG augmentation.
     *
     * @param query User's question
     * @return Simple prompt
     */
    @NonNull
    public String buildSimplePrompt(@NonNull String query) {
        return getSystemPrefix() + query;
    }

    /**
     * Set a custom system prompt from user settings.
     */
    public void setCustomSystemPrompt(@Nullable String prompt) {
        this.customSystemPrompt = prompt;
    }

    /**
     * Get the active system prompt prefix (custom or default).
     */
    @NonNull
    public String getSystemPrefix() {
        String prompt = customSystemPrompt != null ? customSystemPrompt : DEFAULT_SYSTEM_PREFIX;
        return prompt + "\n\n";
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
     * Build a prompt for location-based POI search (Phase 8).
     *
     * @param query User's question
     * @param poiSources List of nearby POIs
     * @param location User's location context
     * @return Complete prompt for LLM
     */
    @NonNull
    public String buildLocationPrompt(@NonNull String query,
                                       @NonNull List<PoiSource> poiSources,
                                       @NonNull LocationContext location) {
        if (poiSources.isEmpty()) {
            return buildNoPoiPrompt(query);
        }

        StringBuilder poiContext = new StringBuilder();
        for (PoiSource poi : poiSources) {
            poiContext.append(poi.toPromptString()).append("\n");
        }

        return getSystemPrefix() + String.format(LOCATION_TEMPLATE,
                location.getLocationString(),
                poiContext.toString(),
                query);
    }

    /**
     * Build a prompt for location search with token budget (Phase 8).
     */
    @NonNull
    public String buildLocationPrompt(@NonNull String query,
                                       @NonNull List<PoiSource> poiSources,
                                       @NonNull LocationContext location,
                                       int maxContextTokens) {
        if (poiSources.isEmpty()) {
            return buildNoPoiPrompt(query);
        }

        StringBuilder poiContext = new StringBuilder();
        int remainingTokens = maxContextTokens;

        for (PoiSource poi : poiSources) {
            String entry = poi.toPromptString() + "\n";
            int entryTokens = estimateTokens(entry);

            if (remainingTokens - entryTokens < 50) {
                break;
            }

            poiContext.append(entry);
            remainingTokens -= entryTokens;
        }

        return getSystemPrefix() + String.format(LOCATION_TEMPLATE,
                location.getLocationString(),
                poiContext.toString(),
                query);
    }

    /**
     * Build a hybrid prompt with both Wikipedia and POI context (Phase 8).
     */
    @NonNull
    public String buildHybridPrompt(@NonNull String query,
                                     @NonNull List<ArticleSource> wikiSources,
                                     @NonNull List<PoiSource> poiSources) {
        StringBuilder wikiContext = new StringBuilder();
        for (ArticleSource source : wikiSources) {
            wikiContext.append(String.format(ARTICLE_TEMPLATE,
                    source.getTitle(),
                    source.getText()));
        }

        StringBuilder poiContext = new StringBuilder();
        for (PoiSource poi : poiSources) {
            poiContext.append(poi.toPromptString()).append("\n");
        }

        return getSystemPrefix() + String.format(HYBRID_TEMPLATE,
                wikiContext.toString(),
                poiContext.toString(),
                query);
    }

    /**
     * Build a hybrid prompt with token budget (Phase 8).
     */
    @NonNull
    public String buildHybridPrompt(@NonNull String query,
                                     @NonNull List<ArticleSource> wikiSources,
                                     @NonNull List<PoiSource> poiSources,
                                     int maxContextTokens) {
        // Split budget: 60% wiki, 40% POI
        int wikiBudget = (int) (maxContextTokens * 0.6);
        int poiBudget = maxContextTokens - wikiBudget;

        StringBuilder wikiContext = new StringBuilder();
        int remainingWikiTokens = wikiBudget;

        for (ArticleSource source : wikiSources) {
            String header = "[" + source.getTitle() + "]\n";
            int headerTokens = estimateTokens(header);

            if (remainingWikiTokens - headerTokens <= 50) {
                break;
            }

            int textBudget = remainingWikiTokens - headerTokens - 10;
            String text = truncateToTokens(source.getText(), textBudget);

            wikiContext.append(header);
            wikiContext.append(text);
            wikiContext.append("\n\n");

            remainingWikiTokens -= (headerTokens + estimateTokens(text) + 2);
        }

        StringBuilder poiContext = new StringBuilder();
        int remainingPoiTokens = poiBudget;

        for (PoiSource poi : poiSources) {
            String entry = poi.toPromptString() + "\n";
            int entryTokens = estimateTokens(entry);

            if (remainingPoiTokens - entryTokens < 20) {
                break;
            }

            poiContext.append(entry);
            remainingPoiTokens -= entryTokens;
        }

        return getSystemPrefix() + String.format(HYBRID_TEMPLATE,
                wikiContext.toString(),
                poiContext.toString(),
                query);
    }

    /**
     * Build a prompt when no POI results are found (Phase 8).
     */
    @NonNull
    public String buildNoPoiPrompt(@NonNull String query) {
        return getSystemPrefix() + "Note: No places were found nearby for this query.\n\n" +
                "Question: " + query + "\n\n" +
                "Answer:";
    }

    /**
     * Build a prompt for direction/distance queries about places (Phase 8.2).
     *
     * @param query User's question about direction/distance
     * @param placeResults List of places found in map data
     * @param location User's current location
     * @param maxContextTokens Maximum tokens for context
     * @return Complete prompt for LLM
     */
    @NonNull
    public String buildDirectionPrompt(@NonNull String query,
                                        @NonNull List<PlaceResult> placeResults,
                                        @NonNull LocationContext location,
                                        int maxContextTokens) {
        if (placeResults.isEmpty()) {
            return buildNoPoiPrompt(query);
        }

        StringBuilder placeContext = new StringBuilder();
        int remainingTokens = maxContextTokens;

        for (PlaceResult place : placeResults) {
            String entry = place.toPromptString() + "\n";
            int entryTokens = estimateTokens(entry);

            if (remainingTokens - entryTokens < 50) {
                break;
            }

            placeContext.append(entry);
            remainingTokens -= entryTokens;
        }

        return getSystemPrefix() + String.format(DIRECTION_TEMPLATE,
                location.getLocationString(),
                placeContext.toString(),
                query);
    }

    /**
     * Build a direction prompt with default token budget (Phase 8.2).
     */
    @NonNull
    public String buildDirectionPrompt(@NonNull String query,
                                        @NonNull List<PlaceResult> placeResults,
                                        @NonNull LocationContext location) {
        return buildDirectionPrompt(query, placeResults, location, DEFAULT_CONTEXT_BUDGET);
    }

    /**
     * Build a prompt for survival queries with guide context (Phase 14).
     *
     * @param query User's survival-related question
     * @param guideContext Formatted survival guide context from GuideSearchAdapter
     * @param wikiContext Optional Wikipedia context (may be empty)
     * @param maxContextTokens Maximum tokens for context
     * @return Complete prompt for LLM
     */
    @NonNull
    public String buildSurvivalPrompt(@NonNull String query,
                                       @NonNull String guideContext,
                                       @NonNull String wikiContext,
                                       int maxContextTokens) {
        String wikiSection = "";
        if (!wikiContext.isEmpty()) {
            wikiSection = "=== Wikipedia Context ===\n" + wikiContext + "=== End Wikipedia ===\n\n";
        }

        return getSystemPrefix() + String.format(SURVIVAL_TEMPLATE,
                guideContext,
                wikiSection,
                query);
    }

    /**
     * Build a prompt for practical/engineering queries with guide context (Phase v0.3).
     *
     * @param query User's practical/engineering question
     * @param guideContext Formatted guide context from GuideSearchAdapter
     * @param wikiContext Optional Wikipedia context (may be empty)
     * @param maxContextTokens Maximum tokens for context
     * @return Complete prompt for LLM
     */
    @NonNull
    public String buildPracticalPrompt(@NonNull String query,
                                        @NonNull String guideContext,
                                        @NonNull String wikiContext,
                                        int maxContextTokens) {
        String wikiSection = "";
        if (!wikiContext.isEmpty()) {
            wikiSection = "=== Wikipedia Context ===\n" + wikiContext + "=== End Wikipedia ===\n\n";
        }

        return getSystemPrefix() + String.format(PRACTICAL_TEMPLATE,
                guideContext,
                wikiSection,
                query);
    }

    /**
     * Format Wikipedia sources into a context string (Phase 14).
     * Used when building survival prompts that combine guide + wiki context.
     */
    @NonNull
    public String formatWikiSources(@NonNull List<ArticleSource> sources) {
        StringBuilder sb = new StringBuilder();
        for (ArticleSource source : sources) {
            sb.append(String.format(ARTICLE_TEMPLATE, source.getTitle(), source.getText()));
        }
        return sb.toString();
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

    /**
     * Format POI sources for display in chat (Phase 8).
     */
    @NonNull
    public String formatPoiCitations(@NonNull List<PoiSource> poiSources) {
        if (poiSources.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("\n\nNearby: ");
        for (int i = 0; i < poiSources.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(poiSources.get(i).toChatString());
        }
        return sb.toString();
    }
}
