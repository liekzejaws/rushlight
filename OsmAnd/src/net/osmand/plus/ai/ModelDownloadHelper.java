package net.osmand.plus.ai;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reusable utility for downloading LLM model files from HuggingFace.
 * Used by both LlmChatFragment (one-tap download) and LlmModelsFragment (full management).
 *
 * Thread-safe: download runs on a background executor, callbacks posted to main thread.
 */
public class ModelDownloadHelper {

	private static final Log LOG = PlatformUtil.getLog(ModelDownloadHelper.class);

	// TinyLlama — the default "quick download" model
	public static final String TINYLLAMA_URL =
			"https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf";
	public static final String TINYLLAMA_FILENAME = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf";
	public static final String TINYLLAMA_DISPLAY_NAME = "TinyLlama 1.1B";
	public static final long TINYLLAMA_SIZE = 669_000_000L; // ~669 MB

	// Phi-3-mini
	public static final String PHI3_URL =
			"https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf";
	public static final String PHI3_FILENAME = "Phi-3-mini-4k-instruct-q4.gguf";
	public static final long PHI3_SIZE = 2_400_000_000L; // ~2.4 GB

	/**
	 * Progress callback — all methods invoked on the main thread.
	 */
	public interface DownloadCallback {
		void onStarted();
		void onProgress(int percent, long downloadedBytes, long totalBytes);
		void onComplete(@NonNull File modelFile);
		void onError(@NonNull String message);
		void onCancelled();
	}

	private final OsmandApplication app;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private volatile boolean downloading = false;
	private volatile boolean cancelled = false;

	public ModelDownloadHelper(@NonNull OsmandApplication app) {
		this.app = app;
	}

	public boolean isDownloading() {
		return downloading;
	}

	/**
	 * Cancel the current download (if any). The temp file is left for potential resume.
	 */
	public void cancel() {
		cancelled = true;
	}

	/**
	 * Download a model file. Only one download can run at a time.
	 *
	 * @param url           HTTP(S) URL (HuggingFace direct link)
	 * @param filename      Target filename (e.g. "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf")
	 * @param estimatedSize Estimated byte size (used if server doesn't report Content-Length)
	 * @param callback      Progress/completion listener (main thread)
	 */
	public void download(@NonNull String url, @NonNull String filename, long estimatedSize,
	                      @Nullable DownloadCallback callback) {
		if (downloading) {
			if (callback != null) {
				mainHandler.post(() -> callback.onError("Download already in progress"));
			}
			return;
		}

		downloading = true;
		cancelled = false;
		if (callback != null) {
			mainHandler.post(callback::onStarted);
		}

		executor.execute(() -> {
			File modelsDir = new File(app.getAppPath(null), LlmManager.MODELS_DIR);
			modelsDir.mkdirs();
			File outputFile = new File(modelsDir, filename);
			File tempFile = new File(modelsDir, filename + ".tmp");

			try {
				URL downloadUrl = new URL(url);
				HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(30000);
				connection.setReadTimeout(60000);
				connection.setInstanceFollowRedirects(true);
				connection.connect();

				int responseCode = connection.getResponseCode();
				if (responseCode != HttpURLConnection.HTTP_OK) {
					throw new Exception("Server returned HTTP " + responseCode);
				}

				long contentLength = connection.getContentLengthLong();
				if (contentLength <= 0) {
					contentLength = estimatedSize;
				}

				InputStream inputStream = connection.getInputStream();
				FileOutputStream outputStream = new FileOutputStream(tempFile);

				byte[] buffer = new byte[8192];
				long downloaded = 0;
				int bytesRead;
				int lastPercent = -1;

				while ((bytesRead = inputStream.read(buffer)) != -1) {
					if (cancelled) {
						inputStream.close();
						outputStream.close();
						tempFile.delete();
						downloading = false;
						if (callback != null) {
							mainHandler.post(callback::onCancelled);
						}
						return;
					}

					outputStream.write(buffer, 0, bytesRead);
					downloaded += bytesRead;

					int percent = (int) (downloaded * 100 / contentLength);
					if (percent != lastPercent) {
						lastPercent = percent;
						final long dl = downloaded;
						final long total = contentLength;
						if (callback != null) {
							mainHandler.post(() -> callback.onProgress(percent, dl, total));
						}
					}
				}

				inputStream.close();
				outputStream.close();

				if (tempFile.renameTo(outputFile)) {
					downloading = false;
					LOG.info("Model download complete: " + filename);
					if (callback != null) {
						mainHandler.post(() -> callback.onComplete(outputFile));
					}
				} else {
					throw new Exception("Failed to save model file");
				}

			} catch (Exception e) {
				LOG.error("Model download failed: " + filename, e);
				tempFile.delete();
				downloading = false;
				if (callback != null) {
					String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
					mainHandler.post(() -> callback.onError(msg));
				}
			}
		});
	}

	/**
	 * Convenience: download TinyLlama (the default quick-download model).
	 */
	public void downloadTinyLlama(@Nullable DownloadCallback callback) {
		download(TINYLLAMA_URL, TINYLLAMA_FILENAME, TINYLLAMA_SIZE, callback);
	}

	/**
	 * Format byte count as human-readable string.
	 */
	@NonNull
	public static String formatSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return new DecimalFormat("#.#").format(bytes / 1024.0) + " KB";
		} else if (bytes < 1024 * 1024 * 1024) {
			return new DecimalFormat("#.#").format(bytes / (1024.0 * 1024.0)) + " MB";
		} else {
			return new DecimalFormat("#.##").format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
		}
	}

	/**
	 * Release executor resources.
	 */
	public void shutdown() {
		cancel();
		executor.shutdownNow();
	}
}
