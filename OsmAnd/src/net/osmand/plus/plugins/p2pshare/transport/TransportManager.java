package net.osmand.plus.plugins.p2pshare.transport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.p2pshare.ShareableContent;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;

import org.apache.commons.logging.Log;

/**
 * Manages transport connections for P2P file transfer.
 *
 * Strategy:
 * 1. Primary: WiFi Direct (fast, ~250 Mbps)
 * 2. Fallback: Bluetooth Classic (slower, ~720 Kbps, more compatible)
 *
 * TODO Phase 6.4: Full implementation
 */
public class TransportManager {

    private static final Log LOG = PlatformUtil.getLog(TransportManager.class);

    private final OsmandApplication app;

    // TODO Phase 6.4/6.5: These will be implemented
    // private WifiDirectTransport wifiDirectTransport;
    // private BluetoothTransport bluetoothTransport;

    private TransportCallback callback;
    private DiscoveredPeer connectedPeer;

    public interface TransportCallback {
        void onConnected(@NonNull DiscoveredPeer peer);
        void onDisconnected(@NonNull DiscoveredPeer peer, @Nullable String reason);
        void onConnectionFailed(@NonNull DiscoveredPeer peer, @NonNull String error);
        void onManifestReceived(@NonNull DiscoveredPeer peer, @NonNull String manifestJson);
        void onTransferProgress(@NonNull String filename, int percent, long bytesTransferred, long totalBytes);
        void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error);
    }

    public TransportManager(@NonNull OsmandApplication app) {
        this.app = app;
        // TODO Phase 6.4: Initialize transports
        // wifiDirectTransport = new WifiDirectTransport(app);
        // bluetoothTransport = new BluetoothTransport(app);
    }

    public void setCallback(@Nullable TransportCallback callback) {
        this.callback = callback;
    }

    /**
     * Connect to a peer using the best available transport.
     * Tries WiFi Direct first, falls back to Bluetooth if unavailable.
     */
    public void connect(@NonNull DiscoveredPeer peer) {
        LOG.info("Initiating connection to: " + peer.getDeviceName());

        // TODO Phase 6.4: Implement connection logic
        // Try WiFi Direct first
        // if (wifiDirectTransport.isAvailable()) {
        //     wifiDirectTransport.connect(peer, new TransportCallback() { ... });
        // } else if (bluetoothTransport.isAvailable()) {
        //     // Fallback to Bluetooth
        //     bluetoothTransport.connect(peer, new TransportCallback() { ... });
        // } else {
        //     callback.onConnectionFailed(peer, "No transport available");
        // }
    }

    /**
     * Disconnect from the currently connected peer.
     */
    public void disconnect() {
        if (connectedPeer == null) {
            return;
        }

        LOG.info("Disconnecting from: " + connectedPeer.getDeviceName());

        // TODO Phase 6.4: Implement disconnection
        // if (wifiDirectTransport.isConnected()) {
        //     wifiDirectTransport.disconnect();
        // } else if (bluetoothTransport.isConnected()) {
        //     bluetoothTransport.disconnect();
        // }

        connectedPeer = null;
    }

    /**
     * Send our content manifest to the connected peer.
     */
    public void sendManifest(@NonNull String manifestJson) {
        if (connectedPeer == null) {
            LOG.warn("Cannot send manifest: not connected");
            return;
        }

        LOG.info("Sending manifest to: " + connectedPeer.getDeviceName());

        // TODO Phase 6.4: Implement manifest exchange protocol
    }

    /**
     * Request a file from the connected peer.
     */
    public void requestFile(@NonNull ShareableContent content) {
        if (connectedPeer == null) {
            LOG.warn("Cannot request file: not connected");
            return;
        }

        LOG.info("Requesting file: " + content.getFilename());

        // TODO Phase 6.4: Implement file transfer protocol
    }

    /**
     * Cancel an ongoing file transfer.
     */
    public void cancelTransfer() {
        LOG.info("Cancelling transfer");

        // TODO Phase 6.4: Implement transfer cancellation
    }

    /**
     * Check if currently connected to a peer.
     */
    public boolean isConnected() {
        return connectedPeer != null;
    }

    /**
     * Get the currently connected peer.
     */
    @Nullable
    public DiscoveredPeer getConnectedPeer() {
        return connectedPeer;
    }

    /**
     * Check if WiFi Direct is available on this device.
     */
    public boolean isWifiDirectAvailable() {
        // TODO Phase 6.4: Check WiFi Direct availability
        return false;
    }

    /**
     * Check if Bluetooth is available for fallback.
     */
    public boolean isBluetoothAvailable() {
        // TODO Phase 6.5: Check Bluetooth availability
        return false;
    }

    /**
     * Clean up resources.
     */
    public void shutdown() {
        disconnect();

        // TODO Phase 6.4/6.5: Shutdown transports
        // if (wifiDirectTransport != null) {
        //     wifiDirectTransport.shutdown();
        // }
        // if (bluetoothTransport != null) {
        //     bluetoothTransport.shutdown();
        // }
    }
}
