package net.osmand.plus.lampp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.ai.DeviceCapabilityDetector;
import net.osmand.plus.ai.LlmManager;
import net.osmand.plus.ai.rag.RagManager;
import net.osmand.plus.lampp.FeatureAvailability.AvailabilityResult;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-flight readiness checker for demo recording.
 * Validates that all Rushlight subsystems are in a demo-ready state
 * and provides fix actions where possible.
 */
public class DemoPreflightChecker {

	private static final Log LOG = PlatformUtil.getLog(DemoPreflightChecker.class);

	public enum CheckStatus {
		PASS,   // Green check — all good
		WARN,   // Amber warning — demo will work but with limitations
		FAIL    // Red X — critical failure, demo should not proceed
	}

	public static class CheckResult {
		@NonNull public final String name;
		@NonNull public final CheckStatus status;
		@NonNull public final String message;
		@Nullable public final Runnable fixAction;
		public final boolean critical;

		public CheckResult(@NonNull String name, @NonNull CheckStatus status,
		                   @NonNull String message, @Nullable Runnable fixAction,
		                   boolean critical) {
			this.name = name;
			this.status = status;
			this.message = message;
			this.fixAction = fixAction;
			this.critical = critical;
		}
	}

	public static class PreflightReport {
		@NonNull public final List<CheckResult> checks;
		public final boolean allCriticalPassed;
		public final int passCount;
		public final int warnCount;
		public final int failCount;

		public PreflightReport(@NonNull List<CheckResult> checks) {
			this.checks = checks;
			int pass = 0, warn = 0, fail = 0;
			boolean criticalOk = true;
			for (CheckResult check : checks) {
				switch (check.status) {
					case PASS: pass++; break;
					case WARN: warn++; break;
					case FAIL:
						fail++;
						if (check.critical) criticalOk = false;
						break;
				}
			}
			this.passCount = pass;
			this.warnCount = warn;
			this.failCount = fail;
			this.allCriticalPassed = criticalOk;
		}
	}

	/**
	 * Run all pre-flight checks and return a report.
	 * Call from main thread (some checks access UI managers).
	 */
	@NonNull
	public PreflightReport runChecks(@NonNull OsmandApplication app) {
		List<CheckResult> results = new ArrayList<>();
		results.add(checkLlmModel(app));
		results.add(checkWikipediaZim(app));
		results.add(checkGuides(app));
		results.add(checkGps(app));
		results.add(checkCameraFlash(app));
		results.add(checkP2pTransport(app));
		results.add(checkPermissions(app));
		results.add(checkBattery(app));
		return new PreflightReport(results);
	}

	// ---- Individual Checks ----

	@NonNull
	private CheckResult checkLlmModel(@NonNull OsmandApplication app) {
		String name = app.getString(R.string.demo_preflight_check_model);
		LlmManager llm = new LlmManager(app);

		if (llm.isModelLoaded()) {
			return new CheckResult(name, CheckStatus.PASS,
					app.getString(R.string.demo_model_loaded, llm.getCurrentModelName()),
					null, true);
		}

		if (llm.hasDownloadedModels()) {
			return new CheckResult(name, CheckStatus.FAIL,
					app.getString(R.string.demo_model_available),
					null, true);  // Fix action set by dialog (needs callback)
		}

		return new CheckResult(name, CheckStatus.FAIL,
				app.getString(R.string.demo_model_missing),
				null, true);
	}

	@NonNull
	private CheckResult checkWikipediaZim(@NonNull OsmandApplication app) {
		String name = app.getString(R.string.demo_preflight_check_wiki);
		LlmManager llm = new LlmManager(app);
		RagManager rag = new RagManager(app, llm);

		if (rag.isWikipediaAvailable()) {
			String title = rag.getWikipediaTitle();
			return new CheckResult(name, CheckStatus.PASS,
					app.getString(R.string.demo_wiki_loaded, title != null ? title : "Wikipedia"),
					null, true);
		}

		return new CheckResult(name, CheckStatus.FAIL,
				app.getString(R.string.demo_wiki_missing),
				null, true);
	}

