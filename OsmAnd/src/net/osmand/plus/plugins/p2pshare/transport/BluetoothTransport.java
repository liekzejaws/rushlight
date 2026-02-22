package net.osmand.plus.plugins.p2pshare.transport;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.p2pshare.ContentManifest;
import net.osmand.plus.plugins.p2pshare.P2pLogSanitizer;
import net.osmand.plus.plugins.p2pshare.ShareableContent;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.plugins.p2pshare.discovery.PeerDiscoveryManager;
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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LAMPP Phase 6.5: Bluetooth Classic transport as fallback for P2P file transfer.
 *
 * Used when WiFi Direct is not available or fails.
 * Speed: ~720 Kbps (practical ~300 Kbps)
 * Range: ~10 meters
 *
 * Uses the same protocol as WiFi Direct but over RFCOMM.
 */
public class BluetoothTransport {

    private static final Log LOG = PlatformUtil.getLog(BluetoothTransport.class);

    // Service name for discovery (generic to avoid identification)
    private static final String SERVICE_NAME = "RL-P2P";

    // Buffer size (smaller than WiFi Direct due to lower bandwidth)
    private static final int BUFFER_SIZE = 16 * 1024; // 16KB

    // Connection timeout
    private static final int CONNECT_TIMEOUT_MS = 30000; // Bluetooth is slower

    // Protocol message types (same as WiFi Direct)
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

    // Discovery manager reference for rotating UUID access
    private PeerDiscoveryManager discoveryManager;

    // Bluetooth components
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket clientSocket;

    // Streams
    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    // State
    private boolean isAvailable = false;
    private boolean isConnected = false;
    private boolean isListening = false;
    private DiscoveredPeer connectedPeer;

    // Transfer state
    private AtomicBoolean isTransferring = new AtomicBoolean(false);
    private AtomicBoolean cancelRequested = new AtomicBoolean(false);

    // Callback
    private TransportCallback callback;

    // Content manifest
    private ContentManifest localManifest;

    public BluetoothTransport(@NonNull OsmandApplication app) {
        this.app = app;
        initBluetooth();
    }

