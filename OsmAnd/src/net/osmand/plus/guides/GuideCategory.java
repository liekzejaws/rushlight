package net.osmand.plus.guides;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.R;

/**
 * Categories for survival guides. Each category maps to a topic area
 * with an icon and display name used in the Guides panel UI.
 */
public enum GuideCategory {

	FIRST_AID("First Aid", R.drawable.ic_action_sensor_heart_rate_outlined, 0),
	WATER("Water", R.drawable.ic_action_cloud, 1),
	FIRE("Fire & Heat", R.drawable.ic_action_sun, 2),
	SHELTER("Shelter", R.drawable.ic_action_building, 3),
	NAVIGATION("Navigation", R.drawable.ic_action_compass, 4),
	SIGNALING("Signaling", R.drawable.ic_action_antenna, 5),
	FOOD("Food", R.drawable.ic_action_target, 6),
	SECURITY("Security", R.drawable.ic_action_lock, 7),
	ENGINEERING("Engineering", R.drawable.ic_action_settings_outlined, 8),
	AUTOMOTIVE("Automotive", R.drawable.ic_action_car, 9),
	ELECTRICAL("Electrical", R.drawable.ic_action_antenna, 10);

	private final String displayName;
	@DrawableRes
	private final int iconRes;
	private final int sortOrder;

	GuideCategory(@NonNull String displayName, @DrawableRes int iconRes, int sortOrder) {
		this.displayName = displayName;
		this.iconRes = iconRes;
		this.sortOrder = sortOrder;
	}

	@NonNull
	public String getDisplayName() {
		return displayName;
	}

	@DrawableRes
	public int getIconRes() {
		return iconRes;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	/**
	 * Parse a category from a JSON string value. Case-insensitive.
	 * Returns FIRST_AID as default if the value doesn't match any category.
	 */
	@NonNull
	public static GuideCategory fromString(@Nullable String value) {
		if (value == null) return FIRST_AID;
		try {
			return valueOf(value.toUpperCase().replace(" ", "_").replace("&", "AND"));
		} catch (IllegalArgumentException e) {
			// Try matching by display name
			for (GuideCategory cat : values()) {
				if (cat.displayName.equalsIgnoreCase(value)) {
					return cat;
				}
			}
			return FIRST_AID;
		}
	}
}
