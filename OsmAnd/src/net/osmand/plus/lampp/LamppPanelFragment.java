/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.lampp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.lampp.effects.PhosphorGlowView;
import net.osmand.plus.lampp.effects.ScanLineOverlayView;
import net.osmand.plus.utils.AndroidUtils;

import org.apache.commons.logging.Log;

/**
 * LAMPP: Base class for all Pip-Boy side panel fragments.
 * Provides horizontal slide-in/out animation, drag-to-resize, and
 * integration with the map activity. Subclasses provide their content layout.
 *
 * Modeled after OsmAnd's ContextMenuFragment but adapted for horizontal sliding.
 */
public abstract class LamppPanelFragment extends BaseOsmAndFragment {

	private static final Log LOG = PlatformUtil.getLog(LamppPanelFragment.class);
	private static final int ANIMATION_DURATION = 200;

	// Scrim overlay
	private static final int SCRIM_COLOR = 0xFF000000;
	private static final float SCRIM_MAX_ALPHA = 0.4f;

	// Panel states
	public static final int STATE_COLLAPSED = 0;
	public static final int STATE_PARTIAL = 1;
	public static final int STATE_FULL_SCREEN = 2;

	private int currentState = STATE_PARTIAL;
	private View rootView;
	private android.view.ViewTreeObserver.OnGlobalLayoutListener keyboardListener; // v0.6
	private InterceptorFrameLayout panelContainer;
	private View clickCatcher;
	private View dragHandle;
	private FrameLayout contentFrame;

	private int screenWidth;
	private float initialTouchX;
	private float initialTranslationX;
	private Runnable onCollapseComplete;
	@Nullable
	private ValueAnimator panelAnimator;

	// Pip-Boy visual effect overlays (null when non-PIP_BOY or disabled)
	@Nullable
	private ScanLineOverlayView scanLineOverlay;
	@Nullable
	private PhosphorGlowView phosphorGlowOverlay;

	/**
	 * Subclasses must provide the layout resource for their panel content.
	 */
	@LayoutRes
	protected abstract int getPanelLayoutId();

	/**
	 * Fragment tag for panel management.
	 */
	@NonNull
	public abstract String getPanelTag();

	/**
	 * Width ratio for partial state (0.0 to 1.0).
	 * Default: 0.65 portrait, 0.50 landscape (so the map stays usable).
	 */
	protected float getPartialWidthRatio() {
		boolean landscape = getResources().getConfiguration().orientation
				== android.content.res.Configuration.ORIENTATION_LANDSCAPE;
		return landscape ? 0.50f : 0.65f;
	}

	/**
	 * Called after the panel content view is created.
	 * Subclasses should set up their UI here.
	 */
	protected void onPanelViewCreated(@NonNull View contentView, @Nullable Bundle savedInstanceState) {
		// Override in subclasses
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                          @Nullable Bundle savedInstanceState) {
		updateNightMode();

		// Get screen dimensions
		DisplayMetrics dm = getResources().getDisplayMetrics();
		screenWidth = dm.widthPixels;

		// Build the panel layout programmatically
		rootView = buildPanelLayout(inflater, container);

		// Inflate subclass content with preset-aware theme overlay
		OsmandApplication osmApp = (OsmandApplication) requireActivity().getApplication();
		Context themedContext = LamppThemeUtils.getLamppThemedContext(requireContext(), osmApp);
		LayoutInflater themedInflater = inflater.cloneInContext(themedContext);
		View contentView = themedInflater.inflate(getPanelLayoutId(), contentFrame, false);
		contentFrame.addView(contentView);

		// Content fade-in for smooth appearance (especially during preset transitions)
		contentFrame.setAlpha(0f);
		contentFrame.animate()
				.alpha(1f)
				.setDuration(150)
				.setStartDelay(50)
				.setInterpolator(new DecelerateInterpolator())
				.start();

		// Let subclasses set up their content
		onPanelViewCreated(contentView, savedInstanceState);

		// Apply retro monospace font if Pip-Boy + setting enabled
		LamppThemeUtils.applyRetroFontIfNeeded(contentView, osmApp);

		// Start with panel off-screen, then animate in
		panelContainer.setTranslationX(getPanelWidth());
		rootView.post(() -> animateToState(STATE_PARTIAL));

		// v0.6: Keyboard-aware padding — detect keyboard and adjust contentFrame bottom padding
		keyboardListener = () -> {
			if (contentFrame == null || rootView == null) return;
			android.graphics.Rect visibleRect = new android.graphics.Rect();
			rootView.getWindowVisibleDisplayFrame(visibleRect);
			int rootHeight = rootView.getRootView().getHeight();
			int keyboardHeight = rootHeight - visibleRect.bottom;
			int threshold = rootHeight / 6; // keyboard > ~16% of screen
			contentFrame.setPadding(
					contentFrame.getPaddingLeft(),
					contentFrame.getPaddingTop(),
					contentFrame.getPaddingRight(),
					keyboardHeight > threshold ? keyboardHeight : 0);
		};
		rootView.getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);

