package net.osmand.plus.plugins.p2pshare.transport;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.p2pshare.ContentManifest;
import net.osmand.plus.plugins.p2pshare.P2pLogSanitizer;
import net.osmand.plus.plugins.p2pshare.ShareableContent;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.utils.AndroidUtils;

import org.apache.commons.logging.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LAMPP Phase 6.4: WiFi Direct transport for P2P file transfer.
 *
 * Handles high-speed file transfer over WiFi Direct connections.
 * Speed: ~250 Mbps (practical ~50-100 Mbps)
 * Range: ~200 meters (line of sight)
 *
 * Protocol:
 * 1. Group owner creates server socket on PORT
 * 2. Client connects to group owner's IP
 * 3. Exchange manifests (what each device has)
 * 4. Request/receive files
 */
public class WifiDirectTransport {

    private static final Log LOG = PlatformUtil.getLog(WifiDirectTransport.class);

    // Port for P2P file transfer
    private static final int SERVER_PORT = 8765;

    // Buffer size for file transfer (64KB for good throughput)
    private static final int BUFFER_SIZE = 64 * 1024;

    // Connection timeout
    private static final int CONNECT_TIMEOUT_MS = 15000;

    // Protocol message types
    private static final int MSG_MANIFEST = 1;
    private static final int MSG_FILE_REQUEST = 2;
    private static final int MSG_FILE_DATA = 3;
    private static final int MSG_FILE_COMPLETE = 4;
    private static final int MSG_ERROR = 5;
    private static final int MSG_FILE_RESUME = 6;
    private static final int MSG_FIELDNOTE = 7;

    private final OsmandApplication app;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // WiFi P2P components
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver wifiP2pReceiver;
    private boolean receiverRegistered = false;

    // Connection state
    private boolean isAvailable = false;
    private boolean isConnected = false;
    private boolean isGroupOwner = false;
    private InetAddress groupOwnerAddress;
    private DiscoveredPeer connectedPeer;

    // Socket connections
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    // Transfer state
    private AtomicBoolean isTransferring = new AtomicBoolean(false);
    private AtomicBoolean cancelRequested = new AtomicBoolean(false);

    // Callback
    private TransportCallback callback;

    // Content manifest for serving files
    private ContentManifest localManifest;

    public WifiDirectTransport(@NonNull OsmandApplication app) {
        this.app = app;
        initWifiP2p();
    }

    @SuppressLint("MissingPermission")
    private void initWifiP2p() {
        wifiP2pManager = (WifiP2pManager) app.getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            LOG.warn("WiFi P2P not supported on this device");
            return;
        }

