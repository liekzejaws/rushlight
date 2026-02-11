package net.osmand.plus.lampp;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import net.osmand.plus.R;

/**
 * LAMPP: Tab definitions for the Pip-Boy side tab bar.
 * Each tab maps to a feature panel that overlays the map.
 */
public enum LamppTab {

	MAP("Map", R.drawable.ic_action_world_globe, null),
	AI_CHAT("AI Chat", R.drawable.ic_action_help, "LlmChatFragment"),
	WIKI("Wikipedia", R.drawable.ic_action_read_from_file, "ZimBrowserFragment"),
	P2P("P2P Share", R.drawable.ic_action_bluetooth, "P2pShareFragment"),
	MORSE("Morse", R.drawable.ic_action_signal, "MorseFragment"),
	TOOLS("Tools", R.drawable.ic_action_settings, "ToolsFragment");

	private final String title;
	@DrawableRes
	private final int iconRes;
	private final String fragmentTag;

	LamppTab(@NonNull String title, @DrawableRes int iconRes, String fragmentTag) {
		this.title = title;
		this.iconRes = iconRes;
		this.fragmentTag = fragmentTag;
	}

	@NonNull
	public String getTitle() {
		return title;
	}

	@DrawableRes
	public int getIconRes() {
		return iconRes;
	}

	public String getFragmentTag() {
		return fragmentTag;
	}

	public boolean hasPanel() {
		return fragmentTag != null;
	}

	/**
	 * Whether this tab should appear in the visible side tab bar.
	 * MAP is excluded — it's only used as a sentinel for "no panel open".
	 */
	public boolean isVisibleInTabBar() {
		return this != MAP;
	}

	/**
	 * Returns only the tabs that should be displayed in the side tab bar.
	 */
	public static LamppTab[] visibleTabs() {
		return new LamppTab[]{AI_CHAT, WIKI, P2P, MORSE, TOOLS};
	}
}
