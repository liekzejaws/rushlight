package net.osmand.plus.ai;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.fieldnotes.FieldNote;
import net.osmand.plus.fieldnotes.FieldNotesManager;

import org.apache.commons.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches LLM tool calls to their Java handlers.
 *
 * Phase 3: FieldNotes LLM tool integration.
 *
 * Registered tools:
 *   query_fieldnotes(lat, lon, radius_km, categories[], max_results) — spatial search
 *   create_fieldnote(category, title, note) — create at current GPS
 *
 * Architecture: simple String→handler map. When onToolCall() fires from the
 * StreamCallback during inference, the dispatcher looks up the handler and
 * executes it synchronously (called from executor thread — blocking is fine).
 *
 * Tool result is returned as a JSON string suitable for display in the chat
 * and injection into conversation context.
 */
public class ToolDispatcher {

	private static final Log LOG = PlatformUtil.getLog(ToolDispatcher.class);

	/**
	 * Functional interface for tool handlers.
	 */
	public interface ToolHandler {
		@NonNull
		String execute(@NonNull JSONObject args) throws JSONException;
	}

	private final Map<String, ToolHandler> handlers = new HashMap<>();
	private final OsmandApplication app;

	public ToolDispatcher(@NonNull OsmandApplication app) {
		this.app = app;
		registerBuiltinTools();
	}

	private void registerBuiltinTools() {
		handlers.put("query_fieldnotes", this::handleQueryFieldNotes);
		handlers.put("create_fieldnote", this::handleCreateFieldNote);
	}

	/**
	 * Register an additional tool handler (for future extensions).
	 */
	public void registerTool(@NonNull String name, @NonNull ToolHandler handler) {
		handlers.put(name, handler);
	}

	/**
	 * Dispatch a tool call and return the result as a JSON string.
	 *
	 * @param name     Tool name emitted by the model
	 * @param argsJson JSON string of tool arguments
	 * @return JSON string result, or error JSON if unknown tool or execution failure
	 */
	@NonNull
	public String dispatch(@NonNull String name, @NonNull String argsJson) {
		ToolHandler handler = handlers.get(name);
		if (handler == null) {
			LOG.warn("Unknown tool: " + name);
			return errorResult("Unknown tool: " + name);
		}
		try {
			JSONObject args = new JSONObject(argsJson);
			String result = handler.execute(args);
			LOG.info("Tool " + name + " returned " + result.length() + " chars");
			return result;
		} catch (JSONException e) {
			LOG.error("Tool dispatch error for " + name + ": " + e.getMessage());
			return errorResult("Tool error: " + e.getMessage());
		}
	}

	// --- Tool handlers ---

	/**
	 * query_fieldnotes(lat, lon, radius_km, categories[], max_results)
	 *
	 * Returns a JSON array of matching FieldNotes, sorted nearest first.
	 * If lat/lon are absent or zero, falls back to current GPS location.
	 */
	@NonNull
	private String handleQueryFieldNotes(@NonNull JSONObject args) throws JSONException {
		FieldNotesManager mgr = app.getFieldNotesManager();

		// Resolve coordinates: prefer explicit args, fall back to GPS
		double lat = args.optDouble("lat", 0.0);
		double lon = args.optDouble("lon", 0.0);
		if (lat == 0.0 && lon == 0.0) {
			net.osmand.Location loc = app.getLocationProvider().getLastKnownLocation();
			if (loc != null) {
				lat = loc.getLatitude();
				lon = loc.getLongitude();
			}
		}
		if (lat == 0.0 && lon == 0.0) {
			return errorResult("Location unavailable and no coordinates provided");
		}

		double radiusKm = args.optDouble("radius_km", 5.0);
		int maxResults = args.optInt("max_results", 20);

		// Category filter
		String[] categories = null;
		if (args.has("categories")) {
			JSONArray catArray = args.getJSONArray("categories");
			categories = new String[catArray.length()];
			for (int i = 0; i < catArray.length(); i++) {
				categories[i] = catArray.getString(i);
			}
		}

		List<FieldNote> notes = mgr.getNotesInRadius(lat, lon, radiusKm, categories, maxResults);

		// Serialize to compact JSON for LLM consumption
		JSONArray result = new JSONArray();
		for (FieldNote note : notes) {
			JSONObject obj = new JSONObject();
			obj.put("id", note.getId().substring(0, Math.min(12, note.getId().length())));
			obj.put("category", note.getCategory().getKey());
			obj.put("title", note.getTitle());
			obj.put("note", note.getNote());
			obj.put("lat", Math.round(note.getLat() * 10000.0) / 10000.0);
			obj.put("lon", Math.round(note.getLon() * 10000.0) / 10000.0);
			obj.put("confirms", note.getConfirmations());
			result.put(obj);
		}

		if (result.length() == 0) {
			return "{\"results\":[],\"message\":\"No FieldNotes found within "
					+ radiusKm + " km\"}";
		}
		return result.toString();
	}

	/**
	 * create_fieldnote(category, title, note)
	 *
	 * Creates a FieldNote at current GPS location.
	 * Returns confirmation JSON with the new note's ID.
	 */
	@NonNull
	private String handleCreateFieldNote(@NonNull JSONObject args) throws JSONException {
		// Require GPS
		net.osmand.Location loc = app.getLocationProvider().getLastKnownLocation();
		if (loc == null) {
			return errorResult("GPS location unavailable — cannot create FieldNote");
		}

		String categoryKey = args.optString("category", "intel");
		FieldNote.Category category = FieldNote.Category.fromKey(categoryKey);
		String title = args.optString("title", "");
		String noteText = args.optString("note", "");

		if (title.isEmpty() && noteText.isEmpty()) {
			return errorResult("Both title and note are empty");
		}

		FieldNotesManager mgr = app.getFieldNotesManager();
		FieldNote note = mgr.createNote(loc.getLatitude(), loc.getLongitude(),
				category, title, noteText);

		if (note == null) {
			return errorResult("Failed to create FieldNote");
		}

		JSONObject result = new JSONObject();
		result.put("status", "created");
		result.put("id", note.getId().substring(0, Math.min(12, note.getId().length())));
		result.put("category", note.getCategory().getKey());
		result.put("title", note.getTitle());
		result.put("lat", Math.round(note.getLat() * 10000.0) / 10000.0);
		result.put("lon", Math.round(note.getLon() * 10000.0) / 10000.0);
		return result.toString();
	}

	// --- Utilities ---

	@NonNull
	private static String errorResult(@NonNull String message) {
		try {
			JSONObject err = new JSONObject();
			err.put("error", message);
			return err.toString();
		} catch (JSONException e) {
			return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
		}
	}
}
