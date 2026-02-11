package net.osmand.plus.lampp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * LAMPP: Horizontal drag interceptor for the sliding panel system.
 * Adapted from OsmAnd's InterceptorLinearLayout (which intercepts vertical drags).
 * This version intercepts horizontal drags for the side-sliding panel.
 */
public class InterceptorFrameLayout extends FrameLayout {

	private final int mTouchSlop;
	private boolean mIsScrolling;
	private float mDownX;
	private OnTouchListener listener;

	public InterceptorFrameLayout(Context context) {
		this(context, null);
	}

	public InterceptorFrameLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		ViewConfiguration vc = ViewConfiguration.get(context);
		mTouchSlop = vc.getScaledTouchSlop();
	}

	public InterceptorFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		ViewConfiguration vc = ViewConfiguration.get(context);
		mTouchSlop = vc.getScaledTouchSlop();
	}

	public int getTouchSlop() {
		return mTouchSlop;
	}

	public boolean isScrolling() {
		return mIsScrolling;
	}

	public void setListener(OnTouchListener listener) {
		this.listener = listener;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		boolean handled = false;
		int action = ev.getAction();

		if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
			mIsScrolling = false;
			handled = false;
		} else {
			switch (action) {
				case MotionEvent.ACTION_DOWN:
					mIsScrolling = false;
					mDownX = ev.getRawX();
					handled = false;
					break;
				case MotionEvent.ACTION_MOVE:
					if (mIsScrolling) {
						handled = true;
					} else {
						int xDiff = calculateDistanceX(ev);
						if (Math.abs(xDiff) > mTouchSlop) {
							mIsScrolling = true;
							handled = true;
						}
					}
					break;
			}
		}

		if (listener != null) {
			listener.onTouch(this, ev);
		}
		return handled;
	}

	private int calculateDistanceX(MotionEvent ev) {
		return (int) (ev.getRawX() - mDownX);
	}

	public float getDownX() {
		return mDownX;
	}
}
