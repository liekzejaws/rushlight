package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.data.City;
import net.osmand.search.core.ObjectType;
import net.osmand.search.core.SearchResult;

/**
 * LAMPP Phase 8.2: Result from a place/city search.
 *
 * Represents a named place (city, village, town) found in the map data,
 * with distance and direction from the user's location.
 */
public class PlaceResult {

    private final String name;
    private final String type;          // "city", "village", "town", etc.
    private final double latitude;
    private final double longitude;
    private final int distanceMeters;
    private final String direction;     // "N", "NE", "E", etc.
    private final String regionName;    // State/region name if available

    public PlaceResult(@NonNull String name, @NonNull String type,
                       double latitude, double longitude,
                       int distanceMeters, @NonNull String direction,
                       @Nullable String regionName) {
        this.name = name;
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = distanceMeters;
        this.direction = direction;
        this.regionName = regionName;
    }

    /**
     * Create a PlaceResult from a SearchResult (city/village).
     *
     * @param searchResult The search result from SearchUICore
     * @param location User's location for distance/direction calculation
     * @return PlaceResult with distance and direction
     */
    @NonNull
    public static PlaceResult fromSearchResult(@NonNull SearchResult searchResult,
                                                @NonNull LocationContext location) {
        String name = searchResult.localeName != null ? searchResult.localeName : "Unknown";
        String type = getTypeString(searchResult.objectType);

        double lat = 0;
        double lon = 0;
        if (searchResult.location != null) {
            lat = searchResult.location.getLatitude();
            lon = searchResult.location.getLongitude();
        }

        int distance = (int) location.distanceTo(lat, lon);
        String direction = location.directionTo(lat, lon);

        // Try to get region name from related object
        String regionName = null;
        if (searchResult.localeRelatedObjectName != null) {
            regionName = searchResult.localeRelatedObjectName;
        }

        return new PlaceResult(name, type, lat, lon, distance, direction, regionName);
    }

    /**
     * Convert ObjectType to a user-friendly string.
     */
    @NonNull
    private static String getTypeString(@Nullable ObjectType objectType) {
        if (objectType == null) {
            return "place";
        }
        switch (objectType) {
            case CITY:
                return "city";
            case VILLAGE:
                return "village";
            case BOUNDARY:
                return "area";
            case POSTCODE:
                return "postcode";
            case STREET:
                return "street";
            case REGION:
                return "region";
            default:
                return "place";
        }
    }

    // Getters

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getType() {
        return type;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getDistanceMeters() {
        return distanceMeters;
    }

    @NonNull
    public String getDirection() {
        return direction;
    }

    @Nullable
    public String getRegionName() {
        return regionName;
    }

    @NonNull
    public LatLon getLatLon() {
        return new LatLon(latitude, longitude);
    }

    /**
     * Get formatted distance string (e.g., "15 km", "500 m").
     */
    @NonNull
    public String getFormattedDistance() {
        if (distanceMeters < 1000) {
            return distanceMeters + " m";
        } else if (distanceMeters < 10000) {
            return String.format("%.1f km", distanceMeters / 1000.0);
        } else {
            return (distanceMeters / 1000) + " km";
        }
    }

    /**
     * Get distance and direction string (e.g., "15 km NW").
     */
    @NonNull
    public String getDistanceDirection() {
        return getFormattedDistance() + " " + direction;
    }

    /**
     * Get a string suitable for LLM prompt context.
     */
    @NonNull
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("• ").append(name);
        if (regionName != null && !regionName.isEmpty()) {
            sb.append(", ").append(regionName);
        }
        sb.append(" (").append(type).append(")");
        sb.append(" - ").append(getDistanceDirection());
        sb.append(" at coordinates ").append(String.format("%.4f, %.4f", latitude, longitude));
        return sb.toString();
    }

    /**
     * Get a string suitable for chat display.
     */
    @NonNull
    public String toChatString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (regionName != null && !regionName.isEmpty()) {
            sb.append(", ").append(regionName);
        }
        sb.append(" (").append(getDistanceDirection()).append(")");
        return sb.toString();
    }

    @NonNull
    @Override
    public String toString() {
        return "PlaceResult{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", distance=" + distanceMeters +
                ", direction='" + direction + '\'' +
                ", region='" + regionName + '\'' +
                '}';
    }
}
