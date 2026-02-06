package net.osmand.plus.plugins.p2pshare.transport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;

/**
 * Callback interface for transport events (WiFi Direct, Bluetooth).
 */
public interface TransportCallback {
    void onConnected(@NonNull DiscoveredPeer peer);
    void onDisconnected(@NonNull DiscoveredPeer peer, @Nullable String reason);
    void onConnectionFailed(@NonNull DiscoveredPeer peer, @NonNull String error);
    void onManifestReceived(@NonNull DiscoveredPeer peer, @NonNull String manifestJson);
    void onTransferProgress(@NonNull String filename, int percent, long bytesTransferred, long totalBytes);
    void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error);
}
