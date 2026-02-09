package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LAMPP Phase 7: Classifies user queries to determine if Wikipedia lookup is needed.
 *
 * Uses heuristic pattern matching to identify factual queries that would benefit
 * from Wikipedia context vs. conversational or navigation queries that don't need it.
 */
public class QueryClassifier {

    /**
     * Types of queries with different Wikipedia needs.
     */
    public enum QueryType {
        /**
         * "What is X?", "Who is X?" - Strongly needs Wikipedia
         */
        FACTUAL_LOOKUP(true, 100),

        /**
         * "Define X", "What does X mean?" - Strongly needs Wikipedia
         */
        DEFINITION(true, 100),

        /**
         * "Explain X", "How does X work?" - Needs Wikipedia
         */
        EXPLANATION(true, 90),

        /**
         * "Tell me about X" - Needs Wikipedia
         */
        INFORMATION_REQUEST(true, 80),

        /**
         * "Hello", "Thanks", "Okay" - No Wikipedia needed
         */
        CONVERSATIONAL(false, 0),

        /**
         * "How do I get to X?", "Navigate to X" - No Wikipedia needed
         */
        NAVIGATION(false, 0),

        /**
         * "What's the weather?", "What time is it?" - No Wikipedia (real-time)
         */
        REALTIME(false, 0),

        /**
         * Code or technical questions - Usually no Wikipedia
         */
        TECHNICAL(false, 20),

        /**
         * Default: Try Wikipedia if available
         */
        UNKNOWN(true, 50);

        private final boolean needsWikipedia;
        private final int priority;

        QueryType(boolean needsWikipedia, int priority) {
            this.needsWikipedia = needsWikipedia;
            this.priority = priority;
        }

        public boolean needsWikipedia() {
            return needsWikipedia;
        }

        public int getPriority() {
            return priority;
        }
    }

