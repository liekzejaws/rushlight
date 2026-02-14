package net.osmand.plus.guides;

import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.OsmandApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton manager for loading and searching survival guides.
 * Loads bundled guides from assets/guides/ and user guides from
 * the OsmAnd shared directory.
 *
 * Access via OsmandApplication.getGuideManager().
 */
public class GuideManager {

	private static final String TAG = "GuideManager";
	private static final String ASSETS_GUIDES_DIR = "guides";
	private static final String USER_GUIDES_DIR = "guides";

	private final OsmandApplication app;
	private final GuideSearchIndex searchIndex;
	private final ExecutorService executor;
	private final AtomicBoolean loaded = new AtomicBoolean(false);
	private final AtomicBoolean loading = new AtomicBoolean(false);
	private final List<GuideEntry> allGuides = new ArrayList<>();

	public interface LoadCallback {
		void onGuidesLoaded(int count);
	}

	public GuideManager(@NonNull OsmandApplication app) {
		this.app = app;
		this.searchIndex = new GuideSearchIndex();
		this.executor = Executors.newSingleThreadExecutor();
	}

	/**
	 * Load all guides (bundled + user) asynchronously.
	 * Safe to call multiple times — will only load once.
	 */
	public void loadGuidesAsync(@Nullable LoadCallback callback) {
		if (loaded.get() || loading.getAndSet(true)) {
			if (callback != null && loaded.get()) {
				callback.onGuidesLoaded(allGuides.size());
			}
			return;
		}

		executor.execute(() -> {
			try {
				List<GuideEntry> guides = new ArrayList<>();
				guides.addAll(loadBundledGuides());
				guides.addAll(loadUserGuides());

				synchronized (allGuides) {
					allGuides.clear();
					allGuides.addAll(guides);
				}

				searchIndex.buildIndex(guides);
				loaded.set(true);
				loading.set(false);

				Log.i(TAG, "Loaded " + guides.size() + " guides (" +
						searchIndex.getKeywordCount() + " keywords indexed)");

				if (callback != null) {
					app.runInUIThread(() -> callback.onGuidesLoaded(guides.size()));
				}
			} catch (Exception e) {
				Log.e(TAG, "Failed to load guides: " + e.getMessage());
				loading.set(false);
			}
		});
	}

	/**
	 * Load guides synchronously. Blocks until complete.
	 * Primarily used for testing.
	 */
	public void loadGuidesSync() {
		if (loaded.get()) return;

		List<GuideEntry> guides = new ArrayList<>();
		guides.addAll(loadBundledGuides());
		guides.addAll(loadUserGuides());

		synchronized (allGuides) {
			allGuides.clear();
			allGuides.addAll(guides);
		}

		searchIndex.buildIndex(guides);
		loaded.set(true);

		Log.i(TAG, "Loaded " + guides.size() + " guides synchronously");
	}

	/**
	 * Get all loaded guides sorted by category then importance.
	 */
	@NonNull
	public List<GuideEntry> getAllGuides() {
		ensureLoaded();
		return searchIndex.getAllGuides();
	}

	/**
	 * Get guides filtered by category.
	 */
	@NonNull
	public List<GuideEntry> getByCategory(@NonNull GuideCategory category) {
		ensureLoaded();
		return searchIndex.searchByCategory(category);
	}

	/**
	 * Search guides by query string.
	 */
	@NonNull
	public List<GuideEntry> search(@NonNull String query) {
		ensureLoaded();
		return searchIndex.search(query);
	}

	/**
	 * Get a guide by its ID. Returns the full guide with body.
	 * For bundled guides where body was lazy-loaded, this reloads from assets.
	 */
	@Nullable
	public GuideEntry getGuide(@NonNull String id) {
		ensureLoaded();
		return searchIndex.getGuide(id);
	}

	/**
	 * @return The search index for direct access by RAG pipeline
	 */
	@NonNull
	public GuideSearchIndex getSearchIndex() {
		return searchIndex;
	}

	/**
	 * @return Number of loaded guides
	 */
	public int getGuideCount() {
		return searchIndex.getGuideCount();
	}

	/**
	 * @return Whether guides have finished loading
	 */
	public boolean isLoaded() {
		return loaded.get();
	}

	private void ensureLoaded() {
		if (!loaded.get()) {
			loadGuidesSync();
		}
	}

	@NonNull
	private List<GuideEntry> loadBundledGuides() {
		List<GuideEntry> guides = new ArrayList<>();
		try {
			AssetManager assets = app.getAssets();
			String[] files = assets.list(ASSETS_GUIDES_DIR);
			if (files == null) return guides;

			for (String filename : files) {
				if (!filename.endsWith(".json")) continue;
				try {
					String json = readAssetFile(ASSETS_GUIDES_DIR + "/" + filename);
					GuideEntry entry = GuideJsonParser.parseGuide(json, true);
					if (entry != null) {
						guides.add(entry);
					}
				} catch (IOException e) {
					Log.w(TAG, "Failed to read bundled guide: " + filename + " - " + e.getMessage());
				}
			}
			Log.d(TAG, "Loaded " + guides.size() + " bundled guides");
		} catch (IOException e) {
			Log.e(TAG, "Failed to list bundled guides: " + e.getMessage());
		}
		return guides;
	}

	@NonNull
	private List<GuideEntry> loadUserGuides() {
		List<GuideEntry> guides = new ArrayList<>();
		File userDir = getUserGuidesDir();
		if (userDir == null || !userDir.isDirectory()) return guides;

		File[] files = userDir.listFiles((dir, name) -> name.endsWith(".json"));
		if (files == null) return guides;

		for (File file : files) {
			try {
				String json = readFile(file);
				GuideEntry entry = GuideJsonParser.parseGuide(json, false);
				if (entry != null) {
					guides.add(entry);
				}
			} catch (IOException e) {
				Log.w(TAG, "Failed to read user guide: " + file.getName() + " - " + e.getMessage());
			}
		}
		Log.d(TAG, "Loaded " + guides.size() + " user guides");
		return guides;
	}

	@Nullable
	private File getUserGuidesDir() {
		File appDir = app.getAppPath(null);
		if (appDir == null) return null;
		return new File(appDir, USER_GUIDES_DIR);
	}

	@NonNull
	private String readAssetFile(@NonNull String path) throws IOException {
		InputStream is = app.getAssets().open(path);
		return readStream(is);
	}

	@NonNull
	private String readFile(@NonNull File file) throws IOException {
		InputStream is = new FileInputStream(file);
		return readStream(is);
	}

	@NonNull
	private String readStream(@NonNull InputStream is) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (sb.length() > 0) sb.append('\n');
				sb.append(line);
			}
		}
		return sb.toString();
	}
}
