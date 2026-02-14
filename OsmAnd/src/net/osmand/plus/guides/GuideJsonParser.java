package net.osmand.plus.guides;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses survival guide JSON files into GuideEntry objects.
 *
 * Expected JSON format:
 * {
 *   "id": "first-aid-basics",
 *   "title": "First Aid Basics",
 *   "category": "FIRST_AID",
 *   "tags": ["first aid", "emergency", "wounds"],
 *   "importance": "CRITICAL",
 *   "summary": "Essential first aid techniques...",
 *   "body": "# First Aid Basics\n\n## Wound Care\n...",
 *   "lastUpdated": 1707868800000
 * }
 */
public class GuideJsonParser {

	private static final String TAG = "GuideJsonParser";

	private static final String KEY_ID = "id";
	private static final String KEY_TITLE = "title";
	private static final String KEY_CATEGORY = "category";
	private static final String KEY_TAGS = "tags";
	private static final String KEY_IMPORTANCE = "importance";
	private static final String KEY_SUMMARY = "summary";
	private static final String KEY_BODY = "body";
	private static final String KEY_LAST_UPDATED = "lastUpdated";

	/**
	 * Parse a single guide from a JSON string.
	 * @param json The full JSON string of one guide file
	 * @param bundled Whether this guide is bundled with the app
	 * @return Parsed GuideEntry, or null if parsing fails
	 */
	@Nullable
	public static GuideEntry parseGuide(@NonNull String json, boolean bundled) {
		try {
			JSONObject obj = new JSONObject(json);
			return parseGuideObject(obj, bundled, true);
		} catch (JSONException e) {
			Log.e(TAG, "Failed to parse guide JSON: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Parse a list of guides from a JSON array string.
	 * @param json JSON array of guide objects
	 * @param bundled Whether these guides are bundled
	 * @param includeBody Whether to include the full body text
	 * @return List of parsed guides (skipping any that fail to parse)
	 */
	@NonNull
	public static List<GuideEntry> parseGuideList(@NonNull String json, boolean bundled, boolean includeBody) {
		List<GuideEntry> guides = new ArrayList<>();
		try {
			JSONArray array = new JSONArray(json);
			for (int i = 0; i < array.length(); i++) {
				try {
					JSONObject obj = array.getJSONObject(i);
					GuideEntry entry = parseGuideObject(obj, bundled, includeBody);
					if (entry != null) {
						guides.add(entry);
					}
				} catch (JSONException e) {
					Log.w(TAG, "Skipping invalid guide at index " + i + ": " + e.getMessage());
				}
			}
		} catch (JSONException e) {
			Log.e(TAG, "Failed to parse guide list JSON: " + e.getMessage());
		}
		return guides;
	}

	/**
	 * Parse a single guide from a JSONObject.
	 */
	@Nullable
	static GuideEntry parseGuideObject(@NonNull JSONObject obj, boolean bundled, boolean includeBody) {
		try {
			String id = obj.has(KEY_ID) ? obj.getString(KEY_ID) : null;
			String title = obj.has(KEY_TITLE) ? obj.getString(KEY_TITLE) : null;

			if (id == null || id.isEmpty() || title == null || title.isEmpty()) {
				Log.w(TAG, "Guide missing required id or title");
				return null;
			}

			GuideEntry.Builder builder = new GuideEntry.Builder(id, title);

			if (obj.has(KEY_CATEGORY)) {
				builder.setCategory(GuideCategory.fromString(obj.getString(KEY_CATEGORY)));
			}

			if (obj.has(KEY_TAGS)) {
				JSONArray tagsArray = obj.getJSONArray(KEY_TAGS);
				String[] tags = new String[tagsArray.length()];
				for (int i = 0; i < tagsArray.length(); i++) {
					tags[i] = tagsArray.getString(i);
				}
				builder.setTags(tags);
			}

			if (obj.has(KEY_IMPORTANCE)) {
				builder.setImportance(GuideEntry.Importance.fromString(obj.getString(KEY_IMPORTANCE)));
			}

			if (obj.has(KEY_SUMMARY)) {
				builder.setSummary(obj.getString(KEY_SUMMARY));
			}

			if (includeBody && obj.has(KEY_BODY)) {
				builder.setBody(obj.getString(KEY_BODY));
			}

			if (obj.has(KEY_LAST_UPDATED)) {
				builder.setLastUpdated(obj.getLong(KEY_LAST_UPDATED));
			}

			builder.setBundled(bundled);
			return builder.build();

		} catch (JSONException e) {
			Log.e(TAG, "Error parsing guide object: " + e.getMessage());
			return null;
		}
	}
}
