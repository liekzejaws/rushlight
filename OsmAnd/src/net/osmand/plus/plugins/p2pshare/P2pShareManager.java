package net.osmand.plus.plugins.p2pshare;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.plugins.p2pshare.discovery.PeerDiscoveryManager;
import net.osmand.plus.plugins.p2pshare.transport.TransportManager;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core manager for P2P content sharing.
 * Coordinates discovery, transport, and content manifest operations.
 */
public class P2pShareManager {

    private static final Log LOG = PlatformUtil.getLog(P2pShareManager.class);

    private final OsmandApplication app;
    private final ContentManifest localManifest;
    private final List<P2pShareListener> listeners = new CopyOnWriteArrayList<>();

    // Managers for different aspects of P2P (will be initialized in later phases)
    private PeerDiscoveryManager discoveryManager;
    private TransportManager transportManager;

    // Currently discovered peers
    private final List<DiscoveredPeer> discoveredPeers = new ArrayList<>();

    // State
    private boolean isScanning = false;
    private boolean isTransferring = false;

    public interface P2pShareListener {
        default void onPeerDiscovered(@NonNull DiscoveredPeer peer) {}
        default void onPeerLost(@NonNull DiscoveredPeer peer) {}
        default void onScanningStateChanged(boolean isScanning) {}
        default void onTransferProgress(@NonNull String filename, int progress, long bytesTransferred, long totalBytes) {}
        default void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error) {}
    }

    public P2pShareManager(@NonNull OsmandApplication app) {
        this.app = app;
        this.localManifest = new ContentManifest(app);

        LOG.info("P2pShareManager initialized");
    }

    /**
     * Start scanning for nearby peers.
     * Uses BLE beaconing for discovery.
     */
    public void startScanning() {
        if (isScanning) {
            LOG.debug("Already scanning");
            return;
        }

        LOG.info("Starting peer discovery scan");
        isScanning = true;

        // TODO Phase 6.3: Initialize and start BLE discovery
        // discoveryManager = new PeerDiscoveryManager(app, this::onPeerFound);
        // discoveryManager.startScanning();

        notifyScanningStateChanged(true);
    }

    /**
     * Stop scanning for peers.
     */
    public void stopScanning() {
        if (!isScanning) {
            return;
        }

        LOG.info("Stopping peer discovery scan");
        isScanning = false;

        // TODO Phase 6.3: Stop BLE discovery
        // if (discoveryManager != null) {
        //     discoveryManager.stopScanning();
        // }

        notifyScanningStateChanged(false);
    }

    /**
     * Connect to a discovered peer to exchange content manifests.
     */
    public void connectToPeer(@NonNull DiscoveredPeer peer) {
        LOG.info("Connecting to peer: " + peer.getDeviceName());

        // TODO Phase 6.4: Implement WiFi Direct connection
        // transportManager.connect(peer);
    }

    /**
     * Request a file from a connected peer.
     */
    public void requestFile(@NonNull DiscoveredPeer peer, @NonNull ShareableContent content) {
        LOG.info("Requesting file: " + content.getFilename() + " from " + peer.getDeviceName());

        // TODO Phase 6.4: Implement file transfer protocol
        // transportManager.requestFile(peer, content, this::onTransferProgress);
    }

    /**
     * Get the local content manifest (what this device can share).
     */
    @NonNull
    public ContentManifest getLocalManifest() {
        return localManifest;
    }

    /**
     * Refresh the local content manifest by scanning directories.
     */
    public void refreshLocalManifest() {
        localManifest.scanContent();
    }

    /**
     * Get list of currently discovered peers.
     */
    @NonNull
    public List<DiscoveredPeer> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers);
    }

    /**
     * Check if currently scanning for peers.
     */
    public boolean isScanning() {
        return isScanning;
    }

    /**
     * Check if a transfer is in progress.
     */
    public boolean isTransferring() {
        return isTransferring;
    }

    /**
     * Get the app's own APK path for self-spreading feature.
     */
    @Nullable
    public String getOwnApkPath() {
        try {
            return app.getPackageManager()
                    .getApplicationInfo(app.getPackageName(), 0).sourceDir;
        } catch (Exception e) {
            LOG.error("Failed to get own APK path", e);
            return null;
        }
    }

    /**
     * Shutdown the manager and release resources.
     */
    public void shutdown() {
        LOG.info("Shutting down P2pShareManager");
        stopScanning();

        if (transportManager != null) {
            transportManager.shutdown();
            transportManager = null;
        }

        discoveredPeers.clear();
        listeners.clear();
    }

    // Listener management

    public void addListener(@NonNull P2pShareListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull P2pShareListener listener) {
        listeners.remove(listener);
    }

    // Internal notification methods

    private void notifyScanningStateChanged(boolean scanning) {
        for (P2pShareListener listener : listeners) {
            listener.onScanningStateChanged(scanning);
        }
    }

    private void notifyPeerDiscovered(@NonNull DiscoveredPeer peer) {
        for (P2pShareListener listener : listeners) {
            listener.onPeerDiscovered(peer);
        }
    }

    private void notifyPeerLost(@NonNull DiscoveredPeer peer) {
        for (P2pShareListener listener : listeners) {
            listener.onPeerLost(peer);
        }
    }

    // Callbacks from discovery/transport (will be used in later phases)

    void onPeerFound(@NonNull DiscoveredPeer peer) {
        if (!discoveredPeers.contains(peer)) {
            discoveredPeers.add(peer);
            notifyPeerDiscovered(peer);
        }
    }

    void onPeerLost(@NonNull DiscoveredPeer peer) {
        if (discoveredPeers.remove(peer)) {
            notifyPeerLost(peer);
        }
    }

    void onTransferProgress(@NonNull String filename, int progress, long bytesTransferred, long totalBytes) {
        for (P2pShareListener listener : listeners) {
            listener.onTransferProgress(filename, progress, bytesTransferred, totalBytes);
        }
    }

    void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error) {
        isTransferring = false;
        for (P2pShareListener listener : listeners) {
            listener.onTransferComplete(filename, success, error);
        }
    }
}
