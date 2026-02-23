package net.osmand.plus.ai;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

/**
 * v1.4: Lightweight startup profiler for measuring cold start time.
 * Records timestamps at key milestones during app startup.
 * Results consumed by PerformanceBenchmark and DeviceTestReportDialog
 * for OTF grant Month 1 reporting (target: cold start < 5s).
 *
 * Usage: Call milestone() at key points in the startup path.
 * Call finish() when the app is usable (map rendered).
 */
public class StartupProfiler {

	private static final Log LOG = PlatformUtil.getLog(StartupProfiler.class);

	private static StartupProfiler instance;

	// Milestone timestamps (0 = not recorded)
	private long appCreateTime;
	private long activityCreateTime;
	private long activityCreateEndTime;
	private long panelInitTime;
	private long mapReadyTime;
	private long finishTime;

	private final long startTime;
	private boolean finished;

	// Latest result for consumption by benchmark/device report
	private static long lastColdStartMs = -1;
	private static String lastSummary;

	private StartupProfiler() {
		this.startTime = System.currentTimeMillis();
	}

	/**
	 * Begin a new startup profiling session.
	 * Call this at the very beginning of Application.onCreate().
	 */
	public static synchronized StartupProfiler begin() {
		instance = new StartupProfiler();
		instance.appCreateTime = System.currentTimeMillis();
		return instance;
	}

	/**
	 * Get the current profiling session (may be null if not started).
	 */
	public static synchronized StartupProfiler get() {
		return instance;
	}

	/**
	 * Record the MapActivity.onCreate() start.
	 */
	public void markActivityCreate() {
		activityCreateTime = System.currentTimeMillis();
	}

	/**
	 * Record the MapActivity.onCreate() end.
	 */
	public void markActivityCreateEnd() {
		activityCreateEndTime = System.currentTimeMillis();
	}

	/**
	 * Record the LamppPanelManager initialization.
	 */
	public void markPanelInit() {
		panelInitTime = System.currentTimeMillis();
	}

	/**
	 * Record when the map is ready and the app is usable.
	 * This is the end of the cold start window.
	 */
	public void markMapReady() {
		if (finished) return;
		mapReadyTime = System.currentTimeMillis();
		finish();
	}

	/**
	 * Finish profiling and log the summary.
	 */
	private void finish() {
		if (finished) return;
		finished = true;
		finishTime = System.currentTimeMillis();

		long totalMs = finishTime - startTime;
		lastColdStartMs = totalMs;

		StringBuilder sb = new StringBuilder("Rushlight startup: ");
		sb.append(totalMs).append("ms (");

		if (activityCreateTime > 0) {
			sb.append("App=").append(activityCreateTime - startTime).append("ms");
		}
		if (activityCreateEndTime > 0 && activityCreateTime > 0) {
			sb.append(", Activity=").append(activityCreateEndTime - activityCreateTime).append("ms");
		}
		if (panelInitTime > 0 && activityCreateEndTime > 0) {
			sb.append(", Panel=").append(panelInitTime - activityCreateEndTime).append("ms");
		}
		if (mapReadyTime > 0) {
			long mapTime = mapReadyTime - (panelInitTime > 0 ? panelInitTime
					: (activityCreateEndTime > 0 ? activityCreateEndTime : startTime));
			sb.append(", Map=").append(mapTime).append("ms");
		}
		sb.append(")");

		lastSummary = sb.toString();
		LOG.info(lastSummary);
		android.util.Log.i("StartupProfiler", lastSummary);
	}

	/**
	 * Get the last recorded cold start time in milliseconds.
	 * Returns -1 if no measurement is available.
	 */
	public static long getLastColdStartMs() {
		return lastColdStartMs;
	}

	/**
	 * Get the last recorded startup summary string.
	 */
	public static String getLastSummary() {
		return lastSummary;
	}

	/**
	 * Get the total elapsed time so far (or final time if finished).
	 */
	public long getElapsedMs() {
		if (finished) {
			return finishTime - startTime;
		}
		return System.currentTimeMillis() - startTime;
	}
}
