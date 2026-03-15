/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare.transport;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.p2pshare.ContentManifest;
import net.osmand.plus.plugins.p2pshare.ShareableContent;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.plugins.p2pshare.discovery.PeerDiscoveryManager;

import org.apache.commons.logging.Log;

/**
 * LAMPP Phase 6.5: Manages transport connections for P2P file transfer.
 *
 * Strategy:
 * 1. Primary: WiFi Direct (fast, ~250 Mbps)
 * 2. Fallback: Bluetooth Classic (slower, ~720 Kbps, more compatible)
 */
public class TransportManager implements TransportCallback {

    private static final Log LOG = PlatformUtil.getLog(TransportManager.class);

    private final OsmandApplication app;
    private final WifiDirectTransport wifiDirectTransport;
    private final BluetoothTransport bluetoothTransport;

    private TransportCallback callback;
    private DiscoveredPeer connectedPeer;
    private ContentManifest localManifest;
    private Activity currentActivity;

    // Track which transport is active
    private enum ActiveTransport { NONE, WIFI_DIRECT, BLUETOOTH }
    private ActiveTransport activeTransport = ActiveTransport.NONE;

    public TransportManager(@NonNull OsmandApplication app) {
        this.app = app;

        // Initialize both transports
        this.wifiDirectTransport = new WifiDirectTransport(app);
        wifiDirectTransport.setCallback(this);

        this.bluetoothTransport = new BluetoothTransport(app);
        bluetoothTransport.setCallback(this);

        LOG.info("TransportManager initialized with WiFi Direct and Bluetooth");
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
        bluetoothTransport.setLocalManifest(manifest);
    }

    /**
     * Set the discovery manager reference for rotating UUID access.
     * This allows BluetoothTransport to use the same rotating UUID as BLE discovery.
     */
    public void setDiscoveryManager(@Nullable PeerDiscoveryManager discoveryManager) {
        bluetoothTransport.setDiscoveryManager(discoveryManager);
    }

    public void setCallback(@Nullable TransportCallback callback) {
        this.callback = callback;
    }

    /**
     * Start listening for incoming connections (Bluetooth server).
     * Called when starting to scan for peers.
     */
    public void startListening() {
        if (bluetoothTransport.isAvailable()) {
            bluetoothTransport.startListening();
        }
    }

    /**
     * Stop listening for incoming connections.
     */
    public void stopListening() {
        bluetoothTransport.stopListening();
    }

