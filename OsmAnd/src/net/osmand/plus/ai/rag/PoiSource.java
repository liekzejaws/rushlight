/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai.rag;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.Amenity;
import net.osmand.data.LatLon;

/**
 * LAMPP Phase 8: Data class representing a POI search result.
 *
 * Similar to ArticleSource but for map points of interest.
 * Contains location data, distance, and direction relative to user.
 */
public class PoiSource {

    private final String name;
    private final String type;          // POI category (e.g., "cafe", "restaurant")
    private final String subType;       // POI subcategory (e.g., "coffee_shop", "fast_food")
    private final double latitude;
    private final double longitude;
    private final int distanceMeters;
    private final String direction;     // Compass direction (N, NE, E, etc.)
    private final String openingHours;  // Opening hours if available
    private final String address;       // Address if available
    private final String phone;         // Phone number if available
    private final String website;       // Website if available

    /**
     * Full constructor with all fields.
     */
    public PoiSource(@NonNull String name, @NonNull String type, @Nullable String subType,
                     double latitude, double longitude, int distanceMeters,
                     @NonNull String direction, @Nullable String openingHours,
                     @Nullable String address, @Nullable String phone, @Nullable String website) {
        this.name = name;
        this.type = type;
        this.subType = subType != null ? subType : "";
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = distanceMeters;
        this.direction = direction;
        this.openingHours = openingHours;
        this.address = address;
        this.phone = phone;
        this.website = website;
    }

    /**
     * Builder for convenient construction.
     */
    public static class Builder {
        private String name = "";
        private String type = "";
        private String subType = "";
        private double latitude;
        private double longitude;
        private int distanceMeters;
        private String direction = "";
        private String openingHours;
        private String address;
        private String phone;
        private String website;

        public Builder setName(@NonNull String name) {
            this.name = name;
            return this;
        }

        public Builder setType(@NonNull String type) {
            this.type = type;
            return this;
        }

        public Builder setSubType(@Nullable String subType) {
            this.subType = subType != null ? subType : "";
            return this;
        }

        public Builder setLatitude(double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder setLongitude(double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder setDistanceMeters(int distanceMeters) {
            this.distanceMeters = distanceMeters;
            return this;
        }

        public Builder setDirection(@NonNull String direction) {
            this.direction = direction;
            return this;
        }

        public Builder setOpeningHours(@Nullable String openingHours) {
            this.openingHours = openingHours;
            return this;
        }

        public Builder setAddress(@Nullable String address) {
            this.address = address;
            return this;
        }

        public Builder setPhone(@Nullable String phone) {
            this.phone = phone;
            return this;
        }

        public Builder setWebsite(@Nullable String website) {
            this.website = website;
            return this;
        }

        public PoiSource build() {
            return new PoiSource(name, type, subType, latitude, longitude,
                    distanceMeters, direction, openingHours, address, phone, website);
        }
    }

    /**
     * Create a PoiSource from an OsmAnd Amenity.
     *
     * @param amenity OsmAnd Amenity object
     * @param location User's location context for distance/direction calculation
     * @return PoiSource with all available data
     */
    @NonNull
    public static PoiSource fromAmenity(@NonNull Amenity amenity, @NonNull LocationContext location) {
        LatLon amenityLoc = amenity.getLocation();
        double lat = amenityLoc.getLatitude();
        double lon = amenityLoc.getLongitude();

        int distance = (int) location.distanceTo(lat, lon);
        String direction = location.directionTo(lat, lon);

        return new Builder()
                .setName(amenity.getName())
                .setType(amenity.getType() != null ? amenity.getType().getKeyName() : "poi")
                .setSubType(amenity.getSubType())
                .setLatitude(lat)
                .setLongitude(lon)
                .setDistanceMeters(distance)
                .setDirection(direction)
                .setOpeningHours(amenity.getOpeningHours())
                .setAddress(getAddress(amenity))
                .setPhone(amenity.getPhone())
                .setWebsite(amenity.getSite())
                .build();
    }

    /**
     * Extract address from amenity tags.
     */
    @Nullable
    private static String getAddress(@NonNull Amenity amenity) {
        // Try to build address from available tags
        StringBuilder addr = new StringBuilder();

        String street = amenity.getAdditionalInfo("addr:street");
        String houseNumber = amenity.getAdditionalInfo("addr:housenumber");
        String city = amenity.getAdditionalInfo("addr:city");

        if (street != null) {
            if (houseNumber != null) {
                addr.append(houseNumber).append(" ");
            }
            addr.append(street);
        }
        if (city != null) {
            if (addr.length() > 0) {
                addr.append(", ");
            }
            addr.append(city);
        }

        return addr.length() > 0 ? addr.toString() : null;
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

    @NonNull
    public String getSubType() {
        return subType;
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
    public String getOpeningHours() {
        return openingHours;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    @Nullable
    public String getPhone() {
        return phone;
    }

    @Nullable
    public String getWebsite() {
        return website;
    }

    /**
     * Get LatLon for map display.
     */
    @NonNull
    public LatLon getLatLon() {
        return new LatLon(latitude, longitude);
    }

    /**
     * Get display type (prefers subType if available).
     */
    @NonNull
    public String getDisplayType() {
        if (!subType.isEmpty()) {
            return formatTypeName(subType);
        }
        return formatTypeName(type);
    }

    /**
     * Format a type name for display (replace underscores, capitalize).
     */
    @NonNull
    private String formatTypeName(@NonNull String typeName) {
        return typeName.replace('_', ' ');
    }

    /**
     * Get formatted distance string (e.g., "150m" or "1.2 km").
     */
    @NonNull
    public String getFormattedDistance() {
        if (distanceMeters < 1000) {
            return distanceMeters + "m";
        }
        return String.format("%.1f km", distanceMeters / 1000.0);
    }

    /**
     * Get combined distance and direction string (e.g., "150m NE").
     */
    @NonNull
    public String getDistanceDirection() {
        return getFormattedDistance() + " " + direction;
    }

    /**
     * Check if opening hours indicate currently open.
     * Note: This is a simplified check - full parsing requires OpeningHoursParser.
     */
    public boolean hasOpeningHours() {
        return openingHours != null && !openingHours.isEmpty();
    }

    /**
     * Format for prompt context.
     */
    @NonNull
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("• ").append(name);
        sb.append(" (").append(getDisplayType()).append(")");
        sb.append(" - ").append(getDistanceDirection());

        if (openingHours != null && !openingHours.isEmpty()) {
            sb.append(" [").append(openingHours).append("]");
        }

        return sb.toString();
    }

    /**
     * Format for chat display (shorter).
     */
    @NonNull
    public String toChatString() {
        return name + " - " + getDistanceDirection();
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("PoiSource[%s (%s) at %s]", name, type, getDistanceDirection());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PoiSource that = (PoiSource) o;
        return Double.compare(that.latitude, latitude) == 0 &&
                Double.compare(that.longitude, longitude) == 0 &&
                name.equals(that.name) &&
                type.equals(that.type);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + Double.hashCode(latitude);
        result = 31 * result + Double.hashCode(longitude);
        return result;
    }
}
