/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

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
import net.osmand.plus.plugins.p2pshare.P2pLogSanitizer;
import net.osmand.plus.utils.AndroidUtils;

import org.apache.commons.logging.Log;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * LAMPP Phase 6.3: BLE-based peer discovery for P2P sharing.
 *
 * Uses Bluetooth Low Energy beaconing to find nearby devices running Rushlight.
 * This is just the discovery layer - actual file transfer happens via WiFi Direct.
 *
 * Security hardening (v0.2):
 * - Device name NEVER included in BLE advertisements (prevents identity leakage)
 * - Service UUID rotates daily via HKDF derivation (prevents tracking)
 * - Peers identified by ephemeral session IDs (random per scan session)
 * - MAC addresses redacted in all log output
 * - Connection rate limiting prevents Sybil/relay attacks
 *
 * Strategy:
 * 1. Advertise this device with a rotating service UUID so others can find us
 * 2. Scan for other devices advertising the same daily UUID
 * 3. Track signal strength (RSSI) for distance estimation
 * 4. Remove stale peers that haven't been seen recently
 */
public class PeerDiscoveryManager {

    private static final Log LOG = PlatformUtil.getLog(PeerDiscoveryManager.class);

    // Base secret for UUID rotation — combined with daily date to derive service UUID.
    // All Rushlight instances share this secret so they can discover each other on the same day.
    private static final byte[] UUID_ROTATION_SECRET = "rushlight-ble-discovery-2026".getBytes();

    // How long before a peer is considered stale (not seen recently)
    private static final long PEER_TIMEOUT_MS = 30000; // 30 seconds

    // How often to clean up stale peers
    private static final long CLEANUP_INTERVAL_MS = 10000; // 10 seconds

    // Rate limiting: max unique peers accepted per window
    private static final int MAX_PEERS_PER_WINDOW = 10;
    private static final long RATE_LIMIT_WINDOW_MS = 60000; // 60 seconds

    // Rate limiting: min interval between connections from same peer address
    private static final long MIN_RECONNECT_INTERVAL_MS = 5000; // 5 seconds

    private final OsmandApplication app;
    private final PeerDiscoveryCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SecureRandom secureRandom = new SecureRandom();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothLeAdvertiser bleAdvertiser;

    private boolean isScanning = false;
    private boolean isAdvertising = false;

    // Ephemeral session ID — regenerated each time scanning starts
    private String sessionEphemeralId;

    // Track discovered peers with their last seen timestamp
    private final Map<String, DiscoveredPeer> discoveredPeers = new ConcurrentHashMap<>();
    private final Map<String, Long> peerLastSeen = new ConcurrentHashMap<>();

