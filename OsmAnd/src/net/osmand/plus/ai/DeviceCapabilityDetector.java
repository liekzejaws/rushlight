/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

import java.io.File;

/**
 * Detects device hardware capabilities and classifies into performance tiers.
 *
 * Used to:
 * - Recommend appropriate LLM models
 * - Configure thread count, context window size, and max tokens
 * - Manage battery-aware throttling
 * - Prevent OOM on low-end devices
 *
 * Tiers:
 * - LOW: ≤4GB RAM, ≤4 cores → small models only, conservative settings
 * - MEDIUM: 4-8GB RAM, 4-8 cores → most models, balanced settings
 * - HIGH: >8GB RAM, >6 cores → all models, aggressive settings
 */
public class DeviceCapabilityDetector {

	private static final Log LOG = PlatformUtil.getLog(DeviceCapabilityDetector.class);

	public enum DeviceTier {
		MINIMAL("Minimal"),
		LOW("Low"),
		MEDIUM("Medium"),
		HIGH("High");

		private final String displayName;

		DeviceTier(String displayName) {
			this.displayName = displayName;
		}

		@NonNull
		public String getDisplayName() {
			return displayName;
		}
	}

	/**
	 * Model suitability for a given device tier.
	 */
	public enum ModelSuitability {
		RECOMMENDED("Recommended"),
		POSSIBLE("Possible"),
		NOT_RECOMMENDED("Not recommended");

		private final String displayName;

		ModelSuitability(String displayName) {
			this.displayName = displayName;
		}

		@NonNull
		public String getDisplayName() {
			return displayName;
		}
	}

	private final OsmandApplication app;

	// Cached values
	private long totalRamMb = -1;
	private long availableRamMb = -1;
	private int cpuCores = -1;
	private DeviceTier cachedTier = null;

	public DeviceCapabilityDetector(@NonNull OsmandApplication app) {
		this.app = app;
	}

	// ---- Hardware Detection ----

	/**
	 * Get total device RAM in megabytes.
	 */
	public long getTotalRamMb() {
		if (totalRamMb < 0) {
			ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
			if (am != null) {
				ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
				am.getMemoryInfo(memInfo);
				totalRamMb = memInfo.totalMem / (1024 * 1024);
			} else {
				totalRamMb = 2048; // Conservative fallback
			}
		}
		return totalRamMb;
	}

	/**
	 * Get currently available RAM in megabytes.
	 */
	public long getAvailableRamMb() {
		ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
		if (am != null) {
			ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
			am.getMemoryInfo(memInfo);
			availableRamMb = memInfo.availMem / (1024 * 1024);
		}
		return availableRamMb;
	}

	/**
	 * Get number of CPU cores.
	 */
	public int getCpuCores() {
		if (cpuCores < 0) {
			cpuCores = Runtime.getRuntime().availableProcessors();
		}
		return cpuCores;
	}

	/**
	 * Get available storage space in megabytes on the primary external storage.
	 */
	public long getAvailableStorageMb() {
		try {
			File path = Environment.getDataDirectory();
			StatFs stat = new StatFs(path.getAbsolutePath());
			long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
			return availableBytes / (1024 * 1024);
		} catch (Exception e) {
			LOG.warn("Failed to get storage info", e);
			return 0;
		}
	}

	/**
	 * Get available storage in the app's specific external files directory.
	 */
	public long getAvailableAppStorageMb() {
		try {
			File appDir = app.getExternalFilesDir(null);
			if (appDir != null) {
				StatFs stat = new StatFs(appDir.getAbsolutePath());
				long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
				return availableBytes / (1024 * 1024);
			}
		} catch (Exception e) {
			LOG.warn("Failed to get app storage info", e);
		}
		return getAvailableStorageMb(); // Fallback
	}

	/**
	 * Get current battery level as percentage (0-100).
	 */
	public int getBatteryLevel() {
		try {
			IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent batteryStatus = app.registerReceiver(null, filter);
			if (batteryStatus != null) {
				int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				if (level >= 0 && scale > 0) {
					return (int) ((level * 100.0f) / scale);
				}
			}
		} catch (Exception e) {
			LOG.warn("Failed to get battery level", e);
		}
		return 100; // Assume full if unknown
	}

