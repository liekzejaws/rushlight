package net.osmand.plus.lampp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;

import org.apache.commons.logging.Log;

/**
 * LAMPP: Coordinates the side tab bar and panel fragments.
 * Owned by MapActivity, manages which panel is visible.
 * When activeTab is null, no panel is open and no tab is highlighted.
 */
public class LamppPanelManager {

	private static final Log LOG = PlatformUtil.getLog(LamppPanelManager.class);

	private final MapActivity mapActivity;
	@Nullable
	private LamppSideTabBar tabBar;
	@Nullable
	private LamppTab activeTab = null;

	public LamppPanelManager(@NonNull MapActivity mapActivity) {
		this.mapActivity = mapActivity;
	}

	public void setTabBar(@Nullable LamppSideTabBar tabBar) {
		this.tabBar = tabBar;
		if (tabBar != null) {
			tabBar.setOnTabSelectedListener(this::onTabSelected);
			tabBar.setActiveTab(activeTab);
		}
	}

	private void onTabSelected(@NonNull LamppTab tab) {
		openPanel(tab);
	}

	public void openPanel(@NonNull LamppTab tab) {
		// Tapping the already-active tab collapses it (toggle behavior)
		if (tab.equals(activeTab)) {
			closeActivePanel(true);
			return;
		}

		// Close existing panel if any
		closeActivePanel(false);

		// Create and show the panel fragment
		LamppPanelFragment fragment = createFragmentForTab(tab);
		if (fragment == null) {
			LOG.error("Failed to create fragment for tab: " + tab);
			return;
		}

		FragmentManager fm = mapActivity.getSupportFragmentManager();
		fm.beginTransaction()
				.replace(R.id.lamppPanelContainer, fragment, tab.getFragmentTag())
				.addToBackStack(tab.getFragmentTag())
				.commitAllowingStateLoss();

		activeTab = tab;
		saveActiveTabPreference();
		if (tabBar != null) {
			tabBar.setActiveTab(tab);
		}

		// Disable drawer while panel is open
		mapActivity.disableDrawer();
		LOG.info("LAMPP: Opened panel for tab " + tab.getTitle());
	}

	public void closeActivePanel(boolean animated) {
		if (activeTab == null) return;

		FragmentManager fm = mapActivity.getSupportFragmentManager();
		String tag = activeTab.getFragmentTag();
		if (tag != null) {
			Fragment fragment = fm.findFragmentByTag(tag);
			if (fragment != null) {
				if (animated && fragment instanceof LamppPanelFragment) {
					((LamppPanelFragment) fragment).collapsePanel(() -> {
						removeFragment(fm, tag);
					});
				} else {
					removeFragment(fm, tag);
				}
			}
		}

		activeTab = null;
		saveActiveTabPreference();
		if (tabBar != null) {
			tabBar.setActiveTab(null);
		}

		// Re-enable drawer
		mapActivity.enableDrawer();
	}

	private void removeFragment(@NonNull FragmentManager fm, @NonNull String tag) {
		try {
			fm.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE);
		} catch (Exception e) {
			LOG.error("Error removing lampp panel fragment: " + e.getMessage());
		}
	}

	public boolean isPanelOpen() {
		return activeTab != null;
	}

	@Nullable
	public LamppTab getActiveTab() {
		return activeTab;
	}

	@Nullable
	private LamppPanelFragment createFragmentForTab(@NonNull LamppTab tab) {
		try {
			switch (tab) {
				case AI_CHAT:
					return (LamppPanelFragment) Class.forName(
							"net.osmand.plus.ai.LlmChatFragment").newInstance();
				case WIKI:
					return (LamppPanelFragment) Class.forName(
							"net.osmand.plus.wikipedia.ZimBrowserFragment").newInstance();
				case P2P:
					return (LamppPanelFragment) Class.forName(
							"net.osmand.plus.plugins.p2pshare.ui.P2pShareFragment").newInstance();
				case MORSE:
					return new net.osmand.plus.morse.MorseFragment();
				case TOOLS:
					return new ToolsFragment();
				default:
					return null;
			}
		} catch (Exception e) {
			LOG.error("Error creating fragment for tab " + tab + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Called when a back press is intercepted.
	 * @return true if the panel was closed (consuming the back press)
	 */
	public boolean onBackPressed() {
		if (isPanelOpen()) {
			closeActivePanel(true);
			return true;
		}
		return false;
	}

	/**
	 * Re-apply theme colors to tab bar and reopen active panel with new preset overlay.
	 * Called when the user switches presets in the Tools panel.
	 * Uses a crossfade animation instead of an abrupt close+reopen.
	 */
	public void refreshTheme() {
		if (tabBar != null) {
			tabBar.animateColorRefresh();
		}
		// Crossfade: fade out old content, swap fragment, fade in new content
		if (activeTab != null) {
			LamppTab current = activeTab;
			FragmentManager fm = mapActivity.getSupportFragmentManager();
			Fragment fragment = fm.findFragmentByTag(current.getFragmentTag());
			if (fragment instanceof LamppPanelFragment) {
				((LamppPanelFragment) fragment).animatePresetTransition(() -> {
					closeActivePanel(false);
					openPanel(current);
				});
			} else {
				closeActivePanel(false);
				openPanel(current);
			}
		}
	}

	/**
	 * Persist the active tab to SharedPreferences so it survives app restarts.
	 */
	private void saveActiveTabPreference() {
		OsmandApplication app = (OsmandApplication) mapActivity.getApplication();
		app.getSettings().LAMPP_ACTIVE_TAB.set(activeTab != null ? activeTab.name() : "");
	}

	/**
	 * Restore the previously open panel from SharedPreferences.
	 * Called from MapActivity.onCreate() after the tab bar is set up.
	 */
	public void restorePanelIfNeeded() {
		OsmandApplication app = (OsmandApplication) mapActivity.getApplication();
		String tabName = app.getSettings().LAMPP_ACTIVE_TAB.get();
		if (tabName != null && !tabName.isEmpty()) {
			try {
				LamppTab tab = LamppTab.valueOf(tabName);
				if (tab.isVisibleInTabBar() && activeTab == null) {
					openPanel(tab);
				}
			} catch (IllegalArgumentException e) {
				app.getSettings().LAMPP_ACTIVE_TAB.set("");
			}
		}
	}
}
