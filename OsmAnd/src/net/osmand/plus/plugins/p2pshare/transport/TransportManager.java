package net.osmand.plus.plugins.p2pshare.transport;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.p2pshare.ContentManifest;
import net.osmand.plus.plugins.p2pshare.ShareableContent;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;

import org.apache.commons.logging.Log;

/**
 * LAMPP Phase 6.4: Manages transport connections for P2P file transfer.
 *
 * Strategy:
 * 1. Primary: WiFi Direct (fast, ~250 Mbps)
 * 2. Fallback: Bluetooth Classic (slower, ~720 Kbps, more compatible) - Phase 6.5
 */
public class TransportManager implements TransportCallback {

    private static final Log LOG = PlatformUtil.getLog(TransportManager.class);

    private final OsmandApplication app;
    private final WifiDirectTransport wifiDirectTransport;
    // private BluetoothTransport bluetoothTransport; // TODO Phase 6.5

    private TransportCallback callback;
    private DiscoveredPeer connectedPeer;
    private ContentManifest localManifest;
    private Activity currentActivity;

    public TransportManager(@NonNull OsmandApplication app) {
        this.app = app;
        this.wifiDirectTransport = new WifiDirectTransport(app);
        wifiDirectTransport.setCallback(this);
        LOG.info("TransportManager initialized");
    }

    /**
     * Set the current activity for permission requests and receiver registration.
     */
    public void setCurrentActivity(@Nullable Activity activity) {
        // Unregister from old activity
        if (currentActivity != null && activity != currentActivity) {
            wifiDirectTransport.unregisterReceiver(currentActivity);
        }

        currentActivity = activity;

        // Register with new activity
        if (activity != null) {
            wifiDirectTransport.registerReceiver(activity);
        }
    }

    /**
     * Set the local content manifest for serving files.
     */
    public void setLocalManifest(@Nullable ContentManifest manifest) {
        this.localManifest = manifest;
        wifiDirectTransport.setLocalManifest(manifest);
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
        connectedPeer = peer;

        // Try WiFi Direct first
        if (wifiDirectTransport.isAvailable()) {
            wifiDirectTransport.connect(peer, currentActivity);
        } else {
            // TODO Phase 6.5: Try Bluetooth fallback
            // if (bluetoothTransport.isAvailable()) {
            //     bluetoothTransport.connect(peer, ...);
            // } else {
            if (callback != null) {
                callback.onConnectionFailed(peer, "No transport available (WiFi Direct not supported)");
            }
            // }
        }
    }

    /**
     * Disconnect from the currently connected peer.
     */
    public void disconnect() {
        if (connectedPeer == null) {
            return;
        }

        LOG.info("Disconnecting from: " + connectedPeer.getDeviceName());

        if (wifiDirectTransport.isConnected()) {
            wifiDirectTransport.disconnect();
        }
        // TODO Phase 6.5: else if (bluetoothTransport.isConnected()) { ... }

        connectedPeer = null;
    }

    /**
     * Send our content manifest to the connected peer.
     */
    public void sendManifest(@NonNull String manifestJson) {
        if (!isConnected()) {
            LOG.warn("Cannot send manifest: not connected");
            return;
        }

        LOG.info("Sending manifest to: " + connectedPeer.getDeviceName());

        if (wifiDirectTransport.isConnected()) {
            wifiDirectTransport.sendManifest(manifestJson);
        }
    }

    /**
     * Request a file from the connected peer.
     */
    public void requestFile(@NonNull ShareableContent content) {
        if (!isConnected()) {
            LOG.warn("Cannot request file: not connected");
            return;
        }

        LOG.info("Requesting file: " + content.getFilename());

        if (wifiDirectTransport.isConnected()) {
            wifiDirectTransport.requestFile(content);
        }
    }

    /**
     * Cancel an ongoing file transfer.
     */
    public void cancelTransfer() {
        LOG.info("Cancelling transfer");

        if (wifiDirectTransport.isConnected()) {
            wifiDirectTransport.cancelTransfer();
        }
    }

    /**
     * Check if currently connected to a peer.
     */
    public boolean isConnected() {
        return wifiDirectTransport.isConnected();
        // TODO Phase 6.5: || bluetoothTransport.isConnected();
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
        return wifiDirectTransport.isAvailable();
    }

    /**
     * Check if Bluetooth is available for fallback.
     */
    public boolean isBluetoothAvailable() {
        // TODO Phase 6.5: return bluetoothTransport.isAvailable();
        return false;
    }

    /**
     * Clean up resources.
     */
    public void shutdown() {
        if (currentActivity != null) {
            wifiDirectTransport.unregisterReceiver(currentActivity);
        }

        disconnect();
        wifiDirectTransport.shutdown();
        // TODO Phase 6.5: bluetoothTransport.shutdown();
    }

    // ---- TransportCallback implementation (forward to our callback) ----

    @Override
    public void onConnected(@NonNull DiscoveredPeer peer) {
        connectedPeer = peer;
        if (callback != null) {
            callback.onConnected(peer);
        }
    }

    @Override
    public void onDisconnected(@NonNull DiscoveredPeer peer, @Nullable String reason) {
        connectedPeer = null;
        if (callback != null) {
            callback.onDisconnected(peer, reason);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull DiscoveredPeer peer, @NonNull String error) {
        connectedPeer = null;
        if (callback != null) {
            callback.onConnectionFailed(peer, error);
        }
    }

    @Override
    public void onManifestReceived(@NonNull DiscoveredPeer peer, @NonNull String manifestJson) {
        if (callback != null) {
            callback.onManifestReceived(peer, manifestJson);
        }
    }

    @Override
    public void onTransferProgress(@NonNull String filename, int percent, long bytesTransferred, long totalBytes) {
        if (callback != null) {
            callback.onTransferProgress(filename, percent, bytesTransferred, totalBytes);
        }
    }

    @Override
    public void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error) {
        if (callback != null) {
            callback.onTransferComplete(filename, success, error);
        }
    }
}
