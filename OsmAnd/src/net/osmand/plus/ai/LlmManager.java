package net.osmand.plus.ai;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mp.ai_core.NativeLib;
import com.mp.ai_core.StreamCallback;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LAMPP: Manager for local LLM inference using llama.cpp via Ai-Core.
 *
 * Provides offline AI assistant capability for survival scenarios.
 * Supports any GGUF model: Deepseek, Phi, Qwen, Llama, Mistral, etc.
 *
 * NOTE: Requires Android 11 (API 30) or higher due to Ai-Core AAR requirements.
 */
public class LlmManager implements Closeable {

	private static final Log LOG = PlatformUtil.getLog(LlmManager.class);
	private static final String TAG = "LlmManager";

	// Default inference parameters
	private static final int DEFAULT_MAX_TOKENS = 1024;
	private static final int DEFAULT_THREADS = 4;
	private static final int DEFAULT_CTX_SIZE = 4096;
	private static final float DEFAULT_TEMPERATURE = 0.7f;
	private static final int DEFAULT_TOP_K = 40;
	private static final float DEFAULT_TOP_P = 0.9f;
	private static final float DEFAULT_MIN_P = 0.0f;
	private static final int DEFAULT_MIROSTAT = 0;
	private static final float DEFAULT_MIROSTAT_TAU = 5.0f;
	private static final float DEFAULT_MIROSTAT_ETA = 0.1f;
	private static final int DEFAULT_SEED = -1;

	// Model storage directory name
	public static final String MODELS_DIR = "llm_models";

	// Minimum SDK required for AI features
	public static final int MIN_SDK_FOR_AI = Build.VERSION_CODES.R; // Android 11

	private final OsmandApplication app;
	private final ExecutorService executor;
	private final Handler mainHandler;

	private NativeLib nativeLib;
	private File currentModelFile;
	private String currentModelName;
	private boolean isLoading;
	private boolean isGenerating;
	private boolean isModelLoaded;

	public interface LlmCallback {
		void onPartialResult(String partialText);
		void onComplete(String fullResponse);
		void onError(String error);
	}

	public interface ModelLoadCallback {
		void onLoadStarted();
		void onLoadComplete(String modelName);
		void onLoadError(String error);
	}

	public LlmManager(@NonNull OsmandApplication app) {
		this.app = app;
		this.executor = Executors.newSingleThreadExecutor();
		this.mainHandler = new Handler(Looper.getMainLooper());
	}

	/**
	 * Check if AI features are available on this device.
	 * Requires Android 11 (API 30) or higher.
	 */
	public static boolean isAiAvailable() {
		return Build.VERSION.SDK_INT >= MIN_SDK_FOR_AI;
	}

	/**
	 * Get the directory where LLM models are stored.
	 */
	@NonNull
	public File getModelsDirectory() {
		File dir = new File(app.getAppPath(null), MODELS_DIR);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}

	/**
	 * Check if any model files exist on device.
	 */
	public boolean hasDownloadedModels() {
		File dir = getModelsDirectory();
		File[] files = dir.listFiles((d, name) -> name.endsWith(".gguf"));
		return files != null && files.length > 0;
	}

	/**
	 * List available downloaded model files.
	 */
	@NonNull
	public File[] getDownloadedModels() {
		File dir = getModelsDirectory();
		File[] files = dir.listFiles((d, name) -> name.endsWith(".gguf"));
		return files != null ? files : new File[0];
	}

	/**
	 * Load a model for inference.
	 *
	 * @param modelFile The model file to load (.gguf format)
	 * @param callback Callback for load status updates
	 */
	public void loadModel(@NonNull File modelFile, @Nullable ModelLoadCallback callback) {
		if (!isAiAvailable()) {
			if (callback != null) {
				mainHandler.post(() -> callback.onLoadError(
					"AI features require Android 11 or higher. Your device is running Android " +
					Build.VERSION.SDK_INT));
			}
			return;
		}

		if (isLoading) {
			if (callback != null) {
				mainHandler.post(() -> callback.onLoadError("Model loading already in progress"));
			}
			return;
		}

		if (!modelFile.exists()) {
			if (callback != null) {
				mainHandler.post(() -> callback.onLoadError("Model file not found: " + modelFile.getName()));
			}
			return;
		}

		isLoading = true;
		if (callback != null) {
			mainHandler.post(callback::onLoadStarted);
		}

		executor.execute(() -> {
			try {
				// Close any existing model
				closeModel();

				android.util.Log.i(TAG, "Loading model: " + modelFile.getAbsolutePath());

				// Create NativeLib instance using reflection (Kotlin private constructor)
				// The constructor signature is NativeLib(String instanceId)
				java.lang.reflect.Constructor<NativeLib> constructor =
					NativeLib.class.getDeclaredConstructor(String.class);
				constructor.setAccessible(true);
				nativeLib = constructor.newInstance("default");

				// Read inference parameters from user preferences
				int threads = app.getSettings().LAMPP_LLM_THREADS.get();
				int ctxSize = app.getSettings().LAMPP_LLM_CTX_SIZE.get();
				int temp10 = app.getSettings().LAMPP_LLM_TEMPERATURE.get();
				float temperature = temp10 / 10.0f;

				// Initialize with model path and parameters
				// Using the 7-parameter overload: (path, threads, ctxSize, temp, topK, topP, minP)
				android.util.Log.i(TAG, "Init params: threads=" + threads
					+ " ctxSize=" + ctxSize + " temperature=" + temperature);
				boolean success = nativeLib.init(
					modelFile.getAbsolutePath(),
					threads,
					ctxSize,
					temperature,
					DEFAULT_TOP_K,
					DEFAULT_TOP_P,
					DEFAULT_MIN_P
				);

				if (success) {
					isModelLoaded = true;
					currentModelFile = modelFile;
					currentModelName = modelFile.getName().replace(".gguf", "");

					android.util.Log.i(TAG, "Model loaded successfully: " + currentModelName);
					LOG.info("LLM model loaded: " + currentModelName);

					isLoading = false;
					if (callback != null) {
						String name = currentModelName;
						mainHandler.post(() -> callback.onLoadComplete(name));
					}
				} else {
					throw new Exception("NativeLib.init() returned false");
				}

			} catch (Exception e) {
				android.util.Log.e(TAG, "Failed to load model: " + e.getMessage(), e);
				LOG.error("Failed to load LLM model", e);
				isLoading = false;
				isModelLoaded = false;
				nativeLib = null;
				currentModelFile = null;
				currentModelName = null;

				if (callback != null) {
					String error = e.getMessage();
					mainHandler.post(() -> callback.onLoadError(error != null ? error : "Unknown error"));
				}
			}
		});
	}