	@NonNull
	private CheckResult checkGuides(@NonNull OsmandApplication app) {
		String name = app.getString(R.string.demo_preflight_check_guides);

		if (app.getGuideManager().isLoaded()) {
			int count = app.getGuideManager().getGuideCount();
			return new CheckResult(name, CheckStatus.PASS,
					app.getString(R.string.demo_guides_loaded, count),
					null, false);
		}

		return new CheckResult(name, CheckStatus.WARN,
				app.getString(R.string.demo_guides_loading),
				() -> app.getGuideManager().loadGuidesSync(),
				false);
	}

	@NonNull
	private CheckResult checkGps(@NonNull OsmandApplication app) {
		String name = app.getString(R.string.demo_preflight_check_gps);

		try {
			LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
			if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				return new CheckResult(name, CheckStatus.PASS,
						app.getString(R.string.demo_gps_available),
						null, false);
			}
		} catch (Exception e) {
			LOG.warn("GPS check failed: " + e.getMessage());
		}

		return new CheckResult(name, CheckStatus.WARN,
				app.getString(R.string.demo_gps_unavailable),
				null, false);
	}

	@NonNull
	private CheckResult checkCameraFlash(@NonNull OsmandApplication app) {
		String name = app.getString(R.string.demo_preflight_check_flash);
		FeatureAvailability features = new FeatureAvailability(app);
		AvailabilityResult result = features.checkMorseFlashlight();

		if (result.available) {
			return new CheckResult(name, CheckStatus.PASS,
					app.getString(R.string.demo_flash_available),
					null, false);
		}

		return new CheckResult(name, CheckStatus.WARN,
				app.getString(R.string.demo_flash_unavailable),
				null, false);
	}

	@NonNull
	private CheckResult checkP2pTransport(@NonNull OsmandApplication app) {
		String name = app.getString(R.string.demo_preflight_check_p2p);
		FeatureAvailability features = new FeatureAvailability(app);
		AvailabilityResult ble = features.checkBlePeerDiscovery();
		AvailabilityResult wifi = features.checkWifiDirect();

		if (ble.available && wifi.available) {
			return new CheckResult(name, CheckStatus.PASS,
					app.getString(R.string.demo_p2p_available),
					null, false);
		}

		if (ble.available || wifi.available) {
			return new CheckResult(name, CheckStatus.WARN,
					app.getString(R.string.demo_p2p_partial),
					null, false);
		}

		return new CheckResult(name, CheckStatus.WARN,
				app.getString(R.string.demo_p2p_unavailable),
				null, false);
	}

	@NonNull
	private CheckResult checkPermissions(@NonNull OsmandApplication app) {
		String name = app.getString(R.string.demo_preflight_check_perms);

		List<String> missingPerms = new ArrayList<>();
		String[] desiredPerms = {
				Manifest.permission.CAMERA,
				Manifest.permission.ACCESS_FINE_LOCATION,
				Manifest.permission.ACCESS_COARSE_LOCATION,
		};

		// Add Bluetooth permissions for API 31+
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			String[] btPerms = {
					Manifest.permission.BLUETOOTH_SCAN,
					Manifest.permission.BLUETOOTH_ADVERTISE,
					Manifest.permission.BLUETOOTH_CONNECT,
					Manifest.permission.NEARBY_WIFI_DEVICES
			};
			for (String p : btPerms) {
				if (ContextCompat.checkSelfPermission(app, p)
						!= PackageManager.PERMISSION_GRANTED) {
					missingPerms.add(p);
				}
			}
		}

		for (String p : desiredPerms) {
			if (ContextCompat.checkSelfPermission(app, p)
					!= PackageManager.PERMISSION_GRANTED) {
				missingPerms.add(p);
			}
		}

		if (missingPerms.isEmpty()) {
			return new CheckResult(name, CheckStatus.PASS,
					app.getString(R.string.demo_perms_granted),
					null, false);
		}

		return new CheckResult(name, CheckStatus.WARN,
				app.getString(R.string.demo_perms_missing, missingPerms.size()),
				null, false);
	}

	@NonNull
	private CheckResult checkBattery(@NonNull OsmandApplication app) {
		String name = app.getString(R.string.demo_preflight_check_battery);
		DeviceCapabilityDetector detector = new DeviceCapabilityDetector(app);
		int level = detector.getBatteryLevel();

		if (level >= 20) {
			return new CheckResult(name, CheckStatus.PASS,
					app.getString(R.string.demo_battery_good, level),
					null, false);
		}

		return new CheckResult(name, CheckStatus.WARN,
				app.getString(R.string.demo_battery_low, level),
				null, false);
	}
}
