package net.osmand.plus.plugins.p2pshare;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates simulated P2P peers for video demonstration purposes.
 * Activated via long-press on the scan button in P2pShareFragment.
 * Injects realistic-looking peers without requiring BLE/WiFi Direct hardware
 * or a second device.
 */
public class P2pDemoMode {

	private static boolean active = false;
	private static final Handler handler = new Handler(Looper.getMainLooper());

	/**
	 * Create simulated demo peers with realistic names and content manifests.
	 */
	@NonNull
	public static List<DiscoveredPeer> createDemoPeers(@NonNull OsmandApplication app) {
		List<DiscoveredPeer> demoPeers = new ArrayList<>();

		// Peer 1: Reporter with maps and Wikipedia
		DiscoveredPeer reporter = new DiscoveredPeer("demo-001-reporter", "Reporter-K7");
		reporter.setSignalStrength(-45); // Very close
		reporter.setState(DiscoveredPeer.PeerState.CONNECTED);
		reporter.setManifestSummary("2 maps, 1 Wikipedia");
		reporter.setRemoteManifest(createReporterManifest(app));
		demoPeers.add(reporter);

		// Peer 2: Aid worker with model and maps
		DiscoveredPeer aidWorker = new DiscoveredPeer("demo-002-aidworker", "AidWorker-M3");
		aidWorker.setSignalStrength(-62); // Nearby
		aidWorker.setState(DiscoveredPeer.PeerState.CONNECTED);
		aidWorker.setManifestSummary("1 map, 1 model, 1 Wikipedia");
		aidWorker.setRemoteManifest(createAidWorkerManifest(app));
		demoPeers.add(aidWorker);

		// Peer 3: Field coordinator, weaker signal
		DiscoveredPeer coordinator = new DiscoveredPeer("demo-003-coord", "Field-Coord-01");
		coordinator.setSignalStrength(-78); // Moderate distance
		coordinator.setState(DiscoveredPeer.PeerState.DISCOVERED);
		coordinator.setManifestSummary("3 maps");
		demoPeers.add(coordinator);

		return demoPeers;
	}

	/**
	 * Simulate a file transfer with animated progress over ~5 seconds.
	 */
	public static void simulateTransferProgress(@NonNull TransferProgressCallback callback) {
		final String filename = "Turkey_asia_2.obf";
		final long totalSize = 148 * 1024 * 1024L; // 148 MB

		simulateStep(callback, filename, totalSize, 0, 20);
	}

	private static void simulateStep(@NonNull TransferProgressCallback callback,
	                                  @NonNull String filename, long totalSize,
	                                  int currentPercent, int stepSize) {
		if (currentPercent > 100) {
			callback.onComplete(filename, true, null);
			return;
		}

		long transferred = (long) (totalSize * (currentPercent / 100.0));
		callback.onProgress(filename, currentPercent, transferred, totalSize);

		if (currentPercent < 100) {
			int nextStep = Math.min(currentPercent + stepSize, 100);
			// Vary timing to look realistic (250-500ms per step)
			int delay = 250 + (int) (Math.random() * 250);
			handler.postDelayed(() -> simulateStep(callback, filename, totalSize, nextStep, stepSize), delay);
		} else {
			handler.postDelayed(() -> callback.onComplete(filename, true, null), 500);
		}
	}

	@NonNull
	private static ContentManifest createReporterManifest(@NonNull OsmandApplication app) {
		ContentManifest manifest = new ContentManifest(app);
		// Add simulated content items directly
		List<ShareableContent> items = new ArrayList<>();
		items.add(new ShareableContent("Turkey_asia_2.obf", "", 148 * 1024 * 1024L, ContentType.MAP, null));
		items.add(new ShareableContent("Syria_2.obf", "", 87 * 1024 * 1024L, ContentType.MAP, null));
		items.add(new ShareableContent("wikipedia_en_medicine.zim", "", 412 * 1024 * 1024L, ContentType.ZIM, null));
		// Use reflection-free approach: create from JSON then return
		return createManifestFromItems(app, items);
	}

	@NonNull
	private static ContentManifest createAidWorkerManifest(@NonNull OsmandApplication app) {
		List<ShareableContent> items = new ArrayList<>();
		items.add(new ShareableContent("Lebanon_2.obf", "", 95 * 1024 * 1024L, ContentType.MAP, null));
		items.add(new ShareableContent("TinyLlama-1.1B-Chat-v1.0.Q4_K_M.gguf", "", 669 * 1024 * 1024L, ContentType.MODEL, null));
		items.add(new ShareableContent("wikipedia_en_simple.zim", "", 234 * 1024 * 1024L, ContentType.ZIM, null));
		return createManifestFromItems(app, items);
	}

	@NonNull
	private static ContentManifest createManifestFromItems(@NonNull OsmandApplication app,
	                                                       @NonNull List<ShareableContent> items) {
		// Build JSON and parse it, since ContentManifest.contentList is private
		StringBuilder json = new StringBuilder("{\"items\":[");
		for (int i = 0; i < items.size(); i++) {
			ShareableContent item = items.get(i);
			if (i > 0) json.append(",");
			json.append("{\"filename\":\"").append(item.getFilename()).append("\"");
			json.append(",\"size\":").append(item.getFileSize());
			json.append(",\"type\":\"").append(item.getContentType().name()).append("\"");
			json.append("}");
		}
		json.append("],\"summary\":\"").append(items.size()).append(" items\"}");
		return ContentManifest.fromJson(app, json.toString());
	}

	public static boolean isActive() {
		return active;
	}

	public static void setActive(boolean demoActive) {
		active = demoActive;
	}

	/**
	 * Callback interface for simulated transfer progress.
	 */
	public interface TransferProgressCallback {
		void onProgress(@NonNull String filename, int percent, long bytesTransferred, long totalBytes);
		void onComplete(@NonNull String filename, boolean success, String error);
	}
}
