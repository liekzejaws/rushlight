package net.osmand.plus.fieldnotes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.core.jni.PointI;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadRect;
import net.osmand.data.QuadTree;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.utils.NativeUtilities;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.PointImageDrawable;
import net.osmand.plus.views.PointImageUtils;
import net.osmand.plus.views.layers.ContextMenuLayer.IContextMenuProvider;
import net.osmand.plus.views.layers.MapSelectionResult;
import net.osmand.plus.views.layers.MapSelectionRules;
import net.osmand.plus.views.layers.base.OsmandMapLayer;
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter;
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem;

import java.util.ArrayList;
import java.util.List;

import net.osmand.core.android.MapRendererView;

/**
 * Map overlay layer for FieldNotes — draws category-colored pins on the map.
 *
 * Follows the FavouritesLayer pattern:
 * - Extends OsmandMapLayer
 * - Implements IContextMenuProvider for tap-to-select
 * - Uses QuadTree for overlapping icon detection
 * - Zoom threshold: visible at zoom >= 10 (field-level)
 *
 * Listens to FieldNotesManager for add/delete events to refresh the map.
 */
public class FieldNotesLayer extends OsmandMapLayer
		implements IContextMenuProvider, FieldNotesManager.FieldNoteListener {

	// Minimum zoom level to show FieldNote pins
	private static final int MIN_ZOOM = 10;

	private static final float TOUCH_RADIUS_MULTIPLIER = 1.5f;

	private OsmandApplication app;
	private FieldNotesManager manager;

	// Cache of notes currently visible on screen
	private final List<FieldNote> visibleNotes = new ArrayList<>();

	public FieldNotesLayer(@NonNull Context ctx) {
		super(ctx);
	}

	@Override
	public void initLayer(@NonNull OsmandMapTileView view) {
		super.initLayer(view);
		app = view.getApplication();
		manager = app.getFieldNotesManager();
		manager.addListener(this);
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		// Not used — we draw in onPrepareBufferImage instead
	}

	@Override
	public void onPrepareBufferImage(Canvas canvas, RotatedTileBox tileBox, DrawSettings settings) {
		super.onPrepareBufferImage(canvas, tileBox, settings);

		if (tileBox.getZoom() < MIN_ZOOM) {
			visibleNotes.clear();
			return;
		}

		// Get all active notes
		List<FieldNote> allNotes = manager.getAllNotes();
		if (allNotes.isEmpty()) {
			visibleNotes.clear();
			return;
		}

		// Get visible bounds
		QuadRect latLonBounds = tileBox.getLatLonBounds();
		float iconSize = getIconSize(app);
		QuadTree<QuadRect> boundIntersections = initBoundIntersections(tileBox);
		float textScale = getTextScale();

		visibleNotes.clear();
		List<FieldNote> fullObjects = new ArrayList<>();

		// First pass: categorize into small (overlapping) and full (non-overlapping)
		for (FieldNote note : allNotes) {
			double lat = note.getLat();
			double lon = note.getLon();

			// Check if within visible bounds
			if (lat < latLonBounds.bottom || lat > latLonBounds.top
					|| lon < latLonBounds.left || lon > latLonBounds.right) {
				continue;
			}

			visibleNotes.add(note);

			float x = tileBox.getPixXFromLatLon(lat, lon);
			float y = tileBox.getPixYFromLatLon(lat, lon);

			if (intersects(boundIntersections, x, y, iconSize, iconSize)) {
				// Draw small dot for overlapping icons
				PointImageDrawable drawable = PointImageUtils.getOrCreate(
						getContext(), note.getCategory().getColor(), true,
						note.getCategory().getIconRes());
				drawable.drawSmallPoint(canvas, x, y, textScale);
			} else {
				// Defer full icon drawing
				fullObjects.add(note);
			}
		}

		// Second pass: draw full-sized icons
		for (FieldNote note : fullObjects) {
			float x = tileBox.getPixXFromLatLon(note.getLat(), note.getLon());
			float y = tileBox.getPixYFromLatLon(note.getLat(), note.getLon());

			PointImageDrawable drawable = PointImageUtils.getOrCreate(
					getContext(), note.getCategory().getColor(), true,
					note.getCategory().getIconRes());
			drawable.drawPoint(canvas, x, y, textScale, false);
		}
	}

	// --- IContextMenuProvider implementation ---

	@Override
	public void collectObjectsFromPoint(@NonNull MapSelectionResult result,
			@NonNull MapSelectionRules rules) {
		if (result.getTileBox().getZoom() < MIN_ZOOM) {
			return;
		}

		PointF point = result.getPoint();
		RotatedTileBox tb = result.getTileBox();
		float radius = getScaledTouchRadius(app, tb.getDefaultRadiusPoi())
				* TOUCH_RADIUS_MULTIPLIER;

		MapRendererView mapRenderer = getMapRenderer();
		List<PointI> touchPolygon31 = null;
		if (mapRenderer != null) {
			touchPolygon31 = NativeUtilities.getPolygon31FromPixelAndRadius(
					mapRenderer, point, radius);
		}

		List<FieldNote> allNotes = manager.getAllNotes();
		for (FieldNote note : allNotes) {
			double lat = note.getLat();
			double lon = note.getLon();

			boolean add = mapRenderer != null
					? touchPolygon31 != null && NativeUtilities.isPointInsidePolygon(lat, lon, touchPolygon31)
					: tb.isLatLonNearPixel(lat, lon, point.x, point.y, radius);

			if (add) {
				result.collect(note, this);
			}
		}
	}

	@Nullable
	@Override
	public LatLon getObjectLocation(Object o) {
		if (o instanceof FieldNote) {
			FieldNote note = (FieldNote) o;
			return new LatLon(note.getLat(), note.getLon());
		}
		return null;
	}

	@Nullable
	@Override
	public PointDescription getObjectName(Object o) {
		if (o instanceof FieldNote) {
			FieldNote note = (FieldNote) o;
			return new PointDescription(PointDescription.POINT_TYPE_MARKER,
					note.getCategory().getDisplayName() + ": " + note.getTitle());
		}
		return null;
	}

	/**
	 * When a FieldNote pin is tapped, show the ViewFieldNoteDialog directly
	 * instead of the standard context menu. Returns true to consume the event.
	 */
	@Override
	public boolean showMenuAction(@Nullable Object o) {
		if (o instanceof FieldNote) {
			FieldNote note = (FieldNote) o;
			MapActivity mapActivity = getMapActivity();
			if (mapActivity != null) {
				ViewFieldNoteDialog dialog = ViewFieldNoteDialog.newInstance(note.getId());
				dialog.show(mapActivity.getSupportFragmentManager(), ViewFieldNoteDialog.TAG);
				return true;
			}
		}
		return false;
	}

	/**
	 * Add "Add FieldNote" action to the context menu for ANY location.
	 * This is called by MapContextMenu.getActionsContextMenuAdapter() which
	 * iterates all layers — our action appears alongside "Add favorite", "Add marker", etc.
	 */
	@Override
	public void populateObjectContextMenu(@NonNull LatLon latLon, @Nullable Object o,
			@NonNull ContextMenuAdapter adapter) {
		// Only show for empty locations (not when tapping an existing object)
		// Unless it's a FieldNote, which we handle via showMenuAction
		if (o != null && !(o instanceof FieldNote)) {
			return;
		}

		adapter.addItem(new ContextMenuItem("fieldnote_add")
				.setTitle("Add FieldNote")
				.setIcon(R.drawable.ic_action_flag)
				.setOrder(35)
				.setListener((uiAdapter, view, item, isChecked) -> {
					MapActivity mapActivity = getMapActivity();
					if (mapActivity != null) {
						CreateFieldNoteDialog dialog = CreateFieldNoteDialog.newInstance(
								latLon.getLatitude(), latLon.getLongitude());
						dialog.show(mapActivity.getSupportFragmentManager(),
								CreateFieldNoteDialog.TAG);
					}
					return true;
				}));
	}

	// --- FieldNoteListener implementation ---

	@Override
	public void onFieldNoteAdded(@NonNull FieldNote note) {
		// Refresh the map to show the new pin
		if (view != null) {
			view.refreshMap();
		}
	}

	@Override
	public void onFieldNoteDeleted(@NonNull String noteId) {
		// Refresh the map to remove the deleted pin
		if (view != null) {
			view.refreshMap();
		}
	}

	// --- Utility ---

	/**
	 * Get the list of currently visible notes (for context menu actions).
	 */
	@NonNull
	public List<FieldNote> getVisibleNotes() {
		return new ArrayList<>(visibleNotes);
	}

	@Override
	public void destroyLayer() {
		super.destroyLayer();
		if (manager != null) {
			manager.removeListener(this);
		}
	}

	@Override
	public boolean drawInScreenPixels() {
		return true;
	}
}
