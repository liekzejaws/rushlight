package net.osmand.plus.lampp;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;

/**
 * Manages Simple Mode — a reduced-complexity view for non-technical users.
 *
 * When enabled:
 * - Hides advanced tabs (Morse, Guides) from the side tab bar
 * - Simplifies AI chat (hides RAG settings, system prompt editor)
 * - Hides advanced security settings (only shows basic PIN)
 * - Shows only: Map, AI Chat, Wiki, P2P, Tools
 *
 * Reduces cognitive load in crisis situations where users need
 * immediate access to core features without distraction.
 */
public class SimpleModeManager {

	private static final Log LOG = PlatformUtil.getLog(SimpleModeManager.class);
	private static final String PREF_SIMPLE_MODE = "lampp_simple_mode_enabled";

	private final OsmandApplication app;

	public SimpleModeManager(@NonNull OsmandApplication app) {
		this.app = app;
	}

	/**
	 * Check if Simple Mode is enabled.
	 */
	public boolean isSimpleModeEnabled() {
		return app.getSettings().getCustomRenderBooleanProperty(PREF_SIMPLE_MODE).get();
	}

	/**
	 * Toggle Simple Mode on/off.
	 */
	public void setSimpleModeEnabled(boolean enabled) {
		app.getSettings().getCustomRenderBooleanProperty(PREF_SIMPLE_MODE).set(enabled);
		LOG.info("Simple Mode " + (enabled ? "enabled" : "disabled"));
	}

	/**
	 * Get the visible tabs for the current mode.
	 * In Simple Mode, only core tabs are shown.
	 */
	@NonNull
	public LamppTab[] getVisibleTabs() {
		if (isSimpleModeEnabled()) {
			return new LamppTab[]{
					LamppTab.AI_CHAT,
					LamppTab.WIKI,
					LamppTab.P2P,
					LamppTab.TOOLS
			};
		} else {
			return LamppTab.visibleTabs();
		}
	}

	/**
	 * Check if a specific tab should be visible in the current mode.
	 */
	public boolean isTabVisible(@NonNull LamppTab tab) {
		if (!isSimpleModeEnabled()) {
			return tab.isVisibleInTabBar();
		}

		// In simple mode, only show core tabs
		switch (tab) {
			case AI_CHAT:
			case WIKI:
			case P2P:
			case TOOLS:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Check if advanced AI settings should be shown (system prompt, RAG config).
	 */
	public boolean shouldShowAdvancedAiSettings() {
		return !isSimpleModeEnabled();
	}

	/**
	 * Check if advanced security settings should be shown (duress PIN, stealth config).
	 */
	public boolean shouldShowAdvancedSecuritySettings() {
		return !isSimpleModeEnabled();
	}
}
