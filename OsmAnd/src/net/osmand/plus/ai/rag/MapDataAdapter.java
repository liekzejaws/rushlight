/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.data.Amenity;
import net.osmand.data.LatLon;
import net.osmand.osm.AbstractPoiType;
import net.osmand.osm.PoiCategory;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.poi.PoiFiltersHelper;
import net.osmand.plus.poi.PoiUIFilter;
import net.osmand.plus.search.QuickSearchHelper;
import net.osmand.ResultMatcher;
import net.osmand.search.SearchUICore;
import net.osmand.search.SearchUICore.SearchResultCollection;
import net.osmand.search.core.ObjectType;
import net.osmand.search.core.SearchResult;
import net.osmand.search.core.SearchSettings;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * LAMPP Phase 8: Adapter for searching OsmAnd's POI database.
 *
 * Bridges the RAG pipeline to OsmAnd's POI search system (PoiUIFilter, Amenity).
 * Provides location-aware POI search with distance and direction calculation.
 */
public class MapDataAdapter {

    private static final Log LOG = PlatformUtil.getLog(MapDataAdapter.class);

    // Default maximum results to return
    private static final int DEFAULT_MAX_RESULTS = 10;

    // Default search radius in meters
    private static final int DEFAULT_SEARCH_RADIUS = 1000;

    // Maximum search radius in meters
    private static final int MAX_SEARCH_RADIUS = 10000;

    private final OsmandApplication app;
    private final PoiTypeResolver typeResolver;
    private final PoiFiltersHelper poiFiltersHelper;

    public MapDataAdapter(@NonNull OsmandApplication app) {
        this.app = app;
        this.typeResolver = new PoiTypeResolver(app);
        this.poiFiltersHelper = app.getPoiFilters();
    }

    /**
     * Check if POI search is available (map data loaded).
     */
    public boolean isAvailable() {
        // Check if we have any map regions loaded
        return !app.getResourceManager().getIndexFileNames().isEmpty();
    }

