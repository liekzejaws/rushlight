package net.osmand.plus.plugins.p2pshare;

import androidx.annotation.NonNull;

/**
 * Types of content that can be shared via P2P.
 */
public enum ContentType {
    MAP(".obf", "Offline Map"),
    ZIM(".zim", "Wikipedia"),
    MODEL(".gguf", "LLM Model"),
    APK(".apk", "Lampp App");

    private final String extension;
    private final String displayName;

    ContentType(@NonNull String extension, @NonNull String displayName) {
        this.extension = extension;
        this.displayName = displayName;
    }

    @NonNull
    public String getExtension() {
        return extension;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Determine content type from filename.
     */
    @NonNull
    public static ContentType fromFilename(@NonNull String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".obf")) {
            return MAP;
        } else if (lower.endsWith(".zim")) {
            return ZIM;
        } else if (lower.endsWith(".gguf")) {
            return MODEL;
        } else if (lower.endsWith(".apk")) {
            return APK;
        }
        // Default to MAP for unknown extensions
        return MAP;
    }
}
