/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.wikipedia;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LAMPP: Manager for downloading ZIM files from Kiwix.
 *
 * Handles download queue, progress tracking, and file storage.
 */
public class ZimDownloadManager {

	private static final Log LOG = PlatformUtil.getLog(ZimDownloadManager.class);

	public static final String ZIM_DIRECTORY = "zim";

	private final OsmandApplication app;
	private final ExecutorService executor;
	private final Handler mainHandler;
	private final ConcurrentHashMap<String, DownloadTask> activeDownloads;
	private final List<DownloadListener> listeners;

	public interface DownloadListener {
		void onDownloadStarted(ZimCatalogItem item);
		void onDownloadProgress(ZimCatalogItem item, int progress, long downloadedBytes, long totalBytes);
		void onDownloadComplete(ZimCatalogItem item, File file);
		void onDownloadError(ZimCatalogItem item, String error);
		void onDownloadCancelled(ZimCatalogItem item);
	}

	public ZimDownloadManager(@NonNull OsmandApplication app) {
		this.app = app;
		this.executor = Executors.newSingleThreadExecutor();
		this.mainHandler = new Handler(Looper.getMainLooper());
		this.activeDownloads = new ConcurrentHashMap<>();
		this.listeners = new ArrayList<>();

		// Ensure ZIM directory exists
		getZimDirectory().mkdirs();
	}

	/**
	 * Get the directory where ZIM files are stored.
	 */
	@NonNull
	public File getZimDirectory() {
		return new File(app.getAppPath(null), ZIM_DIRECTORY);
	}

	/**
	 * Get list of downloaded ZIM files.
	 */
	@NonNull
	public List<File> getDownloadedFiles() {
		List<File> files = new ArrayList<>();
		File zimDir = getZimDirectory();
		if (zimDir.exists() && zimDir.isDirectory()) {
			File[] zimFiles = zimDir.listFiles((dir, name) -> name.endsWith(".zim"));
			if (zimFiles != null) {
				for (File file : zimFiles) {
					files.add(file);
				}
			}
		}
		return files;
	}

	/**
	 * Check if a ZIM file is already downloaded.
	 */
	public boolean isDownloaded(@NonNull ZimCatalogItem item) {
		String fileName = item.getFileName();
		if (fileName == null) {
			return false;
		}
		File file = new File(getZimDirectory(), fileName);
		return file.exists() && file.length() > 0;
	}

	/**
	 * Check if a download is in progress for this item.
	 */
	public boolean isDownloading(@NonNull ZimCatalogItem item) {
		String id = item.getId();
		return id != null && activeDownloads.containsKey(id);
	}

	/**
	 * Get download progress for an item (0-100).
	 */
	public int getDownloadProgress(@NonNull ZimCatalogItem item) {
		String id = item.getId();
		if (id != null) {
			DownloadTask task = activeDownloads.get(id);
			if (task != null) {
				return task.progress;
			}
		}
		return 0;
	}

	/**
	 * Start downloading a ZIM file.
	 */
	public void startDownload(@NonNull ZimCatalogItem item) {
		if (item.getDownloadUrl() == null || item.getId() == null) {
			notifyError(item, "Invalid download URL");
			return;
		}

		if (isDownloading(item)) {
			LOG.info("Download already in progress: " + item.getTitle());
			return;
		}

		// Check disk space
		long freeSpace = getZimDirectory().getFreeSpace();
		if (freeSpace < item.getFileSize() * 1.1) { // 10% buffer
			notifyError(item, "Insufficient disk space");
			return;
		}

		DownloadTask task = new DownloadTask(item);
		activeDownloads.put(item.getId(), task);
		executor.execute(task);

		notifyStarted(item);
	}

	/**
	 * Cancel a download in progress.
	 */
	public void cancelDownload(@NonNull ZimCatalogItem item) {
		String id = item.getId();
		if (id != null) {
			DownloadTask task = activeDownloads.get(id);
			if (task != null) {
				task.cancel();
			}
		}
	}

	/**
	 * Delete a downloaded ZIM file.
	 */
	public boolean deleteDownload(@NonNull ZimCatalogItem item) {
		String fileName = item.getFileName();
		if (fileName == null) {
			return false;
		}
		File file = new File(getZimDirectory(), fileName);
		if (file.exists()) {
			return file.delete();
		}
		return false;
	}