	/**
	 * Check if the device is currently charging.
	 */
	public boolean isCharging() {
		try {
			IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
			Intent batteryStatus = app.registerReceiver(null, filter);
			if (batteryStatus != null) {
				int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
				return status == BatteryManager.BATTERY_STATUS_CHARGING
						|| status == BatteryManager.BATTERY_STATUS_FULL;
			}
		} catch (Exception e) {
			LOG.warn("Failed to get charging status", e);
		}
		return false;
	}

	/**
	 * Check thermal throttling state.
	 * Returns true if device is thermally throttled (API 29+).
	 */
	public boolean isThermallyThrottled() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			try {
				PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
				if (pm != null) {
					int thermalStatus = pm.getCurrentThermalStatus();
					return thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE;
				}
			} catch (Exception e) {
				LOG.warn("Failed to get thermal status", e);
			}
		}
		return false;
	}

	// ---- Device Classification ----

	/**
	 * Classify this device into a performance tier.
	 */
	@NonNull
	public DeviceTier getDeviceTier() {
		if (cachedTier != null) {
			return cachedTier;
		}

		long ramMb = getTotalRamMb();
		int cores = getCpuCores();

		if (ramMb <= 3072 || cores <= 2) {
			cachedTier = DeviceTier.MINIMAL;
		} else if (ramMb <= 4096 || cores <= 4) {
			cachedTier = DeviceTier.LOW;
		} else if (ramMb <= 8192 || cores <= 6) {
			cachedTier = DeviceTier.MEDIUM;
		} else {
			cachedTier = DeviceTier.HIGH;
		}

		LOG.info("Device tier: " + cachedTier + " (RAM=" + ramMb + "MB, cores=" + cores + ")");
		return cachedTier;
	}

	// ---- LLM Configuration Recommendations ----

	/**
	 * Get recommended thread count for LLM inference.
	 * Factors in device tier, battery level, and thermal state.
	 */
	public int getRecommendedThreadCount() {
		int baseThreads;
		switch (getDeviceTier()) {
			case MINIMAL:
				baseThreads = 1;
				break;
			case LOW:
				baseThreads = 2;
				break;
			case MEDIUM:
				baseThreads = 4;
				break;
			case HIGH:
			default:
				baseThreads = 6;
				break;
		}

		// Throttle on low battery
		if (!isCharging() && getBatteryLevel() < 15) {
			baseThreads = Math.min(baseThreads, 2);
		}

		// Throttle on thermal stress
		if (isThermallyThrottled()) {
			baseThreads = Math.max(2, baseThreads - 2);
		}

		return baseThreads;
	}

	/**
	 * Get recommended context window size for LLM.
	 */
	public int getRecommendedContextSize() {
		switch (getDeviceTier()) {
			case MINIMAL:
				return 1024;
			case LOW:
				return 2048;
			case MEDIUM:
				return 4096;
			case HIGH:
			default:
				return 8192;
		}
	}

	/**
	 * Get recommended max tokens for generation.
	 * Factors in battery level.
	 */
	public int getRecommendedMaxTokens() {
		int baseTokens;
		switch (getDeviceTier()) {
			case MINIMAL:
				baseTokens = 256;
				break;
			case LOW:
				baseTokens = 512;
				break;
			case MEDIUM:
				baseTokens = 1024;
				break;
			case HIGH:
			default:
				baseTokens = 2048;
				break;
		}

		// Reduce on low battery
		if (!isCharging() && getBatteryLevel() < 15) {
			baseTokens = Math.min(baseTokens, 256);
		}

		return baseTokens;
	}

	/**
	 * Get inference timeout in seconds based on device tier.
	 */
	public int getInferenceTimeoutSeconds() {
		switch (getDeviceTier()) {
			case MINIMAL:
				return 90;   // 1.5 minutes
			case LOW:
				return 120;  // 2 minutes
			case MEDIUM:
				return 180;  // 3 minutes
			case HIGH:
			default:
				return 300;  // 5 minutes
		}
	}

	/**
	 * Check if inference should be disabled due to critically low battery.
	 */
	public boolean shouldDisableInference() {
		return !isCharging() && getBatteryLevel() < 5;
	}

	/**
	 * Check if inference should be throttled due to low battery.
	 */
	public boolean shouldThrottleInference() {
		return (!isCharging() && getBatteryLevel() < 15) || isThermallyThrottled();
	}

	/**
	 * Check if a model of given size (in MB) is suitable for this device.
	 */
	@NonNull
	public ModelSuitability getModelSuitability(long modelSizeMb) {
		long ramMb = getTotalRamMb();

		// Rule of thumb: model needs ~1.5x its file size in RAM when loaded
		long estimatedRamNeeded = (long) (modelSizeMb * 1.5);

		// Check if device has enough RAM (need at least model + 1.5GB for OS/app)
		long ramBudget = ramMb - 1536; // Reserve 1.5GB for OS

		if (estimatedRamNeeded > ramBudget) {
			return ModelSuitability.NOT_RECOMMENDED;
		}

		// For LOW tier, only small models
		if (getDeviceTier() == DeviceTier.LOW && modelSizeMb > 1000) {
			return ModelSuitability.NOT_RECOMMENDED;
		}

		// For MEDIUM tier, warn on large models
		if (getDeviceTier() == DeviceTier.MEDIUM && modelSizeMb > 2500) {
			return ModelSuitability.POSSIBLE;
		}

		return ModelSuitability.RECOMMENDED;
	}

	/**
	 * Check if there's enough storage space for a download of given size (in MB).
	 *
	 * @param sizeMb The size of the file to download in MB
	 * @param bufferMb Additional buffer space required (for temp files, etc.)
	 * @return true if sufficient space is available
	 */
	public boolean hasEnoughStorage(long sizeMb, long bufferMb) {
		return getAvailableAppStorageMb() >= (sizeMb + bufferMb);
	}

	/**
	 * Get a human-readable summary of device capabilities.
	 */
	@NonNull
	public String getDeviceSummary() {
		return "RAM: " + getTotalRamMb() + "MB, "
				+ "Cores: " + getCpuCores() + ", "
				+ "Tier: " + getDeviceTier().getDisplayName() + ", "
				+ "Storage: " + getAvailableAppStorageMb() + "MB free, "
				+ "Battery: " + getBatteryLevel() + "%"
				+ (isCharging() ? " (charging)" : "");
	}

	/**
	 * v1.4: Check if this device can run AI features at all.
	 * Returns false for devices with <3GB RAM (even if API 30+).
	 * These devices can still use maps, wiki, morse, etc.
	 */
	public boolean isAiCapable() {
		return getTotalRamMb() >= 3072 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
	}

	/**
	 * v1.4: Get a detailed reason why AI is not available, or null if it is.
	 */
	@Nullable
	public String getAiUnavailableReason() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			return "AI features require Android 11 or higher (current: Android "
					+ Build.VERSION.RELEASE + ", API " + Build.VERSION.SDK_INT + ")";
		}
		if (getTotalRamMb() < 3072) {
			return "AI features require at least 3 GB RAM (this device has "
					+ getTotalRamMb() + " MB). Maps, Wikipedia, Morse code, and P2P sharing still work.";
		}
		return null;
	}

	/**
	 * v1.4: Generate a structured compatibility report for grant testing matrix.
	 * Includes device model, Android version, RAM, CPU cores, tier, AI capability,
	 * and available storage.
	 */
	@NonNull
	public String getCompatibilityReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
		sb.append("Android: ").append(Build.VERSION.RELEASE)
				.append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
		sb.append("RAM: ").append(getTotalRamMb()).append(" MB total, ")
				.append(getAvailableRamMb()).append(" MB available\n");
		sb.append("CPU Cores: ").append(getCpuCores()).append("\n");
		sb.append("Device Tier: ").append(getDeviceTier().getDisplayName()).append("\n");
		sb.append("AI Capable: ").append(isAiCapable() ? "Yes" : "No");
		String reason = getAiUnavailableReason();
		if (reason != null) {
			sb.append(" (").append(reason).append(")");
		}
		sb.append("\n");
		sb.append("Storage: ").append(getAvailableAppStorageMb()).append(" MB free\n");
		sb.append("Battery: ").append(getBatteryLevel()).append("%")
				.append(isCharging() ? " (charging)" : "").append("\n");
		sb.append("Thermal: ").append(isThermallyThrottled() ? "Throttled" : "Normal").append("\n");

		// LLM recommendations
		sb.append("\nLLM Configuration:\n");
		sb.append("  Threads: ").append(getRecommendedThreadCount()).append("\n");
		sb.append("  Context: ").append(getRecommendedContextSize()).append("\n");
		sb.append("  Max Tokens: ").append(getRecommendedMaxTokens()).append("\n");
		sb.append("  Timeout: ").append(getInferenceTimeoutSeconds()).append("s\n");

		return sb.toString();
	}
}
