package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.osm.AbstractPoiType;
import net.osmand.osm.MapPoiTypes;
import net.osmand.osm.PoiCategory;
import net.osmand.osm.PoiFilter;
import net.osmand.osm.PoiType;
import net.osmand.plus.OsmandApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LAMPP Phase 8: Maps natural language query terms to OsmAnd POI categories.
 *
 * Provides a mapping from common user phrases like "coffee", "food", "gas"
 * to OsmAnd's POI type system (PoiCategory, PoiType).
 */
public class PoiTypeResolver {

    /**
     * Mapping of natural language terms to POI type key names.
     * Each term maps to one or more POI types that should be searched.
     */
    private static final Map<String, String[]> TERM_TO_POI_TYPES = new HashMap<>();

    static {
        // Food & Drink
        TERM_TO_POI_TYPES.put("coffee", new String[]{"cafe", "coffee_shop"});
        TERM_TO_POI_TYPES.put("cafe", new String[]{"cafe", "coffee_shop"});
        TERM_TO_POI_TYPES.put("food", new String[]{"restaurant", "fast_food", "cafe", "food_court"});
        TERM_TO_POI_TYPES.put("restaurant", new String[]{"restaurant"});
        TERM_TO_POI_TYPES.put("eat", new String[]{"restaurant", "fast_food", "cafe"});
        TERM_TO_POI_TYPES.put("lunch", new String[]{"restaurant", "fast_food"});
        TERM_TO_POI_TYPES.put("dinner", new String[]{"restaurant"});
        TERM_TO_POI_TYPES.put("breakfast", new String[]{"cafe", "restaurant"});
        TERM_TO_POI_TYPES.put("fast food", new String[]{"fast_food"});
        TERM_TO_POI_TYPES.put("pizza", new String[]{"restaurant", "fast_food"});
        TERM_TO_POI_TYPES.put("burger", new String[]{"fast_food"});
        TERM_TO_POI_TYPES.put("bar", new String[]{"bar", "pub"});
        TERM_TO_POI_TYPES.put("pub", new String[]{"pub", "bar"});
        TERM_TO_POI_TYPES.put("beer", new String[]{"pub", "bar", "biergarten"});
        TERM_TO_POI_TYPES.put("ice cream", new String[]{"ice_cream"});
        TERM_TO_POI_TYPES.put("bakery", new String[]{"bakery"});

        // Transportation & Fuel
        TERM_TO_POI_TYPES.put("gas", new String[]{"fuel"});
        TERM_TO_POI_TYPES.put("gas station", new String[]{"fuel"});
        TERM_TO_POI_TYPES.put("fuel", new String[]{"fuel"});
        TERM_TO_POI_TYPES.put("petrol", new String[]{"fuel"});
        TERM_TO_POI_TYPES.put("charging", new String[]{"charging_station"});
        TERM_TO_POI_TYPES.put("ev charging", new String[]{"charging_station"});
        TERM_TO_POI_TYPES.put("parking", new String[]{"parking"});
        TERM_TO_POI_TYPES.put("car park", new String[]{"parking"});
        TERM_TO_POI_TYPES.put("bus", new String[]{"bus_stop", "bus_station"});
        TERM_TO_POI_TYPES.put("bus stop", new String[]{"bus_stop"});
        TERM_TO_POI_TYPES.put("train", new String[]{"railway_station", "railway_halt"});
        TERM_TO_POI_TYPES.put("train station", new String[]{"railway_station"});
        TERM_TO_POI_TYPES.put("subway", new String[]{"subway_entrance"});
        TERM_TO_POI_TYPES.put("metro", new String[]{"subway_entrance"});
        TERM_TO_POI_TYPES.put("taxi", new String[]{"taxi"});
        TERM_TO_POI_TYPES.put("car rental", new String[]{"car_rental"});

        // Accommodation
        TERM_TO_POI_TYPES.put("hotel", new String[]{"hotel", "motel"});
        TERM_TO_POI_TYPES.put("motel", new String[]{"motel"});
        TERM_TO_POI_TYPES.put("hostel", new String[]{"hostel"});
        TERM_TO_POI_TYPES.put("lodging", new String[]{"hotel", "motel", "hostel", "guest_house"});
        TERM_TO_POI_TYPES.put("accommodation", new String[]{"hotel", "motel", "hostel", "guest_house"});
        TERM_TO_POI_TYPES.put("sleep", new String[]{"hotel", "motel", "hostel"});
        TERM_TO_POI_TYPES.put("camping", new String[]{"camp_site", "caravan_site"});
        TERM_TO_POI_TYPES.put("campsite", new String[]{"camp_site"});

        // Healthcare
        TERM_TO_POI_TYPES.put("hospital", new String[]{"hospital"});
        TERM_TO_POI_TYPES.put("doctor", new String[]{"doctors", "clinic"});
        TERM_TO_POI_TYPES.put("clinic", new String[]{"clinic", "doctors"});
        TERM_TO_POI_TYPES.put("pharmacy", new String[]{"pharmacy"});
        TERM_TO_POI_TYPES.put("drugstore", new String[]{"pharmacy"});
        TERM_TO_POI_TYPES.put("dentist", new String[]{"dentist"});
        TERM_TO_POI_TYPES.put("emergency", new String[]{"hospital", "police", "fire_station"});
        TERM_TO_POI_TYPES.put("urgent care", new String[]{"clinic", "hospital"});

        // Finance
        TERM_TO_POI_TYPES.put("bank", new String[]{"bank"});
        TERM_TO_POI_TYPES.put("atm", new String[]{"atm"});
        TERM_TO_POI_TYPES.put("money", new String[]{"bank", "atm", "bureau_de_change"});
        TERM_TO_POI_TYPES.put("exchange", new String[]{"bureau_de_change"});

        // Shopping
        TERM_TO_POI_TYPES.put("supermarket", new String[]{"supermarket"});
        TERM_TO_POI_TYPES.put("grocery", new String[]{"supermarket", "convenience"});
        TERM_TO_POI_TYPES.put("store", new String[]{"supermarket", "convenience", "department_store"});
        TERM_TO_POI_TYPES.put("shop", new String[]{"supermarket", "convenience", "mall"});
        TERM_TO_POI_TYPES.put("mall", new String[]{"mall", "department_store"});
        TERM_TO_POI_TYPES.put("convenience", new String[]{"convenience"});
        TERM_TO_POI_TYPES.put("clothes", new String[]{"clothes"});
        TERM_TO_POI_TYPES.put("clothing", new String[]{"clothes"});
        TERM_TO_POI_TYPES.put("electronics", new String[]{"electronics"});
        TERM_TO_POI_TYPES.put("hardware", new String[]{"hardware", "doityourself"});

        // Services
        TERM_TO_POI_TYPES.put("toilet", new String[]{"toilets"});
        TERM_TO_POI_TYPES.put("restroom", new String[]{"toilets"});
        TERM_TO_POI_TYPES.put("bathroom", new String[]{"toilets"});
        TERM_TO_POI_TYPES.put("wc", new String[]{"toilets"});
        TERM_TO_POI_TYPES.put("laundry", new String[]{"laundry"});
        TERM_TO_POI_TYPES.put("post office", new String[]{"post_office"});
        TERM_TO_POI_TYPES.put("police", new String[]{"police"});
        TERM_TO_POI_TYPES.put("fire station", new String[]{"fire_station"});
        TERM_TO_POI_TYPES.put("library", new String[]{"library"});

        // Leisure & Entertainment
        TERM_TO_POI_TYPES.put("gym", new String[]{"fitness_centre", "sports_centre"});
        TERM_TO_POI_TYPES.put("fitness", new String[]{"fitness_centre"});
        TERM_TO_POI_TYPES.put("park", new String[]{"park"});
        TERM_TO_POI_TYPES.put("playground", new String[]{"playground"});
        TERM_TO_POI_TYPES.put("cinema", new String[]{"cinema"});
        TERM_TO_POI_TYPES.put("movie", new String[]{"cinema"});
        TERM_TO_POI_TYPES.put("theater", new String[]{"theatre"});
        TERM_TO_POI_TYPES.put("theatre", new String[]{"theatre"});
        TERM_TO_POI_TYPES.put("museum", new String[]{"museum"});
        TERM_TO_POI_TYPES.put("zoo", new String[]{"zoo"});
        TERM_TO_POI_TYPES.put("beach", new String[]{"beach"});
        TERM_TO_POI_TYPES.put("pool", new String[]{"swimming_pool"});
        TERM_TO_POI_TYPES.put("swimming", new String[]{"swimming_pool"});

        // Tourism
        TERM_TO_POI_TYPES.put("tourist", new String[]{"information", "attraction"});
        TERM_TO_POI_TYPES.put("attraction", new String[]{"attraction", "viewpoint"});
        TERM_TO_POI_TYPES.put("viewpoint", new String[]{"viewpoint"});
        TERM_TO_POI_TYPES.put("monument", new String[]{"monument", "memorial"});

        // Religion
        TERM_TO_POI_TYPES.put("church", new String[]{"church"});
        TERM_TO_POI_TYPES.put("mosque", new String[]{"mosque"});
        TERM_TO_POI_TYPES.put("temple", new String[]{"temple"});
        TERM_TO_POI_TYPES.put("synagogue", new String[]{"synagogue"});

        // Education
        TERM_TO_POI_TYPES.put("school", new String[]{"school"});
        TERM_TO_POI_TYPES.put("university", new String[]{"university", "college"});
        TERM_TO_POI_TYPES.put("college", new String[]{"college"});

        // Water
        TERM_TO_POI_TYPES.put("water", new String[]{"drinking_water", "water_point"});
        TERM_TO_POI_TYPES.put("drinking water", new String[]{"drinking_water"});
        TERM_TO_POI_TYPES.put("fountain", new String[]{"fountain", "drinking_water"});
    }