		return rootView;
	}

	private View buildPanelLayout(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
		// Root: full-screen FrameLayout (transparent, catches outside taps)
		FrameLayout root = new FrameLayout(requireContext());
		root.setLayoutParams(new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));

		// Click catcher: scrim overlay over the map (left side)
		clickCatcher = new View(requireContext());
		clickCatcher.setLayoutParams(new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT));
		clickCatcher.setBackgroundColor(SCRIM_COLOR);
		clickCatcher.setAlpha(0f);
		clickCatcher.setOnClickListener(v -> collapsePanel(null));
		root.addView(clickCatcher);

		// Panel container with drag interception
		int panelWidth = getPanelWidth();
		panelContainer = new InterceptorFrameLayout(requireContext());
		FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
				panelWidth,
				FrameLayout.LayoutParams.MATCH_PARENT);
		panelLp.gravity = android.view.Gravity.END;
		panelContainer.setLayoutParams(panelLp);
		// Set panel background with preset-aware color
		float panelCornerRadius = getResources().getDimension(R.dimen.lampp_panel_corner_radius);
		GradientDrawable panelBg = new GradientDrawable();
		panelBg.setShape(GradientDrawable.RECTANGLE);
		panelBg.setCornerRadii(new float[]{
				panelCornerRadius, panelCornerRadius,  // top-left
				0, 0,                                   // top-right
				0, 0,                                   // bottom-right
				panelCornerRadius, panelCornerRadius}); // bottom-left
		OsmandApplication app = getActivity() != null
				? (OsmandApplication) getActivity().getApplication() : null;
		if (app != null) {
			panelBg.setColor(LamppThemeUtils.getPanelBgColor(requireContext(), app));
		} else {
			panelBg.setColor(getResources().getColor(R.color.lampp_bg_panel));
		}
		panelContainer.setBackground(panelBg);
		panelContainer.setElevation(AndroidUtils.dpToPx(requireContext(), 8f));
		panelContainer.setClickable(true); // Prevent clicks from going through to map

		// Set up horizontal drag
		setupDragBehavior();

		// Drag handle (vertical bar on left edge of panel)
		dragHandle = new View(requireContext());
		int handleWidth = getResources().getDimensionPixelSize(R.dimen.lampp_drag_handle_width);
		FrameLayout.LayoutParams handleLp = new FrameLayout.LayoutParams(
				handleWidth, FrameLayout.LayoutParams.MATCH_PARENT);
		handleLp.gravity = android.view.Gravity.START;
		dragHandle.setLayoutParams(handleLp);
		dragHandle.setBackgroundResource(R.drawable.bg_lampp_drag_handle);
		// Tint drag handle to match active preset
		if (app != null) {
			LamppStylePreset preset = LamppThemeUtils.getActivePreset(app);
			boolean night = LamppThemeUtils.isNightMode(app);
			android.graphics.drawable.GradientDrawable handleBg =
					(android.graphics.drawable.GradientDrawable) dragHandle.getBackground();
			handleBg.setColor(preset.getDragHandleColor(requireContext(), night));
		}
		dragHandle.setAlpha(0.5f);
		panelContainer.addView(dragHandle);

		// Content frame (where subclass content goes)
		contentFrame = new FrameLayout(requireContext());
		FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		contentLp.leftMargin = handleWidth;
		contentFrame.setLayoutParams(contentLp);
		panelContainer.addView(contentFrame);

		// Add Pip-Boy visual effect overlays (scan lines, glow)
		setupPipBoyEffects(panelContainer, handleWidth, app);

		root.addView(panelContainer);
		return root;
	}

	/**
	 * Adds Pip-Boy visual effect overlays to the panel container.
	 * Only adds views when PIP_BOY preset is active and individual settings are enabled.
	 */
	private void setupPipBoyEffects(@NonNull FrameLayout container, int leftMargin,
	                                 @Nullable OsmandApplication app) {
		if (app == null || LamppThemeUtils.getActivePreset(app) != LamppStylePreset.PIP_BOY) {
			return;
		}

		// CRT scan line overlay (above content, non-interactive)
		if (app.getSettings().LAMPP_PIPBOY_SCANLINES.get()) {
			scanLineOverlay = new ScanLineOverlayView(requireContext());
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			lp.leftMargin = leftMargin;
			scanLineOverlay.setLayoutParams(lp);
			scanLineOverlay.setClickable(false);
			scanLineOverlay.setFocusable(false);
			container.addView(scanLineOverlay);
		}

		// Phosphor edge glow (above scan lines, non-interactive)
		if (app.getSettings().LAMPP_PIPBOY_GLOW.get()) {
			phosphorGlowOverlay = new PhosphorGlowView(requireContext());
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
					FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			lp.leftMargin = leftMargin;
			phosphorGlowOverlay.setLayoutParams(lp);
			phosphorGlowOverlay.setClickable(false);
			phosphorGlowOverlay.setFocusable(false);
			container.addView(phosphorGlowOverlay);
			phosphorGlowOverlay.post(() -> phosphorGlowOverlay.startPulse());
		}
	}

	private void setupDragBehavior() {
		panelContainer.setListener((v, event) -> {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					initialTouchX = event.getRawX();
					initialTranslationX = panelContainer.getTranslationX();
					break;

				case MotionEvent.ACTION_MOVE:
					if (panelContainer.isScrolling()) {
						float deltaX = event.getRawX() - initialTouchX;
						float newTranslation = initialTranslationX + deltaX;
						// Clamp: can't go past full-screen left (negative)
						// or past fully collapsed (panelWidth)
						newTranslation = Math.max(getFullScreenTranslationX(), newTranslation);
						newTranslation = Math.min(getPanelWidth(), newTranslation);
						panelContainer.setTranslationX(newTranslation);
						updateScrimAlpha();
					}
					break;

				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					if (panelContainer.isScrolling()) {
						float velocity = event.getRawX() - initialTouchX;
						snapToNearestState(velocity);
					}
					break;
			}
			return false;
		});
	}

	private void snapToNearestState(float velocityX) {
		float currentX = panelContainer.getTranslationX();
		int panelWidth = getPanelWidth();

		// Determine target state based on velocity and position
		if (velocityX > panelWidth * 0.25f) {
			// Flung right — collapse
			animateToState(STATE_COLLAPSED);
		} else if (velocityX < -panelWidth * 0.25f) {
			// Flung left — expand to full screen
			animateToState(STATE_FULL_SCREEN);
		} else {
			// Snap to nearest state
			float partialX = 0f; // partial state = 0 translation
			float fullX = getFullScreenTranslationX();
			float collapseX = panelWidth;

			float distPartial = Math.abs(currentX - partialX);
			float distFull = Math.abs(currentX - fullX);
			float distCollapse = Math.abs(currentX - collapseX);

			if (distCollapse < distPartial && distCollapse < distFull) {
				animateToState(STATE_COLLAPSED);
			} else if (distFull < distPartial) {
				animateToState(STATE_FULL_SCREEN);
			} else {
				animateToState(STATE_PARTIAL);
			}
		}
	}

	private void animateToState(int targetState) {
		float targetX;
		switch (targetState) {
			case STATE_FULL_SCREEN:
				targetX = getFullScreenTranslationX();
				break;
			case STATE_COLLAPSED:
				targetX = getPanelWidth();
				break;
			case STATE_PARTIAL:
			default:
				targetX = 0f;
				break;
		}

		// Cancel any running panel animation
		if (panelAnimator != null && panelAnimator.isRunning()) {
			panelAnimator.cancel();
		}

		// Use ValueAnimator so scrim alpha tracks during animation
		float startX = panelContainer.getTranslationX();
		panelAnimator = ValueAnimator.ofFloat(startX, targetX);
		panelAnimator.setDuration(ANIMATION_DURATION);
		panelAnimator.setInterpolator(new DecelerateInterpolator());
		panelAnimator.addUpdateListener(anim -> {
			if (panelContainer == null) return;
			float val = (float) anim.getAnimatedValue();
			panelContainer.setTranslationX(val);
			updateScrimAlpha();
		});
		panelAnimator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				int oldState = currentState;
				currentState = targetState;
				onStateChanged(oldState, targetState);

				if (targetState == STATE_COLLAPSED) {
					if (onCollapseComplete != null) {
						onCollapseComplete.run();
						onCollapseComplete = null;
					} else {
						// Auto-remove when collapsed
						MapActivity activity = getMapActivity();
						if (activity != null) {
							activity.getLamppPanelManager().closeActivePanel(false);
						}
					}
				}
			}
		});
		panelAnimator.start();

		// Show/hide click catcher based on target
		if (clickCatcher != null) {
			clickCatcher.setVisibility(targetState == STATE_COLLAPSED ? View.GONE : View.VISIBLE);
		}
	}

	/**
	 * Collapse the panel with animation.
	 */
	public void collapsePanel(@Nullable Runnable onComplete) {
		onCollapseComplete = onComplete;
		animateToState(STATE_COLLAPSED);
	}

	protected void onStateChanged(int oldState, int newState) {
		// Override in subclasses for state change handling
	}

	/**
	 * Called when the hardware back button is pressed while this panel is active.
	 * Subclasses can override to handle internal navigation (e.g. going back
	 * from a detail view to a list view).
	 * @return true if the back press was consumed, false to let the panel close
	 */
	public boolean onBackPressed() {
		return false;
	}

	/**
	 * Updates the scrim overlay alpha based on the panel's current translationX.
	 * When the panel is at partial state (x=0), scrim is at max alpha (40%).
	 * When collapsed (x=panelWidth), scrim is fully transparent.
	 */
	private void updateScrimAlpha() {
		if (clickCatcher == null || panelContainer == null) return;
		float currentX = panelContainer.getTranslationX();
		float panelWidth = getPanelWidth();
		float fraction = 1f - Math.max(0f, Math.min(1f, currentX / panelWidth));
		clickCatcher.setAlpha(fraction * SCRIM_MAX_ALPHA);
	}

	/**
	 * Animate a preset transition by fading out the content frame, then calling the
	 * midpoint callback (which typically closes and reopens the panel with new theme).
	 */
	public void animatePresetTransition(@NonNull Runnable onMidpoint) {
		if (contentFrame == null) {
			onMidpoint.run();
			return;
		}
		contentFrame.animate()
				.alpha(0f)
				.setDuration(100)
				.setInterpolator(new DecelerateInterpolator())
				.withEndAction(onMidpoint)
				.start();
	}

	private int getPanelWidth() {
		return (int) (screenWidth * getPartialWidthRatio());
	}

	private float getFullScreenTranslationX() {
		// Translate left so the panel covers the full screen
		return -(screenWidth - getPanelWidth());
	}

	public int getCurrentState() {
		return currentState;
	}

	@Nullable
	public MapActivity getMapActivity() {
		android.app.Activity activity = getActivity();
		if (activity instanceof MapActivity) {
			return (MapActivity) activity;
		}
		return null;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		// v0.6: Remove keyboard listener
		if (rootView != null && keyboardListener != null) {
			rootView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardListener);
		}
		keyboardListener = null;

		if (panelAnimator != null && panelAnimator.isRunning()) {
			panelAnimator.cancel();
		}
		panelAnimator = null;

		// Clean up Pip-Boy effect overlays
		if (phosphorGlowOverlay != null) {
			phosphorGlowOverlay.stopPulse();
			phosphorGlowOverlay = null;
		}
		scanLineOverlay = null;

		rootView = null;
		panelContainer = null;
		clickCatcher = null;
		dragHandle = null;
		contentFrame = null;
	}
}
