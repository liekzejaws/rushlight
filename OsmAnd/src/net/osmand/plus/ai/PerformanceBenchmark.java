/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.ai.rag.RagCallback;
import net.osmand.plus.ai.rag.RagManager;
import net.osmand.plus.ai.rag.RagResponse;

import org.apache.commons.logging.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * v0.8: Performance benchmark runner for measuring AI query performance
 * against OTF roadmap targets (<3s queries, <500MB RAM).
 *
 * Runs predefined benchmark queries through the RAG pipeline and collects
 * timing, memory, and token metrics for each.
 */
public class PerformanceBenchmark {

	private static final Log LOG = PlatformUtil.getLog(PerformanceBenchmark.class);
	private static final String RESULTS_FILE = "benchmark_results.json";

	// Timeout per query (seconds)
	private static final int QUERY_TIMEOUT_SEC = 120;

	private static final BenchmarkQuery[] BENCHMARK_QUERIES = {
		new BenchmarkQuery(
			"What are the best practices for purifying water?",
			BenchmarkResult.QueryCategory.CONVERSATIONAL
		),
		new BenchmarkQuery(
			"Tell me about the history of radio communication",
			BenchmarkResult.QueryCategory.WIKIPEDIA_RAG
		),
		new BenchmarkQuery(
			"How do I signal for help in an emergency?",
			BenchmarkResult.QueryCategory.SURVIVAL_GUIDE
		),
		new BenchmarkQuery(
			"Which direction is the nearest city from here?",
			BenchmarkResult.QueryCategory.DIRECTION
		)
	};

	public interface BenchmarkCallback {
		void onProgress(int current, int total, String queryText);
		void onComplete(List<BenchmarkResult> results, String summary);
		void onError(String error);
	}

	private final OsmandApplication app;
	private final RagManager ragManager;
	private final LlmManager llmManager;
	private final DeviceCapabilityDetector deviceDetector;
	private final ExecutorService executor;
	private final Handler mainHandler;

	public PerformanceBenchmark(@NonNull OsmandApplication app,
	                            @NonNull RagManager ragManager,
	                            @NonNull LlmManager llmManager) {
		this.app = app;
		this.ragManager = ragManager;
		this.llmManager = llmManager;
		this.deviceDetector = llmManager.getDeviceDetector();
		this.executor = Executors.newSingleThreadExecutor();
		this.mainHandler = new Handler(Looper.getMainLooper());
	}

	/**
	 * Run all benchmark queries asynchronously.
	 */
	public void runAsync(@NonNull BenchmarkCallback callback) {
		if (!llmManager.isModelLoaded()) {
			mainHandler.post(() -> callback.onError("No AI model loaded. Please load a model first."));
			return;
		}

		executor.execute(() -> {
			try {
				runBenchmarks(callback);
			} catch (Exception e) {
				LOG.error("Benchmark failed", e);
				mainHandler.post(() -> callback.onError("Benchmark failed: " + e.getMessage()));
			}
		});
	}

