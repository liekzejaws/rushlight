package net.osmand.plus.plugins.p2pshare;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for TransferQueue — P2P file transfer job queue.
 */
public class TransferQueueTest {

	private TransferQueue queue;

	@Before
	public void setUp() {
		queue = new TransferQueue();
	}

	private ShareableContent makeContent(String filename) {
		return new ShareableContent(filename, "/path/" + filename, 1024,
				ContentType.fromFilename(filename), null);
	}

	@Test
	public void testEnqueueAddsJob() {
		assertTrue(queue.isEmpty());
		queue.enqueue(makeContent("file1.obf"));
		assertFalse(queue.isEmpty());
		assertEquals(1, queue.getQueueSize());
	}

	@Test
	public void testGetNextJobReturnsFirst() {
		queue.enqueue(makeContent("first.obf"));
		queue.enqueue(makeContent("second.obf"));

		TransferJob job = queue.getNextJob();
		assertNotNull(job);
		assertEquals("first.obf", job.getFilename());
	}

	@Test
	public void testGetNextJobWhenEmpty() {
		assertNull("Empty queue should return null", queue.getNextJob());
	}

	@Test
	public void testGetNextJobWhileProcessing() {
		queue.enqueue(makeContent("file1.obf"));
		queue.enqueue(makeContent("file2.obf"));

		TransferJob job1 = queue.getNextJob();
		assertNotNull(job1);

		// While processing, getNextJob should return null
		TransferJob job2 = queue.getNextJob();
		assertNull("Should not return next job while processing", job2);
	}

	@Test
	public void testOnJobCompletedAdvancesQueue() {
		queue.enqueue(makeContent("file1.obf"));
		queue.enqueue(makeContent("file2.obf"));

		TransferJob job1 = queue.getNextJob();
		assertNotNull(job1);
		assertEquals("file1.obf", job1.getFilename());

		queue.onJobCompleted("file1.obf", true, null);
		assertFalse(queue.isProcessing());

		// Now should be able to get next job
		TransferJob job2 = queue.getNextJob();
		assertNotNull(job2);
		assertEquals("file2.obf", job2.getFilename());
	}

	@Test
	public void testCancelCurrentStopsActiveJob() {
		queue.enqueue(makeContent("file1.obf"));
		queue.enqueue(makeContent("file2.obf"));

		TransferJob job1 = queue.getNextJob();
		assertNotNull(job1);
		assertTrue(queue.isProcessing());

		queue.cancelCurrent();
		assertFalse(queue.isProcessing());
		assertNull(queue.getCurrentJob());
	}

	@Test
	public void testCancelAllClearsQueue() {
		queue.enqueue(makeContent("file1.obf"));
		queue.enqueue(makeContent("file2.obf"));
		queue.enqueue(makeContent("file3.obf"));

		queue.getNextJob(); // Start processing first
		queue.cancelAll();

		assertTrue(queue.isEmpty());
		assertFalse(queue.isProcessing());
		assertNull(queue.getCurrentJob());
		assertEquals(0, queue.getQueueSize());
	}

	@Test
	public void testEnqueueAll() {
		List<ShareableContent> contents = Arrays.asList(
				makeContent("a.obf"),
				makeContent("b.zim"),
				makeContent("c.apk"));

		queue.enqueueAll(contents);
		assertEquals(3, queue.getQueueSize());
	}

	@Test
	public void testQueuePreservesOrder() {
		queue.enqueue(makeContent("first.obf"));
		queue.enqueue(makeContent("second.zim"));
		queue.enqueue(makeContent("third.apk"));

		TransferJob job1 = queue.getNextJob();
		assertEquals("first.obf", job1.getFilename());

		queue.onJobCompleted("first.obf", true, null);

		TransferJob job2 = queue.getNextJob();
		assertEquals("second.zim", job2.getFilename());

		queue.onJobCompleted("second.zim", true, null);

		TransferJob job3 = queue.getNextJob();
		assertEquals("third.apk", job3.getFilename());
	}