	public void addListener(@NonNull DownloadListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void removeListener(@NonNull DownloadListener listener) {
		listeners.remove(listener);
	}

	private void notifyStarted(ZimCatalogItem item) {
		mainHandler.post(() -> {
			for (DownloadListener listener : listeners) {
				listener.onDownloadStarted(item);
			}
		});
	}

	private void notifyProgress(ZimCatalogItem item, int progress, long downloaded, long total) {
		mainHandler.post(() -> {
			for (DownloadListener listener : listeners) {
				listener.onDownloadProgress(item, progress, downloaded, total);
			}
		});
	}

	private void notifyComplete(ZimCatalogItem item, File file) {
		mainHandler.post(() -> {
			for (DownloadListener listener : listeners) {
				listener.onDownloadComplete(item, file);
			}
		});
	}

	private void notifyError(ZimCatalogItem item, String error) {
		mainHandler.post(() -> {
			for (DownloadListener listener : listeners) {
				listener.onDownloadError(item, error);
			}
		});
	}

	private void notifyCancelled(ZimCatalogItem item) {
		mainHandler.post(() -> {
			for (DownloadListener listener : listeners) {
				listener.onDownloadCancelled(item);
			}
		});
	}

	/**
	 * Shutdown the download manager.
	 */
	public void shutdown() {
		executor.shutdownNow();
		activeDownloads.clear();
	}

	/**
	 * Internal download task.
	 */
	private class DownloadTask implements Runnable {
		private final ZimCatalogItem item;
		private volatile boolean cancelled = false;
		private volatile int progress = 0;

		DownloadTask(ZimCatalogItem item) {
			this.item = item;
		}

		void cancel() {
			cancelled = true;
		}

		@Override
		public void run() {
			String fileName = item.getFileName();
			if (fileName == null) {
				fileName = "download_" + System.currentTimeMillis() + ".zim";
			}

			File outputFile = new File(getZimDirectory(), fileName);
			File tempFile = new File(getZimDirectory(), fileName + ".tmp");

			HttpURLConnection connection = null;
			InputStream inputStream = null;
			FileOutputStream outputStream = null;

			try {
				URL url = new URL(item.getDownloadUrl());
				connection = (HttpURLConnection) url.openConnection();
				connection.setConnectTimeout(30000);
				connection.setReadTimeout(60000);
				connection.setRequestProperty("User-Agent", "Lampp/1.0");

				// Support resume
				long existingSize = 0;
				if (tempFile.exists()) {
					existingSize = tempFile.length();
					connection.setRequestProperty("Range", "bytes=" + existingSize + "-");
				}

				int responseCode = connection.getResponseCode();
				if (responseCode != HttpURLConnection.HTTP_OK &&
						responseCode != HttpURLConnection.HTTP_PARTIAL) {
					throw new IOException("HTTP error: " + responseCode);
				}

				long totalSize = item.getFileSize();
				if (totalSize <= 0) {
					totalSize = connection.getContentLength() + existingSize;
				}

				inputStream = connection.getInputStream();
				outputStream = new FileOutputStream(tempFile, existingSize > 0);

				byte[] buffer = new byte[8192];
				long downloadedBytes = existingSize;
				int bytesRead;
				int lastProgress = -1;

				while (!cancelled && (bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
					downloadedBytes += bytesRead;

					if (totalSize > 0) {
						progress = (int) (downloadedBytes * 100 / totalSize);
						if (progress != lastProgress) {
							lastProgress = progress;
							notifyProgress(item, progress, downloadedBytes, totalSize);
						}
					}
				}

				outputStream.close();
				outputStream = null;

				if (cancelled) {
					// Keep temp file for resume
					activeDownloads.remove(item.getId());
					notifyCancelled(item);
					return;
				}

				// Rename temp file to final name
				if (tempFile.renameTo(outputFile)) {
					LOG.info("Download complete: " + outputFile.getName());
					activeDownloads.remove(item.getId());
					notifyComplete(item, outputFile);
				} else {
					throw new IOException("Failed to rename temp file");
				}

			} catch (Exception e) {
				LOG.error("Download failed: " + item.getTitle(), e);
				activeDownloads.remove(item.getId());
				notifyError(item, e.getMessage());
			} finally {
				try {
					if (inputStream != null) inputStream.close();
					if (outputStream != null) outputStream.close();
					if (connection != null) connection.disconnect();
				} catch (IOException e) {
					// Ignore
				}
			}
		}
	}
}