        channel = wifiP2pManager.initialize(app, app.getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                LOG.warn("WiFi P2P channel disconnected");
                isAvailable = false;
                handleDisconnect("Channel disconnected");
            }
        });

        isAvailable = channel != null;
        LOG.info("WiFi P2P initialized, available: " + isAvailable);
    }

    /**
     * Register broadcast receiver for WiFi P2P state changes.
     * Must be called when activity is active.
     */
    public void registerReceiver(@NonNull Activity activity) {
        if (receiverRegistered || wifiP2pManager == null) {
            return;
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        wifiP2pReceiver = new WiFiP2pBroadcastReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(wifiP2pReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(wifiP2pReceiver, intentFilter);
        }

        receiverRegistered = true;
        LOG.info("WiFi P2P receiver registered");
    }

    /**
     * Unregister broadcast receiver.
     */
    public void unregisterReceiver(@NonNull Activity activity) {
        if (!receiverRegistered || wifiP2pReceiver == null) {
            return;
        }

        try {
            activity.unregisterReceiver(wifiP2pReceiver);
        } catch (Exception e) {
            LOG.error("Error unregistering receiver", e);
        }

        receiverRegistered = false;
        LOG.info("WiFi P2P receiver unregistered");
    }

    public void setCallback(@Nullable TransportCallback callback) {
        this.callback = callback;
    }

    public void setLocalManifest(@Nullable ContentManifest manifest) {
        this.localManifest = manifest;
    }

    public boolean isAvailable() {
        return isAvailable && wifiP2pManager != null && channel != null;
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Connect to a peer via WiFi Direct.
     */
    @SuppressLint("MissingPermission")
    public void connect(@NonNull DiscoveredPeer peer, @Nullable Activity activity) {
        if (!isAvailable()) {
            notifyConnectionFailed(peer, "WiFi Direct not available");
            return;
        }

        // Check permissions
        if (activity != null && !hasWifiDirectPermissions(activity)) {
            notifyConnectionFailed(peer, "WiFi Direct permissions not granted");
            return;
        }

        LOG.info("Connecting via WiFi Direct to: " + peer.getDeviceName());
        peer.setState(DiscoveredPeer.PeerState.CONNECTING);
        connectedPeer = peer;

        // First discover WiFi Direct peers
        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                LOG.info("WiFi P2P discovery started");
                // We'll connect when we find the peer in PEERS_CHANGED callback
                // For now, attempt direct connection using MAC address
                connectToPeerDirect(peer);
            }

            @Override
            public void onFailure(int reason) {
                LOG.error("WiFi P2P discovery failed: " + getFailureReason(reason));
                notifyConnectionFailed(peer, "Discovery failed: " + getFailureReason(reason));
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void connectToPeerDirect(@NonNull DiscoveredPeer peer) {
        String macAddress = peer.getMacAddress();
        if (macAddress == null || macAddress.isEmpty()) {
            notifyConnectionFailed(peer, "No MAC address for peer");
            return;
        }

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = macAddress;
        config.wps.setup = WpsInfo.PBC; // Push Button Config (no PIN needed)
        config.groupOwnerIntent = 0; // Let the framework decide who is GO

        wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                LOG.info("WiFi P2P connection initiated to: " + P2pLogSanitizer.redactMac(macAddress));
                // Actual connection status comes via CONNECTION_CHANGED broadcast
            }

            @Override
            public void onFailure(int reason) {
                LOG.error("WiFi P2P connection failed: " + getFailureReason(reason));
                notifyConnectionFailed(peer, "Connection failed: " + getFailureReason(reason));
            }
        });
    }

    /**
     * Disconnect from the current peer.
     */
    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (!isConnected) {
            return;
        }

        LOG.info("Disconnecting WiFi Direct");
        cancelRequested.set(true);

        // Close sockets
        closeConnections();

        // Remove WiFi P2P group
        if (wifiP2pManager != null && channel != null) {
            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    LOG.info("WiFi P2P group removed");
                }

                @Override
                public void onFailure(int reason) {
                    LOG.warn("Failed to remove WiFi P2P group: " + getFailureReason(reason));
                }
            });
        }

        handleDisconnect("User disconnected");
    }

    /**
     * Send our content manifest to the connected peer.
     */
    public void sendManifest(@NonNull String manifestJson) {
        if (!isConnected || outputStream == null) {
            LOG.warn("Cannot send manifest: not connected");
            return;
        }

        executor.execute(() -> {
            try {
                LOG.info("Sending manifest (" + manifestJson.length() + " bytes)");
                byte[] data = manifestJson.getBytes(StandardCharsets.UTF_8);

                outputStream.writeInt(MSG_MANIFEST);
                outputStream.writeInt(data.length);
                outputStream.write(data);
                outputStream.flush();

                LOG.info("Manifest sent successfully");
            } catch (IOException e) {
                LOG.error("Failed to send manifest", e);
                handleDisconnect("Send failed: " + e.getMessage());
            }
        });
    }

    /**
     * Request a file from the connected peer.
     */
    public void requestFile(@NonNull ShareableContent content) {
        if (!isConnected || outputStream == null) {
            LOG.warn("Cannot request file: not connected");
            return;
        }

        if (isTransferring.get()) {
            LOG.warn("Transfer already in progress");
            return;
        }

        executor.execute(() -> {
            try {
                isTransferring.set(true);
                cancelRequested.set(false);

                String filename = content.getFilename();
                LOG.info("Requesting file: " + filename);

                // Send file request
                byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
                outputStream.writeInt(MSG_FILE_REQUEST);
                outputStream.writeInt(filenameBytes.length);
                outputStream.write(filenameBytes);
                outputStream.flush();

                // Wait for file data response
                receiveFile(content);

            } catch (IOException e) {
                LOG.error("Failed to request file", e);
                notifyTransferComplete(content.getFilename(), false, e.getMessage());
            } finally {
                isTransferring.set(false);
            }
        });
    }

    /**
     * Request a file with resume from a given offset.
     * Used when a .tmp file exists from a previous interrupted transfer.
     * Falls back to full download if the remote peer doesn't support resume.
     */
    public void requestFileResume(@NonNull ShareableContent content, long offset) {
        if (!isConnected || outputStream == null) {
            LOG.warn("Cannot resume file: not connected");
            return;
        }

        if (isTransferring.get()) {
            LOG.warn("Transfer already in progress");
            return;
        }

        executor.execute(() -> {
            try {
                isTransferring.set(true);
                cancelRequested.set(false);

                String filename = content.getFilename();
                LOG.info("Requesting file resume: " + filename + " from offset " + offset);

                // Send resume request
                byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
                outputStream.writeInt(MSG_FILE_RESUME);
                outputStream.writeLong(offset);
                outputStream.writeInt(filenameBytes.length);
                outputStream.write(filenameBytes);
                outputStream.flush();

                // Wait for response — may be FILE_DATA (resume) or ERROR (fallback)
                receiveFileResume(content, offset);

            } catch (IOException e) {
                LOG.error("Failed to resume file", e);
                notifyTransferComplete(content.getFilename(), false, e.getMessage());
            } finally {
                isTransferring.set(false);
            }
        });
    }

    private void receiveFileResume(@NonNull ShareableContent content, long offset) throws IOException {
        String filename = content.getFilename();

        int msgType = inputStream.readInt();
        if (msgType == MSG_ERROR) {
            int errorLen = inputStream.readInt();
            byte[] errorBytes = new byte[errorLen];
            inputStream.readFully(errorBytes);
            String error = new String(errorBytes, StandardCharsets.UTF_8);
            LOG.warn("Resume not supported by peer: " + error + ", falling back to full download");
            // Fall back to regular request
            byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
            outputStream.writeInt(MSG_FILE_REQUEST);
            outputStream.writeInt(filenameBytes.length);
            outputStream.write(filenameBytes);
            outputStream.flush();
            receiveFile(content);
            return;
        }

        if (msgType != MSG_FILE_DATA) {
            throw new IOException("Unexpected message type: " + msgType);
        }

        // Read remaining size
        long remainingSize = inputStream.readLong();
        long totalSize = offset + remainingSize;
        LOG.info("Resuming file: " + filename + " from offset " + offset
                + ", remaining " + remainingSize + " bytes");

        File destFile = getDestinationFile(content);
        File tempFile = new File(destFile.getParent(), destFile.getName() + ".tmp");

        // Append to existing .tmp file
        try (BufferedOutputStream fileOut = new BufferedOutputStream(
                new FileOutputStream(tempFile, true), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long totalReceived = 0;
            int lastProgress = 0;

            while (totalReceived < remainingSize && !cancelRequested.get()) {
                int toRead = (int) Math.min(buffer.length, remainingSize - totalReceived);
                int bytesRead = inputStream.read(buffer, 0, toRead);

                if (bytesRead == -1) {
                    throw new IOException("Connection closed unexpectedly");
                }

                fileOut.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;

                int progress = (int) (((offset + totalReceived) * 100) / totalSize);
                if (progress > lastProgress) {
                    lastProgress = progress;
                    notifyTransferProgress(filename, progress, offset + totalReceived, totalSize);
                }
            }

            if (cancelRequested.get()) {
                throw new IOException("Transfer cancelled");
            }

            if (totalReceived != remainingSize) {
                throw new IOException("Size mismatch: expected " + remainingSize + ", got " + totalReceived);
            }
        }

        // Read completion
        int completeMsg = inputStream.readInt();
        if (completeMsg != MSG_FILE_COMPLETE) {
            throw new IOException("Expected completion message");
        }

        // Move temp to final
        if (destFile.exists()) {
            destFile.delete();
        }
        if (!tempFile.renameTo(destFile)) {
            throw new IOException("Failed to move temp file");
        }

        // Verify checksum
        String expectedChecksum = content.getChecksum();
        if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
            LOG.info("Verifying checksum after resume for: " + filename);
            try {
                String actualChecksum = ShareableContent.computeSha256(destFile);
                if (!expectedChecksum.equals(actualChecksum)) {
                    destFile.delete();
                    notifyTransferComplete(filename, false, "Checksum mismatch after resume: file corrupted");
                    return;
                }
                LOG.info("Checksum verified after resume: " + filename);
            } catch (Exception e) {
                LOG.warn("Checksum verification failed: " + e.getMessage());
            }
        }

        LOG.info("File resumed successfully: " + filename);
        notifyTransferComplete(filename, true, null);
    }

    private void receiveFile(@NonNull ShareableContent content) throws IOException {
        String filename = content.getFilename();
        long expectedSize = content.getFileSize();

        // Read message type
        int msgType = inputStream.readInt();
        if (msgType == MSG_ERROR) {
            int errorLen = inputStream.readInt();
            byte[] errorBytes = new byte[errorLen];
            inputStream.readFully(errorBytes);
            String error = new String(errorBytes, StandardCharsets.UTF_8);
            throw new IOException("Remote error: " + error);
        }

        if (msgType != MSG_FILE_DATA) {
            throw new IOException("Unexpected message type: " + msgType);
        }

        // Read file size
        long fileSize = inputStream.readLong();
        LOG.info("Receiving file: " + filename + " (" + fileSize + " bytes)");

        // Determine destination path based on content type
        File destFile = getDestinationFile(content);
        File tempFile = new File(destFile.getParent(), destFile.getName() + ".tmp");

        try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(tempFile), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long totalReceived = 0;
            int lastProgress = 0;

            while (totalReceived < fileSize && !cancelRequested.get()) {
                int toRead = (int) Math.min(buffer.length, fileSize - totalReceived);
                int bytesRead = inputStream.read(buffer, 0, toRead);

                if (bytesRead == -1) {
                    throw new IOException("Connection closed unexpectedly");
                }

                fileOut.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;

                // Report progress (every 1%)
                int progress = (int) ((totalReceived * 100) / fileSize);
                if (progress > lastProgress) {
                    lastProgress = progress;
                    notifyTransferProgress(filename, progress, totalReceived, fileSize);
                }
            }

            if (cancelRequested.get()) {
                throw new IOException("Transfer cancelled");
            }

            // Verify size
            if (totalReceived != fileSize) {
                throw new IOException("Size mismatch: expected " + fileSize + ", got " + totalReceived);
            }
        }

        // Read completion message
        int completeMsg = inputStream.readInt();
        if (completeMsg != MSG_FILE_COMPLETE) {
            throw new IOException("Expected completion message");
        }

        // Move temp file to final location
        if (destFile.exists()) {
            destFile.delete();
        }
        if (!tempFile.renameTo(destFile)) {
            throw new IOException("Failed to move temp file");
        }

        // Verify checksum if provided by remote peer
        String expectedChecksum = content.getChecksum();
        if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
            LOG.info("Verifying checksum for: " + filename);
            try {
                String actualChecksum = ShareableContent.computeSha256(destFile);
                if (!expectedChecksum.equals(actualChecksum)) {
                    destFile.delete();
                    LOG.error("Checksum mismatch for " + filename
                            + ": expected " + expectedChecksum.substring(0, 12)
                            + ", got " + actualChecksum.substring(0, 12));
                    notifyTransferComplete(filename, false, "Checksum mismatch: file corrupted");
                    return;
                }
                LOG.info("Checksum verified for: " + filename);
            } catch (Exception e) {
                LOG.warn("Checksum verification failed for " + filename + ": " + e.getMessage());
                // Don't delete file — verification failure is non-fatal
            }
        }

        LOG.info("File received successfully: " + filename);
        notifyTransferComplete(filename, true, null);
    }

    /**
     * Cancel an ongoing transfer.
     */
    public void cancelTransfer() {
        cancelRequested.set(true);
    }

    /**
     * Shutdown and release resources.
     */
    public void shutdown() {
        disconnect();
        executor.shutdownNow();

        if (channel != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                channel.close();
            }
            channel = null;
        }
    }

    // ---- Private helper methods ----

    private File getDestinationFile(@NonNull ShareableContent content) {
        switch (content.getType()) {
            case MAP:
                return new File(app.getAppPath(""), content.getFilename());
            case ZIM:
                return new File(app.getAppPath("wikipedia"), content.getFilename());
            case MODEL:
                return new File(app.getAppPath("llm_models"), content.getFilename());
            case APK:
                return new File(app.getExternalFilesDir(null), content.getFilename());
            default:
                return new File(app.getExternalFilesDir(null), content.getFilename());
        }
    }

    private void handleConnectionInfo(@NonNull WifiP2pInfo info) {
        if (!info.groupFormed) {
            LOG.info("P2P group not formed yet");
            return;
        }

        isGroupOwner = info.isGroupOwner;
        groupOwnerAddress = info.groupOwnerAddress;

        LOG.info("WiFi P2P connected - GroupOwner: " + isGroupOwner +
                ", GO Address: " + groupOwnerAddress);

        if (isGroupOwner) {
            // We are the server - start listening
            startServer();
        } else {
            // We are the client - connect to server
            connectToServer(groupOwnerAddress);
        }
    }

    private void startServer() {
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                serverSocket.setSoTimeout(CONNECT_TIMEOUT_MS);
                LOG.info("Server listening on port " + SERVER_PORT);

                clientSocket = serverSocket.accept();
                LOG.info("Client connected from: " + clientSocket.getInetAddress());

                setupStreams();
                handleConnectionEstablished();

                // Start message handling loop
                handleIncomingMessages();

            } catch (IOException e) {
                LOG.error("Server error", e);
                handleDisconnect("Server error: " + e.getMessage());
            }
        });
    }

    private void connectToServer(@NonNull InetAddress serverAddress) {
        executor.execute(() -> {
            try {
                LOG.info("Connecting to server: " + serverAddress);

                clientSocket = new Socket();
                clientSocket.connect(new InetSocketAddress(serverAddress, SERVER_PORT), CONNECT_TIMEOUT_MS);
                LOG.info("Connected to server");

                setupStreams();
                handleConnectionEstablished();

                // Start message handling loop
                handleIncomingMessages();

            } catch (IOException e) {
                LOG.error("Client connection error", e);
                handleDisconnect("Connection failed: " + e.getMessage());
            }
        });
    }

    private void setupStreams() throws IOException {
        java.io.InputStream rawIn = clientSocket.getInputStream();
        java.io.OutputStream rawOut = clientSocket.getOutputStream();

        // Phase 12: P2P transport encryption via ECDH + ChaCha20-Poly1305
        if (isEncryptionEnabled()) {
            try {
                LOG.info("Starting encrypted handshake (WiFi Direct)...");
                CryptoHandshake.HandshakeResult handshake = CryptoHandshake.perform(rawIn, rawOut);
                EncryptedStreamPair encryptedPair = new EncryptedStreamPair(
                        handshake.sendKey, handshake.receiveKey);
                rawIn = encryptedPair.wrapInput(rawIn);
                rawOut = encryptedPair.wrapOutput(rawOut);
                LOG.info("Encrypted P2P stream established (WiFi Direct), fingerprint=" + handshake.fingerprint);
            } catch (IOException e) {
                LOG.error("Encryption handshake failed (WiFi Direct): " + e.getMessage());
                throw new IOException("Encryption handshake failed: " + e.getMessage(), e);
            }
        } else {
            LOG.warn("P2P encryption DISABLED — data sent in plaintext (WiFi Direct)");
        }

        inputStream = new DataInputStream(new BufferedInputStream(rawIn, BUFFER_SIZE));
        outputStream = new DataOutputStream(new BufferedOutputStream(rawOut, BUFFER_SIZE));
    }

    private boolean isEncryptionEnabled() {
        try {
            return app.getSettings().LAMPP_P2P_ENCRYPTION_ENABLED.get();
        } catch (Exception e) {
            return true; // Default to encrypted if setting unavailable
        }
    }

    private void handleConnectionEstablished() {
        isConnected = true;

        mainHandler.post(() -> {
            if (connectedPeer != null) {
                connectedPeer.setState(DiscoveredPeer.PeerState.CONNECTED);
                if (callback != null) {
                    callback.onConnected(connectedPeer);
                }
            }

            // Send our manifest
            if (localManifest != null) {
                sendManifest(localManifest.toJson());
            }
        });
    }

    private void handleIncomingMessages() {
        try {
            while (isConnected && !cancelRequested.get()) {
                int msgType = inputStream.readInt();

                switch (msgType) {
                    case MSG_MANIFEST:
                        receiveManifest();
                        break;
                    case MSG_FILE_REQUEST:
                        handleFileRequest();
                        break;
                    case MSG_FILE_RESUME:
                        handleFileResumeRequest();
                        break;
                    case MSG_FIELDNOTE:
                        receiveFieldNote();
                        break;
                    default:
                        LOG.warn("Unknown message type: " + msgType);
                }
            }
        } catch (IOException e) {
            if (!cancelRequested.get()) {
                LOG.error("Message handling error", e);
                handleDisconnect("Communication error: " + e.getMessage());
            }
        }
    }

    /**
     * Send a FieldNote sync packet to the connected peer.
     * Phase 2: FieldNotes P2P sync.
     */
    public void sendFieldNote(@NonNull org.json.JSONObject noteJson) {
        if (!isConnected || outputStream == null) {
            LOG.warn("Cannot send FieldNote: not connected");
            return;
        }

        executor.execute(() -> {
            try {
                byte[] data = noteJson.toString().getBytes(StandardCharsets.UTF_8);
                outputStream.writeInt(MSG_FIELDNOTE);
                outputStream.writeInt(data.length);
                outputStream.write(data);
                outputStream.flush();
                LOG.info("FieldNote sent (" + data.length + " bytes)");
            } catch (IOException e) {
                LOG.error("Failed to send FieldNote", e);
                handleDisconnect("FieldNote send failed: " + e.getMessage());
            }
        });
    }

    private void receiveFieldNote() throws IOException {
        int length = inputStream.readInt();
        byte[] data = new byte[length];
        inputStream.readFully(data);
        String noteJson = new String(data, StandardCharsets.UTF_8);
        LOG.info("Received FieldNote (" + length + " bytes)");

        mainHandler.post(() -> {
            if (callback != null && connectedPeer != null) {
                callback.onFieldNoteReceived(connectedPeer, noteJson);
            }
        });
    }

    private void receiveManifest() throws IOException {
        int length = inputStream.readInt();
        byte[] data = new byte[length];
        inputStream.readFully(data);

        String manifestJson = new String(data, StandardCharsets.UTF_8);
        LOG.info("Received manifest (" + length + " bytes)");

        mainHandler.post(() -> {
            if (callback != null && connectedPeer != null) {
                callback.onManifestReceived(connectedPeer, manifestJson);
            }
        });
    }

    private void handleFileRequest() throws IOException {
        int filenameLen = inputStream.readInt();
        byte[] filenameBytes = new byte[filenameLen];
        inputStream.readFully(filenameBytes);

        String requestedFilename = new String(filenameBytes, StandardCharsets.UTF_8);
        LOG.info("File requested: " + requestedFilename);

        // Find the file in our manifest
        ShareableContent content = findContentByFilename(requestedFilename);
        if (content == null || !content.isShared()) {
            sendError("File not available: " + requestedFilename);
            return;
        }

        // Send the file
        sendFile(content);
    }

    private void handleFileResumeRequest() throws IOException {
        long offset = inputStream.readLong();
        int filenameLen = inputStream.readInt();
        byte[] filenameBytes = new byte[filenameLen];
        inputStream.readFully(filenameBytes);

        String requestedFilename = new String(filenameBytes, StandardCharsets.UTF_8);
        LOG.info("File resume requested: " + requestedFilename + " from offset " + offset);

        ShareableContent content = findContentByFilename(requestedFilename);
        if (content == null || !content.isShared()) {
            sendError("File not available: " + requestedFilename);
            return;
        }

        sendFileFromOffset(content, offset);
    }

    private void sendFileFromOffset(@NonNull ShareableContent content, long offset) throws IOException {
        File file = new File(content.getFilePath());
        if (!file.exists()) {
            sendError("File not found: " + content.getFilename());
            return;
        }

        long fileSize = file.length();
        if (offset < 0 || offset >= fileSize) {
            sendError("Invalid resume offset: " + offset);
            return;
        }

        long remainingSize = fileSize - offset;
        LOG.info("Sending file from offset " + offset + ": " + content.getFilename()
                + " (" + remainingSize + " remaining of " + fileSize + " bytes)");

        isTransferring.set(true);
        cancelRequested.set(false);

        try {
            // Send file data header with remaining size
            outputStream.writeInt(MSG_FILE_DATA);
            outputStream.writeLong(remainingSize);

            // Send file content from offset
            try (FileInputStream fis = new FileInputStream(file)) {
                long skipped = fis.skip(offset);
                if (skipped != offset) {
                    throw new IOException("Failed to seek to offset " + offset);
                }

                BufferedInputStream fileIn = new BufferedInputStream(fis, BUFFER_SIZE);
                byte[] buffer = new byte[BUFFER_SIZE];
                long totalSent = 0;
                int lastProgress = 0;
                int bytesRead;

                while ((bytesRead = fileIn.read(buffer)) != -1 && !cancelRequested.get()) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;

                    int progress = (int) (((offset + totalSent) * 100) / fileSize);
                    if (progress > lastProgress) {
                        lastProgress = progress;
                        notifyTransferProgress(content.getFilename(), progress, offset + totalSent, fileSize);
                    }
                }
            }

            outputStream.flush();
            outputStream.writeInt(MSG_FILE_COMPLETE);
            outputStream.flush();

            LOG.info("File resume sent successfully: " + content.getFilename());
            notifyTransferComplete(content.getFilename(), true, null);

        } finally {
            isTransferring.set(false);
        }
    }

    private void sendFile(@NonNull ShareableContent content) throws IOException {
        File file = new File(content.getFilePath());
        if (!file.exists()) {
            sendError("File not found: " + content.getFilename());
            return;
        }

        long fileSize = file.length();
        LOG.info("Sending file: " + content.getFilename() + " (" + fileSize + " bytes)");

        isTransferring.set(true);
        cancelRequested.set(false);

        try {
            // Send file data header
            outputStream.writeInt(MSG_FILE_DATA);
            outputStream.writeLong(fileSize);

            // Send file content
            try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long totalSent = 0;
                int lastProgress = 0;
                int bytesRead;

                while ((bytesRead = fileIn.read(buffer)) != -1 && !cancelRequested.get()) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;

                    // Report progress
                    int progress = (int) ((totalSent * 100) / fileSize);
                    if (progress > lastProgress) {
                        lastProgress = progress;
                        notifyTransferProgress(content.getFilename(), progress, totalSent, fileSize);
                    }
                }
            }

            outputStream.flush();

            // Send completion
            outputStream.writeInt(MSG_FILE_COMPLETE);
            outputStream.flush();

            LOG.info("File sent successfully: " + content.getFilename());
            notifyTransferComplete(content.getFilename(), true, null);

        } finally {
            isTransferring.set(false);
        }
    }

    private void sendError(@NonNull String error) throws IOException {
        byte[] errorBytes = error.getBytes(StandardCharsets.UTF_8);
        outputStream.writeInt(MSG_ERROR);
        outputStream.writeInt(errorBytes.length);
        outputStream.write(errorBytes);
        outputStream.flush();
    }

    @Nullable
    private ShareableContent findContentByFilename(@NonNull String filename) {
        if (localManifest == null) {
            return null;
        }
        for (ShareableContent content : localManifest.getAllContent()) {
            if (content.getFilename().equals(filename)) {
                return content;
            }
        }
        return null;
    }

    private void handleDisconnect(@Nullable String reason) {
        boolean wasConnected = isConnected;
        isConnected = false;
        isGroupOwner = false;
        groupOwnerAddress = null;

        closeConnections();

        if (wasConnected && connectedPeer != null) {
            DiscoveredPeer peer = connectedPeer;
            peer.setState(DiscoveredPeer.PeerState.DISCONNECTED);

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onDisconnected(peer, reason);
                }
            });
        }

        connectedPeer = null;
    }

    private void closeConnections() {
        try {
            if (inputStream != null) inputStream.close();
        } catch (Exception ignored) {}
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {}
        try {
            if (clientSocket != null) clientSocket.close();
        } catch (Exception ignored) {}
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}

        inputStream = null;
        outputStream = null;
        clientSocket = null;
        serverSocket = null;
    }

    private void notifyConnectionFailed(@NonNull DiscoveredPeer peer, @NonNull String error) {
        peer.setState(DiscoveredPeer.PeerState.DISCONNECTED);
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onConnectionFailed(peer, error);
            }
        });
    }

    private void notifyTransferProgress(@NonNull String filename, int progress, long transferred, long total) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onTransferProgress(filename, progress, transferred, total);
            }
        });
    }

    private void notifyTransferComplete(@NonNull String filename, boolean success, @Nullable String error) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onTransferComplete(filename, success, error);
            }
        });
    }

    private boolean hasWifiDirectPermissions(@NonNull Activity activity) {
        // On Android 13+, need NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return AndroidUtils.hasPermission(activity, "android.permission.NEARBY_WIFI_DEVICES");
        }
        // On older versions, need ACCESS_FINE_LOCATION
        return OsmAndLocationProvider.isLocationPermissionAvailable(activity);
    }

    @NonNull
    private String getFailureReason(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P not supported";
            case WifiP2pManager.ERROR:
                return "Internal error";
            case WifiP2pManager.BUSY:
                return "Device busy";
            default:
                return "Unknown error (" + reason + ")";
        }
    }

    // ---- Broadcast Receiver ----

    private class WiFiP2pBroadcastReceiver extends BroadcastReceiver {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    isAvailable = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
                    LOG.info("WiFi P2P state changed: " + (isAvailable ? "enabled" : "disabled"));
                    break;

                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    // Peer list changed - could update our list of available peers
                    if (wifiP2pManager != null) {
                        wifiP2pManager.requestPeers(channel, peers -> {
                            LOG.info("WiFi P2P peers updated: " + peers.getDeviceList().size());
                            // Could match BLE-discovered peers with WiFi Direct peers here
                        });
                    }
                    break;

                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    if (wifiP2pManager != null) {
                        wifiP2pManager.requestConnectionInfo(channel, info -> {
                            if (info != null) {
                                handleConnectionInfo(info);
                            }
                        });
                    }
                    break;

                case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                    WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                    if (device != null) {
                        LOG.info("This device changed: " + P2pLogSanitizer.redactDeviceName(device.deviceName)
                                + " status: " + device.status);
                    }
                    break;
            }
        }
    }
}
