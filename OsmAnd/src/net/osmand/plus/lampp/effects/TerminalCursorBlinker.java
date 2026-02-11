package net.osmand.plus.lampp.effects;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Appends a blinking block cursor (█) to a {@link TextView},
 * simulating a terminal cursor for the Pip-Boy theme.
 * <p>
 * Usage: call {@link #attachTo} to start blinking on a view,
 * {@link #updateBaseText} during streaming updates, and
 * {@link #stop} when generation completes or the view is destroyed.
 */
public class TerminalCursorBlinker {

	private static final String CURSOR_CHAR = "\u2588"; // Full block
	private static final long BLINK_INTERVAL_MS = 530;   // Classic terminal rate

	private final Handler handler = new Handler(Looper.getMainLooper());

	@Nullable
	private TextView targetView;

	@NonNull
	private String baseText = "";

	private boolean cursorVisible = true;
	private boolean running = false;

	private final Runnable blinkRunnable = new Runnable() {
		@Override
		public void run() {
			if (!running || targetView == null) return;
			cursorVisible = !cursorVisible;
			updateDisplay();
			handler.postDelayed(this, BLINK_INTERVAL_MS);
		}
	};

	/**
	 * Attach the blinker to a TextView and start blinking.
	 * Any previous attachment is stopped first.
	 */
	public void attachTo(@NonNull TextView view, @NonNull String text) {
		stop();
		this.targetView = view;
		this.baseText = text;
		this.cursorVisible = true;
		this.running = true;
		updateDisplay();
		handler.postDelayed(blinkRunnable, BLINK_INTERVAL_MS);
	}

	/**
	 * Update the base text while the cursor continues blinking.
	 * Called during streaming partial results.
	 */
	public void updateBaseText(@NonNull String text) {
		this.baseText = text;
		if (running) {
			updateDisplay();
		}
	}

	/** Stop blinking and remove the cursor character. */
	public void stop() {
		running = false;
		handler.removeCallbacks(blinkRunnable);
		if (targetView != null && baseText.length() > 0) {
			targetView.setText(baseText);
		}
		targetView = null;
	}

	public boolean isRunning() {
		return running;
	}

	private void updateDisplay() {
		if (targetView != null) {
			targetView.setText(cursorVisible ? baseText + CURSOR_CHAR : baseText);
		}
	}
}