    // Patterns for different query types
    private static final Pattern WHAT_IS_PATTERN = Pattern.compile(
            "^(what|who|where|when)('?s|\\s+is|\\s+was|\\s+are|\\s+were)\\s+",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEFINE_PATTERN = Pattern.compile(
            "^(define|definition\\s+of|meaning\\s+of|what\\s+does\\s+.+\\s+mean)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXPLAIN_PATTERN = Pattern.compile(
            "^(explain|how\\s+does|how\\s+do|why\\s+does|why\\s+do|tell\\s+me\\s+how)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TELL_ME_PATTERN = Pattern.compile(
            "^(tell\\s+me\\s+about|describe|what\\s+can\\s+you\\s+tell\\s+me\\s+about)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NAVIGATION_PATTERN = Pattern.compile(
            "(navigate|directions?|route|how\\s+(do\\s+i\\s+)?get\\s+to|take\\s+me\\s+to)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REALTIME_PATTERN = Pattern.compile(
            "(weather|time|date|today|tomorrow|current|now|live)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "(code|program|function|class|method|variable|syntax|bug|error|compile)",
            Pattern.CASE_INSENSITIVE);

    // Conversational phrases
    private static final Set<String> CONVERSATIONAL_PHRASES = new HashSet<>(Arrays.asList(
            "hello", "hi", "hey", "thanks", "thank you", "okay", "ok",
            "yes", "no", "sure", "please", "sorry", "bye", "goodbye",
            "good morning", "good evening", "good night", "how are you"
    ));

    // Common stop words to filter out from search terms
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "about", "above",
            "after", "again", "all", "also", "and", "any", "because", "before",
            "between", "both", "but", "by", "for", "from", "if", "in", "into",
            "just", "more", "most", "of", "on", "or", "other", "out", "over",
            "so", "some", "such", "than", "that", "their", "them", "then",
            "there", "these", "they", "this", "those", "through", "to", "too",
            "under", "up", "very", "what", "when", "where", "which", "while",
            "who", "why", "with", "you", "your", "me", "my", "i", "we", "us",
            "tell", "explain", "describe", "know", "understand", "mean"
    ));

    /**
     * Classify a user query.
     *
     * @param query The user's query
     * @return The query type classification
     */
    @NonNull
    public QueryType classify(@NonNull String query) {
        String normalized = query.trim().toLowerCase(Locale.ENGLISH);

        // Check for empty or very short queries
        if (normalized.length() < 3) {
            return QueryType.CONVERSATIONAL;
        }

        // Check for exact conversational phrases
        if (CONVERSATIONAL_PHRASES.contains(normalized)) {
            return QueryType.CONVERSATIONAL;
        }

        // Check navigation queries
        if (NAVIGATION_PATTERN.matcher(normalized).find()) {
            return QueryType.NAVIGATION;
        }

        // Check real-time queries
        if (REALTIME_PATTERN.matcher(normalized).find()) {
            return QueryType.REALTIME;
        }

        // Check factual lookup patterns
        if (WHAT_IS_PATTERN.matcher(normalized).find()) {
            return QueryType.FACTUAL_LOOKUP;
        }

        // Check definition patterns
        if (DEFINE_PATTERN.matcher(normalized).find()) {
            return QueryType.DEFINITION;
        }

        // Check explanation patterns
        if (EXPLAIN_PATTERN.matcher(normalized).find()) {
            return QueryType.EXPLANATION;
        }

        // Check information request patterns
        if (TELL_ME_PATTERN.matcher(normalized).find()) {
            return QueryType.INFORMATION_REQUEST;
        }

        // Check code/technical patterns
        if (CODE_PATTERN.matcher(normalized).find()) {
            return QueryType.TECHNICAL;
        }

        // Default: Try Wikipedia if query seems substantive
        if (normalized.length() > 10 && containsNoun(normalized)) {
            return QueryType.UNKNOWN;
        }

        return QueryType.CONVERSATIONAL;
    }

    /**
     * Extract search terms from a query for Wikipedia lookup.
     *
     * @param query The user's query
     * @return List of search terms
     */
    @NonNull
    public List<String> extractSearchTerms(@NonNull String query) {
        List<String> terms = new ArrayList<>();

        // Normalize and tokenize
        String normalized = query.trim().toLowerCase(Locale.ENGLISH);

        // Remove question marks and other punctuation
        normalized = normalized.replaceAll("[?!.,;:]", "");

        // Remove common query prefixes
        normalized = normalized.replaceAll("^(what\\s+is|who\\s+is|tell\\s+me\\s+about|" +
                "explain|define|describe|where\\s+is|when\\s+was|how\\s+does)\\s+", "");
        normalized = normalized.replaceAll("^(the|a|an)\\s+", "");

        // Split into words
        String[] words = normalized.split("\\s+");

        // Build search terms from content words
        StringBuilder termBuilder = new StringBuilder();
        for (String word : words) {
            if (!STOP_WORDS.contains(word) && word.length() > 2) {
                if (termBuilder.length() > 0) {
                    termBuilder.append(" ");
                }
                termBuilder.append(word);
            }
        }

        String mainTerm = termBuilder.toString().trim();
        if (!mainTerm.isEmpty()) {
            terms.add(capitalize(mainTerm));
        }

        // Also add individual significant words as backup search terms
        for (String word : words) {
            if (!STOP_WORDS.contains(word) && word.length() > 3) {
                String capitalized = capitalize(word);
                if (!terms.contains(capitalized)) {
                    terms.add(capitalized);
                }
            }
        }

        // Limit to top 3 terms
        if (terms.size() > 3) {
            terms = terms.subList(0, 3);
        }

        return terms;
    }

    /**
     * Check if the query likely contains a proper noun or subject.
     */
    private boolean containsNoun(@NonNull String query) {
        // Simple heuristic: check for capitalized words (after first word)
        String[] words = query.split("\\s+");
        for (int i = 1; i < words.length; i++) {
            if (!words[i].isEmpty() && Character.isUpperCase(words[i].charAt(0))) {
                return true;
            }
        }

        // Check if query has enough content words
        int contentWords = 0;
        for (String word : words) {
            if (!STOP_WORDS.contains(word.toLowerCase()) && word.length() > 2) {
                contentWords++;
            }
        }
        return contentWords >= 1;
    }

    /**
     * Capitalize first letter of each word.
     */
    @NonNull
    private String capitalize(@NonNull String text) {
        if (text.isEmpty()) {
            return text;
        }

        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        return result.toString();
    }
}
