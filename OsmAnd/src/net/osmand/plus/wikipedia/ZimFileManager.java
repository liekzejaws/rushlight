/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.wikipedia;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

import com.getkeepsafe.relinker.ReLinker;

import org.kiwix.libzim.Archive;
import org.kiwix.libzim.Entry;
import org.kiwix.libzim.Item;
import org.kiwix.libzim.SuggestionSearcher;
import org.kiwix.libzim.SuggestionSearch;
import org.kiwix.libzim.SuggestionItem;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * LAMPP: Manager for reading Kiwix ZIM files for offline Wikipedia access.
 *
 * Uses libkiwix for full zstd and LZMA compression support.
 * ZIM files are compressed archives containing Wikipedia content,
 * optimized for offline reading (typically 10-20% of original size).
 */
public class ZimFileManager implements Closeable {

	private static final Log LOG = PlatformUtil.getLog(ZimFileManager.class);
	private static final int DEFAULT_SEARCH_LIMIT = 20;

	private static boolean nativeLibraryLoaded = false;

	private final OsmandApplication app;
	private Archive archive;
	private SuggestionSearcher searcher;
	private File currentFile;
	private String zimTitle;

	public ZimFileManager(@NonNull OsmandApplication app) {
		this.app = app;
		loadNativeLibraries();
	}

	/**
	 * Load the native libraries required by libkiwix.
	 * Uses ReLinker for robust library loading on Android.
	 */
	private synchronized void loadNativeLibraries() {
		if (nativeLibraryLoaded) {
			return;
		}

		try {
			android.util.Log.i("ZimFileManager", "Loading native libraries...");
			// Load libraries in dependency order
			ReLinker.loadLibrary(app, "c++_shared");
			ReLinker.loadLibrary(app, "kiwix");
			ReLinker.loadLibrary(app, "zim");
			ReLinker.loadLibrary(app, "kiwix_wrapper");
			ReLinker.loadLibrary(app, "zim_wrapper");
			nativeLibraryLoaded = true;
			android.util.Log.i("ZimFileManager", "Native libraries loaded successfully");
		} catch (Exception e) {
			android.util.Log.e("ZimFileManager", "Failed to load native libraries: " + e.getMessage(), e);
			LOG.error("Failed to load native libraries", e);
		}
	}

	/**
	 * Open a ZIM file for reading.
	 *
	 * @param file The ZIM file to open
	 * @return true if successfully opened, false otherwise
	 */
	public boolean openZimFile(@NonNull File file) {
		close(); // Close any previously opened file

		android.util.Log.e("ZimFileManager", "Attempting to open ZIM file: " + file.getAbsolutePath());

		if (!file.exists() || !file.canRead()) {
			android.util.Log.e("ZimFileManager", "ZIM file does not exist or cannot be read: " + file.getAbsolutePath());
			LOG.error("ZIM file does not exist or cannot be read: " + file.getAbsolutePath());
			return false;
		}

		android.util.Log.i("ZimFileManager", "File exists and readable: " + file.length() + " bytes");

		try {
			android.util.Log.i("ZimFileManager", "Creating Archive...");
			archive = new Archive(file.getAbsolutePath());
			android.util.Log.i("ZimFileManager", "Archive created successfully");

			android.util.Log.i("ZimFileManager", "Creating SuggestionSearcher...");
			searcher = new SuggestionSearcher(archive);
			android.util.Log.i("ZimFileManager", "SuggestionSearcher created");

			currentFile = file;
			zimTitle = archive.getMetadata("Title");

			android.util.Log.i("ZimFileManager", "Opened ZIM file: " + file.getName() + " (Title: " + zimTitle + ")");
			LOG.info("Opened ZIM file: " + file.getName() + " (Title: " + zimTitle + ")");
			return true;
		} catch (Exception e) {
			android.util.Log.e("ZimFileManager", "Failed to open ZIM file: " + e.getMessage(), e);
			LOG.error("Failed to open ZIM file: " + file.getAbsolutePath(), e);
			close();
			return false;
		}
	}

	/**
	 * Open a ZIM file from a content URI.
	 * Note: This copies the file to a temporary location since Archive needs file path access.
	 *
	 * @param uri The content URI of the ZIM file
	 * @return true if successfully opened, false otherwise
	 */
	public boolean openZimFile(@NonNull Uri uri) {
		close();

		try {
			// Archive needs a file path, so we need to copy the content to a temp file
			// For large ZIM files, this could be slow/problematic - consider alternatives later
			InputStream inputStream = app.getContentResolver().openInputStream(uri);
			if (inputStream == null) {
				LOG.error("Could not open input stream for URI: " + uri);
				return false;
			}

			// Create temp file in app's cache directory
			File tempFile = new File(app.getCacheDir(), "temp_zim_" + System.currentTimeMillis() + ".zim");
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					fos.write(buffer, 0, bytesRead);
				}
			}
			inputStream.close();

