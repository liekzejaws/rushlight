package net.osmand.plus.lampp.effects;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws a soft green phosphor glow along the edges of the panel,
 * simulating CRT phosphor bleed for the Pip-Boy theme.
 * <p>
 * Four {@link LinearGradient} shaders (one per edge) fade from
 * Pip-Boy green to transparent. Gradients are pre-computed in
 * {@link #onSizeChanged} and rebuilt only when the breathing
 * animation changes the alpha.
 * <p>
 * The optional breathing pulse uses a {@link ValueAnimator} that
 * oscillates alpha between 0.17 and 0.33 over a 3-second cycle.
 */
public class PhosphorGlowView extends View {

	private static final int GLOW_WIDTH_DP = 12;
	private static final float BASE_ALPHA = 0.25f;
	private static final float PULSE_AMPLITUDE = 0.08f;
	private static final long PULSE_DURATION_MS = 3000;

	// Pip-Boy green RGB components
	private static final int GREEN_R = 0x33;
	private static final int GREEN_G = 0xFF;
	private static final int GREEN_B = 0x33;

	private Paint leftPaint;
	private Paint topPaint;
	private Paint rightPaint;
	private Paint bottomPaint;

	private int glowWidthPx;
	private float currentAlpha = BASE_ALPHA;

	@Nullable
	private ValueAnimator pulseAnimator;

	public PhosphorGlowView(@NonNull Context context) {
		super(context);
		init();
	}

	public PhosphorGlowView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public PhosphorGlowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		float density = getResources().getDisplayMetrics().density;
		glowWidthPx = (int) (GLOW_WIDTH_DP * density + 0.5f);
		leftPaint = new Paint();
		topPaint = new Paint();
		rightPaint = new Paint();
		bottomPaint = new Paint();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (w > 0 && h > 0) {
			rebuildGradients(w, h);
		}
	}

	private void rebuildGradients(int w, int h) {
		int glowAlpha = (int) (currentAlpha * 255);
		int startColor = Color.argb(glowAlpha, GREEN_R, GREEN_G, GREEN_B);
		int endColor = Color.TRANSPARENT;

		// Left edge: horizontal gradient inward
		leftPaint.setShader(new LinearGradient(
				0, 0, glowWidthPx, 0,
				startColor, endColor, Shader.TileMode.CLAMP));

		// Top edge: vertical gradient downward
		topPaint.setShader(new LinearGradient(
				0, 0, 0, glowWidthPx,
				startColor, endColor, Shader.TileMode.CLAMP));

		// Right edge: horizontal gradient inward from right
		rightPaint.setShader(new LinearGradient(
				w, 0, w - glowWidthPx, 0,
				startColor, endColor, Shader.TileMode.CLAMP));

		// Bottom edge: vertical gradient upward from bottom
		bottomPaint.setShader(new LinearGradient(
				0, h, 0, h - glowWidthPx,
				startColor, endColor, Shader.TileMode.CLAMP));
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0) return;

		canvas.drawRect(0, 0, glowWidthPx, h, leftPaint);
		canvas.drawRect(0, 0, w, glowWidthPx, topPaint);
		canvas.drawRect(w - glowWidthPx, 0, w, h, rightPaint);
		canvas.drawRect(0, h - glowWidthPx, w, h, bottomPaint);
	}

	/**
	 * Start the ambient breathing pulse animation.
	 * Alpha oscillates between (BASE - AMPLITUDE) and (BASE + AMPLITUDE).
	 */
	public void startPulse() {
		if (pulseAnimator != null) return;

		pulseAnimator = ValueAnimator.ofFloat(
				BASE_ALPHA - PULSE_AMPLITUDE,
				BASE_ALPHA + PULSE_AMPLITUDE);
		pulseAnimator.setDuration(PULSE_DURATION_MS);
		pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
		pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
		pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
		pulseAnimator.addUpdateListener(anim -> {
			currentAlpha = (float) anim.getAnimatedValue();
			int w = getWidth();
			int h = getHeight();
			if (w > 0 && h > 0) {
				rebuildGradients(w, h);
				invalidate();
			}
		});
		pulseAnimator.start();
	}

	/** Stop the breathing pulse animation. */
	public void stopPulse() {
		if (pulseAnimator != null) {
			pulseAnimator.cancel();
			pulseAnimator = null;
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		stopPulse();
		super.onDetachedFromWindow();
	}
}
