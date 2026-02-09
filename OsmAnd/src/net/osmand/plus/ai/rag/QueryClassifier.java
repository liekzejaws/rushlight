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
 * LAMPP Phase 7 & 8: Classifies user queries to determine if Wikipedia or POI lookup is needed.
 *
 * Uses heuristic pattern matching to identify:
 * - Factual queries that benefit from Wikipedia context
 * - Location queries that need POI database search
 * - Conversational or navigation queries that don't need external data
 */
public class QueryClassifier {

    /**
     * Types of queries with different data source needs.
     */
    public enum QueryType {
        /**
         * "What is X?", "Who is X?" - Strongly needs Wikipedia
         */
        FACTUAL_LOOKUP(true, false, 100),

        /**
         * "Define X", "What does X mean?" - Strongly needs Wikipedia
         */
        DEFINITION(true, false, 100),

        /**
         * "Explain X", "How does X work?" - Needs Wikipedia
         */
        EXPLANATION(true, false, 90),

        /**
         * "Tell me about X" - Needs Wikipedia
         */
        INFORMATION_REQUEST(true, false, 80),

        /**
         * "Find coffee near me", "Where is the nearest gas station?" - Needs POI search
         */
        LOCATION_SEARCH(false, true, 100),

        /**
         * "Which direction is X from here?", "How far is X?" - Needs map/place search
         */
        DIRECTION_QUERY(false, true, 100),

        /**
         * "Hello", "Thanks", "Okay" - No Wikipedia needed
         */
        CONVERSATIONAL(false, false, 0),

        /**
         * "How do I get to X?", "Navigate to X" - No Wikipedia needed
         */
        NAVIGATION(false, false, 0),

        /**
         * "What's the weather?", "What time is it?" - No Wikipedia (real-time)
         */
        REALTIME(false, false, 0),

        /**
         * Code or technical questions - Usually no Wikipedia
         */
        TECHNICAL(false, false, 20),

        /**
         * Default: Try Wikipedia if available
         */
        UNKNOWN(true, false, 50);

        private final boolean needsWikipedia;
        private final boolean needsPoiSearch;
        private final int priority;

        QueryType(boolean needsWikipedia, boolean needsPoiSearch, int priority) {
            this.needsWikipedia = needsWikipedia;
            this.needsPoiSearch = needsPoiSearch;
            this.priority = priority;
        }

        public boolean needsWikipedia() {
            return needsWikipedia;
        }