    // Rate limiting: track peer connection timestamps (bounded size)
    private final Map<String, Long> peerConnectionTimestamps = Collections.synchronizedMap(
            new LinkedHashMap<String, Long>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > 64; // Keep at most 64 entries
                }
            }
    );
    // Track unique peer count within current rate limit window
    private long rateLimitWindowStart = 0;
    private int uniquePeersInWindow = 0;

    // Cached daily UUID — regenerated when the date changes
    private UUID cachedDailyUuid;
    private String cachedDateKey;

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
     * Get the daily-rotating service UUID.
     *
     * All Rushlight instances derive the same UUID for a given calendar day,
     * so they can discover each other. The UUID changes at midnight UTC,
     * making it impossible to track a device across days by its service UUID.
     *
     * Derivation: HMAC-SHA256(secret, "YYYY-MM-dd") → first 16 bytes → UUID
     */
    @NonNull
    public UUID getRotatingServiceUuid() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        if (cachedDailyUuid != null && today.equals(cachedDateKey)) {
            return cachedDailyUuid;
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(UUID_ROTATION_SECRET, "HmacSHA256"));
            byte[] hash = hmac.doFinal(today.getBytes());

            // Use first 16 bytes of HMAC output as UUID
            // Set version 4 (random) and variant 2 (RFC 4122) bits for valid UUID format
            hash[6] = (byte) ((hash[6] & 0x0F) | 0x40); // Version 4
            hash[8] = (byte) ((hash[8] & 0x3F) | 0x80); // Variant 2

            ByteBuffer bb = ByteBuffer.wrap(hash, 0, 16);
            cachedDailyUuid = new UUID(bb.getLong(), bb.getLong());
            cachedDateKey = today;

            LOG.info("Rotating service UUID derived for " + today
                    + ": ..." + cachedDailyUuid.toString().substring(cachedDailyUuid.toString().length() - 8));

            return cachedDailyUuid;
        } catch (Exception e) {
            LOG.error("Failed to derive rotating UUID, using fallback", e);
            // Fallback: deterministic but less secure UUID
            return UUID.nameUUIDFromBytes(("rushlight-" + today).getBytes());
        }
    }

    /**
     * Generate a new ephemeral session ID.
     * Called once per scanning session to avoid tracking across sessions.
     */
    private void regenerateSessionId() {
        byte[] bytes = new byte[8];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        sessionEphemeralId = sb.toString();
        LOG.info("New ephemeral session ID: " + P2pLogSanitizer.peerAlias(sessionEphemeralId));
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
     * Start scanning for nearby Rushlight devices.
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

        // Generate a fresh ephemeral ID for this scanning session
        regenerateSessionId();

        // Reset rate limiter for new session
        rateLimitWindowStart = System.currentTimeMillis();
        uniquePeersInWindow = 0;

        LOG.info("Starting BLE peer discovery scan");

        try {
            UUID serviceUuid = getRotatingServiceUuid();

            // Configure scan filters for our rotating service UUID
            List<ScanFilter> filters = Collections.singletonList(
                    new ScanFilter.Builder()
                            .setServiceUuid(new ParcelUuid(serviceUuid))
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
     *
     * SECURITY: Device name is NEVER included in advertisements.
     * Only the rotating service UUID is broadcast, which is shared
     * by all Rushlight instances on the same day.
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
            UUID serviceUuid = getRotatingServiceUuid();

            // Configure advertising settings
            // Use LOW_LATENCY for faster discovery, but with timeout to save battery
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(false) // We don't need GATT connections, just discovery
                    .setTimeout(0) // Advertise indefinitely until stopped
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .build();

            // Build advertise data with rotating service UUID ONLY.
            // SECURITY: setIncludeDeviceName(false) — never broadcast device identity.
            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addServiceUuid(new ParcelUuid(serviceUuid))
                    .build();

            // Optional: scan response data with TX power only (no identifying info)
            AdvertiseData scanResponse = new AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(true)
                    .build();

            bleAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback);
            isAdvertising = true;

            LOG.info("BLE advertising started successfully (device name NOT included)");
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
        peerConnectionTimestamps.clear();
        mainHandler.removeCallbacks(cleanupRunnable);
    }

    // ---- Private methods ----

    /**
     * Check rate limits for a new peer connection.
     *
     * @param peerAddress The BLE address of the peer
     * @return true if the connection is allowed, false if rate-limited
     */
    private boolean checkRateLimit(@NonNull String peerAddress) {
        long now = System.currentTimeMillis();

        // Check per-peer reconnection rate
        Long lastSeen = peerConnectionTimestamps.get(peerAddress);
        if (lastSeen != null && (now - lastSeen) < MIN_RECONNECT_INTERVAL_MS) {
            LOG.debug("Rate limited: peer " + P2pLogSanitizer.redactMac(peerAddress)
                    + " reconnecting too fast");
            return false;
        }

        // Check window-based rate limit for new unique peers
        if (now - rateLimitWindowStart > RATE_LIMIT_WINDOW_MS) {
            // Reset window
            rateLimitWindowStart = now;
            uniquePeersInWindow = 0;
        }

        // If this is a known peer (already in discovered list), always allow updates
        if (discoveredPeers.containsKey(peerAddress)) {
            peerConnectionTimestamps.put(peerAddress, now);
            return true;
        }

        // New peer — check if we've hit the limit
        if (uniquePeersInWindow >= MAX_PEERS_PER_WINDOW) {
            LOG.warn("Rate limited: too many unique peers (" + uniquePeersInWindow
                    + ") in window, rejecting new peer");
            return false;
        }

        uniquePeersInWindow++;
        peerConnectionTimestamps.put(peerAddress, now);
        return true;
    }

    /**
     * Generate an anonymous display name for a discovered peer.
     * Uses a hash of the BLE address to create a consistent but non-identifying alias.
     */
    @NonNull
    private String generatePeerAlias(@NonNull String address) {
        // Generate a short, consistent alias from the address
        // This way the same peer always shows the same alias within a session,
        // but doesn't reveal any real device identity
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(
                    (sessionEphemeralId != null ? sessionEphemeralId : "default").getBytes(),
                    "HmacSHA256"));
            byte[] hash = hmac.doFinal(address.getBytes());

            // Use first 4 bytes as a hex alias
            return "Peer-" + String.format("%02x%02x", hash[0], hash[1]);
        } catch (Exception e) {
            // Fallback to simple hash
            return "Peer-" + Integer.toHexString(address.hashCode()).substring(0, 4);
        }
    }

    /**
     * Process a scan result and create/update a DiscoveredPeer.
     *
     * SECURITY: Device name is intentionally ignored even if present in scan data.
     * Peers are identified by anonymous aliases derived from their BLE address.
     */
    @SuppressLint("MissingPermission")
    private void processScanResult(@NonNull ScanResult result) {
        if (!AndroidUtils.hasBLEPermission(app)) {
            return;
        }

        String address = result.getDevice().getAddress();
        int rssi = result.getRssi();

        // Rate limit check
        if (!checkRateLimit(address)) {
            return;
        }

        // SECURITY: Generate anonymous alias instead of using real device name.
        // The device's actual Bluetooth name is intentionally never read or stored.
        String peerAlias = generatePeerAlias(address);

        long now = System.currentTimeMillis();
        peerLastSeen.put(address, now);

        DiscoveredPeer existingPeer = discoveredPeers.get(address);
        if (existingPeer != null) {
            // Update existing peer
            existingPeer.setSignalStrength(rssi);
            callback.onPeerUpdated(existingPeer);
        } else {
            // New peer discovered
            DiscoveredPeer newPeer = new DiscoveredPeer(address, peerAlias);
            newPeer.setMacAddress(address);
            newPeer.setSignalStrength(rssi);

            discoveredPeers.put(address, newPeer);
            callback.onPeerDiscovered(newPeer);

            LOG.info("Discovered new peer: " + peerAlias
                    + " [" + P2pLogSanitizer.redactMac(address) + "] RSSI: " + rssi);
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
