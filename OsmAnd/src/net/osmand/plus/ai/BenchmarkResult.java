package net.osmand.plus.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * v0.8: Data class for a single benchmark query result.
 * Tracks timing, memory, and device context for performance evaluation
 * against OTF roadmap targets.
 */
public class BenchmarkResult {

	// OTF roadmap performance targets
	public static final long TARGET_QUERY_TIME_MS = 3000;
	public static final long TARGET_MEMORY_MB = 500;

	public enum QueryCategory {
		CONVERSATIONAL("Conversational"),
		WIKIPEDIA_RAG("Wikipedia RAG"),
		SURVIVAL_GUIDE("Survival Guide"),
		DIRECTION("Direction");

		private final String displayName;

		QueryCategory(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return displayName;
		}
	}

	private final String queryText;
	private final QueryCategory category;
	private final long totalTimeMs;
	private final long firstTokenTimeMs;
	private final int tokensGenerated;
	private final float tokensPerSecond;
	private final long peakMemoryMb;
	private final String deviceTier;
	private final String modelName;
	private final long timestamp;

	public BenchmarkResult(@NonNull String queryText, @NonNull QueryCategory category,
	                       long totalTimeMs, long firstTokenTimeMs,
	                       int tokensGenerated, float tokensPerSecond,
	                       long peakMemoryMb, @NonNull String deviceTier,
	                       @NonNull String modelName) {
		this.queryText = queryText;
		this.category = category;
		this.totalTimeMs = totalTimeMs;
		this.firstTokenTimeMs = firstTokenTimeMs;
		this.tokensGenerated = tokensGenerated;
		this.tokensPerSecond = tokensPerSecond;
		this.peakMemoryMb = peakMemoryMb;
		this.deviceTier = deviceTier;
		this.modelName = modelName;
		this.timestamp = System.currentTimeMillis();
	}

	public String getQueryText() { return queryText; }
	public QueryCategory getCategory() { return category; }
	public long getTotalTimeMs() { return totalTimeMs; }
	public long getFirstTokenTimeMs() { return firstTokenTimeMs; }
	public int getTokensGenerated() { return tokensGenerated; }
	public float getTokensPerSecond() { return tokensPerSecond; }
	public long getPeakMemoryMb() { return peakMemoryMb; }
	public String getDeviceTier() { return deviceTier; }
	public String getModelName() { return modelName; }
	public long getTimestamp() { return timestamp; }

	public boolean passesTimeTarget() {
		return totalTimeMs <= TARGET_QUERY_TIME_MS;
	}

	public boolean passesMemoryTarget() {
		return peakMemoryMb <= TARGET_MEMORY_MB;
	}

	@NonNull
	public String getTimeSummary() {
		String timeStr = String.format("%.1fs", totalTimeMs / 1000.0);
		String pass = passesTimeTarget() ? "PASS" : "FAIL";
		return category.getDisplayName() + ": " + timeStr + "  " + pass;
	}

	@NonNull
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("query", queryText);
		json.put("category", category.name());
		json.put("totalTimeMs", totalTimeMs);
		json.put("firstTokenTimeMs", firstTokenTimeMs);
		json.put("tokensGenerated", tokensGenerated);
		json.put("tokensPerSecond", tokensPerSecond);
		json.put("peakMemoryMb", peakMemoryMb);
		json.put("deviceTier", deviceTier);
		json.put("modelName", modelName);
		json.put("timestamp", timestamp);
		json.put("passesTime", passesTimeTarget());
		json.put("passesMemory", passesMemoryTarget());
		return json;
	}
}
