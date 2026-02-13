package net.osmand.plus.plugins.p2pshare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Represents a piece of content that can be shared via P2P.
 * Could be a map file, Wikipedia ZIM, LLM model, or the app APK itself.
 */
public class ShareableContent {

    private final String filename;
    private final String filePath;
    private final long fileSize;
    private final ContentType contentType;
    private String checksum; // SHA-256 for verification (computed async after scan)

    // For display
    private final String displayName;

    // Sharing state
    private boolean isShared = true; // Whether user has enabled sharing this content

    public ShareableContent(@NonNull String filename, @NonNull String filePath, long fileSize,
                            @NonNull ContentType contentType, @Nullable String checksum) {
        this.filename = filename;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.checksum = checksum;
        this.displayName = generateDisplayName(filename, contentType);
    }

    /**
     * Create ShareableContent from a file.
     */
    @NonNull
    public static ShareableContent fromFile(@NonNull File file) {
        String filename = file.getName();
        ContentType type = ContentType.fromFilename(filename);
        // Note: Checksum computation would be done lazily or in background
        return new ShareableContent(filename, file.getAbsolutePath(), file.length(), type, null);
    }

    /**
     * Create ShareableContent for the app's own APK.
     */
    @NonNull
    public static ShareableContent forApk(@NonNull String apkPath, @NonNull String versionName) {
        File file = new File(apkPath);
        String displayFilename = "Lampp-" + versionName + ".apk";
        return new ShareableContent(displayFilename, apkPath, file.length(), ContentType.APK, null);
    }

    @NonNull
    public String getFilename() {
        return filename;
    }

    @NonNull
    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    @NonNull
    public ContentType getContentType() {
        return contentType;
    }

    /**
     * Alias for getContentType() for convenience.
     */
    @NonNull
    public ContentType getType() {
        return contentType;
    }

    @Nullable
    public String getChecksum() {
        return checksum;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    public boolean isShared() {
        return isShared;
    }

    public void setShared(boolean shared) {
        isShared = shared;
    }

    /**
     * Set the checksum after async computation.
     */
    public void setChecksum(@Nullable String checksum) {
        this.checksum = checksum;
    }

    /**
     * Compute SHA-256 checksum of a file.
     * Should be called on a background thread — blocks until complete.
     *
     * @param file The file to hash
     * @return Hex-encoded SHA-256 hash string
     * @throws IOException if file cannot be read
     */
    @NonNull
    public static String computeSha256(@NonNull File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            try (FileInputStream fis = new FileInputStream(file)) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    /**
     * Get human-readable file size.
     */
    @NonNull
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024));
        }
    }

    private static String generateDisplayName(@NonNull String filename, @NonNull ContentType type) {
        // Remove extension and clean up name
        String name = filename;
        String ext = type.getExtension();
        if (name.toLowerCase().endsWith(ext)) {
            name = name.substring(0, name.length() - ext.length());
        }
        // Replace underscores with spaces
        name = name.replace('_', ' ');
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShareableContent that = (ShareableContent) o;
        return Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }

    @Override
    public String toString() {
        return "ShareableContent{" +
                "filename='" + filename + '\'' +
                ", type=" + contentType +
                ", size=" + getFormattedSize() +
                '}';
    }
}
