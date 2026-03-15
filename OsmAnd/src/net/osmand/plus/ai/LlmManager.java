/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
	private final ScheduledExecutorService timeoutScheduler;
	private final Handler mainHandler;
	private final DeviceCapabilityDetector deviceDetector;

	private NativeLib nativeLib;
	private File currentModelFile;
	private String currentModelName;
	private boolean isLoading;
	private boolean isGenerating;
	private boolean isModelLoaded;
	private ScheduledFuture<?> inferenceTimeoutFuture;
	private final AtomicBoolean inferenceTimedOut = new AtomicBoolean(false);

	// Adaptive performance tracking
	private float lastTokensPerSecond = -1;
	private long lastFirstTokenTimeMs = -1;

	// v0.9: Crisis Mode max_tokens override (null = use normal logic)
	@Nullable
	private Integer maxTokensOverride = null;

	// Phase 3: LLM tool dispatch for FieldNotes integration
	@Nullable
	private ToolDispatcher toolDispatcher;

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
		this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
		this.mainHandler = new Handler(Looper.getMainLooper());
		this.deviceDetector = new DeviceCapabilityDetector(app);

		LOG.info("LlmManager initialized. " + deviceDetector.getDeviceSummary());
	}

	/**
	 * Set the tool dispatcher for LLM tool call integration.
	 * Phase 3: FieldNotes tools (query_fieldnotes, create_fieldnote).
	 */
	public void setToolDispatcher(@Nullable ToolDispatcher dispatcher) {
		this.toolDispatcher = dispatcher;
	}

	/**
	 * Get the device capability detector for this device.
	 */
	@NonNull
	public DeviceCapabilityDetector getDeviceDetector() {
		return deviceDetector;
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

				// Check if device has enough RAM for this model
				long modelSizeMb = modelFile.length() / (1024 * 1024);
				long availRam = deviceDetector.getAvailableRamMb();
				long estimatedNeed = (long) (modelSizeMb * 1.5);
				if (availRam > 0 && estimatedNeed > availRam) {
					LOG.warn("Low available RAM (" + availRam + "MB) for model (" + modelSizeMb
							+ "MB). May cause OOM.");
				}

				// Create NativeLib instance using reflection (Kotlin private constructor)
				// The constructor signature is NativeLib(String instanceId)
				java.lang.reflect.Constructor<NativeLib> constructor =
					NativeLib.class.getDeclaredConstructor(String.class);
				constructor.setAccessible(true);
				nativeLib = constructor.newInstance("default");

				// Read inference parameters from user preferences, with device-aware defaults
				int userThreads = app.getSettings().LAMPP_LLM_THREADS.get();
				int userCtxSize = app.getSettings().LAMPP_LLM_CTX_SIZE.get();

				// Use device-recommended values if user hasn't customized (i.e., using defaults)
				int threads = (userThreads == DEFAULT_THREADS)
						? deviceDetector.getRecommendedThreadCount() : userThreads;
				int ctxSize = (userCtxSize == DEFAULT_CTX_SIZE)
						? deviceDetector.getRecommendedContextSize() : userCtxSize;

				int temp10 = app.getSettings().LAMPP_LLM_TEMPERATURE.get();
				float temperature = temp10 / 10.0f;

				// Initialize with model path and parameters
				// Using the 7-parameter overload: (path, threads, ctxSize, temp, topK, topP, minP)
				android.util.Log.i(TAG, "Init params: threads=" + threads
					+ " ctxSize=" + ctxSize + " temperature=" + temperature
					+ " (tier=" + deviceDetector.getDeviceTier() + ")");
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
	 * Pre-warm the LLM by loading the first available model if none is loaded.
	 * Used by the demo pre-flight system and auto-load on AI Chat open.
	 *
	 * @param callback Optional callback for load status updates
	 */
	public void preWarmIfNeeded(@Nullable ModelLoadCallback callback) {
		if (isModelLoaded()) {
			if (callback != null) {
				mainHandler.post(() -> callback.onLoadComplete(getCurrentModelName()));
			}
			return;
		}

		if (isLoading) {
			if (callback != null) {
				mainHandler.post(() -> callback.onLoadStarted());
			}
			return;
		}

		File[] models = getDownloadedModels();
		if (models.length > 0) {
			LOG.info("Pre-warming LLM with model: " + models[0].getName());
			loadModel(models[0], callback);
		} else {
			if (callback != null) {
				mainHandler.post(() -> callback.onLoadError("No models downloaded"));
			}
		}
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

		// Battery check: disable inference on critically low battery
		if (deviceDetector.shouldDisableInference()) {
			mainHandler.post(() -> callback.onError(
					"Battery critically low (" + deviceDetector.getBatteryLevel()
							+ "%). Please charge device before using AI."));
			return;
		}

		isGenerating = true;
		inferenceTimedOut.set(false);

		executor.execute(() -> {
			try {
				android.util.Log.i(TAG, "Starting streaming generation for: " +
					prompt.substring(0, Math.min(50, prompt.length())) + "...");

				StringBuilder fullResponse = new StringBuilder();
				final long startTime = System.currentTimeMillis();
				final int[] tokenCount = {0};

				// Use device-aware max tokens, with battery throttling and crisis override
				int maxTokens;
				if (maxTokensOverride != null) {
					// v0.9: Crisis Mode or external override
					maxTokens = maxTokensOverride;
					// Still respect battery throttling
					if (deviceDetector.shouldThrottleInference()) {
						maxTokens = Math.min(maxTokens, deviceDetector.getRecommendedMaxTokens());
					}
				} else {
					int userMaxTokens = app.getSettings().LAMPP_LLM_MAX_TOKENS.get();
					int deviceMaxTokens = deviceDetector.getRecommendedMaxTokens();
					maxTokens = deviceDetector.shouldThrottleInference()
							? Math.min(userMaxTokens, deviceMaxTokens) : userMaxTokens;
				}

				if (deviceDetector.shouldThrottleInference()) {
					LOG.info("Inference throttled: battery=" + deviceDetector.getBatteryLevel()
							+ "%, maxTokens capped at " + maxTokens);
				}

				// Set up inference timeout
				int timeoutSec = deviceDetector.getInferenceTimeoutSeconds();
				inferenceTimeoutFuture = timeoutScheduler.schedule(() -> {
					if (isGenerating) {
						LOG.warn("Inference timeout after " + timeoutSec + " seconds, stopping generation");
						inferenceTimedOut.set(true);
						stopGeneration();
					}
				}, timeoutSec, TimeUnit.SECONDS);

				boolean success = nativeLib.nativeGenerateStream(prompt, maxTokens, new StreamCallback() {
					@Override
					public void onToken(@NonNull String token) {
						if (tokenCount[0] == 0) {
							lastFirstTokenTimeMs = System.currentTimeMillis() - startTime;
						}
						tokenCount[0]++;
						fullResponse.append(token);
						mainHandler.post(() -> callback.onPartialResult(fullResponse.toString()));
					}

					@Override
					public void onToolCall(@NonNull String name, @NonNull String argsJson) {
						if (toolDispatcher != null) {
							android.util.Log.d(TAG, "Dispatching tool call: " + name);
							String result = toolDispatcher.dispatch(name, argsJson);
							fullResponse.append("\n\n[Tool: ").append(name).append("]\n").append(result);
							mainHandler.post(() -> callback.onPartialResult(fullResponse.toString()));
						} else {
							android.util.Log.d(TAG, "Tool call (no dispatcher): " + name + " args: " + argsJson);
						}
					}

					@Override
					public void onDone() {
						cancelInferenceTimeout();
						isGenerating = false;

						// Track performance for adaptive context sizing
						long elapsedMs = System.currentTimeMillis() - startTime;
						if (elapsedMs > 0 && tokenCount[0] > 0) {
							lastTokensPerSecond = (tokenCount[0] * 1000.0f) / elapsedMs;
							android.util.Log.i(TAG, "Generation complete. " + tokenCount[0]
									+ " tokens in " + elapsedMs + "ms ("
									+ String.format("%.1f", lastTokensPerSecond) + " tok/s)");
						}

						if (inferenceTimedOut.get()) {
							String partial = fullResponse.toString();
							mainHandler.post(() -> callback.onComplete(
									partial + "\n\n⚠️ *Response truncated due to timeout.*"));
						} else {
							String finalResponse = fullResponse.toString();
							mainHandler.post(() -> callback.onComplete(finalResponse));
						}
					}

					@Override
					public void onError(@NonNull String message) {
						cancelInferenceTimeout();
						android.util.Log.e(TAG, "Generation error: " + message);
						isGenerating = false;
						mainHandler.post(() -> callback.onError(message));
					}
				});

				if (!success) {
					cancelInferenceTimeout();
					isGenerating = false;
					mainHandler.post(() -> callback.onError("nativeGenerateStream returned false"));
				}

			} catch (Exception e) {
				cancelInferenceTimeout();
				android.util.Log.e(TAG, "Error in streaming generation: " + e.getMessage(), e);
				LOG.error("Error in LLM streaming generation", e);
				isGenerating = false;
				String error = e.getMessage();
				mainHandler.post(() -> callback.onError(error != null ? error : "Unknown error"));
			}
		});
	}

	/**
	 * Cancel any pending inference timeout.
	 */
	private void cancelInferenceTimeout() {
		if (inferenceTimeoutFuture != null && !inferenceTimeoutFuture.isDone()) {
			inferenceTimeoutFuture.cancel(false);
			inferenceTimeoutFuture = null;
		}
	}

	/**
	 * Get the last measured inference speed in tokens per second.
	 * Returns -1 if no measurement available yet.
	 */
	public float getLastTokensPerSecond() {
		return lastTokensPerSecond;
	}

	/**
	 * Get the time to first token from the last generation in milliseconds.
	 * Returns -1 if no measurement available yet.
	 */
	public long getLastFirstTokenTimeMs() {
		return lastFirstTokenTimeMs;
	}

	/**
	 * v0.9: Set a max_tokens override that takes precedence over user settings
	 * and device-recommended values. Used by Crisis Mode to cap generation length.
	 * Pass null to clear the override and restore normal behavior.
	 */
	public void setMaxTokensOverride(@Nullable Integer override) {
		this.maxTokensOverride = override;
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
		cancelInferenceTimeout();
		closeModel();
		executor.shutdown();
		timeoutScheduler.shutdown();
	}
}