			// Now open the temp file
			return openZimFile(tempFile);
		} catch (IOException e) {
			LOG.error("Failed to open ZIM from URI: " + uri, e);
			close();
			return false;
		}
	}

	/**
	 * Check if a ZIM file is currently open.
	 */
	public boolean isOpen() {
		return archive != null;
	}

	/**
	 * Get the title of the currently open ZIM file.
	 */
	@Nullable
	public String getZimTitle() {
		return zimTitle;
	}

	/**
	 * Get the number of articles in the currently open ZIM file.
	 */
	public int getArticleCount() {
		return archive != null ? (int) archive.getArticleCount() : 0;
	}

	/**
	 * Get the currently open ZIM file.
	 */
	@Nullable
	public File getCurrentFile() {
		return currentFile;
	}

	/**
	 * Get the size of the currently open ZIM file in bytes.
	 */
	public long getFileSize() {
		return currentFile != null ? currentFile.length() : 0;
	}

	/**
	 * Search for articles by title prefix using SuggestionSearcher.
	 *
	 * @param prefix The search prefix
	 * @param limit Maximum number of results to return
	 * @return List of matching article titles
	 */
	@NonNull
	public List<String> searchArticles(@NonNull String prefix, int limit) {
		List<String> results = new ArrayList<>();

		if (archive == null || searcher == null || prefix.isEmpty()) {
			return results;
		}

		try {
			SuggestionSearch search = searcher.suggest(prefix);
			if (search != null) {
				int searchLimit = limit > 0 ? limit : DEFAULT_SEARCH_LIMIT;
				Iterator<SuggestionItem> iterator = search.getResults(0, searchLimit);
				while (iterator.hasNext()) {
					SuggestionItem item = iterator.next();
					results.add(item.getTitle());
				}
			}
		} catch (Exception e) {
			// Some ZIM files don't have search index - graceful degradation
			LOG.warn("Search not available for this ZIM file: " + e.getMessage());
		}

		return results;
	}

	/**
	 * Search for articles by title prefix with default limit.
	 */
	@NonNull
	public List<String> searchArticles(@NonNull String prefix) {
		return searchArticles(prefix, DEFAULT_SEARCH_LIMIT);
	}

	/**
	 * Get the HTML content of an article by title.
	 *
	 * @param title The article title
	 * @return HTML content or null if not found
	 */
	@Nullable
	public String getArticleHtml(@NonNull String title) {
		if (archive == null) {
			return null;
		}

		try {
			Entry entry = archive.getEntryByTitle(title);
			Item item = entry.getItem(true); // true = follow redirects
			byte[] data = item.getData().getData();
			return new String(data, StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOG.error("Error reading article: " + title, e);
			return null;
		}
	}

	/**
	 * Get a random article title from the ZIM file.
	 *
	 * @return A random article title, or null if unavailable
	 */
	@Nullable
	public String getRandomTitle() {
		if (archive == null) {
			return null;
		}

		try {
			Entry entry = archive.getRandomEntry();
			return entry.getTitle();
		} catch (Exception e) {
			LOG.error("Error getting random title", e);
			return null;
		}
	}

	/**
	 * Get the main page title of the ZIM file.
	 *
	 * @return The main page title, or null if unavailable
	 */
	@Nullable
	public String getMainPageTitle() {
		if (archive == null) {
			return null;
		}

		try {
			Entry entry = archive.getMainEntry();
			return entry.getItem(true).getTitle();
		} catch (Exception e) {
			LOG.error("Error getting main page", e);
			return null;
		}
	}

	/**
	 * Close the ZIM file and release resources.
	 */
	@Override
	public void close() {
		if (searcher != null) {
			try {
				searcher.dispose();
			} catch (Exception e) {
				LOG.error("Error disposing searcher", e);
			}
			searcher = null;
		}
		if (archive != null) {
			try {
				archive.dispose();
			} catch (Exception e) {
				LOG.error("Error disposing archive", e);
			}
			archive = null;
		}
		currentFile = null;
		zimTitle = null;
	}
}
