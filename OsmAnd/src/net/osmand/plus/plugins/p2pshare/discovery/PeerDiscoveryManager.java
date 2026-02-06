package net.osmand.plus.plugins.p2pshare.discovery;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.utils.AndroidUtils;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LAMPP Phase 6.3: BLE-based peer discovery for P2P sharing.
 *
 * Uses Bluetooth Low Energy beaconing to find nearby devices running Lampp.
 * This is just the discovery layer - actual file transfer happens via WiFi Direct.
 *
 * Strategy:
 * 1. Advertise this device with a unique service UUID so others can find us
 * 2. Scan for other devices advertising the same service UUID
 * 3. Track signal strength (RSSI) for distance estimation
 * 4. Remove stale peers that haven't been seen recently
 */
public class PeerDiscoveryManager {

    private static final Log LOG = PlatformUtil.getLog(PeerDiscoveryManager.class);

    // Service UUID for Lampp P2P discovery
    // Custom UUID based on "Lampp-P2P" in hex-like encoding
    public static final UUID SERVICE_UUID = UUID.fromString("4c616d70-7032-7053-6861-726500000001");

    // How long before a peer is considered stale (not seen recently)
    private static final long PEER_TIMEOUT_MS = 30000; // 30 seconds

    // How often to clean up stale peers
    private static final long CLEANUP_INTERVAL_MS = 10000; // 10 seconds

    private final OsmandApplication app;
    private final PeerDiscoveryCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothLeAdvertiser bleAdvertiser;

    private boolean isScanning = false;
    private boolean isAdvertising = false;

    // Track discovered peers with their last seen timestamp
    private final Map<String, DiscoveredPeer> discoveredPeers = new ConcurrentHashMap<>();
    private final Map<String, Long> peerLastSeen = new ConcurrentHashMap<>();

    // Runnable for periodic cleanup
    private final Runnable cleanupRunnable = this::cleanupStalePeers;

    public interface PeerDiscoveryCallback {
        void onPeerDiscovered(@NonNull DiscoveredPeer peer);
        void onPeerUpdated(@NonNull DiscoveredPeer peer);
        void onPeerLost(@NonNull DiscoveredPeer peer);
        void onDiscoveryError(@NonNull String error);
    }

    public PeerDiscoveryManager(@NonNull OsmandApplication app, @NonNull PeerDiscoveryCallback callback) {
        this.app = app;
        this.callback = callback;
        initBluetooth();
    }

