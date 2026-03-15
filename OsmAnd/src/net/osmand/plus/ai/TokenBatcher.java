/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.Markwon;

/**
 * Phase 17: Batches streaming LLM tokens to reduce Markwon re-renders.
 *
 * Problem: onPartialResult() fires per token, causing a full Markwon parse + render
 * for every token (500 renders for a 500-token response). This causes jank.
 *
 * Solution: Accumulate tokens and render every N tokens or every T milliseconds
 * (whichever comes first). During the batch interval, just append raw text to the
 * TextView (fast path). On flush, do a single Markwon parse (rich path).
 *
 * Usage:
 *   batcher = new TokenBatcher(markwon, textView, 5, 100);
 *   // In onPartialResult:
 *   batcher.onToken(fullTextSoFar);
 *   // In onComplete:
 *   batcher.flush();
 *   batcher.stop();
 */
public class TokenBatcher {

	private static final int DEFAULT_BATCH_SIZE = 5;
	private static final long DEFAULT_BATCH_INTERVAL_MS = 100;

	private final Handler handler = new Handler(Looper.getMainLooper());
	private final int batchSize;
	private final long batchIntervalMs;

	@Nullable
	private Markwon markwon;
	@Nullable
	private TextView textView;

	private String lastRenderedText = "";
	private String pendingText = "";
	private int tokensSinceRender = 0;
	private boolean flushScheduled = false;
	private boolean stopped = false;

	/**
	 * Callback for when a batch render occurs (so the adapter can scroll, etc.)
	 */
	public interface BatchRenderCallback {
		void onBatchRendered();
	}

	@Nullable
	private BatchRenderCallback callback;

	public TokenBatcher(@Nullable Markwon markwon, @Nullable TextView textView) {
		this(markwon, textView, DEFAULT_BATCH_SIZE, DEFAULT_BATCH_INTERVAL_MS);
	}

	public TokenBatcher(@Nullable Markwon markwon, @Nullable TextView textView,
	                     int batchSize, long batchIntervalMs) {
		this.markwon = markwon;
		this.textView = textView;
		this.batchSize = batchSize;
		this.batchIntervalMs = batchIntervalMs;
	}

	public void setCallback(@Nullable BatchRenderCallback callback) {
		this.callback = callback;
	}

	/**
	 * Update the target TextView (called when ViewHolder is rebound).
	 */
	public void setTextView(@Nullable TextView textView) {
		this.textView = textView;
	}

	/**
	 * Update the Markwon instance (e.g., after theme change).
	 */
	public void setMarkwon(@Nullable Markwon markwon) {
		this.markwon = markwon;
	}

	/**
	 * Called each time the full accumulated text is updated (per-token from LLM).
	 * Decides whether to do a fast path (plain text append) or rich path (Markwon render).
	 */
	public void onToken(@NonNull String fullText) {
		if (stopped) return;

		pendingText = fullText;
		tokensSinceRender++;

		if (tokensSinceRender >= batchSize) {
			// Hit batch size threshold — do a rich render now
			doRichRender();
		} else if (!flushScheduled) {
			// Schedule a time-based flush
			flushScheduled = true;
			handler.postDelayed(this::timedFlush, batchIntervalMs);

			// Fast path: just set plain text (no Markwon parsing)
			if (textView != null) {
				textView.setText(pendingText);
			}
		} else {
			// Flush is already scheduled — just set plain text for now
			if (textView != null) {
				textView.setText(pendingText);
			}
		}
	}

	private void timedFlush() {
		flushScheduled = false;
		if (!stopped && tokensSinceRender > 0) {
			doRichRender();
		}
	}

	/**
	 * Perform a full Markwon render of the accumulated text.
	 */
	private void doRichRender() {
		if (textView == null || pendingText.isEmpty()) return;

		if (markwon != null) {
			markwon.setMarkdown(textView, pendingText);
		} else {
			textView.setText(pendingText);
		}

		lastRenderedText = pendingText;
		tokensSinceRender = 0;

		// Cancel any pending timed flush since we just rendered
		if (flushScheduled) {
			handler.removeCallbacks(this::timedFlush);
			flushScheduled = false;
		}

		if (callback != null) {
			callback.onBatchRendered();
		}
	}

	/**
	 * Force a final rich render (call when generation is complete).
	 */
	public void flush() {
		if (flushScheduled) {
			handler.removeCallbacks(this::timedFlush);
			flushScheduled = false;
		}
		if (!pendingText.isEmpty()) {
			doRichRender();
		}
	}

	/**
	 * Stop the batcher and clean up handler callbacks.
	 */
	public void stop() {
		stopped = true;
		handler.removeCallbacksAndMessages(null);
		flushScheduled = false;
	}

	/**
	 * Reset state for a new generation cycle.
	 */
	public void reset() {
		stop();
		stopped = false;
		lastRenderedText = "";
		pendingText = "";
		tokensSinceRender = 0;
	}

	/**
	 * Get the last text that was fully rendered with Markwon.
	 */
	@NonNull
	public String getLastRenderedText() {
		return lastRenderedText;
	}
}
