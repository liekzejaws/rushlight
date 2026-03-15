/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

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

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.MarkwonTheme;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.settings.enums.DayNightMode;
import net.osmand.plus.settings.enums.ThemeUsageContext;

import org.apache.commons.logging.Log;

/**
 * LAMPP: Theme utilities for the Pip-Boy UI system.
 * Resolves colors based on the active {@link LamppStylePreset}.
 */
public class LamppThemeUtils {

	private static final Log LOG = PlatformUtil.getLog(LamppThemeUtils.class);

	/**
	 * Phase 12: Sync map day/night mode with theme preset.
	 *
	 * When Pip-Boy theme is active, forces the map to NIGHT mode (dark roads, dark bg)
	 * to match the green-on-black Pip-Boy aesthetic.
	 * When switching away from Pip-Boy, restores the original DayNightMode.
	 */
	public static void syncMapDarkMode(@NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		String savedMode = app.getSettings().LAMPP_ORIGINAL_DAY_NIGHT_MODE.get();

		if (preset == LamppStylePreset.PIP_BOY) {
			// Save current mode if not already saved (first time activating Pip-Boy)
			DayNightMode currentMode = app.getSettings().DAYNIGHT_MODE.get();
			if (savedMode.isEmpty() && currentMode != DayNightMode.NIGHT) {
				app.getSettings().LAMPP_ORIGINAL_DAY_NIGHT_MODE.set(currentMode.name());
				LOG.info("Dark map sync: saved original mode=" + currentMode.name());
			}

			// Force NIGHT mode for Pip-Boy
			if (currentMode != DayNightMode.NIGHT) {
				app.getSettings().DAYNIGHT_MODE.set(DayNightMode.NIGHT);
				LOG.info("Dark map sync: forced NIGHT mode for Pip-Boy");
			}
		} else {
			// Restore original mode if we previously overrode it
			if (!savedMode.isEmpty()) {
				try {
					DayNightMode originalMode = DayNightMode.valueOf(savedMode);
					app.getSettings().DAYNIGHT_MODE.set(originalMode);
					LOG.info("Dark map sync: restored original mode=" + originalMode.name());
				} catch (IllegalArgumentException e) {
					LOG.warn("Dark map sync: invalid saved mode '" + savedMode + "', ignoring");
				}
				app.getSettings().LAMPP_ORIGINAL_DAY_NIGHT_MODE.set("");
			}
		}
	}

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
		// v0.6: Skip EditText — monospace on input fields makes typed text cramped
		if (view instanceof TextView && !(view instanceof android.widget.EditText)) {
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

	// ---- Shared Markwon instance (Phase 17) ----

	/**
	 * Cached Markwon instance key: preset ordinal + nightMode flag.
	 * Invalidated when the theme changes.
	 */
	private static Markwon cachedMarkwon;
	private static int cachedMarkwonKey = -1;

	/**
	 * Returns a shared Markwon instance themed to the active preset.
	 * The instance is cached and only rebuilt when the theme preset or night mode changes.
	 * Use this instead of creating per-fragment Markwon builders.
	 */
	@NonNull
	public static Markwon getMarkwon(@NonNull Context context, @NonNull OsmandApplication app) {
		LamppStylePreset preset = getActivePreset(app);
		boolean night = isNightMode(app);
		int key = preset.ordinal() * 2 + (night ? 1 : 0);

		if (cachedMarkwon != null && cachedMarkwonKey == key) {
			return cachedMarkwon;
		}

		@ColorInt int linkColor = preset.getPrimaryColor(context, night);
		@ColorInt int codeBgColor = preset.getAiMessageBgColor(context, night);
		@ColorInt int codeTextColor = preset.getAiMessageTextColor(context, night);

		cachedMarkwon = Markwon.builder(context)
				.usePlugin(new AbstractMarkwonPlugin() {
					@Override
					public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
						builder.linkColor(linkColor)
								.codeTextColor(codeTextColor)
								.codeBackgroundColor(codeBgColor)
								.codeBlockTextColor(codeTextColor)
								.codeBlockBackgroundColor(codeBgColor)
								.codeTypeface(Typeface.MONOSPACE)
								.headingBreakHeight(0);
					}
				})
				.build();
		cachedMarkwonKey = key;
		LOG.info("Markwon instance rebuilt for preset=" + preset.name() + " night=" + night);
		return cachedMarkwon;
	}

	/**
	 * Invalidate the cached Markwon instance (e.g., after theme change).
	 */
	public static void invalidateMarkwon() {
		cachedMarkwon = null;
		cachedMarkwonKey = -1;
	}
}
