package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
         * Survival/emergency queries — needs offline survival guides + Wikipedia background
         * "How do I purify water?", "First aid for burns", "Build a shelter"
         */
        SURVIVAL_QUERY(true, false, 95),

        /**
         * Practical/engineering queries — needs offline guides + Wikipedia background
         * "How do I patch a radiator hose?", "Improvised voltage regulator", "Fix a leaky pipe"
         */
        PRACTICAL_QUERY(true, false, 95),

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

        /**
         * Whether this query type should search the offline guide database
         * (survival guides + practical engineering references).
         */
        public boolean needsGuideSearch() {
            return this == SURVIVAL_QUERY || this == PRACTICAL_QUERY;
        }

        public int getPriority() {
            return priority;
        }
    }

    // Survival/emergency query pattern - Phase 14
    private static final Pattern SURVIVAL_PATTERN = Pattern.compile(
            "(first\\s*aid|cpr|wound|bleed(ing)?|fracture|splint|tourniquet|bandage|suture" +
            "|burn\\s*(treatment|care)|hypotherm|heatstroke|frostbite|dehydration" +
            "|purif(y|ication)|filter.*water|boil.*water|safe.*drink|water\\s*source|solar\\s*still" +
            "|fire\\s*start|ferro\\s*rod|bow\\s*drill|tinder|kindling|flint" +
            "|shelter|lean[\\s-]?to|debris\\s*hut|quinzhee|bivouac" +
            "|compass|navigate.*without|dead\\s*reckon|star.*navigation|shadow\\s*stick" +
            "|signal.*rescue|sos|distress\\s*signal|signal\\s*mirror|signal\\s*fire" +
            "|forag(e|ing)|edib(le|ility)\\s*test|wild.*food|insect.*eat|snare|trap" +
            "|surviv(al|e)|emergency\\s*(prep|kit|supply)|grid[\\s-]?down|bug[\\s-]?out" +
            "|gray\\s*man|opsec|situational\\s*awareness|panic\\s*wipe" +
            "|improvise|makeshift|jury[\\s-]?rig|field[\\s-]?repair|off[\\s-]?grid)",
            Pattern.CASE_INSENSITIVE);

    // Practical/engineering query pattern - Phase v0.3
    private static final Pattern PRACTICAL_PATTERN = Pattern.compile(
            "(repair|fix(ing)?|patch|weld(ing)?|solder(ing)?|splice|improvise|makeshift|jury[\\s-]?rig" +
            "|macgyver|mcgyver" +
            "|engine|motor|radiator|alternator|carburetor|fuel\\s*(pump|line|filter)|brake|transmission" +
            "|battery\\s*(test|charg|jump|dead)|volt(age)?|amp(erage)?|watt|circuit|wire\\s*(splic|strip|gauge)|fuse|generator|inverter" +
            "|pipe|plumb(ing)?|leak|gasket|seal(ant)?|clamp|hose|fitting|valve" +
            "|lever|pulley|gear|bearing|axle|shaft|torque|tension" +
            "|concrete|mortar|rebar|rivet|bolt|thread|tap\\s*and\\s*die" +
            "|insulate|waterproof|adhesive|epoxy|jb\\s*weld|duct\\s*tape" +
            "|pump|siphon|hydraulic|pneumatic" +
            "|structural|load[\\s-]?bearing|truss|brace|reinforce|anchor" +
            "|sharpen|temper|anneal|harden|forge" +
            "|tire\\s*(plug|patch|flat)|brake\\s*(pad|fluid|bleed)" +
            "|solar\\s*(panel|charge)|off[\\s-]?grid|deep\\s*cycle" +
            "|multimeter|ohm|resistor|capacitor|relay)",
            Pattern.CASE_INSENSITIVE);

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

        // Check survival/emergency queries (Phase 14)
        if (SURVIVAL_PATTERN.matcher(normalized).find()) {
            return QueryType.SURVIVAL_QUERY;
        }

        // Check practical/engineering queries (Phase v0.3)
        if (PRACTICAL_PATTERN.matcher(normalized).find()) {
            return QueryType.PRACTICAL_QUERY;
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

    // ---- Synonym Expansion (Phase 3.2) ----

    /**
     * Domain-focused synonym map for survival, medical, navigation, and legal terms.
     * Maps common query words to related terms that might match Wikipedia article titles.
     * Kept small and targeted — not a general thesaurus.
     */
    private static final Map<String, List<String>> SYNONYM_MAP = new HashMap<>();
    static {
        // Medical / First Aid
        SYNONYM_MAP.put("dehydration", Arrays.asList("water loss", "fluid deficit"));
        SYNONYM_MAP.put("bleeding", Arrays.asList("hemorrhage", "blood loss"));
        SYNONYM_MAP.put("fracture", Arrays.asList("broken bone", "bone injury"));
        SYNONYM_MAP.put("infection", Arrays.asList("sepsis", "bacterial infection"));
        SYNONYM_MAP.put("fever", Arrays.asList("hyperthermia", "elevated temperature"));
        SYNONYM_MAP.put("hypothermia", Arrays.asList("cold exposure", "low body temperature"));
        SYNONYM_MAP.put("heatstroke", Arrays.asList("heat exhaustion", "hyperthermia"));
        SYNONYM_MAP.put("wound", Arrays.asList("laceration", "injury"));
        SYNONYM_MAP.put("burn", Arrays.asList("thermal injury", "scald"));
        SYNONYM_MAP.put("poison", Arrays.asList("toxin", "toxic substance"));
        SYNONYM_MAP.put("cpr", Arrays.asList("cardiopulmonary resuscitation", "chest compressions"));
        SYNONYM_MAP.put("splint", Arrays.asList("immobilize", "fracture support"));
        SYNONYM_MAP.put("tourniquet", Arrays.asList("bleeding control", "hemostasis"));
        SYNONYM_MAP.put("antibiotics", Arrays.asList("antimicrobial", "anti-infective"));
        SYNONYM_MAP.put("painkillers", Arrays.asList("analgesic", "pain relief"));
        SYNONYM_MAP.put("bandage", Arrays.asList("dressing", "wound cover"));
        SYNONYM_MAP.put("medicine", Arrays.asList("medication", "drug treatment"));

        // Water / Survival
        SYNONYM_MAP.put("water purification", Arrays.asList("water treatment", "water disinfection"));
        SYNONYM_MAP.put("shelter", Arrays.asList("emergency shelter", "bivouac"));
        SYNONYM_MAP.put("fire starting", Arrays.asList("fire making", "ignition"));
        SYNONYM_MAP.put("foraging", Arrays.asList("wild food", "edible plants"));
        SYNONYM_MAP.put("navigation", Arrays.asList("wayfinding", "orienteering"));
        SYNONYM_MAP.put("compass", Arrays.asList("magnetic compass", "direction finding"));
        SYNONYM_MAP.put("survival", Arrays.asList("wilderness survival", "emergency preparedness"));
        SYNONYM_MAP.put("rescue", Arrays.asList("search and rescue", "emergency rescue"));
        SYNONYM_MAP.put("signal", Arrays.asList("distress signal", "sos"));

        // Navigation / Geography
        SYNONYM_MAP.put("earthquake", Arrays.asList("seismic activity", "tremor"));
        SYNONYM_MAP.put("flood", Arrays.asList("flooding", "flash flood"));
        SYNONYM_MAP.put("tsunami", Arrays.asList("tidal wave", "seismic sea wave"));
        SYNONYM_MAP.put("hurricane", Arrays.asList("cyclone", "typhoon"));
        SYNONYM_MAP.put("volcano", Arrays.asList("volcanic eruption", "volcanism"));
        SYNONYM_MAP.put("landslide", Arrays.asList("mudslide", "mass movement"));
        SYNONYM_MAP.put("drought", Arrays.asList("water scarcity", "arid conditions"));

        // Legal / Human Rights
        SYNONYM_MAP.put("asylum", Arrays.asList("refugee status", "political asylum"));
        SYNONYM_MAP.put("detention", Arrays.asList("imprisonment", "arrest"));
        SYNONYM_MAP.put("censorship", Arrays.asList("information control", "media suppression"));
        SYNONYM_MAP.put("surveillance", Arrays.asList("monitoring", "tracking"));
        SYNONYM_MAP.put("encryption", Arrays.asList("cryptography", "data protection"));
        SYNONYM_MAP.put("protest", Arrays.asList("demonstration", "civil disobedience"));
        SYNONYM_MAP.put("refugee", Arrays.asList("displaced person", "asylum seeker"));

        // Technology
        SYNONYM_MAP.put("radio", Arrays.asList("two-way radio", "wireless communication"));
        SYNONYM_MAP.put("battery", Arrays.asList("power source", "energy storage"));
        SYNONYM_MAP.put("solar", Arrays.asList("solar power", "photovoltaic"));
        SYNONYM_MAP.put("morse", Arrays.asList("morse code", "telegraphy"));
        SYNONYM_MAP.put("gps", Arrays.asList("global positioning system", "satellite navigation"));

        // Mechanical / Automotive (Phase v0.3)
        SYNONYM_MAP.put("engine", Arrays.asList("internal combustion", "motor"));
        SYNONYM_MAP.put("radiator", Arrays.asList("cooling system", "coolant"));
        SYNONYM_MAP.put("alternator", Arrays.asList("charging system", "generator"));
        SYNONYM_MAP.put("brake", Arrays.asList("braking system", "disc brake"));
        SYNONYM_MAP.put("transmission", Arrays.asList("gearbox", "drivetrain"));
        SYNONYM_MAP.put("carburetor", Arrays.asList("fuel system", "fuel mixture"));
        SYNONYM_MAP.put("tire", Arrays.asList("wheel", "flat tire"));
        SYNONYM_MAP.put("starter", Arrays.asList("starter motor", "cranking"));
        SYNONYM_MAP.put("exhaust", Arrays.asList("muffler", "catalytic converter"));

        // Electrical (Phase v0.3)
        SYNONYM_MAP.put("voltage", Arrays.asList("electrical potential", "volts"));
        SYNONYM_MAP.put("circuit", Arrays.asList("electrical circuit", "wiring"));
        SYNONYM_MAP.put("generator", Arrays.asList("power generation", "dynamo"));
        SYNONYM_MAP.put("inverter", Arrays.asList("power inverter", "DC to AC"));
        SYNONYM_MAP.put("fuse", Arrays.asList("circuit breaker", "overcurrent protection"));
        SYNONYM_MAP.put("multimeter", Arrays.asList("voltmeter", "electrical testing"));
        SYNONYM_MAP.put("capacitor", Arrays.asList("start capacitor", "run capacitor"));

        // Structural / Materials (Phase v0.3)
        SYNONYM_MAP.put("concrete", Arrays.asList("cement", "masonry"));
        SYNONYM_MAP.put("welding", Arrays.asList("metal joining", "brazing"));
        SYNONYM_MAP.put("insulation", Arrays.asList("thermal insulation", "insulate"));
        SYNONYM_MAP.put("plumbing", Arrays.asList("pipe repair", "water system"));
        SYNONYM_MAP.put("adhesive", Arrays.asList("glue", "bonding agent", "epoxy"));
        SYNONYM_MAP.put("lever", Arrays.asList("simple machine", "mechanical advantage"));
        SYNONYM_MAP.put("pulley", Arrays.asList("block and tackle", "mechanical advantage"));
        SYNONYM_MAP.put("hydraulic", Arrays.asList("fluid power", "hydraulic system"));
        SYNONYM_MAP.put("gasket", Arrays.asList("seal", "engine gasket"));
        SYNONYM_MAP.put("solder", Arrays.asList("soldering", "tin-lead joint"));
    }

    /**
     * Expand search terms with domain-specific synonyms.
     *
     * For each input term, adds up to 2 synonyms from the synonym map,
     * increasing the chance of matching Wikipedia article titles.
     *
     * @param terms Original search terms
     * @return Expanded list including synonyms (originals first, then synonyms)
     */
    @NonNull
    public List<String> expandWithSynonyms(@NonNull List<String> terms) {
        List<String> expanded = new ArrayList<>(terms);
        Set<String> seen = new HashSet<>();
        for (String t : terms) {
            seen.add(t.toLowerCase());
        }

        for (String term : terms) {
            String lowerTerm = term.toLowerCase();

            // Check exact match in synonym map
            List<String> synonyms = SYNONYM_MAP.get(lowerTerm);
            if (synonyms != null) {
                for (String syn : synonyms) {
                    if (seen.add(syn.toLowerCase())) {
                        expanded.add(capitalize(syn));
                    }
                }
                continue;
            }

            // Check if any synonym map key is contained in the term
            for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
                if (lowerTerm.contains(entry.getKey())) {
                    for (String syn : entry.getValue()) {
                        if (seen.add(syn.toLowerCase())) {
                            expanded.add(capitalize(syn));
                            if (expanded.size() - terms.size() >= terms.size() * 2) {
                                // Don't add too many synonyms
                                return expanded;
                            }
                        }
                    }
                }
            }
        }

        return expanded;
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
