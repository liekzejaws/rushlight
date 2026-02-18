package net.osmand.plus.plugins.p2pshare;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.plugins.p2pshare.discovery.PeerDiscoveryManager;
import net.osmand.plus.plugins.p2pshare.history.TransferHistoryDatabase;
import net.osmand.plus.plugins.p2pshare.history.TransferRecord;
import net.osmand.plus.plugins.p2pshare.transport.TransportCallback;
import net.osmand.plus.plugins.p2pshare.transport.TransportManager;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LAMPP Phase 6.4: Core manager for P2P content sharing.
 * Coordinates discovery, transport, and content manifest operations.
 */
public class P2pShareManager implements PeerDiscoveryManager.PeerDiscoveryCallback, TransportCallback {

    private static final Log LOG = PlatformUtil.getLog(P2pShareManager.class);

    private final OsmandApplication app;
    private final ContentManifest localManifest;
    private final List<P2pShareListener> listeners = new CopyOnWriteArrayList<>();

    // Managers for different aspects of P2P
    private PeerDiscoveryManager discoveryManager;
    private TransportManager transportManager;

    // Currently discovered peers
    private final List<DiscoveredPeer> discoveredPeers = new ArrayList<>();

    // State
    private boolean isScanning = false;
    private boolean isTransferring = false;

    // Transfer history
    private TransferHistoryDatabase historyDb;
    @Nullable
    private TransferRecord activeTransferRecord;

    // Transfer queue for multi-file downloads
    private final TransferQueue transferQueue = new TransferQueue();

    // Activity reference for permission requests
    private Activity currentActivity;

