/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.plus.OsmandApplication;
import net.osmand.util.MapUtils;

/**
 * LAMPP Phase 8: Encapsulates user's location context for POI searches.
 *
 * Provides location data and utility methods for calculating distances
 * and directions to points of interest.
 */
public class LocationContext {

    // Default search radius in meters
    public static final int DEFAULT_RADIUS_METERS = 1000;

    // Maximum search radius in meters
    public static final int MAX_RADIUS_METERS = 10000;

    private final double latitude;
    private final double longitude;
    private final float accuracy;
    private final float bearing;  // Direction user is facing (0-360 degrees)
    private final int searchRadiusMeters;
    private final long timestamp;

    /**
     * Create a location context with all parameters.
     */
    public LocationContext(double latitude, double longitude, float accuracy,
                           float bearing, int searchRadiusMeters) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.bearing = bearing;
        this.searchRadiusMeters = Math.min(searchRadiusMeters, MAX_RADIUS_METERS);
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Create a location context with default search radius.
     */
    public LocationContext(double latitude, double longitude) {
        this(latitude, longitude, 0f, 0f, DEFAULT_RADIUS_METERS);
    }

    /**
     * Create a LocationContext from OsmAnd's location provider.
     *
     * @param app OsmandApplication instance
     * @return LocationContext or null if location unavailable
     */
    @Nullable
    public static LocationContext fromOsmAnd(@NonNull OsmandApplication app) {
        return fromOsmAnd(app, DEFAULT_RADIUS_METERS);
    }

    /**
     * Create a LocationContext from OsmAnd's location provider with custom radius.
     *
     * @param app OsmandApplication instance
     * @param radiusMeters Search radius in meters
     * @return LocationContext or null if location unavailable
     */
    @Nullable
    public static LocationContext fromOsmAnd(@NonNull OsmandApplication app, int radiusMeters) {
        net.osmand.Location location = app.getLocationProvider().getLastKnownLocation();
        if (location == null) {
            return null;
        }

        return new LocationContext(
                location.getLatitude(),
                location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f,
                location.hasBearing() ? location.getBearing() : 0f,
                radiusMeters
        );
    }

    /**
     * Create a LocationContext from a LatLon.
     */
    @NonNull
    public static LocationContext fromLatLon(@NonNull LatLon latLon, int radiusMeters) {
        return new LocationContext(latLon.getLatitude(), latLon.getLongitude(),
                0f, 0f, radiusMeters);
    }

    /**
     * Calculate distance in meters to a given point.
     */
    public double distanceTo(double lat, double lon) {
        return MapUtils.getDistance(latitude, longitude, lat, lon);
    }

    /**
     * Calculate bearing (direction) to a given point.
     *
     * @return Bearing in degrees (0-360, where 0=North, 90=East)
     */
    public float bearingTo(double lat, double lon) {
        // Calculate bearing using spherical law of cosines
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(lat);
        double dLon = Math.toRadians(lon - longitude);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                   Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));

        // Normalize to 0-360
        return (float) ((bearing + 360) % 360);
    }

    /**
     * Get compass direction string (N, NE, E, etc.) to a point.
     */
    @NonNull
    public String directionTo(double lat, double lon) {
        float bearing = bearingTo(lat, lon);
        return bearingToDirection(bearing);
    }

    /**
     * Convert bearing angle to compass direction string.
     */
    @NonNull
    public static String bearingToDirection(float bearing) {
        // Normalize bearing to 0-360
        while (bearing < 0) bearing += 360;
        while (bearing >= 360) bearing -= 360;

        if (bearing >= 337.5 || bearing < 22.5) return "N";
        if (bearing >= 22.5 && bearing < 67.5) return "NE";
        if (bearing >= 67.5 && bearing < 112.5) return "E";
        if (bearing >= 112.5 && bearing < 157.5) return "SE";
        if (bearing >= 157.5 && bearing < 202.5) return "S";
        if (bearing >= 202.5 && bearing < 247.5) return "SW";
        if (bearing >= 247.5 && bearing < 292.5) return "W";
        return "NW";
    }

    /**
     * Check if a point is within the search radius.
     */
    public boolean isWithinRadius(double lat, double lon) {
        return distanceTo(lat, lon) <= searchRadiusMeters;
    }

    /**
     * Get a human-readable location string.
     */
    @NonNull
    public String getLocationString() {
        return String.format("%.4f°, %.4f°", latitude, longitude);
    }

    // Getters

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public float getBearing() {
        return bearing;
    }

    public int getSearchRadiusMeters() {
        return searchRadiusMeters;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get LatLon representation.
     */
    @NonNull
    public LatLon getLatLon() {
        return new LatLon(latitude, longitude);
    }

    /**
     * Check if this location context is still fresh (less than 5 minutes old).
     */
    public boolean isFresh() {
        return System.currentTimeMillis() - timestamp < 5 * 60 * 1000;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("LocationContext[%.4f, %.4f, radius=%dm, accuracy=%.1fm]",
                latitude, longitude, searchRadiusMeters, accuracy);
    }
}