    private final OsmandApplication app;
    private final MapPoiTypes mapPoiTypes;

    public PoiTypeResolver(@NonNull OsmandApplication app) {
        this.app = app;
        this.mapPoiTypes = app.getPoiTypes();
    }

    /**
     * Resolve query terms to OsmAnd POI types.
     *
     * @param query The user's search query
     * @return List of POI type key names to search for
     */
    @NonNull
    public List<String> resolvePoiTypes(@NonNull String query) {
        Set<String> poiTypes = new LinkedHashSet<>();
        String lowerQuery = query.toLowerCase();

        // Check each mapping term
        for (Map.Entry<String, String[]> entry : TERM_TO_POI_TYPES.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                poiTypes.addAll(Arrays.asList(entry.getValue()));
            }
        }

        // If no matches found, try to match individual words
        if (poiTypes.isEmpty()) {
            String[] words = lowerQuery.split("\\s+");
            for (String word : words) {
                String[] types = TERM_TO_POI_TYPES.get(word);
                if (types != null) {
                    poiTypes.addAll(Arrays.asList(types));
                }
            }
        }

        // If still no matches, try to find POI types directly from OsmAnd
        if (poiTypes.isEmpty()) {
            List<String> directMatches = findDirectPoiMatches(lowerQuery);
            poiTypes.addAll(directMatches);
        }