    public interface P2pShareListener {
        default void onPeerDiscovered(@NonNull DiscoveredPeer peer) {}
        default void onPeerLost(@NonNull DiscoveredPeer peer) {}
        default void onPeerConnected(@NonNull DiscoveredPeer peer) {}
        default void onManifestReceived(@NonNull DiscoveredPeer peer) {}
        default void onScanningStateChanged(boolean isScanning) {}
        default void onTransferProgress(@NonNull String filename, int progress, long bytesTransferred, long totalBytes) {}
        default void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error) {}
    }

    public P2pShareManager(@NonNull OsmandApplication app) {
        this.app = app;
        this.localManifest = new ContentManifest(app);

        // Initialize the discovery manager
        discoveryManager = new PeerDiscoveryManager(app, this);

        // Initialize the transport manager
        transportManager = new TransportManager(app);
        transportManager.setCallback(this);
        transportManager.setLocalManifest(localManifest);

        // Wire up discovery manager to transport for rotating UUID access
        transportManager.setDiscoveryManager(discoveryManager);

        // Initialize transfer history database
        historyDb = TransferHistoryDatabase.getInstance(app);

        // Setup queue callback to auto-process next job
        transferQueue.addCallback(new TransferQueue.QueueCallback() {
            @Override
            public void onQueueUpdated(@NonNull TransferQueue queue) {
                // UI can query queue state
            }

            @Override
            public void onJobStarted(@NonNull TransferJob job, int position, int total) {
                LOG.info("Queue: starting job " + position + " of " + total + ": " + job.getFilename());
            }

            @Override
            public void onQueueCompleted(int successful, int failed, int cancelled) {
                LOG.info("Queue completed: " + successful + " ok, " + failed + " failed, " + cancelled + " cancelled");
                isTransferring = false;
            }
        });

        LOG.info("P2pShareManager initialized");
    }

    /**
     * Set the current activity for permission requests.
     */
    public void setCurrentActivity(@Nullable Activity activity) {
        this.currentActivity = activity;
        if (transportManager != null) {
            transportManager.setCurrentActivity(activity);
        }
    }

    /**
     * Start scanning for nearby peers.
     * Uses BLE beaconing for discovery.
     * Also starts advertising so others can find us.
     */
    public void startScanning() {
        if (isScanning) {
            LOG.debug("Already scanning");
            return;
        }

        LOG.info("Starting peer discovery scan");

        // Start both scanning and advertising
        if (discoveryManager != null) {
            discoveryManager.startScanning(currentActivity);
            discoveryManager.startAdvertising(currentActivity);
        }

        isScanning = discoveryManager != null && discoveryManager.isScanning();
        notifyScanningStateChanged(isScanning);
    }

    /**
     * Stop scanning for peers.
     */
    public void stopScanning() {
        if (!isScanning) {
            return;
        }

        LOG.info("Stopping peer discovery scan");

        if (discoveryManager != null) {
            discoveryManager.stopScanning();
            discoveryManager.stopAdvertising();
        }

        isScanning = false;
        notifyScanningStateChanged(false);
    }

    /**
     * Connect to a discovered peer to exchange content manifests.
     */
    public void connectToPeer(@NonNull DiscoveredPeer peer) {
        LOG.info("Connecting to peer: " + peer.getDeviceName());
        peer.setState(DiscoveredPeer.PeerState.CONNECTING);

        if (transportManager != null) {
            transportManager.connect(peer);
        } else {
            app.showShortToastMessage("Transport not available");
        }
    }

    /**
     * Disconnect from current peer.
     */
    public void disconnectFromPeer() {
        if (transportManager != null) {
            transportManager.disconnect();
        }
    }

    /**
     * Request a file from a connected peer.
     */
    public void requestFile(@NonNull ShareableContent content) {
        LOG.info("Requesting file: " + content.getFilename());

        if (transportManager != null && transportManager.isConnected()) {
            isTransferring = true;

            // Create transfer history record
            activeTransferRecord = new TransferRecord(
                    content.getFilename(), content.getFileSize(),
                    TransferRecord.DIRECTION_RECEIVED);
            activeTransferRecord.setContentType(content.getContentType().name());

            DiscoveredPeer connectedPeer = transportManager.getConnectedPeer();
            if (connectedPeer != null) {
                activeTransferRecord.setPeerName(connectedPeer.getDeviceName());
                activeTransferRecord.setPeerId(connectedPeer.getDeviceId());
            }

            String transport = transportManager.getActiveTransportName();
            activeTransferRecord.setTransport(transport);

            historyDb.insertRecord(activeTransferRecord);

            transportManager.requestFile(content);
        } else {
            app.showShortToastMessage("Not connected to peer");
        }
    }

    /**
     * Request a file with resume support.
     * Checks if a .tmp file exists from a previous interrupted transfer.
     * If found, requests resume from offset; otherwise does full download.
     */
    public void requestFileWithResume(@NonNull ShareableContent content) {
        if (transportManager == null || !transportManager.isConnected()) {
            app.showShortToastMessage("Not connected to peer");
            return;
        }

        // Check for existing .tmp file
        File tempFile = getTempFileForContent(content);
        if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
            long offset = tempFile.length();
            LOG.info("Found .tmp file for " + content.getFilename()
                    + " with " + offset + " bytes, requesting resume");

            isTransferring = true;

            // Create history record
            activeTransferRecord = new TransferRecord(
                    content.getFilename(), content.getFileSize(),
                    TransferRecord.DIRECTION_RECEIVED);
            activeTransferRecord.setContentType(content.getContentType().name());
            activeTransferRecord.setBytesTransferred(offset);

            DiscoveredPeer connectedPeer = transportManager.getConnectedPeer();
            if (connectedPeer != null) {
                activeTransferRecord.setPeerName(connectedPeer.getDeviceName());
                activeTransferRecord.setPeerId(connectedPeer.getDeviceId());
            }
            activeTransferRecord.setTransport(transportManager.getActiveTransportName());
            historyDb.insertRecord(activeTransferRecord);

            transportManager.requestFileResume(content, offset);
        } else {
            // No .tmp file — do regular download
            requestFile(content);
        }
    }

    /**
     * Get the temp file path for a content item (mirrors transport getDestinationFile logic).
     */
    @Nullable
    private File getTempFileForContent(@NonNull ShareableContent content) {
        File destDir;
        switch (content.getContentType()) {
            case MAP:
                destDir = app.getAppPath("");
                break;
            case ZIM:
                destDir = app.getAppPath("wikipedia");
                break;
            case MODEL:
                destDir = app.getAppPath("llm_models");
                break;
            case APK:
            default:
                destDir = app.getExternalFilesDir(null);
                break;
        }
        if (destDir == null) return null;
        return new File(destDir, content.getFilename() + ".tmp");
    }

    /**
     * Enqueue multiple files for sequential download.
     * Starts processing immediately if not already transferring.
     */
    public void enqueueFiles(@NonNull java.util.List<ShareableContent> files) {
        transferQueue.enqueueAll(files);
        if (!isTransferring) {
            processNextInQueue();
        }
    }

    /**
     * Process the next job in the transfer queue.
     */
    private void processNextInQueue() {
        TransferJob job = transferQueue.getNextJob();
        if (job == null) {
            return;
        }

        requestFileWithResume(job.getContent());
    }

    /**
     * Get the transfer queue.
     */
    @NonNull
    public TransferQueue getTransferQueue() {
        return transferQueue;
    }

    /**
     * Cancel ongoing file transfer.
     */
    public void cancelTransfer() {
        if (transportManager != null) {
            transportManager.cancelTransfer();
        }
        isTransferring = false;

        // Update transfer queue
        transferQueue.cancelCurrent();

        // Update history record
        if (activeTransferRecord != null) {
            activeTransferRecord.markCancelled();
            historyDb.updateRecord(activeTransferRecord);
            activeTransferRecord = null;
        }

        // Auto-advance to next in queue
        processNextInQueue();
    }

    /**
     * Cancel all remaining files in the queue.
     */
    public void cancelAllTransfers() {
        if (transportManager != null) {
            transportManager.cancelTransfer();
        }
        isTransferring = false;
        transferQueue.cancelAll();

        if (activeTransferRecord != null) {
            activeTransferRecord.markCancelled();
            historyDb.updateRecord(activeTransferRecord);
            activeTransferRecord = null;
        }
    }

    /**
     * Check if WiFi Direct is available.
     */
    public boolean isWifiDirectAvailable() {
        return transportManager != null && transportManager.isWifiDirectAvailable();
    }

    /**
     * Check if connected to a peer.
     */
    public boolean isConnected() {
        return transportManager != null && transportManager.isConnected();
    }

    /**
     * Get currently connected peer.
     */
    @Nullable
    public DiscoveredPeer getConnectedPeer() {
        return transportManager != null ? transportManager.getConnectedPeer() : null;
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
        // Return peers from discovery manager if available
        if (discoveryManager != null) {
            return discoveryManager.getDiscoveredPeers();
        }
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
     * Check if BLE is available.
     */
    public boolean isBleAvailable() {
        return discoveryManager != null && discoveryManager.isBleAvailable();
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
     * Get the transfer history database.
     */
    @NonNull
    public TransferHistoryDatabase getHistoryDatabase() {
        return historyDb;
    }

    /**
     * Shutdown the manager and release resources.
     */
    public void shutdown() {
        LOG.info("Shutting down P2pShareManager");
        stopScanning();

        if (discoveryManager != null) {
            discoveryManager.shutdown();
            discoveryManager = null;
        }

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

    // PeerDiscoveryManager.PeerDiscoveryCallback implementation

    @Override
    public void onPeerDiscovered(@NonNull DiscoveredPeer peer) {
        LOG.info("Peer discovered: " + peer.getDeviceName());
        if (!discoveredPeers.contains(peer)) {
            discoveredPeers.add(peer);
        }
        notifyPeerDiscovered(peer);
    }

    @Override
    public void onPeerUpdated(@NonNull DiscoveredPeer peer) {
        // Signal strength update - UI can refresh if needed
        LOG.debug("Peer updated: " + peer.getDeviceName() + " RSSI: " + peer.getSignalStrength());
    }

    @Override
    public void onPeerLost(@NonNull DiscoveredPeer peer) {
        LOG.info("Peer lost: " + peer.getDeviceName());
        discoveredPeers.remove(peer);
        notifyPeerLost(peer);
    }

    @Override
    public void onDiscoveryError(@NonNull String error) {
        LOG.error("Discovery error: " + error);
        app.showShortToastMessage(error);
    }

    // TransportCallback implementation

    @Override
    public void onConnected(@NonNull DiscoveredPeer peer) {
        LOG.info("Connected to peer: " + peer.getDeviceName());
        peer.setState(DiscoveredPeer.PeerState.CONNECTED);
        app.showShortToastMessage("Connected to " + peer.getDeviceName());
        for (P2pShareListener listener : listeners) {
            listener.onPeerConnected(peer);
        }
    }

    @Override
    public void onDisconnected(@NonNull DiscoveredPeer peer, @Nullable String reason) {
        LOG.info("Disconnected from peer: " + peer.getDeviceName() + " reason: " + reason);
        app.showShortToastMessage("Disconnected from " + peer.getDeviceName());
    }

    @Override
    public void onConnectionFailed(@NonNull DiscoveredPeer peer, @NonNull String error) {
        LOG.error("Connection failed to " + peer.getDeviceName() + ": " + error);
        peer.setState(DiscoveredPeer.PeerState.DISCOVERED);
        app.showShortToastMessage("Connection failed: " + error);
    }

    @Override
    public void onManifestReceived(@NonNull DiscoveredPeer peer, @NonNull String manifestJson) {
        LOG.info("Manifest received from peer: " + peer.getDeviceName());
        // Parse manifest and store in peer
        ContentManifest remoteManifest = ContentManifest.fromJson(app, manifestJson);
        peer.setRemoteManifest(remoteManifest);
        peer.setManifestSummary(remoteManifest.getSummary());
        app.showShortToastMessage("Received: " + remoteManifest.getSummary());
        for (P2pShareListener listener : listeners) {
            listener.onManifestReceived(peer);
        }
    }

    @Override
    public void onTransferProgress(@NonNull String filename, int progress, long bytesTransferred, long totalBytes) {
        // Update history record with progress (throttled to every 10%)
        if (activeTransferRecord != null && progress % 10 == 0) {
            activeTransferRecord.setBytesTransferred(bytesTransferred);
            historyDb.updateRecord(activeTransferRecord);
        }

        // Update queue
        transferQueue.onJobProgress(filename, progress, bytesTransferred);

        for (P2pShareListener listener : listeners) {
            listener.onTransferProgress(filename, progress, bytesTransferred, totalBytes);
        }
    }

    @Override
    public void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error) {
        isTransferring = false;

        // Finalize history record
        if (activeTransferRecord != null) {
            activeTransferRecord.markComplete(success);
            if (!success && error != null) {
                activeTransferRecord.setError(error);
                // Detect checksum failure
                if (error.contains("checksum") || error.contains("Checksum")) {
                    activeTransferRecord.setChecksumOk(TransferRecord.CHECKSUM_FAILED);
                }
            } else if (success) {
                activeTransferRecord.setBytesTransferred(activeTransferRecord.getFileSize());
                activeTransferRecord.setChecksumOk(TransferRecord.CHECKSUM_PASSED);
            }
            historyDb.updateRecord(activeTransferRecord);
            activeTransferRecord = null;
        }

        // Update queue and auto-advance to next job
        transferQueue.onJobCompleted(filename, success, error);
        if (!transferQueue.isEmpty()) {
            processNextInQueue();
        }

        for (P2pShareListener listener : listeners) {
            listener.onTransferComplete(filename, success, error);
        }
    }
}
