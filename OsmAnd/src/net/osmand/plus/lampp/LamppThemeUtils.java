package net.osmand.plus.lampp;

import android.content.Context;
import android.graphics.Typeface;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.settings.enums.ThemeUsageContext;

/**
 * LAMPP: Theme utilities for the Pip-Boy UI system.
 * Resolves colors based on the active {@link LamppStylePreset}.
 */
public class LamppThemeUtils {

	/**
	 * Wraps the given context with the active preset's theme overlay.
	 * Classic preset returns the context unchanged (no overlay — OsmAnd native theme).
	 * Used to inflate panel content layouts so ?attr/ references resolve to preset colors.
	 */
	@NonNull
	public static Context getLamppThemedContext(@NonNull Context context,
	                                            @NonNull OsmandApplication app) {
		int overlayStyle = getActivePreset(app).getOverlayStyleRes();
		if (overlayStyle == 0) {
			return context; // Classic: no overlay, use OsmAnd default
		}
		return new ContextThemeWrapper(context, overlayStyle);
	}

	/**
	 * Returns the currently selected style preset.
	 */
	@NonNull
	public static LamppStylePreset getActivePreset(@NonNull OsmandApplication app) {
		return app.getSettings().LAMPP_STYLE_PRESET.get();
	}

	/**
	 * Whether the device is currently in night mode (for Classic preset).
	 */
	public static boolean isNightMode(@NonNull OsmandApplication app) {
		return app.getDaynightHelper().isNightMode(ThemeUsageContext.OVER_MAP);
	}

	// ---- Pip-Boy retro font application ----

	/**
	 * Applies the retro monospace font to all TextViews in the given view hierarchy,
	 * if PIP_BOY preset is active and the retro font setting is enabled.
	 * Call after inflating panel content in onCreateView().
	 */
	public static void applyRetroFontIfNeeded(@NonNull View rootView,
	                                           @NonNull OsmandApplication app) {
		if (getActivePreset(app) != LamppStylePreset.PIP_BOY) return;
		if (!app.getSettings().LAMPP_PIPBOY_RETRO_FONT.get()) return;

		Typeface retroFont = ResourcesCompat.getFont(rootView.getContext(), R.font.share_tech_mono);
		if (retroFont != null) {
			applyFontRecursive(rootView, retroFont);
		}
	}

	private static void applyFontRecursive(@NonNull View view, @NonNull Typeface typeface) {
		if (view instanceof TextView) {
			((TextView) view).setTypeface(typeface);
		}
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup) view;
			for (int i = 0; i < group.getChildCount(); i++) {
				applyFontRecursive(group.getChildAt(i), typeface);
			}
		}
	}

	// ---- Convenience color resolution methods ----

	@ColorInt
	public static int getTabActiveColor(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		return preset.getTabActiveColor(context, isNightMode(app));
	}

	@ColorInt
	public static int getTabInactiveColor(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		return preset.getTabInactiveColor(context, isNightMode(app));
	}

	@ColorInt
	public static int getTabBarBgColor(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		return preset.getTabBarBgColor(context, isNightMode(app));
	}

	@ColorInt
	public static int getPanelBgColor(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		return preset.getPanelBgColor(context, isNightMode(app));
	}

	@ColorInt
	public static int getTextPrimaryColor(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		return preset.getTextPrimaryColor(context, isNightMode(app));
	}

	@ColorInt
	public static int getTextSecondaryColor(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		return preset.getTextSecondaryColor(context, isNightMode(app));
	}

	@ColorInt
	public static int getTabActiveBgColor(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		return preset.getTabActiveBgColor(context, isNightMode(app));
	}

	@ColorInt
	public static int getPrimaryColor(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		return preset.getPrimaryColor(context, isNightMode(app));
	}
}