    private void initBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            isAvailable = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        }

        if (!isAvailable) {
            LOG.warn("Bluetooth Classic not available or not enabled");
        } else {
            LOG.info("Bluetooth Classic transport initialized");
        }
    }

    public void setDiscoveryManager(@Nullable PeerDiscoveryManager discoveryManager) {
        this.discoveryManager = discoveryManager;
    }

    /**
     * Get the service UUID — uses rotating UUID from discovery manager if available,
     * otherwise falls back to a static UUID (for backwards compatibility).
     */
    private UUID getServiceUuid() {
        if (discoveryManager != null) {
            return discoveryManager.getRotatingServiceUuid();
        }
        // Fallback: deterministic UUID (less secure but functional)
        return UUID.nameUUIDFromBytes("rushlight-bt-service".getBytes());
    }

    public void setCallback(@Nullable TransportCallback callback) {
        this.callback = callback;
    }

    public void setLocalManifest(@Nullable ContentManifest manifest) {
        this.localManifest = manifest;
    }

    public boolean isAvailable() {
        return isAvailable && bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Start listening for incoming Bluetooth connections.
     * Should be started when scanning for peers.
     */
    @SuppressLint("MissingPermission")
    public void startListening() {
        if (isListening || !isAvailable()) {
            return;
        }

        if (!AndroidUtils.hasBLEPermission(app)) {
            LOG.warn("Cannot listen: Bluetooth permissions not granted");
            return;
        }

        executor.execute(() -> {
            try {
                LOG.info("Starting Bluetooth server socket");
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, getServiceUuid());
                isListening = true;

                while (isListening && !Thread.currentThread().isInterrupted()) {
                    try {
                        LOG.info("Waiting for Bluetooth connection...");
                        BluetoothSocket socket = serverSocket.accept();

                        if (socket != null) {
                            LOG.info("Bluetooth connection accepted from: "
                                    + P2pLogSanitizer.redactMac(socket.getRemoteDevice().getAddress()));
                            handleIncomingConnection(socket);
                        }
                    } catch (IOException e) {
                        if (isListening) {
                            LOG.error("Error accepting Bluetooth connection", e);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                LOG.error("Failed to create Bluetooth server socket", e);
            } finally {
                isListening = false;
            }
        });
    }

    /**
     * Stop listening for incoming connections.
     */
    public void stopListening() {
        isListening = false;
        closeServerSocket();
    }

    /**
     * Connect to a peer via Bluetooth Classic.
     */
    @SuppressLint("MissingPermission")
    public void connect(@NonNull DiscoveredPeer peer) {
        if (!isAvailable()) {
            notifyConnectionFailed(peer, "Bluetooth not available");
            return;
        }

        String macAddress = peer.getMacAddress();
        if (macAddress == null || macAddress.isEmpty()) {
            notifyConnectionFailed(peer, "No MAC address for peer");
            return;
        }

        if (!AndroidUtils.hasBLEPermission(app)) {
            notifyConnectionFailed(peer, "Bluetooth permissions not granted");
            return;
        }

        LOG.info("Connecting via Bluetooth to: " + peer.getDeviceName()
                + " (" + P2pLogSanitizer.redactMac(macAddress) + ")");
        connectedPeer = peer;
        peer.setState(DiscoveredPeer.PeerState.CONNECTING);

        executor.execute(() -> {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                clientSocket = device.createRfcommSocketToServiceRecord(getServiceUuid());

                LOG.info("Attempting Bluetooth RFCOMM connection...");
                clientSocket.connect();

                LOG.info("Bluetooth connected successfully");
                setupStreams(clientSocket);
                handleConnectionEstablished();

                // Start message handling
                handleIncomingMessages();

            } catch (IOException e) {
                LOG.error("Bluetooth connection failed", e);
                notifyConnectionFailed(peer, "Connection failed: " + e.getMessage());
                closeClientSocket();
            }
        });
    }

    /**
     * Disconnect from the current peer.
     */
    public void disconnect() {
        if (!isConnected) {
            return;
        }

        LOG.info("Disconnecting Bluetooth");
        cancelRequested.set(true);
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
                LOG.info("Sending manifest via Bluetooth (" + manifestJson.length() + " bytes)");
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
                LOG.info("Requesting file via Bluetooth: " + filename);

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
     * Falls back to full download if remote peer doesn't support resume.
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
                LOG.info("Requesting file resume via Bluetooth: " + filename + " from offset " + offset);

                byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
                outputStream.writeInt(MSG_FILE_RESUME);
                outputStream.writeLong(offset);
                outputStream.writeInt(filenameBytes.length);
                outputStream.write(filenameBytes);
                outputStream.flush();

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

        long remainingSize = inputStream.readLong();
        long totalSize = offset + remainingSize;
        LOG.info("Resuming file via Bluetooth: " + filename + " from offset " + offset
                + ", remaining " + remainingSize + " bytes");

        File destFile = getDestinationFile(content);
        File tempFile = new File(destFile.getParent(), destFile.getName() + ".tmp");

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

        int completeMsg = inputStream.readInt();
        if (completeMsg != MSG_FILE_COMPLETE) {
            throw new IOException("Expected completion message");
        }

        if (destFile.exists()) {
            destFile.delete();
        }
        if (!tempFile.renameTo(destFile)) {
            throw new IOException("Failed to move temp file");
        }

        // Verify checksum
        String expectedChecksum = content.getChecksum();
        if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
            LOG.info("Verifying checksum after Bluetooth resume: " + filename);
            try {
                String actualChecksum = ShareableContent.computeSha256(destFile);
                if (!expectedChecksum.equals(actualChecksum)) {
                    destFile.delete();
                    notifyTransferComplete(filename, false, "Checksum mismatch after resume: file corrupted");
                    return;
                }
                LOG.info("Checksum verified after Bluetooth resume: " + filename);
            } catch (Exception e) {
                LOG.warn("Checksum verification failed: " + e.getMessage());
            }
        }

        LOG.info("File resumed successfully via Bluetooth: " + filename);
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
        stopListening();
        disconnect();
        executor.shutdownNow();
    }

    // ---- Private helper methods ----

    @SuppressLint("MissingPermission")
    private void handleIncomingConnection(@NonNull BluetoothSocket socket) {
        // Close any existing connection
        if (isConnected) {
            handleDisconnect("New connection incoming");
        }

        try {
            clientSocket = socket;
            String deviceAddress = socket.getRemoteDevice().getAddress();

            // SECURITY: Don't use the real device name — use anonymous alias
            String peerAlias = "Peer-" + Integer.toHexString(deviceAddress.hashCode()).substring(0, 4);

            connectedPeer = new DiscoveredPeer(deviceAddress, peerAlias);
            connectedPeer.setMacAddress(deviceAddress);

            setupStreams(socket);
            handleConnectionEstablished();

            // Start message handling
            handleIncomingMessages();

        } catch (IOException e) {
            LOG.error("Failed to handle incoming connection", e);
            closeClientSocket();
        }
    }

    private void setupStreams(@NonNull BluetoothSocket socket) throws IOException {
        java.io.InputStream rawIn = socket.getInputStream();
        java.io.OutputStream rawOut = socket.getOutputStream();

        // Phase 12: P2P transport encryption via ECDH + ChaCha20-Poly1305
        if (isEncryptionEnabled()) {
            try {
                LOG.info("Starting encrypted handshake (Bluetooth)...");
                CryptoHandshake.HandshakeResult handshake = CryptoHandshake.perform(rawIn, rawOut);
                EncryptedStreamPair encryptedPair = new EncryptedStreamPair(
                        handshake.sendKey, handshake.receiveKey);
                rawIn = encryptedPair.wrapInput(rawIn);
                rawOut = encryptedPair.wrapOutput(rawOut);
                LOG.info("Encrypted P2P stream established (Bluetooth), fingerprint=" + handshake.fingerprint);
            } catch (IOException e) {
                LOG.error("Encryption handshake failed (Bluetooth): " + e.getMessage());
                throw new IOException("Encryption handshake failed: " + e.getMessage(), e);
            }
        } else {
            LOG.warn("P2P encryption DISABLED — data sent in plaintext (Bluetooth)");
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
                LOG.info("FieldNote sent via Bluetooth (" + data.length + " bytes)");
            } catch (IOException e) {
                LOG.error("Failed to send FieldNote via Bluetooth", e);
                handleDisconnect("FieldNote send failed: " + e.getMessage());
            }
        });
    }

    private void receiveFieldNote() throws IOException {
        int length = inputStream.readInt();
        byte[] data = new byte[length];
        inputStream.readFully(data);
        String noteJson = new String(data, StandardCharsets.UTF_8);
        LOG.info("Received FieldNote via Bluetooth (" + length + " bytes)");

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
        LOG.info("Received manifest via Bluetooth (" + length + " bytes)");

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
        LOG.info("File requested via Bluetooth: " + requestedFilename);

        ShareableContent content = findContentByFilename(requestedFilename);
        if (content == null || !content.isShared()) {
            sendError("File not available: " + requestedFilename);
            return;
        }

        sendFile(content);
    }

    private void handleFileResumeRequest() throws IOException {
        long offset = inputStream.readLong();
        int filenameLen = inputStream.readInt();
        byte[] filenameBytes = new byte[filenameLen];
        inputStream.readFully(filenameBytes);

        String requestedFilename = new String(filenameBytes, StandardCharsets.UTF_8);
        LOG.info("File resume requested via Bluetooth: " + requestedFilename + " from offset " + offset);

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
        LOG.info("Sending file from offset " + offset + " via Bluetooth: " + content.getFilename()
                + " (" + remainingSize + " remaining of " + fileSize + " bytes)");

        isTransferring.set(true);
        cancelRequested.set(false);

        try {
            outputStream.writeInt(MSG_FILE_DATA);
            outputStream.writeLong(remainingSize);

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

            LOG.info("File resume sent successfully via Bluetooth: " + content.getFilename());
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
        LOG.info("Sending file via Bluetooth: " + content.getFilename() + " (" + fileSize + " bytes)");

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

                    int progress = (int) ((totalSent * 100) / fileSize);
                    if (progress > lastProgress) {
                        lastProgress = progress;
                        notifyTransferProgress(content.getFilename(), progress, totalSent, fileSize);
                    }
                }
            }

            outputStream.flush();
            outputStream.writeInt(MSG_FILE_COMPLETE);
            outputStream.flush();

            LOG.info("File sent successfully via Bluetooth: " + content.getFilename());
            notifyTransferComplete(content.getFilename(), true, null);

        } finally {
            isTransferring.set(false);
        }
    }

    private void receiveFile(@NonNull ShareableContent content) throws IOException {
        String filename = content.getFilename();

        int msgType = inputStream.readInt();
        if (msgType == MSG_ERROR) {
            int errorLen = inputStream.readInt();
            byte[] errorBytes = new byte[errorLen];
            inputStream.readFully(errorBytes);
            throw new IOException("Remote error: " + new String(errorBytes, StandardCharsets.UTF_8));
        }

        if (msgType != MSG_FILE_DATA) {
            throw new IOException("Unexpected message type: " + msgType);
        }

        long fileSize = inputStream.readLong();
        LOG.info("Receiving file via Bluetooth: " + filename + " (" + fileSize + " bytes)");

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

                int progress = (int) ((totalReceived * 100) / fileSize);
                if (progress > lastProgress) {
                    lastProgress = progress;
                    notifyTransferProgress(filename, progress, totalReceived, fileSize);
                }
            }

            if (cancelRequested.get()) {
                throw new IOException("Transfer cancelled");
            }

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
            }
        }

        LOG.info("File received successfully via Bluetooth: " + filename);
        notifyTransferComplete(filename, true, null);
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

    private void handleDisconnect(@Nullable String reason) {
        boolean wasConnected = isConnected;
        isConnected = false;

        closeClientSocket();

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

    private void closeServerSocket() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        serverSocket = null;
    }

    private void closeClientSocket() {
        try {
            if (inputStream != null) inputStream.close();
        } catch (Exception ignored) {}
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {}
        try {
            if (clientSocket != null) clientSocket.close();
        } catch (Exception ignored) {}

        inputStream = null;
        outputStream = null;
        clientSocket = null;
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
}
