/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.lampp;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.ai.DeviceCapabilityDetector;
import net.osmand.plus.ai.StartupProfiler;

import org.apache.commons.logging.Log;

/**
 * v1.4: Device test report dialog for grant hardware testing matrix.
 * Shows device model, Android version, RAM, CPU cores, device tier,
 * AI capability, benchmark results, and cold start time.
 * Provides "Copy Report" and "Share Report" buttons for pasting
 * into grant spreadsheets.
 */
public class DeviceTestReportDialog extends DialogFragment {

	public static final String TAG = "DeviceTestReportDialog";
	private static final Log LOG = PlatformUtil.getLog(DeviceTestReportDialog.class);

	public static void showInstance(@NonNull FragmentManager fragmentManager) {
		if (fragmentManager.findFragmentByTag(TAG) != null) return;
		DeviceTestReportDialog dialog = new DeviceTestReportDialog();
		dialog.show(fragmentManager, TAG);
	}

	@NonNull
	@Override
	public AlertDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
		DeviceCapabilityDetector detector = new DeviceCapabilityDetector(app);

		String report = buildReport(detector);

		return new AlertDialog.Builder(requireContext())
				.setTitle("Device Test Report")
				.setMessage(report)
				.setPositiveButton("Copy Report", (dialog, which) -> {
					ClipboardManager clipboard = (ClipboardManager)
							requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
					if (clipboard != null) {
						clipboard.setPrimaryClip(ClipData.newPlainText("Device Report", report));
						Toast.makeText(getContext(), "Report copied to clipboard", Toast.LENGTH_SHORT).show();
					}
				})
				.setNeutralButton("Share", (dialog, which) -> {
					Intent shareIntent = new Intent(Intent.ACTION_SEND);
					shareIntent.setType("text/plain");
					shareIntent.putExtra(Intent.EXTRA_TEXT, buildMarkdownReport(detector));
					shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Rushlight Device Test Report");
					startActivity(Intent.createChooser(shareIntent, "Share Report"));
				})
				.setNegativeButton("Close", null)
				.create();
	}

	@NonNull
	private String buildReport(@NonNull DeviceCapabilityDetector detector) {
		StringBuilder sb = new StringBuilder();
		sb.append("Rushlight v1.4 Device Report\n");
		sb.append("═══════════════════════════\n\n");

		// Device compatibility report
		sb.append(detector.getCompatibilityReport());

		// Cold start time
		long coldStartMs = StartupProfiler.getLastColdStartMs();
		sb.append("\nStartup Performance:\n");
		if (coldStartMs >= 0) {
			sb.append("  Cold Start: ").append(String.format("%.1fs", coldStartMs / 1000.0));
			sb.append(coldStartMs <= 5000 ? " ✓ PASS" : " ✗ FAIL");
			sb.append(" (target <5s)\n");
		} else {
			sb.append("  Cold Start: N/A (restart app to measure)\n");
		}

		String startupSummary = StartupProfiler.getLastSummary();
		if (startupSummary != null) {
			sb.append("  ").append(startupSummary).append("\n");
		}

		return sb.toString();
	}

	@NonNull
	private String buildMarkdownReport(@NonNull DeviceCapabilityDetector detector) {
		StringBuilder md = new StringBuilder();
		md.append("# Rushlight Device Test Report\n\n");
		md.append("**Date:** ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
				java.util.Locale.US).format(new java.util.Date())).append("\n\n");
		md.append("```\n").append(buildReport(detector)).append("```\n");
		return md.toString();
	}
}
