package net.osmand.plus.plugins.p2pshare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.IndexConstants;
import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.Version;

import org.apache.commons.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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

        // Apply saved exclusion preferences
        Set<String> excludedFiles = app.getApplicationContext()
                .getSharedPreferences("p2p_share_config", Context.MODE_PRIVATE)
                .getStringSet("excluded_files", Collections.emptySet());
        if (excludedFiles != null && !excludedFiles.isEmpty()) {
            for (ShareableContent content : contentList) {
                if (excludedFiles.contains(content.getFilename())) {
                    content.setShared(false);
                }
            }
        }

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

    /**
     * Serialize manifest to JSON for network exchange.
     */
    @NonNull
    public String toJson() {
        try {
            JSONObject json = new JSONObject();
            JSONArray items = new JSONArray();

            for (ShareableContent content : contentList) {
                if (content.isShared()) {
                    JSONObject item = new JSONObject();
                    item.put("filename", content.getFilename());
                    item.put("size", content.getFileSize());
                    item.put("type", content.getContentType().name());
                    if (content.getChecksum() != null) {
                        item.put("checksum", content.getChecksum());
                    }
                    items.put(item);
                }
            }

            json.put("items", items);
            json.put("summary", getSummary());

            return json.toString();
        } catch (JSONException e) {
            LOG.error("Failed to serialize manifest", e);
            return "{}";
        }
    }

    /**
     * Parse a manifest received from a peer.
     * Creates a ContentManifest with content items for display/selection.
     */
    @NonNull
    public static ContentManifest fromJson(@NonNull OsmandApplication app, @NonNull String jsonStr) {
        ContentManifest manifest = new ContentManifest(app);

        try {
            JSONObject json = new JSONObject(jsonStr);
            JSONArray items = json.optJSONArray("items");

            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String filename = item.getString("filename");
                    long size = item.getLong("size");
                    String typeName = item.optString("type", "MAP");
                    String checksum = item.optString("checksum", null);

                    ContentType type;
                    try {
                        type = ContentType.valueOf(typeName);
                    } catch (IllegalArgumentException e) {
                        type = ContentType.MAP;
                    }

                    // For remote content, we don't have a local path
                    ShareableContent content = new ShareableContent(
                            filename, "", size, type, checksum);
                    manifest.contentList.add(content);
                }
            }
        } catch (JSONException e) {
            LOG.error("Failed to parse manifest JSON", e);
        }

        return manifest;
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