	@Test
	public void testCancelWhenEmpty() {
		// Should not throw
		queue.cancelCurrent();
		queue.cancelAll();
		assertTrue(queue.isEmpty());
	}

	@Test
	public void testGetQueueSize() {
		assertEquals(0, queue.getQueueSize());
		queue.enqueue(makeContent("a.obf"));
		assertEquals(1, queue.getQueueSize());
		queue.enqueue(makeContent("b.obf"));
		assertEquals(2, queue.getQueueSize());
	}

	@Test
	public void testGetCurrentJob() {
		assertNull(queue.getCurrentJob());

		queue.enqueue(makeContent("test.obf"));
		TransferJob job = queue.getNextJob();

		assertEquals(job, queue.getCurrentJob());
	}

	@Test
	public void testJobCompletedWhenNoCurrent() {
		// Should not throw
		queue.onJobCompleted("nonexistent.obf", true, null);
	}

	@Test
	public void testEnqueueAfterCancel() {
		queue.enqueue(makeContent("original.obf"));
		queue.getNextJob();
		queue.cancelAll();

		queue.enqueue(makeContent("new.obf"));
		assertFalse(queue.isEmpty());
		assertEquals(1, queue.getQueueSize());

		TransferJob job = queue.getNextJob();
		assertNotNull(job);
		assertEquals("new.obf", job.getFilename());
	}

	@Test
	public void testJobFailure() {
		queue.enqueue(makeContent("fail.obf"));
		queue.enqueue(makeContent("success.obf"));

		TransferJob job1 = queue.getNextJob();
		queue.onJobCompleted("fail.obf", false, "Network error");

		assertEquals(TransferJob.JobState.FAILED, job1.getState());
		assertEquals("Network error", job1.getError());

		// Queue should still be usable
		TransferJob job2 = queue.getNextJob();
		assertNotNull(job2);
		assertEquals("success.obf", job2.getFilename());
	}

	@Test
	public void testJobProgress() {
		queue.enqueue(makeContent("big.obf"));
		TransferJob job = queue.getNextJob();

		queue.onJobProgress("big.obf", 50, 512);
		assertEquals(50, job.getProgress());
		assertEquals(512, job.getBytesTransferred());
	}

	@Test
	public void testGetAllJobs() {
		queue.enqueue(makeContent("a.obf"));
		queue.enqueue(makeContent("b.obf"));
		queue.getNextJob(); // Start processing a (peek, not poll)

		// getAllJobs returns currentJob + queue contents
		// currentJob = a.obf, queue still has [a.obf, b.obf] (peek doesn't remove)
		List<TransferJob> all = queue.getAllJobs();
		assertEquals(3, all.size());
		assertEquals("a.obf", all.get(0).getFilename()); // current
		assertEquals("a.obf", all.get(1).getFilename()); // still in queue
		assertEquals("b.obf", all.get(2).getFilename());
	}

	@Test
	public void testReset() {
		queue.enqueue(makeContent("a.obf"));
		queue.getNextJob();
		queue.onJobCompleted("a.obf", true, null);

		queue.reset();

		assertTrue(queue.isEmpty());
		assertFalse(queue.isProcessing());
		assertEquals(0, queue.getQueueSize());
		assertEquals(0, queue.getProcessedCount());
	}

	@Test
	public void testCallbackNotification() {
		final int[] updateCount = {0};
		queue.addCallback(new TransferQueue.QueueCallback() {
			@Override
			public void onQueueUpdated(TransferQueue q) {
				updateCount[0]++;
			}

			@Override
			public void onJobStarted(TransferJob job, int position, int total) {
			}

			@Override
			public void onQueueCompleted(int successful, int failed, int cancelled) {
			}
		});

		queue.enqueue(makeContent("test.obf"));
		assertTrue("Callback should be notified on enqueue", updateCount[0] > 0);
	}
}