    private void initBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();
                bleAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            }
        }

        if (bluetoothAdapter == null) {
            LOG.warn("Bluetooth not available on this device");
        } else if (!bluetoothAdapter.isEnabled()) {
            LOG.warn("Bluetooth is disabled");
        } else if (bleAdvertiser == null) {
            LOG.warn("BLE advertising not supported on this device");
        }
    }

    /**
     * Check if BLE is available and enabled.
     */
    public boolean isBleAvailable() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bleScanner != null;
    }

    /**
     * Check if BLE advertising is supported.
     */
    public boolean isAdvertisingSupported() {
        return bleAdvertiser != null;
    }

    /**
     * Start scanning for nearby Lampp devices.
     */
    @SuppressLint("MissingPermission")
    public void startScanning(@Nullable Activity activity) {
        if (isScanning) {
            LOG.debug("Already scanning");
            return;
        }

        if (!isBleAvailable()) {
            callback.onDiscoveryError("Bluetooth is not available or not enabled");
            return;
        }

        // Check and request permissions
        if (activity != null && !AndroidUtils.hasBLEPermission(activity)) {
            AndroidUtils.requestBLEPermissions(activity);
            callback.onDiscoveryError("Bluetooth permissions required");
            return;
        }

        if (!AndroidUtils.hasBLEPermission(app)) {
            callback.onDiscoveryError("Bluetooth permissions not granted");
            return;
        }

        LOG.info("Starting BLE peer discovery scan");

        try {
            // Configure scan filters for our service UUID
            List<ScanFilter> filters = Collections.singletonList(
                    new ScanFilter.Builder()
                            .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                            .build()
            );

            // Configure scan settings
            // Use LOW_LATENCY for faster discovery when actively looking
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    .setReportDelay(0L)
                    .build();

            bleScanner.startScan(filters, settings, scanCallback);
            isScanning = true;

            // Start periodic cleanup of stale peers
            mainHandler.postDelayed(cleanupRunnable, CLEANUP_INTERVAL_MS);

            LOG.info("BLE scan started successfully");
        } catch (Exception e) {
            LOG.error("Failed to start BLE scan", e);
            callback.onDiscoveryError("Failed to start scan: " + e.getMessage());
        }
    }

    /**
     * Stop scanning for peers.
     */
    @SuppressLint("MissingPermission")
    public void stopScanning() {
        if (!isScanning) {
            return;
        }

        LOG.info("Stopping BLE peer discovery");
        isScanning = false;

        // Stop cleanup runnable
        mainHandler.removeCallbacks(cleanupRunnable);

        try {
            if (bleScanner != null && AndroidUtils.hasBLEPermission(app)) {
                bleScanner.stopScan(scanCallback);
            }
        } catch (Exception e) {
            LOG.error("Error stopping BLE scan", e);
        }
    }

    /**
     * Start advertising this device as available for P2P sharing.
     */
    @SuppressLint("MissingPermission")
    public void startAdvertising(@Nullable Activity activity) {
        if (isAdvertising) {
            LOG.debug("Already advertising");
            return;
        }

        if (!isAdvertisingSupported()) {
            LOG.warn("BLE advertising not supported");
            return;
        }

        // Check permissions
        if (activity != null && !AndroidUtils.hasBLEPermission(activity)) {
            AndroidUtils.requestBLEPermissions(activity);
            return;
        }

        if (!AndroidUtils.hasBLEPermission(app)) {
            LOG.warn("Cannot advertise: Bluetooth permissions not granted");
            return;
        }

        LOG.info("Starting BLE advertising for P2P discovery");

        try {
            // Configure advertising settings
            // Use LOW_LATENCY for faster discovery, but with timeout to save battery
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(false) // We don't need GATT connections, just discovery
                    .setTimeout(0) // Advertise indefinitely until stopped
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .build();

            // Build advertise data with our service UUID
            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                    .build();

            // Optional: scan response data with additional info
            AdvertiseData scanResponse = new AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(true)
                    .build();

            bleAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback);
            isAdvertising = true;

            LOG.info("BLE advertising started successfully");
        } catch (Exception e) {
            LOG.error("Failed to start BLE advertising", e);
        }
    }

    /**
     * Stop advertising.
     */
    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        if (!isAdvertising) {
            return;
        }

        LOG.info("Stopping BLE advertising");
        isAdvertising = false;

        try {
            if (bleAdvertiser != null && AndroidUtils.hasBLEPermission(app)) {
                bleAdvertiser.stopAdvertising(advertiseCallback);
            }
        } catch (Exception e) {
            LOG.error("Error stopping BLE advertising", e);
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public boolean isAdvertising() {
        return isAdvertising;
    }

    /**
     * Get list of currently discovered peers.
     */
    @NonNull
    public List<DiscoveredPeer> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers.values());
    }

    /**
     * Clean up resources.
     */
    public void shutdown() {
        stopScanning();
        stopAdvertising();
        discoveredPeers.clear();
        peerLastSeen.clear();
        mainHandler.removeCallbacks(cleanupRunnable);
    }

    // ---- Private methods ----

    /**
     * Process a scan result and create/update a DiscoveredPeer.
     */
    @SuppressLint("MissingPermission")
    private void processScanResult(@NonNull ScanResult result) {
        if (!AndroidUtils.hasBLEPermission(app)) {
            return;
        }

        String address = result.getDevice().getAddress();
        String deviceName = result.getDevice().getName();
        int rssi = result.getRssi();

        // Use address if name is null
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = "Lampp Device";
        }

        long now = System.currentTimeMillis();
        peerLastSeen.put(address, now);

        DiscoveredPeer existingPeer = discoveredPeers.get(address);
        if (existingPeer != null) {
            // Update existing peer
            existingPeer.setSignalStrength(rssi);
            callback.onPeerUpdated(existingPeer);
        } else {
            // New peer discovered
            DiscoveredPeer newPeer = new DiscoveredPeer(address, deviceName);
            newPeer.setMacAddress(address);
            newPeer.setSignalStrength(rssi);

            discoveredPeers.put(address, newPeer);
            callback.onPeerDiscovered(newPeer);

            LOG.info("Discovered new peer: " + deviceName + " (" + address + ") RSSI: " + rssi);
        }
    }

    /**
     * Remove peers that haven't been seen recently.
     */
    private void cleanupStalePeers() {
        if (!isScanning) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = peerLastSeen.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > PEER_TIMEOUT_MS) {
                String address = entry.getKey();
                DiscoveredPeer peer = discoveredPeers.remove(address);
                iterator.remove();

                if (peer != null) {
                    LOG.info("Peer lost (timeout): " + peer.getDeviceName());
                    callback.onPeerLost(peer);
                }
            }
        }

        // Schedule next cleanup
        if (isScanning) {
            mainHandler.postDelayed(cleanupRunnable, CLEANUP_INTERVAL_MS);
        }
    }

    // ---- Callbacks ----

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            processScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                processScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            isScanning = false;

            String errorMsg;
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    errorMsg = "Scan already started";
                    // Try restarting
                    stopScanning();
                    startScanning(null);
                    return;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorMsg = "App registration failed";
                    break;
                case SCAN_FAILED_INTERNAL_ERROR:
                    errorMsg = "Internal error";
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorMsg = "Feature unsupported";
                    break;
                default:
                    errorMsg = "Unknown error: " + errorCode;
            }

            LOG.error("BLE scan failed: " + errorMsg);
            callback.onDiscoveryError("Scan failed: " + errorMsg);
        }
    };

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            LOG.info("BLE advertising started successfully");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            isAdvertising = false;

            String errorMsg;
            switch (errorCode) {
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    errorMsg = "Already advertising";
                    break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    errorMsg = "Data too large";
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    errorMsg = "Feature unsupported";
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    errorMsg = "Internal error";
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    errorMsg = "Too many advertisers";
                    break;
                default:
                    errorMsg = "Unknown error: " + errorCode;
            }

            LOG.error("BLE advertising failed: " + errorMsg);
        }
    };
}
