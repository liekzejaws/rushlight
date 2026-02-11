package net.osmand.plus.lampp.effects;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws semi-transparent horizontal scan lines over panel content,
 * simulating a CRT monitor effect for the Pip-Boy theme.
 * <p>
 * Uses a tiled {@link BitmapShader} for GPU-efficient rendering:
 * a tiny 1×4px bitmap pattern (2px dark line + 2px gap) is repeated
 * across the entire view. {@link #onDraw} is a single {@code drawRect()}.
 * <p>
 * Non-interactive: touches pass through to content beneath.
 */
public class ScanLineOverlayView extends View {

	private static final int LINE_HEIGHT_PX = 2;
	private static final int GAP_HEIGHT_PX = 2;
	private static final int SCAN_LINE_ALPHA = 30; // ~12% opacity

	@Nullable
	private Paint scanPaint;

	public ScanLineOverlayView(@NonNull Context context) {
		super(context);
	}

	public ScanLineOverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public ScanLineOverlayView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	private void ensureInitialized() {
		if (scanPaint != null) {
			return;
		}
		int patternHeight = LINE_HEIGHT_PX + GAP_HEIGHT_PX;
		Bitmap pattern = Bitmap.createBitmap(1, patternHeight, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(pattern);

		// Draw the dark scan line portion
		Paint linePaint = new Paint();
		linePaint.setColor(0xFF000000);
		linePaint.setAlpha(SCAN_LINE_ALPHA);
		c.drawRect(0, 0, 1, LINE_HEIGHT_PX, linePaint);
		// Gap pixels are transparent by default (0x00000000)

		BitmapShader shader = new BitmapShader(pattern,
				Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
		scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		scanPaint.setShader(shader);
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		ensureInitialized();
		if (scanPaint != null) {
			canvas.drawRect(0, 0, getWidth(), getHeight(), scanPaint);
		}
	}
}