        return new ArrayList<>(poiTypes);
    }

    /**
     * Try to find POI types directly from OsmAnd's POI type registry.
     */
    @NonNull
    private List<String> findDirectPoiMatches(@NonNull String query) {
        List<String> matches = new ArrayList<>();

        // Try to find by key name
        AbstractPoiType poiType = mapPoiTypes.getAnyPoiTypeByKey(query.replace(' ', '_'));
        if (poiType != null) {
            matches.add(poiType.getKeyName());
            return matches;
        }

        // Try to find by translation
        Map<String, PoiType> translated = mapPoiTypes.getAllTranslatedNames(false);
        PoiType translatedType = translated.get(query);
        if (translatedType != null) {
            matches.add(translatedType.getKeyName());
        }

        return matches;
    }

    /**
     * Get the AbstractPoiType object for a given key name.
     */
    @Nullable
    public AbstractPoiType getPoiType(@NonNull String keyName) {
        return mapPoiTypes.getAnyPoiTypeByKey(keyName);
    }

    /**
     * Get PoiCategory by name.
     */
    @Nullable
    public PoiCategory getPoiCategory(@NonNull String categoryName) {
        return mapPoiTypes.getPoiCategoryByName(categoryName);
    }

    /**
     * Extract POI-related search terms from a query.
     *
     * @param query User's full query
     * @return List of POI-related terms found in the query
     */
    @NonNull
    public List<String> extractPoiTerms(@NonNull String query) {
        List<String> terms = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        // Find all matching terms
        for (String term : TERM_TO_POI_TYPES.keySet()) {
            if (lowerQuery.contains(term)) {
                terms.add(term);
            }
        }

        // Remove shorter terms if a longer term contains them
        // e.g., if "gas station" matches, remove "gas"
        List<String> filtered = new ArrayList<>();
        for (String term : terms) {
            boolean isSubstring = false;
            for (String other : terms) {
                if (!term.equals(other) && other.contains(term)) {
                    isSubstring = true;
                    break;
                }
            }
            if (!isSubstring) {
                filtered.add(term);
            }
        }

        return filtered;
    }

    /**
     * Get a human-readable description for a POI type.
     */
    @NonNull
    public String getTypeDescription(@NonNull String poiTypeKey) {
        AbstractPoiType poiType = mapPoiTypes.getAnyPoiTypeByKey(poiTypeKey);
        if (poiType != null) {
            String translation = poiType.getTranslation();
            if (translation != null && !translation.isEmpty()) {
                return translation;
            }
        }
        // Fallback: format the key name
        return poiTypeKey.replace('_', ' ');
    }

    /**
     * Check if a query contains any POI-related terms.
     */
    public boolean containsPoiTerms(@NonNull String query) {
        String lowerQuery = query.toLowerCase();
        for (String term : TERM_TO_POI_TYPES.keySet()) {
            if (lowerQuery.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all known POI terms (for debugging/testing).
     */
    @NonNull
    public Set<String> getAllKnownTerms() {
        return new HashSet<>(TERM_TO_POI_TYPES.keySet());
    }
}
