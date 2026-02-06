package net.osmand.plus.plugins.p2pshare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.IndexConstants;
import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.Version;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the list of content available for P2P sharing from this device.
 * Scans map files, ZIMs, LLM models, and includes the app APK.
 */
public class ContentManifest {

    private static final Log LOG = PlatformUtil.getLog(ContentManifest.class);

    private final OsmandApplication app;
    private final List<ShareableContent> contentList = new CopyOnWriteArrayList<>();

    // Directories to scan for shareable content
    private static final String WIKIPEDIA_DIR = "wikipedia";
    private static final String LLM_MODELS_DIR = "llm_models";

    public ContentManifest(@NonNull OsmandApplication app) {
        this.app = app;
    }

    /**
     * Scan all content directories and build the manifest.
     * Should be called on a background thread.
     */
    public void scanContent() {
        LOG.info("Scanning content for P2P sharing manifest");
        List<ShareableContent> newContent = new ArrayList<>();

        // Scan maps (.obf files)
        scanMaps(newContent);

        // Scan Wikipedia (.zim files)
        scanZimFiles(newContent);

        // Scan LLM models (.gguf files)
        scanLlmModels(newContent);

        // Add own APK for self-spreading
        addOwnApk(newContent);

        contentList.clear();
        contentList.addAll(newContent);

        LOG.info("Content manifest updated: " + contentList.size() + " items");
    }

    /**
     * Get all shareable content.
     */
    @NonNull
    public List<ShareableContent> getAllContent() {
        return new ArrayList<>(contentList);
    }

    /**
     * Get content that user has enabled for sharing.
     */
    @NonNull
    public List<ShareableContent> getSharedContent() {
        List<ShareableContent> shared = new ArrayList<>();
        for (ShareableContent content : contentList) {
            if (content.isShared()) {
                shared.add(content);
            }
        }
        return shared;
    }

    /**
     * Get content by type.
     */
    @NonNull
    public List<ShareableContent> getContentByType(@NonNull ContentType type) {
        List<ShareableContent> filtered = new ArrayList<>();
        for (ShareableContent content : contentList) {
            if (content.getContentType() == type) {
                filtered.add(content);
            }
        }
        return filtered;
    }

    /**
     * Get total size of shared content.
     */
    public long getTotalSharedSize() {
        long total = 0;
        for (ShareableContent content : contentList) {
            if (content.isShared()) {
                total += content.getFileSize();
            }
        }
        return total;
    }

    /**
     * Get number of shared items.
     */
    public int getSharedCount() {
        int count = 0;
        for (ShareableContent content : contentList) {
            if (content.isShared()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Find content by filename.
     */
    @Nullable
    public ShareableContent findByFilename(@NonNull String filename) {
        for (ShareableContent content : contentList) {
            if (content.getFilename().equals(filename)) {
                return content;
            }
        }
        return null;
    }

    /**
     * Get summary string for display (e.g., "3 maps, 1 model")
     */
    @NonNull
    public String getSummary() {
        int maps = 0, zims = 0, models = 0;
        for (ShareableContent content : contentList) {
            if (content.isShared()) {
                switch (content.getContentType()) {
                    case MAP:
                        maps++;
                        break;
                    case ZIM:
                        zims++;
                        break;
                    case MODEL:
                        models++;
                        break;
                    case APK:
                        // Don't count APK in summary
                        break;
                }
            }
        }

        List<String> parts = new ArrayList<>();
        if (maps > 0) parts.add(maps + " map" + (maps > 1 ? "s" : ""));
        if (zims > 0) parts.add(zims + " Wikipedia");
        if (models > 0) parts.add(models + " model" + (models > 1 ? "s" : ""));

        if (parts.isEmpty()) {
            return "No content shared";
        }
        return String.join(", ", parts);
    }

    // Private scanning methods

    private void scanMaps(@NonNull List<ShareableContent> content) {
        File mapsDir = app.getAppPath(IndexConstants.MAPS_PATH);
        if (mapsDir.exists() && mapsDir.isDirectory()) {
            File[] files = mapsDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(IndexConstants.BINARY_MAP_INDEX_EXT));
            if (files != null) {
                for (File file : files) {
                    content.add(ShareableContent.fromFile(file));
                    LOG.debug("Found map: " + file.getName());
                }
            }
        }
    }

    private void scanZimFiles(@NonNull List<ShareableContent> content) {
        File zimDir = app.getAppPath(WIKIPEDIA_DIR);
        if (zimDir.exists() && zimDir.isDirectory()) {
            File[] files = zimDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".zim"));
            if (files != null) {
                for (File file : files) {
                    content.add(ShareableContent.fromFile(file));
                    LOG.debug("Found ZIM: " + file.getName());
                }
            }
        }
    }

    private void scanLlmModels(@NonNull List<ShareableContent> content) {
        File modelsDir = app.getAppPath(LLM_MODELS_DIR);
        if (modelsDir.exists() && modelsDir.isDirectory()) {
            File[] files = modelsDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".gguf"));
            if (files != null) {
                for (File file : files) {
                    content.add(ShareableContent.fromFile(file));
                    LOG.debug("Found model: " + file.getName());
                }
            }
        }
    }

    private void addOwnApk(@NonNull List<ShareableContent> content) {
        try {
            String apkPath = app.getPackageManager()
                    .getApplicationInfo(app.getPackageName(), 0).sourceDir;
            String versionName = Version.getFullVersion(app);
            ShareableContent apk = ShareableContent.forApk(apkPath, versionName);
            content.add(apk);
            LOG.debug("Added own APK: " + apk.getFilename());
        } catch (Exception e) {
            LOG.error("Failed to add own APK to manifest", e);
        }
    }
}
