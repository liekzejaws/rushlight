package net.osmand.plus.lampp;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;

/**
 * LAMPP: Three visual presets for the Pip-Boy UI system.
 * Each preset defines a complete color palette for tab bar, panels, and text.
 */
public enum LamppStylePreset {

	PIP_BOY("Pip-Boy", "Retro-futuristic terminal"),
	MODERN("Modern", "Sleek teal and grey"),
	CLASSIC("Classic OsmAnd", "Default OsmAnd look");

	private final String displayName;
	private final String description;

	LamppStylePreset(@NonNull String displayName, @NonNull String description) {
		this.displayName = displayName;
		this.description = description;
	}

	@NonNull
	public String getDisplayName() {
		return displayName;
	}

	@NonNull
	public String getDescription() {
		return description;
	}

	// ---- Theme overlay ----

	/**
	 * Returns the style resource for the ContextThemeWrapper overlay applied to panel content.
	 * Classic returns 0 (no overlay — use OsmAnd's native theme).
	 */
	@StyleRes
	public int getOverlayStyleRes() {
		switch (this) {
			case PIP_BOY:
				return R.style.LamppPanelOverlay_PipBoy;
			case MODERN:
				return R.style.LamppPanelOverlay_Modern;
			case CLASSIC:
			default:
				return 0;
		}
	}

	// ---- Color resolution ----

	@ColorInt
	public int getTabActiveColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_tab_active);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.lampp_classic_tab_active_dark
								: R.color.lampp_classic_tab_active_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_tab_active);
		}
	}

	@ColorInt
	public int getTabInactiveColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_tab_inactive);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.lampp_classic_tab_inactive_dark
								: R.color.lampp_classic_tab_inactive_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_tab_inactive);
		}
	}

	@ColorInt
	public int getTabBarBgColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_tab_bar_bg);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.lampp_classic_tab_bar_bg_dark
								: R.color.lampp_classic_tab_bar_bg_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_bg_tab_bar);
		}
	}

	@ColorInt
	public int getPanelBgColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_panel_bg);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.lampp_classic_panel_bg_dark
								: R.color.lampp_classic_panel_bg_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_bg_panel);
		}
	}

	@ColorInt
	public int getTextPrimaryColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_text_primary);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.lampp_classic_text_primary_dark
								: R.color.lampp_classic_text_primary_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_text_primary);
		}
	}

	@ColorInt
	public int getTextSecondaryColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_text_secondary);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.lampp_classic_text_secondary_dark
								: R.color.lampp_classic_text_secondary_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_text_secondary);
		}
	}

	@ColorInt
	public int getTabActiveBgColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_tab_active_bg);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.lampp_classic_tab_active_bg_dark
								: R.color.lampp_classic_tab_active_bg_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_tab_active_bg);
		}
	}

	@ColorInt
	public int getPrimaryColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_primary);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.lampp_classic_tab_active_dark
								: R.color.lampp_classic_tab_active_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_green_primary);
		}
	}

	// ---- Drag handle ----

	@ColorInt
	public int getDragHandleColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_tab_inactive);
			case CLASSIC:
				return ContextCompat.getColor(context, R.color.lampp_drag_handle);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_green_dim);
		}
	}

	// ---- Chat message colors ----

	@ColorInt
	public int getUserMessageBgColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_primary);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.active_color_primary_dark
								: R.color.active_color_primary_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_green_dark);
		}
	}

	@ColorInt
	public int getUserMessageTextColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_text_primary);
			case CLASSIC:
				return ContextCompat.getColor(context, R.color.text_color_primary_dark);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_bg_solid);
		}
	}

	@ColorInt
	public int getAiMessageBgColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_card_bg);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.card_and_list_background_dark
								: R.color.card_and_list_background_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_pipboy_card_bg);
		}
	}

	@ColorInt
	public int getAiMessageTextColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case MODERN:
				return ContextCompat.getColor(context, R.color.lampp_modern_text_primary);
			case CLASSIC:
				return ContextCompat.getColor(context,
						nightMode ? R.color.text_color_primary_dark
								: R.color.text_color_primary_light);
			case PIP_BOY:
			default:
				return ContextCompat.getColor(context, R.color.lampp_text_primary);
		}
	}

	@ColorInt
	public int getSystemMessageBgColor(@NonNull Context context, boolean nightMode) {
		switch (this) {
			case PIP_BOY:
				return ContextCompat.getColor(context, R.color.lampp_accent_red);
			case MODERN:
			case CLASSIC:
			default:
				return ContextCompat.getColor(context, R.color.color_warning);
		}
	}
}
