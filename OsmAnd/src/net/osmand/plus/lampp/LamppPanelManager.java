/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.lampp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.ai.LlmManager;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.p2pshare.P2pShareManager;
import net.osmand.plus.plugins.p2pshare.P2pSharePlugin;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.security.PanelLockManager;
import net.osmand.plus.wikipedia.WikipediaPlugin;
import net.osmand.plus.wikipedia.ZimFileManager;

import org.apache.commons.logging.Log;

/**
 * LAMPP: Coordinates the side tab bar and panel fragments.
 * Owned by MapActivity, manages which panel is visible.
 * When activeTab is null, no panel is open and no tab is highlighted.
 */
public class LamppPanelManager {

	private static final Log LOG = PlatformUtil.getLog(LamppPanelManager.class);

	private final MapActivity mapActivity;
	private final PanelLockManager panelLockManager;
	private final TabBadgeManager tabBadgeManager;
	@Nullable
	private LamppSideTabBar tabBar;
	@Nullable
	private LamppTab activeTab = null;
	private boolean p2pListenerRegistered = false;
	@Nullable
	private String pendingQuery = null; // Phase 17: query to pass to next opened panel

	public LamppPanelManager(@NonNull MapActivity mapActivity) {
		this.mapActivity = mapActivity;
		OsmandApplication app = (OsmandApplication) mapActivity.getApplication();
		this.panelLockManager = new PanelLockManager(app.getSettings());
		this.tabBadgeManager = new TabBadgeManager();
	}

	public void setTabBar(@Nullable LamppSideTabBar tabBar) {
		this.tabBar = tabBar;
		if (tabBar != null) {
			tabBar.setOnTabSelectedListener(this::onTabSelected);
			tabBar.setActiveTab(activeTab);

			// Phase 12: Wire badge manager to tab bar display
			tabBadgeManager.setListener((tab, count) -> {
				mapActivity.runOnUiThread(() -> tabBar.setBadgeCount(tab, count));
			});

			// Phase 16: Register P2P badge listener early so peer discoveries
			// before first panel open are not missed
			registerP2pBadgeListener();
		}
	}

	/**
	 * Phase 12: Get the tab badge manager for fragments to increment badges.
	 */
	@NonNull
	public TabBadgeManager getTabBadgeManager() {
		return tabBadgeManager;
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

		// Authenticate before opening panel (if screen lock is enabled)
		panelLockManager.authenticate(mapActivity,
				() -> openPanelInternal(tab),
				() -> {
					OsmandApplication app = (OsmandApplication) mapActivity.getApplication();
					app.showToastMessage(R.string.rushlight_auth_required);
					LOG.info("Panel access denied - authentication failed");
				});
	}

	private void openPanelInternal(@NonNull LamppTab tab) {
		// Phase 12: Clear badge when opening this tab
		tabBadgeManager.clearBadge(tab);

		// Close existing panel if any
		closeActivePanel(false);

		// Create and show the panel fragment
		LamppPanelFragment fragment = createFragmentForTab(tab);
		if (fragment == null) {
			LOG.error("Failed to create fragment for tab: " + tab);
			OsmandApplication app2 = (OsmandApplication) mapActivity.getApplication();
			app2.showShortToastMessage(R.string.panel_open_failed);
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
			// v0.7: Fade out tab bar when panel opens to prevent overlap
			tabBar.animate()
					.alpha(0f)
					.setDuration(200)
					.setInterpolator(new DecelerateInterpolator())
					.withEndAction(() -> tabBar.setVisibility(View.INVISIBLE))
					.start();
		}

		// Disable drawer while panel is open
		mapActivity.disableDrawer();
		LOG.info("Rushlight: Opened panel for tab " + tab.getTitle());
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
			// v0.7: Restore tab bar when panel closes
			tabBar.setVisibility(View.VISIBLE);
			tabBar.animate()
					.alpha(1f)
					.setDuration(200)
					.setInterpolator(new DecelerateInterpolator())
					.start();
		}