    /**
     * Search for POIs near a location based on query terms.
     *
     * @param location User's location context
     * @param query The user's search query
     * @param maxResults Maximum number of results to return
     * @return List of PoiSource objects sorted by distance
     */
    @NonNull
    public List<PoiSource> searchNearby(@NonNull LocationContext location,
                                         @NonNull String query,
                                         int maxResults) {
        LOG.info("Searching POIs near " + location + " for: " + query);
        long startTime = System.currentTimeMillis();

        // Resolve query to POI types
        List<String> poiTypeKeys = typeResolver.resolvePoiTypes(query);
        if (poiTypeKeys.isEmpty()) {
            LOG.info("No POI types matched for query: " + query);
            return new ArrayList<>();
        }

        LOG.debug("Resolved POI types: " + poiTypeKeys);

        // Search for each POI type
        Set<Amenity> allAmenities = new HashSet<>();

        for (String poiTypeKey : poiTypeKeys) {
            List<Amenity> amenities = searchByPoiType(poiTypeKey, location);
            allAmenities.addAll(amenities);
        }

        // Convert to PoiSource and filter by distance
        List<PoiSource> results = new ArrayList<>();
        for (Amenity amenity : allAmenities) {
            if (amenity.getLocation() != null) {
                PoiSource source = PoiSource.fromAmenity(amenity, location);
                if (source.getDistanceMeters() <= location.getSearchRadiusMeters()) {
                    results.add(source);
                }
            }
        }

        // Sort by distance
        results.sort(Comparator.comparingInt(PoiSource::getDistanceMeters));

        // Limit results
        if (results.size() > maxResults) {
            results = results.subList(0, maxResults);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOG.info("POI search found " + results.size() + " results in " + elapsed + "ms");

        return results;
    }

    /**
     * Search for POIs near a location using default settings.
     */
    @NonNull
    public List<PoiSource> searchNearby(@NonNull LocationContext location, @NonNull String query) {
        return searchNearby(location, query, DEFAULT_MAX_RESULTS);
    }

    /**
     * Search for POIs by type key name.
     */
    @NonNull
    private List<Amenity> searchByPoiType(@NonNull String poiTypeKey, @NonNull LocationContext location) {
        try {
            // Get or create a filter for this POI type
            AbstractPoiType poiType = typeResolver.getPoiType(poiTypeKey);
            if (poiType == null) {
                LOG.debug("POI type not found: " + poiTypeKey);
                return new ArrayList<>();
            }

            // Create a filter for this POI type
            PoiUIFilter filter = new PoiUIFilter(poiType, app, "");

            // Set the search distance
            setSearchDistance(filter, location.getSearchRadiusMeters());

            // Calculate bounding box
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            double radius = location.getSearchRadiusMeters();

            double baseDistY = MapUtils.getDistance(lat, lon, lat - 1, lon);
            double baseDistX = MapUtils.getDistance(lat, lon, lat, lon - 1);

            double topLat = Math.min(lat + (radius / baseDistY), 84.);
            double bottomLat = Math.max(lat - (radius / baseDistY), -84.);
            double leftLon = Math.max(lon - (radius / baseDistX), -180);
            double rightLon = Math.min(lon + (radius / baseDistX), 180);

            // Search amenities
            List<Amenity> results = filter.searchAmenities(topLat, leftLon, bottomLat, rightLon, -1, null, false);

            LOG.debug("Found " + results.size() + " amenities for type: " + poiTypeKey);
            return results;

        } catch (Exception e) {
            LOG.error("Error searching POI type: " + poiTypeKey, e);
            return new ArrayList<>();
        }
    }

    /**
     * Set the search distance index on a filter.
     */
    private void setSearchDistance(@NonNull PoiUIFilter filter, int radiusMeters) {
        // The filter uses distance indices that map to specific values
        // We need to find the appropriate index for our desired radius
        // distanceToSearchValues = {1, 2, 5, 10, 20, 50, 100, 200, 500} (in km)

        int radiusKm = radiusMeters / 1000;
        int[] distances = {1, 2, 5, 10, 20, 50, 100, 200, 500};

        int index = 0;
        for (int i = 0; i < distances.length; i++) {
            if (distances[i] >= radiusKm) {
                index = i;
                break;
            }
            index = i;
        }

        // Use reflection or just accept the default for now
        // The filter will search within its configured distance
    }

    /**
     * Search for POIs using a custom filter configuration.
     *
     * @param location User's location
     * @param acceptedTypes Map of POI categories to accepted subtypes
     * @param maxResults Maximum results
     * @return List of PoiSource
     */
    @NonNull
    public List<PoiSource> searchWithFilter(@NonNull LocationContext location,
                                             @NonNull Map<PoiCategory, LinkedHashSet<String>> acceptedTypes,
                                             int maxResults) {
        try {
            // Create custom filter
            PoiUIFilter filter = new PoiUIFilter("AI Search", null, acceptedTypes, app);

            // Calculate bounding box
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            double radius = location.getSearchRadiusMeters();

            double baseDistY = MapUtils.getDistance(lat, lon, lat - 1, lon);
            double baseDistX = MapUtils.getDistance(lat, lon, lat, lon - 1);

            double topLat = Math.min(lat + (radius / baseDistY), 84.);
            double bottomLat = Math.max(lat - (radius / baseDistY), -84.);
            double leftLon = Math.max(lon - (radius / baseDistX), -180);
            double rightLon = Math.min(lon + (radius / baseDistX), 180);

            // Search
            List<Amenity> amenities = filter.searchAmenities(topLat, leftLon, bottomLat, rightLon, -1, null, false);

            // Convert and sort
            List<PoiSource> results = new ArrayList<>();
            for (Amenity amenity : amenities) {
                if (amenity.getLocation() != null) {
                    PoiSource source = PoiSource.fromAmenity(amenity, location);
                    if (source.getDistanceMeters() <= radius) {
                        results.add(source);
                    }
                }
            }

            results.sort(Comparator.comparingInt(PoiSource::getDistanceMeters));

            if (results.size() > maxResults) {
                results = results.subList(0, maxResults);
            }

            return results;

        } catch (Exception e) {
            LOG.error("Error in custom filter search", e);
            return new ArrayList<>();
        }
    }

    /**
     * Get the current location context from OsmAnd.
     *
     * @return LocationContext or null if location unavailable
     */
    @Nullable
    public LocationContext getCurrentLocation() {
        return LocationContext.fromOsmAnd(app, DEFAULT_SEARCH_RADIUS);
    }

    /**
     * Get location context with custom radius.
     */
    @Nullable
    public LocationContext getCurrentLocation(int radiusMeters) {
        return LocationContext.fromOsmAnd(app, Math.min(radiusMeters, MAX_SEARCH_RADIUS));
    }

    /**
     * Check if a query contains POI-related terms.
     */
    public boolean containsPoiTerms(@NonNull String query) {
        return typeResolver.containsPoiTerms(query);
    }

    /**
     * Extract POI search terms from a query.
     */
    @NonNull
    public List<String> extractPoiTerms(@NonNull String query) {
        return typeResolver.extractPoiTerms(query);
    }

    /**
     * Get the PoiTypeResolver for direct access.
     */
    @NonNull
    public PoiTypeResolver getTypeResolver() {
        return typeResolver;
    }

    /**
     * Search for a place (city, village, town) by name.
     *
     * @param placeName Name of the place to search for
     * @param location User's location for distance/direction calculation
     * @param maxResults Maximum number of results
     * @return List of PlaceResult objects sorted by relevance
     */
    @NonNull
    public List<PlaceResult> searchPlace(@NonNull String placeName,
                                          @NonNull LocationContext location,
                                          int maxResults) {
        LOG.info("Searching for place: " + placeName + " from " + location);
        long startTime = System.currentTimeMillis();

        List<PlaceResult> results = new ArrayList<>();

        try {
            QuickSearchHelper searchHelper = app.getSearchUICore();
            if (searchHelper == null) {
                LOG.warn("SearchUICore not available");
                return results;
            }

            SearchUICore searchUICore = searchHelper.getCore();
            if (searchUICore == null) {
                LOG.warn("SearchUICore core not available");
                return results;
            }

            // Set up search settings with user's location and search for addresses
            SearchSettings settings = searchUICore.getSearchSettings()
                    .setOriginalLocation(new LatLon(location.getLatitude(), location.getLongitude()))
                    .setRadiusLevel(1)
                    .setSearchTypes(ObjectType.CITY, ObjectType.VILLAGE, ObjectType.BOUNDARY, ObjectType.REGION);

            searchUICore.updateSettings(settings);

            // Perform the search with a latch to wait for results
            final List<SearchResult> searchResults = new ArrayList<>();
            final CountDownLatch latch = new CountDownLatch(1);

            searchUICore.search(placeName, false, new ResultMatcher<SearchResult>() {
                @Override
                public boolean publish(SearchResult result) {
                    // Filter to only cities, villages, boundaries, and regions
                    if (result.objectType == ObjectType.CITY ||
                        result.objectType == ObjectType.VILLAGE ||
                        result.objectType == ObjectType.BOUNDARY ||
                        result.objectType == ObjectType.REGION) {
                        synchronized (searchResults) {
                            searchResults.add(result);
                        }
                    }
                    // Check if search finished
                    if (result.objectType == ObjectType.SEARCH_FINISHED) {
                        latch.countDown();
                    }
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            });

            // Wait for search to complete (max 3 seconds)
            latch.await(3, TimeUnit.SECONDS);

            // Also get from the result collection
            SearchResultCollection resultCollection = searchHelper.getResultCollection();
            if (resultCollection != null && resultCollection.getCurrentSearchResults() != null) {
                for (SearchResult result : resultCollection.getCurrentSearchResults()) {
                    if (result.objectType == ObjectType.CITY ||
                        result.objectType == ObjectType.VILLAGE ||
                        result.objectType == ObjectType.BOUNDARY ||
                        result.objectType == ObjectType.REGION) {
                        synchronized (searchResults) {
                            if (!searchResults.contains(result)) {
                                searchResults.add(result);
                            }
                        }
                    }
                }
            }

            // Convert to PlaceResult
            for (SearchResult result : searchResults) {
                if (result.location != null) {
                    PlaceResult placeResult = PlaceResult.fromSearchResult(result, location);
                    results.add(placeResult);
                }
            }

            // Sort by relevance (closest match first, then by distance)
            results.sort((a, b) -> {
                // Prefer exact name matches
                boolean aExact = a.getName().equalsIgnoreCase(placeName);
                boolean bExact = b.getName().equalsIgnoreCase(placeName);
                if (aExact && !bExact) return -1;
                if (!aExact && bExact) return 1;

                // Then by distance
                return Integer.compare(a.getDistanceMeters(), b.getDistanceMeters());
            });

            // Limit results
            if (results.size() > maxResults) {
                results = results.subList(0, maxResults);
            }

        } catch (Exception e) {
            LOG.error("Error searching for place: " + placeName, e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOG.info("Place search found " + results.size() + " results in " + elapsed + "ms");

        return results;
    }

    /**
     * Search for a place with default max results.
     */
    @NonNull
    public List<PlaceResult> searchPlace(@NonNull String placeName, @NonNull LocationContext location) {
        return searchPlace(placeName, location, 5);
    }

    /**
     * Get a formatted status string for display.
     */
    @NonNull
    public String getStatusString() {
        if (isAvailable()) {
            int mapCount = app.getResourceManager().getIndexFileNames().size();
            return "Map data: " + mapCount + " region(s) loaded";
        } else {
            return "No map data loaded";
        }
    }
}
