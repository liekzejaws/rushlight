package net.osmand.plus.lampp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;

/**
 * LAMPP: Floating vertical tab bar for the Pip-Boy interface.
 * Positioned on the right edge of the map screen.
 * Shows only visible tabs (AI Chat, Wiki, P2P, Tools — no MAP icon).
 * When no panel is open, no tab is highlighted.
 */
public class LamppSideTabBar extends LinearLayout {

	@Nullable
	private LamppTab activeTab = null;
	private OnTabSelectedListener tabSelectedListener;
	private final ImageView[] tabViews = new ImageView[LamppTab.visibleTabs().length];
	private final TextView[] badgeViews = new TextView[LamppTab.visibleTabs().length];

	// Tab transition animation
	private int previousActiveIndex = -1;
	@Nullable
	private ValueAnimator tabTransitionAnimator;
	private static final int TAB_TRANSITION_DURATION = 200;

	// Cached tab bar background color for animated refresh
	private int cachedTabBarBgColor = 0;

	public interface OnTabSelectedListener {
		void onTabSelected(@NonNull LamppTab tab);
	}

	public LamppSideTabBar(Context context) {
		super(context);
		init(context);
	}

	public LamppSideTabBar(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public LamppSideTabBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		setOrientation(VERTICAL);
		setClipChildren(false);
		setClipToPadding(false);

		LayoutInflater inflater = LayoutInflater.from(context);
		LamppTab[] tabs = LamppTab.visibleTabs();

		for (int i = 0; i < tabs.length; i++) {
			LamppTab tab = tabs[i];
			View itemView = inflater.inflate(R.layout.lampp_tab_item_badged, this, false);
			ImageView tabView = itemView.findViewById(R.id.tab_icon);
			TextView badgeView = itemView.findViewById(R.id.tab_badge);

			tabView.setImageResource(tab.getIconRes());
			tabView.setContentDescription(tab.getTitle());

			// Add spacing between tabs
			if (i > 0) {
				LayoutParams lp = new LayoutParams(
						LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				int spacing = context.getResources().getDimensionPixelSize(R.dimen.lampp_tab_spacing);
				lp.topMargin = spacing;
				itemView.setLayoutParams(lp);
			}

			final LamppTab clickedTab = tab;
			itemView.setOnClickListener(v -> {
				if (tabSelectedListener != null) {
					tabSelectedListener.onTabSelected(clickedTab);
				}
			});

			tabViews[i] = tabView;
			badgeViews[i] = badgeView;
			addView(itemView);
		}

		updateTabStates();

		// Entrance animation: fade in after a short delay so the map loads first
		setAlpha(0f);
		animate()
				.alpha(1f)
				.setDuration(300)
				.setStartDelay(200)
				.setInterpolator(new DecelerateInterpolator())
				.start();
	}

	public void setOnTabSelectedListener(@Nullable OnTabSelectedListener listener) {
		this.tabSelectedListener = listener;
	}

	public void setActiveTab(@Nullable LamppTab tab) {
		if (activeTab != tab) {
			// Track the previous active tab index for animated transition
			LamppTab[] tabs = LamppTab.visibleTabs();
			previousActiveIndex = -1;
			if (activeTab != null) {
				for (int i = 0; i < tabs.length; i++) {
					if (tabs[i] == activeTab) {
						previousActiveIndex = i;
						break;
					}
				}
			}
			activeTab = tab;
			animateTabTransition();
		}
	}

	@Nullable
	public LamppTab getActiveTab() {
		return activeTab;
	}

	/**
	 * Re-apply colors from the current theme preset.
	 * Called when the user changes the theme preset in Tools.
	 */
	public void refreshColors() {
		updateTabStates();
	}

	/**
	 * Animate the tab bar background color to the new preset's color.
	 * Called during theme refresh for a smooth color transition.
	 */
	public void animateColorRefresh() {
		Context context = getContext();
		OsmandApplication app = context != null
				? (OsmandApplication) context.getApplicationContext() : null;
		if (app == null) {
			updateTabStates();
			return;
		}

		int oldBg = cachedTabBarBgColor;
		int newBg = LamppThemeUtils.getTabBarBgColor(context, app);

		if (oldBg == 0 || oldBg == newBg) {
			// No previous color or same color — just update immediately
			updateTabStates();
			return;
		}

		ValueAnimator bgAnimator = ValueAnimator.ofArgb(oldBg, newBg);
		bgAnimator.setDuration(TAB_TRANSITION_DURATION);
		bgAnimator.setInterpolator(new DecelerateInterpolator());
		bgAnimator.addUpdateListener(anim -> {
			int color = (int) anim.getAnimatedValue();
			if (getBackground() instanceof GradientDrawable) {
				((GradientDrawable) getBackground()).setColor(color);
			}
		});
		bgAnimator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				updateTabStates(); // finalize exact colors
			}
		});
		bgAnimator.start();
	}

	/**
	 * Animate the tab icon tint cross-fade between old and new active tabs.
	 */
	private void animateTabTransition() {
		// Cancel any running animation
		if (tabTransitionAnimator != null && tabTransitionAnimator.isRunning()) {
			tabTransitionAnimator.cancel();
		}

		Context context = getContext();
		OsmandApplication app = context != null
				? (OsmandApplication) context.getApplicationContext() : null;
		if (app == null) {
			updateTabStates(); // fallback to instant
			return;
		}

		int activeColor = LamppThemeUtils.getTabActiveColor(context, app);
		int inactiveColor = LamppThemeUtils.getTabInactiveColor(context, app);

		// Update tab bar background immediately
		updateTabBarBackground(context, app);

		LamppTab[] tabs = LamppTab.visibleTabs();
		int newActiveIndex = -1;
		if (activeTab != null) {
			for (int i = 0; i < tabs.length; i++) {
				if (tabs[i] == activeTab) {
					newActiveIndex = i;
					break;
				}
			}
		}

		final int oldIdx = previousActiveIndex;
		final int newIdx = newActiveIndex;

		// Set non-animated tabs immediately
		for (int i = 0; i < tabs.length; i++) {
			if (i == oldIdx || i == newIdx) continue;
			if (tabViews[i] != null) {
				tabViews[i].setBackgroundResource(R.drawable.bg_lampp_tab_inactive);
				ImageViewCompat.setImageTintList(tabViews[i],
						ColorStateList.valueOf(inactiveColor));
			}
		}

		// Set backgrounds immediately (they can't cross-fade easily)
		if (newIdx >= 0 && newIdx < tabViews.length && tabViews[newIdx] != null) {
			setTabActiveBackground(tabViews[newIdx], context, app);
		}
		if (oldIdx >= 0 && oldIdx < tabViews.length && tabViews[oldIdx] != null) {
			tabViews[oldIdx].setBackgroundResource(R.drawable.bg_lampp_tab_inactive);
		}

		// Animate icon tint cross-fade
		tabTransitionAnimator = ValueAnimator.ofFloat(0f, 1f);
		tabTransitionAnimator.setDuration(TAB_TRANSITION_DURATION);
		tabTransitionAnimator.setInterpolator(new DecelerateInterpolator());
		tabTransitionAnimator.addUpdateListener(anim -> {
			float fraction = (float) anim.getAnimatedValue();

			// Old active tab: tint fades from activeColor to inactiveColor
			if (oldIdx >= 0 && oldIdx < tabViews.length && tabViews[oldIdx] != null) {
				int tint = blendColors(activeColor, inactiveColor, fraction);
				ImageViewCompat.setImageTintList(tabViews[oldIdx],
						ColorStateList.valueOf(tint));
			}

			// New active tab: tint fades from inactiveColor to activeColor
			if (newIdx >= 0 && newIdx < tabViews.length && tabViews[newIdx] != null) {
				int tint = blendColors(inactiveColor, activeColor, fraction);
				ImageViewCompat.setImageTintList(tabViews[newIdx],
						ColorStateList.valueOf(tint));
			}
		});
		tabTransitionAnimator.addListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				// Set final precise states
				updateTabStates();
			}
		});
		tabTransitionAnimator.start();
	}

	/**
	 * Set the active tab background (LayerDrawable with fill + border).
	 */
	private void setTabActiveBackground(@NonNull ImageView tabView,
	                                    @NonNull Context context,
	                                    @NonNull OsmandApplication app) {
		int activeBgColor = LamppThemeUtils.getTabActiveBgColor(context, app);
		int activeBorderColor = LamppThemeUtils.getPrimaryColor(context, app);
		float cornerRadius = 8 * getResources().getDisplayMetrics().density;
		float borderWidth = 1.5f * getResources().getDisplayMetrics().density;

		GradientDrawable bgShape = new GradientDrawable();
		bgShape.setShape(GradientDrawable.RECTANGLE);
		bgShape.setColor(activeBgColor);
		bgShape.setCornerRadius(cornerRadius);

		GradientDrawable borderShape = new GradientDrawable();
		borderShape.setShape(GradientDrawable.RECTANGLE);
		borderShape.setColor(0x00000000);
		borderShape.setStroke((int) borderWidth, activeBorderColor);
		borderShape.setCornerRadius(cornerRadius);

		tabView.setBackground(new LayerDrawable(
				new android.graphics.drawable.Drawable[]{bgShape, borderShape}));
	}

	/**
	 * Update the tab bar container background with preset color.
	 */
	private void updateTabBarBackground(@NonNull Context context, @NonNull OsmandApplication app) {
		int tabBarBgColor = LamppThemeUtils.getTabBarBgColor(context, app);
		cachedTabBarBgColor = tabBarBgColor;
		float tabBarCornerRadius = getResources().getDimension(R.dimen.lampp_tab_bar_corner_radius);
		GradientDrawable tabBarBg = new GradientDrawable();
		tabBarBg.setShape(GradientDrawable.RECTANGLE);
		tabBarBg.setColor(tabBarBgColor);
		tabBarBg.setCornerRadius(tabBarCornerRadius);
		setBackground(tabBarBg);
	}

	private void updateTabStates() {
		LamppTab[] tabs = LamppTab.visibleTabs();
		Context context = getContext();
		OsmandApplication app = context != null
				? (OsmandApplication) context.getApplicationContext() : null;

		int activeColor;
		int inactiveColor;

		if (app != null) {
			activeColor = LamppThemeUtils.getTabActiveColor(context, app);
			inactiveColor = LamppThemeUtils.getTabInactiveColor(context, app);
			updateTabBarBackground(context, app);
		} else {
			// Fallback to Pip-Boy defaults
			activeColor = getResources().getColor(R.color.lampp_tab_active);
			inactiveColor = getResources().getColor(R.color.lampp_tab_inactive);
		}

		for (int i = 0; i < tabs.length; i++) {
			ImageView tabView = tabViews[i];
			if (tabView == null) continue;

			boolean isActive = activeTab != null && tabs[i] == activeTab;
			if (isActive && app != null) {
				setTabActiveBackground(tabView, context, app);
			} else {
				tabView.setBackgroundResource(R.drawable.bg_lampp_tab_inactive);
			}
			ImageViewCompat.setImageTintList(tabView,
					ColorStateList.valueOf(isActive ? activeColor : inactiveColor));
		}
	}

	/**
	 * Phase 12: Set badge count for a specific tab.
	 * Shows a small red dot with count at the top-right corner of the tab icon.
	 *
	 * @param tab   The tab to update
	 * @param count Badge count (0 = hidden)
	 */
	public void setBadgeCount(@NonNull LamppTab tab, int count) {
		LamppTab[] tabs = LamppTab.visibleTabs();
		for (int i = 0; i < tabs.length; i++) {
			if (tabs[i] == tab && i < badgeViews.length && badgeViews[i] != null) {
				TextView badge = badgeViews[i];
				if (count <= 0) {
					badge.setVisibility(View.GONE);
				} else {
					badge.setVisibility(View.VISIBLE);
					badge.setText(count > 99 ? "99+" : String.valueOf(count));

					// Red circle background
					GradientDrawable badgeBg = new GradientDrawable();
					badgeBg.setShape(GradientDrawable.OVAL);
					badgeBg.setColor(0xFFE53935); // Material Red 600
					badge.setBackground(badgeBg);
				}
				break;
			}
		}
	}

	/**
	 * Get the screen coordinates of a tab view for onboarding overlays.
	 */
	@NonNull
	public int[] getTabViewBounds(int index) {
		int[] location = new int[2];
		if (index >= 0 && index < tabViews.length && tabViews[index] != null) {
			tabViews[index].getLocationOnScreen(location);
		}
		return location;
	}

	/**
	 * Blend two ARGB colors by a fraction (0.0 = from, 1.0 = to).
	 */
	private static int blendColors(int from, int to, float fraction) {
		int startA = Color.alpha(from), startR = Color.red(from);
		int startG = Color.green(from), startB = Color.blue(from);
		int endA = Color.alpha(to), endR = Color.red(to);
		int endG = Color.green(to), endB = Color.blue(to);
		return Color.argb(
				startA + (int) ((endA - startA) * fraction),
				startR + (int) ((endR - startR) * fraction),
				startG + (int) ((endG - startG) * fraction),
				startB + (int) ((endB - startB) * fraction));
	}
}