		// v0.5: Refresh content status badges (content may have changed in panel)
		updateContentStatusBadges();

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

	/**
	 * Phase 17: Open a panel tab with a prefilled query.
	 * Used by "Ask AI" buttons in guides and Wikipedia to switch to AI Chat
	 * with a pre-composed question.
	 */
	public void openPanelWithQuery(@NonNull LamppTab tab, @NonNull String query) {
		pendingQuery = query;
		// If the target panel is already open, deliver query directly
		if (tab.equals(activeTab)) {
			deliverPendingQuery();
			return;
		}
		openPanel(tab);
	}

	/**
	 * Phase 17: Retrieve and clear the pending query.
	 * Called by LlmChatFragment.onPanelViewCreated() to check for a pending query.
	 */
	@Nullable
	public String consumePendingQuery() {
		String query = pendingQuery;
		pendingQuery = null;
		return query;
	}

	/**
	 * Phase 17: Deliver a pending query to the active AI Chat fragment.
	 */
	private void deliverPendingQuery() {
		if (pendingQuery == null || activeTab != LamppTab.AI_CHAT) return;
		FragmentManager fm = mapActivity.getSupportFragmentManager();
		String tag = activeTab.getFragmentTag();
		if (tag != null) {
			Fragment fragment = fm.findFragmentByTag(tag);
			if (fragment != null) {
				try {
					java.lang.reflect.Method prefill = fragment.getClass()
							.getMethod("prefillQuery", String.class);
					prefill.invoke(fragment, pendingQuery);
				} catch (Exception e) {
					LOG.warn("Failed to deliver query to AI panel: " + e.getMessage());
				}
			}
		}
		pendingQuery = null;
	}

	@Nullable
	private LamppPanelFragment createFragmentForTab(@NonNull LamppTab tab) {
		switch (tab) {
			case AI_CHAT:
				return new net.osmand.plus.ai.LlmChatFragment();
			case WIKI:
				return new net.osmand.plus.wikipedia.ZimBrowserFragment();
			case P2P:
				return new net.osmand.plus.plugins.p2pshare.ui.P2pShareFragment();
			case MORSE:
				return new net.osmand.plus.morse.MorseFragment();
			case GUIDES:
				return new net.osmand.plus.guides.GuidesFragment();
			case TOOLS:
				return new ToolsFragment();
			default:
				return null;
		}
	}