        public boolean needsPoiSearch() {
            return needsPoiSearch;
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

    // Location search patterns - Phase 8
    private static final Pattern LOCATION_NEAR_PATTERN = Pattern.compile(
            "(near\\s+me|nearby|around\\s+here|close\\s+by|in\\s+this\\s+area)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOCATION_FIND_PATTERN = Pattern.compile(
            "(find|where|locate|search\\s+for).*(near|nearby|around|close|closest|nearest)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOCATION_NEAREST_PATTERN = Pattern.compile(
            "(closest|nearest|near(est)?\\s+(to\\s+me)?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LOCATION_RADIUS_PATTERN = Pattern.compile(
            "(within|in)\\s+\\d+\\s*(km|kilometers?|miles?|meters?|m|feet|ft)",
            Pattern.CASE_INSENSITIVE);

    // Direction/distance queries about places - Phase 8.2
    private static final Pattern DIRECTION_QUERY_PATTERN = Pattern.compile(
            "(which\\s+direction|what\\s+direction|where)\\s+(is|are)\\s+.+\\s+(from\\s+here|from\\s+me|from\\s+my\\s+location)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DISTANCE_QUERY_PATTERN = Pattern.compile(
            "(how\\s+far|what\\s+is\\s+the\\s+distance|distance\\s+to)\\s+.*(from\\s+here|from\\s+me|to\\s+here)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_IS_PLACE_PATTERN = Pattern.compile(
            "^(where\\s+is|where's)\\s+(?!the\\s+(nearest|closest))\\w+",
            Pattern.CASE_INSENSITIVE);

    // POI-related keywords for location searches
    private static final Set<String> POI_KEYWORDS = new HashSet<>(Arrays.asList(
            "coffee", "cafe", "restaurant", "food", "eat", "lunch", "dinner", "breakfast",
            "gas", "fuel", "petrol", "charging", "parking",
            "hotel", "motel", "hostel", "lodging", "accommodation",
            "hospital", "doctor", "clinic", "pharmacy", "drugstore", "dentist",
            "bank", "atm", "money",
            "supermarket", "grocery", "store", "shop", "mall",
            "toilet", "restroom", "bathroom", "wc",
            "gym", "fitness", "park", "playground",
            "cinema", "movie", "theater", "theatre", "museum",
            "bar", "pub", "beer",
            "bus", "train", "subway", "metro", "taxi"
    ));

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

        // Check direction/distance queries about places (Phase 8.2) - highest priority for map search
        if (isDirectionQuery(normalized)) {
            return QueryType.DIRECTION_QUERY;
        }

        // Check location search queries (Phase 8) - check before navigation
        if (isLocationSearch(normalized)) {
            return QueryType.LOCATION_SEARCH;
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

    /**
     * Check if a query is asking about direction or distance to a place.
     *
     * @param query Normalized query string
     * @return true if this is a direction/distance query
     */
    private boolean isDirectionQuery(@NonNull String query) {
        return DIRECTION_QUERY_PATTERN.matcher(query).find() ||
               DISTANCE_QUERY_PATTERN.matcher(query).find() ||
               (WHERE_IS_PLACE_PATTERN.matcher(query).find() && query.contains("from"));
    }

    /**
     * Extract place name from a direction query.
     *
     * @param query The user's query
     * @return The place name being asked about, or empty string if not found
     */
    @NonNull
    public String extractPlaceName(@NonNull String query) {
        String normalized = query.trim().toLowerCase(Locale.ENGLISH);

        // Pattern: "which direction is X from here"
        Pattern pattern1 = Pattern.compile(
                "(which|what)\\s+direction\\s+(is|are)\\s+(.+?)\\s+(from\\s+here|from\\s+me)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = pattern1.matcher(normalized);
        if (matcher1.find()) {
            return capitalize(matcher1.group(3).trim());
        }

        // Pattern: "where is X from here"
        Pattern pattern2 = Pattern.compile(
                "where\\s+(is|are)\\s+(.+?)\\s+(from\\s+here|from\\s+me)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = pattern2.matcher(normalized);
        if (matcher2.find()) {
            return capitalize(matcher2.group(2).trim());
        }

        // Pattern: "how far is X" or "how far to X"
        Pattern pattern3 = Pattern.compile(
                "how\\s+far\\s+(is|to)\\s+(.+?)(?:\\s+from\\s+here|\\s+from\\s+me|\\?|$)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher3 = pattern3.matcher(normalized);
        if (matcher3.find()) {
            return capitalize(matcher3.group(2).trim());
        }

        return "";
    }

    /**
     * Check if a query is a location-based search.
     *
     * @param query Normalized query string
     * @return true if this is a location search
     */
    private boolean isLocationSearch(@NonNull String query) {
        // Check for explicit location patterns
        if (LOCATION_NEAR_PATTERN.matcher(query).find() ||
            LOCATION_FIND_PATTERN.matcher(query).find() ||
            LOCATION_NEAREST_PATTERN.matcher(query).find() ||
            LOCATION_RADIUS_PATTERN.matcher(query).find()) {
            return true;
        }

        // Check for POI keywords combined with search-like phrases
        boolean hasPoiKeyword = false;
        boolean hasSearchPhrase = false;

        for (String keyword : POI_KEYWORDS) {
            if (query.contains(keyword)) {
                hasPoiKeyword = true;
                break;
            }
        }

        // Check for search-like phrases
        if (query.startsWith("find ") ||
            query.startsWith("where ") ||
            query.startsWith("locate ") ||
            query.contains("looking for") ||
            query.contains("need a ") ||
            query.contains("need to find")) {
            hasSearchPhrase = true;
        }

        return hasPoiKeyword && hasSearchPhrase;
    }

    /**
     * Check if query contains location-related terms (for hybrid queries).
     *
     * @param query The user's query
     * @return true if the query has location context
     */
    public boolean hasLocationContext(@NonNull String query) {
        String normalized = query.trim().toLowerCase(Locale.ENGLISH);
        return LOCATION_NEAR_PATTERN.matcher(normalized).find() ||
               LOCATION_NEAREST_PATTERN.matcher(normalized).find();
    }

    /**
     * Check if query contains POI-related keywords.
     *
     * @param query The user's query
     * @return true if the query references POI types
     */
    public boolean containsPoiKeywords(@NonNull String query) {
        String normalized = query.trim().toLowerCase(Locale.ENGLISH);
        for (String keyword : POI_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract POI-related search terms from a query.
     *
     * @param query The user's query
     * @return List of POI search terms
     */
    @NonNull
    public List<String> extractPoiSearchTerms(@NonNull String query) {
        List<String> terms = new ArrayList<>();
        String normalized = query.trim().toLowerCase(Locale.ENGLISH);

        // Remove location qualifiers to extract POI type
        normalized = normalized.replaceAll("(near\\s+me|nearby|around\\s+here|close\\s+by)", "");
        normalized = normalized.replaceAll("(closest|nearest)", "");
        normalized = normalized.replaceAll("(find|where|locate|search\\s+for)", "");
        normalized = normalized.replaceAll("(within|in)\\s+\\d+\\s*(km|miles?|meters?|m)", "");
        normalized = normalized.replaceAll("^(a|an|the|some)\\s+", "");
        normalized = normalized.replaceAll("[?!.,]", "").trim();

        // Find POI keywords in the remaining text
        for (String keyword : POI_KEYWORDS) {
            if (normalized.contains(keyword)) {
                terms.add(keyword);
            }
        }

        // If no keywords found, use the cleaned query as the search term
        if (terms.isEmpty() && !normalized.isEmpty()) {
            // Split and filter
            String[] words = normalized.split("\\s+");
            for (String word : words) {
                if (!STOP_WORDS.contains(word) && word.length() > 2) {
                    terms.add(word);
                }
            }
        }

        return terms;
    }

    /**
     * Extract search radius from query if specified.
     *
     * @param query The user's query
     * @return Radius in meters, or -1 if not specified
     */
    public int extractSearchRadius(@NonNull String query) {
        String normalized = query.trim().toLowerCase(Locale.ENGLISH);

        // Pattern: "within X km/miles/meters"
        Pattern radiusPattern = Pattern.compile(
                "(within|in)\\s+(\\d+(?:\\.\\d+)?)\\s*(km|kilometers?|miles?|meters?|m)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = radiusPattern.matcher(normalized);

        if (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(2));
                String unit = matcher.group(3).toLowerCase();

                if (unit.startsWith("km") || unit.startsWith("kilometer")) {
                    return (int) (value * 1000);
                } else if (unit.startsWith("mile")) {
                    return (int) (value * 1609.34);
                } else {
                    // meters
                    return (int) value;
                }
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return -1; // Not specified
    }
}