	/**
	 * Check if a model is loaded and ready for inference.
	 */
	public boolean isModelLoaded() {
		return isModelLoaded && nativeLib != null;
	}

	/**
	 * Check if currently loading a model.
	 */
	public boolean isLoading() {
		return isLoading;
	}

	/**
	 * Check if currently generating a response.
	 */
	public boolean isGenerating() {
		return isGenerating;
	}

	/**
	 * Get the name of the currently loaded model.
	 */
	@Nullable
	public String getCurrentModelName() {
		return currentModelName;
	}

	/**
	 * Generate a response asynchronously with streaming updates.
	 *
	 * @param prompt The user's prompt/question
	 * @param callback Callback for partial and complete results
	 */
	public void generateResponseAsync(@NonNull String prompt, @NonNull LlmCallback callback) {
		if (!isAiAvailable()) {
			mainHandler.post(() -> callback.onError("AI features require Android 11 or higher"));
			return;
		}

		if (!isModelLoaded()) {
			mainHandler.post(() -> callback.onError("No model loaded. Please download and load a model first."));
			return;
		}

		if (isGenerating) {
			mainHandler.post(() -> callback.onError("Generation already in progress"));
			return;
		}

		isGenerating = true;

		executor.execute(() -> {
			try {
				android.util.Log.i(TAG, "Starting streaming generation for: " +
					prompt.substring(0, Math.min(50, prompt.length())) + "...");

				StringBuilder fullResponse = new StringBuilder();

				// Use the native streaming generation with user-configured max tokens
				int maxTokens = app.getSettings().LAMPP_LLM_MAX_TOKENS.get();
				boolean success = nativeLib.nativeGenerateStream(prompt, maxTokens, new StreamCallback() {
					@Override
					public void onToken(@NonNull String token) {
						fullResponse.append(token);
						mainHandler.post(() -> callback.onPartialResult(fullResponse.toString()));
					}

					@Override
					public void onToolCall(@NonNull String name, @NonNull String argsJson) {
						// Not used for basic chat
						android.util.Log.d(TAG, "Tool call: " + name + " args: " + argsJson);
					}

					@Override
					public void onDone() {
						isGenerating = false;
						String finalResponse = fullResponse.toString();
						android.util.Log.i(TAG, "Generation complete. Length: " + finalResponse.length());
						mainHandler.post(() -> callback.onComplete(finalResponse));
					}

					@Override
					public void onError(@NonNull String message) {
						android.util.Log.e(TAG, "Generation error: " + message);
						isGenerating = false;
						mainHandler.post(() -> callback.onError(message));
					}
				});

				if (!success) {
					isGenerating = false;
					mainHandler.post(() -> callback.onError("nativeGenerateStream returned false"));
				}

			} catch (Exception e) {
				android.util.Log.e(TAG, "Error in streaming generation: " + e.getMessage(), e);
				LOG.error("Error in LLM streaming generation", e);
				isGenerating = false;
				String error = e.getMessage();
				mainHandler.post(() -> callback.onError(error != null ? error : "Unknown error"));
			}
		});
	}

	/**
	 * Stop the current generation.
	 */
	public void stopGeneration() {
		if (nativeLib != null && isGenerating) {
			try {
				nativeLib.stop();
			} catch (Exception e) {
				LOG.error("Error stopping generation", e);
			}
		}
	}

	/**
	 * Close the currently loaded model and free resources.
	 */
	public void closeModel() {
		if (nativeLib != null) {
			try {
				nativeLib.nativeRelease();
			} catch (Exception e) {
				LOG.error("Error releasing NativeLib", e);
			}
			nativeLib = null;
		}
		isModelLoaded = false;
		currentModelFile = null;
		currentModelName = null;
	}

	/**
	 * Delete a downloaded model file.
	 *
	 * @param modelFile The model file to delete
	 * @return true if deleted successfully
	 */
	public boolean deleteModel(@NonNull File modelFile) {
		// If this is the currently loaded model, close it first
		if (modelFile.equals(currentModelFile)) {
			closeModel();
		}

		boolean deleted = modelFile.delete();
		if (deleted) {
			LOG.info("Deleted model: " + modelFile.getName());
		} else {
			LOG.error("Failed to delete model: " + modelFile.getName());
		}
		return deleted;
	}

	/**
	 * Get the total size of all downloaded models in bytes.
	 */
	public long getTotalModelsSize() {
		long total = 0;
		for (File file : getDownloadedModels()) {
			total += file.length();
		}
		return total;
	}

	@Override
	public void close() {
		closeModel();
		executor.shutdown();
	}
}
