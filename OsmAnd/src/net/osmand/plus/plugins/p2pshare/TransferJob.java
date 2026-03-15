/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents a single file transfer job in the queue.
 * Wraps a ShareableContent with queue-specific state tracking.
 */
public class TransferJob {

	public enum JobState {
		QUEUED,
		IN_PROGRESS,
		COMPLETED,
		FAILED,
		CANCELLED
	}

	@NonNull
	private final ShareableContent content;
	@NonNull
	private JobState state;
	private int progress;
	private long bytesTransferred;
	@Nullable
	private String error;

	public TransferJob(@NonNull ShareableContent content) {
		this.content = content;
		this.state = JobState.QUEUED;
		this.progress = 0;
		this.bytesTransferred = 0;
	}

	@NonNull
	public ShareableContent getContent() {
		return content;
	}

	@NonNull
	public JobState getState() {
		return state;
	}

	public void setState(@NonNull JobState state) {
		this.state = state;
	}

	public int getProgress() {
		return progress;
	}

	public void setProgress(int progress) {
		this.progress = progress;
	}

	public long getBytesTransferred() {
		return bytesTransferred;
	}

	public void setBytesTransferred(long bytesTransferred) {
		this.bytesTransferred = bytesTransferred;
	}

	@Nullable
	public String getError() {
		return error;
	}

	public void setError(@Nullable String error) {
		this.error = error;
	}

	@NonNull
	public String getFilename() {
		return content.getFilename();
	}

	public long getFileSize() {
		return content.getFileSize();
	}

	public boolean isTerminal() {
		return state == JobState.COMPLETED || state == JobState.FAILED || state == JobState.CANCELLED;
	}
}