    /**
     * Connect to a peer using the best available transport.
     * Tries WiFi Direct first, falls back to Bluetooth if unavailable.
     */
    public void connect(@NonNull DiscoveredPeer peer) {
        LOG.info("Initiating connection to: " + peer.getDeviceName());
        connectedPeer = peer;

        // Try WiFi Direct first (faster)
        if (wifiDirectTransport.isAvailable()) {
            LOG.info("Using WiFi Direct transport");
            activeTransport = ActiveTransport.WIFI_DIRECT;
            wifiDirectTransport.connect(peer, currentActivity);
        } else if (bluetoothTransport.isAvailable()) {
            // Fallback to Bluetooth
            LOG.info("WiFi Direct not available, falling back to Bluetooth");
            activeTransport = ActiveTransport.BLUETOOTH;
            bluetoothTransport.connect(peer);
        } else {
            activeTransport = ActiveTransport.NONE;
            if (callback != null) {
                callback.onConnectionFailed(peer, "No transport available (WiFi Direct and Bluetooth unavailable)");
            }
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

        switch (activeTransport) {
            case WIFI_DIRECT:
                wifiDirectTransport.disconnect();
                break;
            case BLUETOOTH:
                bluetoothTransport.disconnect();
                break;
        }

        activeTransport = ActiveTransport.NONE;
        connectedPeer = null;
    }

    /**
     * Send a FieldNote sync packet to the connected peer.
     * Phase 2: FieldNotes P2P sync.
     */
    public void sendFieldNote(@NonNull org.json.JSONObject noteJson) {
        if (!isConnected()) {
            LOG.warn("Cannot send FieldNote: not connected");
            return;
        }

        switch (activeTransport) {
            case WIFI_DIRECT:
                wifiDirectTransport.sendFieldNote(noteJson);
                break;
            case BLUETOOTH:
                bluetoothTransport.sendFieldNote(noteJson);
                break;
        }
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

        switch (activeTransport) {
            case WIFI_DIRECT:
                wifiDirectTransport.sendManifest(manifestJson);
                break;
            case BLUETOOTH:
                bluetoothTransport.sendManifest(manifestJson);
                break;
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

        switch (activeTransport) {
            case WIFI_DIRECT:
                wifiDirectTransport.requestFile(content);
                break;
            case BLUETOOTH:
                bluetoothTransport.requestFile(content);
                break;
        }
    }

    /**
     * Request a file with resume from offset.
     * Used when a .tmp file exists from a previous interrupted transfer.
     */
    public void requestFileResume(@NonNull ShareableContent content, long offset) {
        if (!isConnected()) {
            LOG.warn("Cannot resume file: not connected");
            return;
        }

        LOG.info("Requesting file resume: " + content.getFilename() + " from offset " + offset);

        switch (activeTransport) {
            case WIFI_DIRECT:
                wifiDirectTransport.requestFileResume(content, offset);
                break;
            case BLUETOOTH:
                bluetoothTransport.requestFileResume(content, offset);
                break;
        }
    }

    /**
     * Cancel an ongoing file transfer.
     */
    public void cancelTransfer() {
        LOG.info("Cancelling transfer");

        switch (activeTransport) {
            case WIFI_DIRECT:
                wifiDirectTransport.cancelTransfer();
                break;
            case BLUETOOTH:
                bluetoothTransport.cancelTransfer();
                break;
        }
    }

    /**
     * Check if currently connected to a peer.
     */
    public boolean isConnected() {
        return wifiDirectTransport.isConnected() || bluetoothTransport.isConnected();
    }

    /**
     * Get the currently connected peer.
     */
    @Nullable
    public DiscoveredPeer getConnectedPeer() {
        return connectedPeer;
    }

    /**
     * Get the name of the currently active transport (for logging/history).
     */
    @NonNull
    public String getActiveTransportName() {
        switch (activeTransport) {
            case WIFI_DIRECT:
                return "wifi_direct";
            case BLUETOOTH:
                return "bluetooth";
            default:
                return "none";
        }
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
        return bluetoothTransport.isAvailable();
    }

    /**
     * Get a description of available transports.
     */
    @NonNull
    public String getTransportStatus() {
        StringBuilder status = new StringBuilder();
        if (wifiDirectTransport.isAvailable()) {
            status.append("WiFi Direct: Available");
        } else {
            status.append("WiFi Direct: Unavailable");
        }
        status.append("\n");
        if (bluetoothTransport.isAvailable()) {
            status.append("Bluetooth: Available");
        } else {
            status.append("Bluetooth: Unavailable");
        }
        return status.toString();
    }

    /**
     * Check if P2P encryption is enabled.
     */
    public boolean isEncryptionEnabled() {
        try {
            return app.getSettings().LAMPP_P2P_ENCRYPTION_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Clean up resources.
     */
    public void shutdown() {
        if (currentActivity != null) {
            wifiDirectTransport.unregisterReceiver(currentActivity);
        }

        disconnect();
        stopListening();
        wifiDirectTransport.shutdown();
        bluetoothTransport.shutdown();
    }

    // ---- TransportCallback implementation (forward to our callback) ----

    @Override
    public void onConnected(@NonNull DiscoveredPeer peer) {
        connectedPeer = peer;
        LOG.info("P2P connected to " + peer.getDeviceName()
                + " via " + getActiveTransportName()
                + " (encryption=" + (isEncryptionEnabled() ? "ON" : "OFF") + ")");
        if (callback != null) {
            callback.onConnected(peer);
        }
    }

    @Override
    public void onDisconnected(@NonNull DiscoveredPeer peer, @Nullable String reason) {
        connectedPeer = null;
        activeTransport = ActiveTransport.NONE;
        if (callback != null) {
            callback.onDisconnected(peer, reason);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull DiscoveredPeer peer, @NonNull String error) {
        // If WiFi Direct failed, try Bluetooth as fallback
        if (activeTransport == ActiveTransport.WIFI_DIRECT && bluetoothTransport.isAvailable()) {
            LOG.info("WiFi Direct failed, trying Bluetooth fallback: " + error);
            activeTransport = ActiveTransport.BLUETOOTH;
            bluetoothTransport.connect(peer);
            return;
        }

        connectedPeer = null;
        activeTransport = ActiveTransport.NONE;
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

    @Override
    public void onFieldNoteReceived(@NonNull DiscoveredPeer peer, @NonNull String noteJson) {
        if (callback != null) {
            callback.onFieldNoteReceived(peer, noteJson);
        }
    }
}