	/**
	 * Called when a back press is intercepted.
	 * @return true if the panel was closed (consuming the back press)
	 */
	public boolean onBackPressed() {
		if (isPanelOpen()) {
			// First, let the active fragment handle internal back navigation
			if (activeTab != null) {
				FragmentManager fm = mapActivity.getSupportFragmentManager();
				String tag = activeTab.getFragmentTag();
				if (tag != null) {
					Fragment fragment = fm.findFragmentByTag(tag);
					if (fragment instanceof LamppPanelFragment
							&& ((LamppPanelFragment) fragment).onBackPressed()) {
						return true;
					}
				}
			}
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
		// Phase 12: Sync map dark mode with theme preset
		OsmandApplication app = (OsmandApplication) mapActivity.getApplication();
		LamppThemeUtils.syncMapDarkMode(app);

		if (tabBar != null) {
			tabBar.animateColorRefresh();
		}
		// Crossfade: fade out old content, swap fragment, fade in new content
		// Use openPanelInternal to bypass auth (panel is already open)
		if (activeTab != null) {
			LamppTab current = activeTab;
			FragmentManager fm = mapActivity.getSupportFragmentManager();
			Fragment fragment = fm.findFragmentByTag(current.getFragmentTag());
			if (fragment instanceof LamppPanelFragment) {
				((LamppPanelFragment) fragment).animatePresetTransition(() -> {
					closeActivePanel(false);
					openPanelInternal(current);
				});
			} else {
				closeActivePanel(false);
				openPanelInternal(current);
			}
		}
	}

	/**
	 * v0.5: Update content readiness badges on the tab bar.
	 * Shows a red dot (badge count 1) on tabs that need attention:
	 * - AI Chat tab: red dot if no model downloaded
	 * - Wiki tab: red dot if no ZIM loaded
	 * Called on startup and after panel operations.
	 */
	public void updateContentStatusBadges() {
		if (tabBar == null) return;
		OsmandApplication app = (OsmandApplication) mapActivity.getApplication();

		// AI Chat: check if any models are downloaded
		// v1.4: Check filesystem directly to avoid creating expensive LlmManager instance
		try {
			java.io.File modelsDir = new java.io.File(app.getAppPath(null),
					net.osmand.plus.ai.LlmManager.MODELS_DIR);
			boolean hasModels = false;
			if (modelsDir.exists()) {
				java.io.File[] ggufFiles = modelsDir.listFiles(
						(d, name) -> name.endsWith(".gguf"));
				hasModels = ggufFiles != null && ggufFiles.length > 0;
			}
			tabBar.setBadgeCount(LamppTab.AI_CHAT, hasModels ? 0 : 1);
		} catch (Exception e) {
			LOG.warn("Could not check AI model status for badge: " + e.getMessage());
		}

		// Wiki: check if any ZIM is open
		try {
			WikipediaPlugin plugin = PluginsHelper.getPlugin(WikipediaPlugin.class);
			if (plugin != null) {
				ZimFileManager zfm = plugin.getZimFileManager();
				if (zfm == null || !zfm.isOpen()) {
					tabBar.setBadgeCount(LamppTab.WIKI, 1);
				} else {
					tabBar.setBadgeCount(LamppTab.WIKI, 0);
				}
			}
		} catch (Exception e) {
			LOG.warn("Could not check ZIM status for badge: " + e.getMessage());
		}
	}

	/**
	 * Phase 12: Register a P2P event listener that increments badges
	 * when the P2P panel is not the active tab.
	 */
	private void registerP2pBadgeListener() {
		if (p2pListenerRegistered) return;
		p2pListenerRegistered = true;
		P2pSharePlugin plugin = PluginsHelper.getPlugin(P2pSharePlugin.class);
		if (plugin != null) {
			P2pShareManager shareManager = plugin.getShareManager();
			if (shareManager != null) {
				shareManager.addListener(new P2pShareManager.P2pShareListener() {
					@Override
					public void onPeerDiscovered(@NonNull DiscoveredPeer peer) {
						if (activeTab != LamppTab.P2P) {
							mapActivity.runOnUiThread(() -> tabBadgeManager.incrementBadge(LamppTab.P2P));
						}
					}

					@Override
					public void onTransferComplete(@NonNull String filename, boolean success,
					                                @Nullable String error) {
						if (activeTab != LamppTab.P2P && success) {
							mapActivity.runOnUiThread(() -> tabBadgeManager.incrementBadge(LamppTab.P2P));
						}
					}
				});
				LOG.info("P2P badge listener registered");
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

		// Phase 12: Apply dark map sync on startup if Pip-Boy is active
		LamppThemeUtils.syncMapDarkMode(app);

		// Phase 12: Register P2P badge listener for events while P2P panel is inactive
		registerP2pBadgeListener();

		// v1.4: Defer badge check to avoid expensive LlmManager creation during cold start
		mapActivity.getWindow().getDecorView().postDelayed(this::updateContentStatusBadges, 2000);

		// v1.4: Startup profiler milestone
		net.osmand.plus.ai.StartupProfiler profiler = net.osmand.plus.ai.StartupProfiler.get();
		if (profiler != null) {
			profiler.markPanelInit();
		}

		// Phase 12: Show onboarding overlay on first launch (slight delay for map to load)
		mapActivity.getWindow().getDecorView().postDelayed(() -> {
			OnboardingOverlay.showIfNeeded(mapActivity);
		}, 2500);

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
