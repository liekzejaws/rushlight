/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.plugins.p2pshare.ContentManifest;

import java.util.Objects;

/**
 * Represents a discovered peer device that can participate in P2P sharing.
 */
public class DiscoveredPeer {

    private final String deviceId;      // Unique identifier (BLE address or similar)
    private final String deviceName;    // User-friendly name
    private final long discoveredAt;    // Timestamp when discovered

    // Connection info
    private String macAddress;          // For Bluetooth connection
    private int signalStrength;         // RSSI for distance estimation

    // Content info (populated after manifest exchange)
    private ContentManifest remoteManifest;
    private String manifestSummary;     // e.g., "3 maps, 1 model"

    // State
    private PeerState state = PeerState.DISCOVERED;

    public enum PeerState {
        DISCOVERED,     // Just found via BLE beacon
        CONNECTING,     // Establishing WiFi Direct connection
        CONNECTED,      // Connected and exchanged manifests
        TRANSFERRING,   // File transfer in progress
        DISCONNECTED    // Was connected, now lost
    }

    public DiscoveredPeer(@NonNull String deviceId, @NonNull String deviceName) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.discoveredAt = System.currentTimeMillis();
    }

    @NonNull
    public String getDeviceId() {
        return deviceId;
    }

    @NonNull
    public String getDeviceName() {
        return deviceName;
    }

    public long getDiscoveredAt() {
        return discoveredAt;
    }

    @Nullable
    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(@Nullable String macAddress) {
        this.macAddress = macAddress;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }

    @Nullable
    public ContentManifest getRemoteManifest() {
        return remoteManifest;
    }

    public void setRemoteManifest(@Nullable ContentManifest remoteManifest) {
        this.remoteManifest = remoteManifest;
    }

    @Nullable
    public String getManifestSummary() {
        return manifestSummary;
    }

    public void setManifestSummary(@Nullable String manifestSummary) {
        this.manifestSummary = manifestSummary;
    }

    @NonNull
    public PeerState getState() {
        return state;
    }

    public void setState(@NonNull PeerState state) {
        this.state = state;
    }

    /**
     * Estimate distance based on signal strength.
     * Returns descriptive string like "Very close", "Nearby", "Far"
     */
    @NonNull
    public String getDistanceEstimate() {
        if (signalStrength > -50) {
            return "Very close";
        } else if (signalStrength > -70) {
            return "Nearby";
        } else if (signalStrength > -85) {
            return "Moderate distance";
        } else {
            return "Far";
        }
    }

    /**
     * Check if this peer has been seen recently (within last 30 seconds).
     */
    public boolean isRecent() {
        return System.currentTimeMillis() - discoveredAt < 30000;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscoveredPeer that = (DiscoveredPeer) o;
        return Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId);
    }

    @Override
    public String toString() {
        return "DiscoveredPeer{" +
                "name='" + deviceName + '\'' +
                ", state=" + state +
                ", signal=" + signalStrength +
                '}';
    }
}
