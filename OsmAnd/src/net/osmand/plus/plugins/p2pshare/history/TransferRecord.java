/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data class representing a single transfer history entry.
 * Stored in SQLite and displayed in the Transfer History bottom sheet.
 */
public class TransferRecord {

	// Transfer directions
	public static final String DIRECTION_SENT = "sent";
	public static final String DIRECTION_RECEIVED = "received";

	// Transfer statuses
	public static final String STATUS_IN_PROGRESS = "in_progress";
	public static final String STATUS_SUCCESS = "success";
	public static final String STATUS_FAILED = "failed";
	public static final String STATUS_CANCELLED = "cancelled";

	// Checksum verification results
	public static final int CHECKSUM_NOT_CHECKED = -1;
	public static final int CHECKSUM_FAILED = 0;
	public static final int CHECKSUM_PASSED = 1;

	private long id;
	@NonNull private String filename;
	private long fileSize;
	private long bytesTransferred;
	@Nullable private String peerName;
	@Nullable private String peerId;
	@Nullable private String transport;
	@NonNull private String direction;
	@NonNull private String status;
	@Nullable private String error;
	private long startTime;
	private long endTime;
	private float speedKbps;
	private int checksumOk;
	@Nullable private String contentType;

	public TransferRecord(@NonNull String filename, long fileSize,
	                       @NonNull String direction) {
		this.filename = filename;
		this.fileSize = fileSize;
		this.direction = direction;
		this.status = STATUS_IN_PROGRESS;
		this.startTime = System.currentTimeMillis();
		this.checksumOk = CHECKSUM_NOT_CHECKED;
	}

	// Full constructor for database reads
	public TransferRecord(long id, @NonNull String filename, long fileSize,
	                       long bytesTransferred, @Nullable String peerName,
	                       @Nullable String peerId, @Nullable String transport,
	                       @NonNull String direction, @NonNull String status,
	                       @Nullable String error, long startTime, long endTime,
	                       float speedKbps, int checksumOk,
	                       @Nullable String contentType) {
		this.id = id;
		this.filename = filename;
		this.fileSize = fileSize;
		this.bytesTransferred = bytesTransferred;
		this.peerName = peerName;
		this.peerId = peerId;
		this.transport = transport;
		this.direction = direction;
		this.status = status;
		this.error = error;
		this.startTime = startTime;
		this.endTime = endTime;
		this.speedKbps = speedKbps;
		this.checksumOk = checksumOk;
		this.contentType = contentType;
	}

	// Getters

	public long getId() { return id; }
	public void setId(long id) { this.id = id; }

	@NonNull
	public String getFilename() { return filename; }

	public long getFileSize() { return fileSize; }

	public long getBytesTransferred() { return bytesTransferred; }
	public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }

	@Nullable
	public String getPeerName() { return peerName; }
	public void setPeerName(@Nullable String peerName) { this.peerName = peerName; }

	@Nullable
	public String getPeerId() { return peerId; }
	public void setPeerId(@Nullable String peerId) { this.peerId = peerId; }

	@Nullable
	public String getTransport() { return transport; }
	public void setTransport(@Nullable String transport) { this.transport = transport; }

	@NonNull
	public String getDirection() { return direction; }

	@NonNull
	public String getStatus() { return status; }
	public void setStatus(@NonNull String status) { this.status = status; }

	@Nullable
	public String getError() { return error; }
	public void setError(@Nullable String error) { this.error = error; }

	public long getStartTime() { return startTime; }

	public long getEndTime() { return endTime; }
	public void setEndTime(long endTime) { this.endTime = endTime; }

	public float getSpeedKbps() { return speedKbps; }
	public void setSpeedKbps(float speedKbps) { this.speedKbps = speedKbps; }

	public int getChecksumOk() { return checksumOk; }
	public void setChecksumOk(int checksumOk) { this.checksumOk = checksumOk; }

	@Nullable
	public String getContentType() { return contentType; }
	public void setContentType(@Nullable String contentType) { this.contentType = contentType; }

	// Formatting helpers

	/**
	 * Format the start time for display (e.g., "Feb 13, 14:32").
	 */
	@NonNull
	public String getFormattedTime() {
		SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.US);
		return sdf.format(new Date(startTime));
	}

	/**
	 * Format the file size for display (e.g., "1.2 MB").
	 */
	@NonNull
	public String getFormattedSize() {
		return formatBytes(fileSize);
	}

	/**
	 * Format bytes transferred for display.
	 */
	@NonNull
	public String getFormattedBytesTransferred() {
		return formatBytes(bytesTransferred);
	}

	/**
	 * Format transfer speed for display (e.g., "256.3 KB/s").
	 */
	@NonNull
	public String getFormattedSpeed() {
		if (speedKbps <= 0) {
			return "";
		} else if (speedKbps < 1024) {
			return String.format(Locale.US, "%.1f KB/s", speedKbps);
		} else {
			return String.format(Locale.US, "%.1f MB/s", speedKbps / 1024);
		}
	}

	/**
	 * Get duration as human-readable string (e.g., "1m 23s").
	 */
	@NonNull
	public String getFormattedDuration() {
		long end = endTime > 0 ? endTime : System.currentTimeMillis();
		long durationMs = end - startTime;

		if (durationMs < 1000) {
			return "<1s";
		}

		long seconds = durationMs / 1000;
		if (seconds < 60) {
			return seconds + "s";
		}

		long minutes = seconds / 60;
		seconds = seconds % 60;
		if (minutes < 60) {
			return minutes + "m " + seconds + "s";
		}

		long hours = minutes / 60;
		minutes = minutes % 60;
		return hours + "h " + minutes + "m";
	}

	/**
	 * Get a status display string with direction arrow.
	 */
	@NonNull
	public String getStatusDisplay() {
		String arrow = DIRECTION_RECEIVED.equals(direction) ? "↓ " : "↑ ";
		switch (status) {
			case STATUS_SUCCESS:
				return arrow + "Complete";
			case STATUS_FAILED:
				return arrow + "Failed";
			case STATUS_CANCELLED:
				return arrow + "Cancelled";
			case STATUS_IN_PROGRESS:
				return arrow + "In progress";
			default:
				return arrow + status;
		}
	}

	/**
	 * Check if transfer completed successfully.
	 */
	public boolean isSuccess() {
		return STATUS_SUCCESS.equals(status);
	}

	/**
	 * Mark transfer as complete with speed calculation.
	 */
	public void markComplete(boolean success) {
		this.endTime = System.currentTimeMillis();
		this.status = success ? STATUS_SUCCESS : STATUS_FAILED;

		// Calculate speed
		long durationMs = endTime - startTime;
		if (durationMs > 0 && bytesTransferred > 0) {
			this.speedKbps = (bytesTransferred / 1024f) / (durationMs / 1000f);
		}
	}

	/**
	 * Mark as cancelled.
	 */
	public void markCancelled() {
		this.endTime = System.currentTimeMillis();
		this.status = STATUS_CANCELLED;
	}

	@NonNull
	private static String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
		} else if (bytes < 1024L * 1024 * 1024) {
			return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
		} else {
			return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
		}
	}
}