	private void runBenchmarks(@NonNull BenchmarkCallback callback) {
		List<BenchmarkResult> results = new ArrayList<>();
		String deviceTier = deviceDetector.getDeviceTier().name();
		String modelName = llmManager.getCurrentModelName();
		if (modelName == null) modelName = "unknown";

		// v1.4: Record battery level before benchmark for drain estimation
		int batteryBefore = deviceDetector.getBatteryLevel();
		long benchmarkStartTime = System.currentTimeMillis();

		for (int i = 0; i < BENCHMARK_QUERIES.length; i++) {
			BenchmarkQuery bq = BENCHMARK_QUERIES[i];
			final int queryNum = i + 1;
			mainHandler.post(() -> callback.onProgress(queryNum, BENCHMARK_QUERIES.length, bq.text));

			// Measure memory before query
			Runtime runtime = Runtime.getRuntime();
			runtime.gc(); // Request GC for more accurate baseline
			long memBefore = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

			// Run the query and wait for completion
			final CountDownLatch latch = new CountDownLatch(1);
			final long[] pipelineTime = {0};
			final int[] tokens = {0};
			final boolean[] error = {false};

			long queryStart = System.currentTimeMillis();

			ragManager.queryAsync(bq.text, new RagCallback.SimpleCallback() {
				@Override
				public void onPartialResult(@NonNull String partialAnswer) {
					// Count tokens by tracking partial result changes
				}

				@Override
				public void onComplete(@NonNull RagResponse response) {
					pipelineTime[0] = response.getTotalPipelineTimeMs();
					latch.countDown();
				}

				@Override
				public void onError(@NonNull String errorMsg) {
					LOG.error("Benchmark query error: " + errorMsg);
					error[0] = true;
					latch.countDown();
				}
			});

			try {
				boolean completed = latch.await(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
				if (!completed) {
					LOG.warn("Benchmark query timed out: " + bq.text);
					llmManager.stopGeneration();
					error[0] = true;
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			long totalTime = System.currentTimeMillis() - queryStart;

			// Measure memory after query
			long memAfter = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
			long peakMemory = Math.max(memAfter, memBefore);

			// Get performance metrics from LlmManager
			float tokPerSec = llmManager.getLastTokensPerSecond();
			long firstTokenTime = llmManager.getLastFirstTokenTimeMs();

			// Use pipeline time if available, otherwise wall clock
			long reportedTime = pipelineTime[0] > 0 ? pipelineTime[0] : totalTime;

			// Estimate token count from tokens/sec * time
			int estimatedTokens = tokPerSec > 0
				? (int)(tokPerSec * (reportedTime / 1000.0f))
				: 0;

			if (!error[0]) {
				BenchmarkResult result = new BenchmarkResult(
					bq.text, bq.category,
					reportedTime, firstTokenTime,
					estimatedTokens, tokPerSec,
					peakMemory, deviceTier, modelName
				);
				results.add(result);
			} else {
				// Record failed query with timeout time
				BenchmarkResult result = new BenchmarkResult(
					bq.text, bq.category,
					totalTime, -1,
					0, 0,
					peakMemory, deviceTier, "ERROR"
				);
				results.add(result);
			}

			// Brief pause between queries to let system settle
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		// v1.4: Measure battery drain and compute hourly estimate
		int batteryAfter = deviceDetector.getBatteryLevel();
		long benchmarkDurationMs = System.currentTimeMillis() - benchmarkStartTime;
		int batteryDelta = batteryBefore - batteryAfter;
		float batteryDrainPerHour = benchmarkDurationMs > 0
				? (batteryDelta * 3600000.0f / benchmarkDurationMs) : 0;

		// v1.4: Get cold start time from profiler
		long coldStartMs = StartupProfiler.getLastColdStartMs();

		// Save results to file
		saveResults(results);

		// Build summary with all 4 OTF targets
		String summary = buildSummary(results, deviceTier, modelName,
				batteryDrainPerHour, coldStartMs);

		mainHandler.post(() -> callback.onComplete(results, summary));
	}

	@NonNull
	private String buildSummary(@NonNull List<BenchmarkResult> results,
	                            @NonNull String deviceTier, @NonNull String modelName,
	                            float batteryDrainPerHour, long coldStartMs) {
		StringBuilder sb = new StringBuilder();
		sb.append("Rushlight v1.4 Benchmark Report\n");
		sb.append("═══════════════════════════════\n");
		sb.append("Device: ").append(deviceDetector.getDeviceSummary()).append("\n");
		sb.append("Model: ").append(modelName).append("\n");
		sb.append("Android: ").append(android.os.Build.VERSION.SDK_INT)
				.append(" (").append(android.os.Build.MODEL).append(")\n\n");

		// Per-query results
		sb.append("Query Results:\n");
		long totalTime = 0;
		float totalTokSec = 0;
		long maxMemory = 0;
		int passCount = 0;
		int tokSecCount = 0;

		for (int i = 0; i < results.size(); i++) {
			BenchmarkResult r = results.get(i);
			sb.append("  ").append(i + 1).append(". ")
				.append(r.getCategory().getDisplayName()).append(": ");
			sb.append(String.format("%.1fs", r.getTotalTimeMs() / 1000.0));
			sb.append("  ").append(r.passesTimeTarget() ? "✓ PASS" : "✗ FAIL");
			sb.append("\n");

			totalTime += r.getTotalTimeMs();
			if (r.getTokensPerSecond() > 0) {
				totalTokSec += r.getTokensPerSecond();
				tokSecCount++;
			}
			maxMemory = Math.max(maxMemory, r.getPeakMemoryMb());
			if (r.passesTimeTarget()) passCount++;
		}

		// OTF Grant Targets — all 4 metrics
		sb.append("\n");
		sb.append("OTF Grant Targets:\n");
		sb.append("═══════════════════════════════\n");

		long avgTime = results.isEmpty() ? 0 : totalTime / results.size();
		float avgTokSec = tokSecCount > 0 ? totalTokSec / tokSecCount : 0;
		boolean queryPasses = avgTime <= BenchmarkResult.TARGET_QUERY_TIME_MS;
		boolean memPasses = maxMemory <= BenchmarkResult.TARGET_MEMORY_MB;
		boolean batteryPasses = batteryDrainPerHour <= BenchmarkResult.TARGET_BATTERY_PCT_HR;
		boolean coldStartPasses = coldStartMs >= 0 && coldStartMs <= BenchmarkResult.TARGET_COLD_START_MS;

		sb.append("  1. AI Query Time:   ").append(String.format("%.1fs", avgTime / 1000.0));
		sb.append(" (target <3s)       ").append(queryPasses ? "✓ PASS" : "✗ FAIL").append("\n");

		sb.append("  2. Peak RAM:        ").append(maxMemory).append("MB");
		sb.append(" (target <500MB)    ").append(memPasses ? "✓ PASS" : "✗ FAIL").append("\n");

		sb.append("  3. Battery Drain:   ").append(String.format("%.1f%%/hr", batteryDrainPerHour));
		sb.append(" (target <15%/hr)   ").append(batteryPasses ? "✓ PASS" : "✗ FAIL").append("\n");

		if (coldStartMs >= 0) {
			sb.append("  4. Cold Start:      ").append(String.format("%.1fs", coldStartMs / 1000.0));
			sb.append(" (target <5s)       ").append(coldStartPasses ? "✓ PASS" : "✗ FAIL").append("\n");
		} else {
			sb.append("  4. Cold Start:      N/A (restart app to measure)\n");
		}

		sb.append("\nTokens/sec: ").append(String.format("%.1f", avgTokSec));
		sb.append("  |  Queries passed: ").append(passCount).append("/").append(results.size());

		int totalTargetsPassed = (queryPasses ? 1 : 0) + (memPasses ? 1 : 0)
				+ (batteryPasses ? 1 : 0) + (coldStartPasses ? 1 : 0);
		sb.append("\nOverall: ").append(totalTargetsPassed).append("/4 OTF targets met");

		return sb.toString();
	}

	/**
	 * v1.4: Export benchmark results as a grant-ready markdown report.
	 */
	@NonNull
	public static String exportMarkdownReport(@NonNull String summary,
	                                          @NonNull DeviceCapabilityDetector detector) {
		StringBuilder md = new StringBuilder();
		md.append("# Rushlight Performance Benchmark Report\n\n");
		md.append("**Date:** ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
				java.util.Locale.US).format(new java.util.Date())).append("\n");
		md.append("**Device:** ").append(android.os.Build.MANUFACTURER).append(" ")
				.append(android.os.Build.MODEL).append("\n");
		md.append("**Android:** ").append(android.os.Build.VERSION.RELEASE)
				.append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
		md.append("**RAM:** ").append(detector.getTotalRamMb()).append(" MB\n");
		md.append("**CPU Cores:** ").append(detector.getCpuCores()).append("\n");
		md.append("**Device Tier:** ").append(detector.getDeviceTier().getDisplayName()).append("\n\n");
		md.append("```\n").append(summary).append("\n```\n");
		return md.toString();
	}

	private void saveResults(@NonNull List<BenchmarkResult> results) {
		try {
			JSONArray jsonArray = new JSONArray();
			for (BenchmarkResult r : results) {
				jsonArray.put(r.toJson());
			}

			JSONObject root = new JSONObject();
			root.put("benchmarkVersion", "0.8.0");
			root.put("deviceSummary", deviceDetector.getDeviceSummary());
			root.put("results", jsonArray);

			File outputFile = new File(app.getAppPath(null), RESULTS_FILE);
			try (FileWriter writer = new FileWriter(outputFile)) {
				writer.write(root.toString(2));
			}
			LOG.info("Benchmark results saved to: " + outputFile.getAbsolutePath());
		} catch (Exception e) {
			LOG.error("Failed to save benchmark results", e);
		}
	}

	/**
	 * Get the current model name from LlmManager (package-visible for BenchmarkResult).
	 */
	static String getCurrentModelName(LlmManager llm) {
		return llm.getCurrentModelName();
	}

	private static class BenchmarkQuery {
		final String text;
		final BenchmarkResult.QueryCategory category;

		BenchmarkQuery(String text, BenchmarkResult.QueryCategory category) {
			this.text = text;
			this.category = category;
		}
	}
}
