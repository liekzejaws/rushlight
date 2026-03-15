/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages a queue of file transfer jobs for sequential processing.
 * Replaces the single-file isTransferring boolean with a proper queue.
 *
 * Usage:
 * 1. Enqueue files via enqueue()
 * 2. Call processNext() to start the first job
 * 3. On transfer complete, call onJobCompleted() which auto-advances
 * 4. Cancel individual jobs or the entire queue
 */
public class TransferQueue {

	private static final Log LOG = PlatformUtil.getLog(TransferQueue.class);

	/**
	 * Callback interface for queue state changes.
	 */
	public interface QueueCallback {
		void onQueueUpdated(@NonNull TransferQueue queue);
		void onJobStarted(@NonNull TransferJob job, int position, int total);
		void onQueueCompleted(int successful, int failed, int cancelled);
	}

	private final LinkedList<TransferJob> queue = new LinkedList<>();
	private final List<QueueCallback> callbacks = new CopyOnWriteArrayList<>();

	@Nullable
	private TransferJob currentJob;
	private boolean isProcessing = false;

	// Statistics
	private int completedCount = 0;
	private int failedCount = 0;
	private int cancelledCount = 0;

	/**
	 * Add a content item to the download queue.
	 */
	public void enqueue(@NonNull ShareableContent content) {
		TransferJob job = new TransferJob(content);
		queue.add(job);
		LOG.info("Enqueued: " + content.getFilename() + " (queue size: " + queue.size() + ")");
		notifyQueueUpdated();
	}

	/**
	 * Add multiple content items to the queue.
	 */
	public void enqueueAll(@NonNull List<ShareableContent> contentList) {
		for (ShareableContent content : contentList) {
			queue.add(new TransferJob(content));
		}
		LOG.info("Enqueued " + contentList.size() + " items (queue size: " + queue.size() + ")");
		notifyQueueUpdated();
	}

	/**
	 * Get the next job to process.
	 * Returns null if queue is empty or already processing.
	 */
	@Nullable
	public TransferJob getNextJob() {
		if (isProcessing || queue.isEmpty()) {
			return null;
		}

		currentJob = queue.peek();
		if (currentJob != null) {
			currentJob.setState(TransferJob.JobState.IN_PROGRESS);
			isProcessing = true;

			int position = completedCount + failedCount + cancelledCount + 1;
			int total = position + queue.size() - 1;
			notifyJobStarted(currentJob, position, total);
		}

		return currentJob;
	}

	/**
	 * Called when the current job completes (success or failure).
	 * Automatically advances to the next job in queue.
	 */
	public void onJobCompleted(@NonNull String filename, boolean success, @Nullable String error) {
		if (currentJob != null && currentJob.getFilename().equals(filename)) {
			queue.poll(); // Remove from queue

			if (success) {
				currentJob.setState(TransferJob.JobState.COMPLETED);
				currentJob.setProgress(100);
				completedCount++;
			} else {
				currentJob.setState(TransferJob.JobState.FAILED);
				currentJob.setError(error);
				failedCount++;
			}

			currentJob = null;
			isProcessing = false;
			notifyQueueUpdated();

			// Check if queue is done
			if (queue.isEmpty()) {
				notifyQueueCompleted();
			}
		}
	}

	/**
	 * Update progress for the current job.
	 */
	public void onJobProgress(@NonNull String filename, int progress, long bytesTransferred) {
		if (currentJob != null && currentJob.getFilename().equals(filename)) {
			currentJob.setProgress(progress);
			currentJob.setBytesTransferred(bytesTransferred);
		}
	}

	/**
	 * Cancel the current job and stop processing.
	 */
	public void cancelCurrent() {
		if (currentJob != null) {
			currentJob.setState(TransferJob.JobState.CANCELLED);
			queue.poll();
			cancelledCount++;
			currentJob = null;
			isProcessing = false;
			notifyQueueUpdated();

			// Continue with next if queue has items
			if (queue.isEmpty()) {
				notifyQueueCompleted();
			}
		}
	}

	/**
	 * Cancel all remaining jobs in the queue.
	 */
	public void cancelAll() {
		if (currentJob != null) {
			currentJob.setState(TransferJob.JobState.CANCELLED);
			cancelledCount++;
			currentJob = null;
		}

		for (TransferJob job : queue) {
			job.setState(TransferJob.JobState.CANCELLED);
			cancelledCount++;
		}
		queue.clear();
		isProcessing = false;

		notifyQueueUpdated();
		notifyQueueCompleted();
	}

	/**
	 * Remove a specific queued (not in-progress) job.
	 */
	public boolean removeJob(@NonNull TransferJob job) {
		if (job == currentJob) {
			return false; // Can't remove in-progress job
		}
		boolean removed = queue.remove(job);
		if (removed) {
			notifyQueueUpdated();
		}
		return removed;
	}

	// Query methods

	public boolean isEmpty() {
		return queue.isEmpty() && currentJob == null;
	}

	public boolean isProcessing() {
		return isProcessing;
	}

	public int getQueueSize() {
		return queue.size();
	}

	public int getTotalJobs() {
		return queue.size() + completedCount + failedCount + cancelledCount
				+ (currentJob != null ? 1 : 0);
	}

	public int getProcessedCount() {
		return completedCount + failedCount + cancelledCount;
	}

	@Nullable
	public TransferJob getCurrentJob() {
		return currentJob;
	}

	/**
	 * Get all jobs including current and queued.
	 */
	@NonNull
	public List<TransferJob> getAllJobs() {
		List<TransferJob> all = new ArrayList<>();
		if (currentJob != null) {
			all.add(currentJob);
		}
		all.addAll(queue);
		return all;
	}

	/**
	 * Get a status string for display (e.g., "File 2 of 5").
	 */
	@NonNull
	public String getStatusString() {
		if (isEmpty()) {
			return "Queue empty";
		}
		int current = completedCount + failedCount + cancelledCount + 1;
		int total = current + queue.size() - (currentJob != null ? 0 : 1);
		return "File " + current + " of " + total;
	}

	/**
	 * Reset queue statistics.
	 */
	public void reset() {
		queue.clear();
		currentJob = null;
		isProcessing = false;
		completedCount = 0;
		failedCount = 0;
		cancelledCount = 0;
	}

	// Callback management

	public void addCallback(@NonNull QueueCallback callback) {
		if (!callbacks.contains(callback)) {
			callbacks.add(callback);
		}
	}

	public void removeCallback(@NonNull QueueCallback callback) {
		callbacks.remove(callback);
	}

	private void notifyQueueUpdated() {
		for (QueueCallback cb : callbacks) {
			cb.onQueueUpdated(this);
		}
	}

	private void notifyJobStarted(@NonNull TransferJob job, int position, int total) {
		for (QueueCallback cb : callbacks) {
			cb.onJobStarted(job, position, total);
		}
	}

	private void notifyQueueCompleted() {
		for (QueueCallback cb : callbacks) {
			cb.onQueueCompleted(completedCount, failedCount, cancelledCount);
		}
	}
}
