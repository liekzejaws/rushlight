package net.osmand.plus.guides;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.lampp.LamppPanelFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * LAMPP: Survival Guides panel fragment.
 * Shows an 8-category grid that drills into filtered guide lists.
 * Search bar filters across all guides in real-time.
 */
public class GuidesFragment extends LamppPanelFragment {

	public static final String TAG = "GuidesFragment";

	private static final int MODE_CATEGORIES = 0;
	private static final int MODE_LIST = 1;

	private RecyclerView recyclerView;
	private EditText searchInput;
	private TextView titleView;
	private ImageView backButton;
	private ProgressBar guidesLoading;
	private View emptyView;

	private int currentMode = MODE_CATEGORIES;
	private GuideCategory currentCategory = null;

	@Override
	protected int getPanelLayoutId() {
		return R.layout.fragment_guides;
	}

	@NonNull
	@Override
	public String getPanelTag() {
		return TAG;
	}

	@Override
	protected void onPanelViewCreated(@NonNull View contentView, @Nullable Bundle savedInstanceState) {
		recyclerView = contentView.findViewById(R.id.guides_recycler);
		searchInput = contentView.findViewById(R.id.guides_search_input);
		titleView = contentView.findViewById(R.id.guides_title);
		backButton = contentView.findViewById(R.id.guides_back_button);
		guidesLoading = contentView.findViewById(R.id.guides_loading);

		backButton.setOnClickListener(v -> onBackToCategories());

		searchInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				onSearchTextChanged(s.toString());
			}

			@Override
			public void afterTextChanged(Editable s) {}
		});

		// Load guides and show categories
		OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
		if (!app.getGuideManager().isLoaded()) {
			guidesLoading.setVisibility(View.VISIBLE);
		}
		app.getGuideManager().loadGuidesAsync(count -> {
			if (isAdded()) {
				guidesLoading.setVisibility(View.GONE);
				showCategories();
			}
		});

		// Show categories immediately (may be empty if first load, but spinner indicates loading)
		showCategories();
	}

	private void showCategories() {
		currentMode = MODE_CATEGORIES;
		currentCategory = null;
		titleView.setText(R.string.survival_guides);
		backButton.setVisibility(View.GONE);

		recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
		recyclerView.setAdapter(new CategoryAdapter());
	}

	private void showGuidesForCategory(@NonNull GuideCategory category) {
		currentMode = MODE_LIST;
		currentCategory = category;
		titleView.setText(category.getDisplayName());
		backButton.setVisibility(View.VISIBLE);

		OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
		List<GuideEntry> guides = app.getGuideManager().getByCategory(category);

		recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
		recyclerView.setAdapter(new GuideListAdapter(guides));
	}

	private void showSearchResults(@NonNull String query) {
		currentMode = MODE_LIST;
		titleView.setText(R.string.survival_guides);
		backButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

		OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
		List<GuideEntry> results = app.getGuideManager().search(query);

		recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
		recyclerView.setAdapter(new GuideListAdapter(results));
	}

	private void onSearchTextChanged(String text) {
		if (text.isEmpty()) {
			if (currentCategory != null) {
				showGuidesForCategory(currentCategory);
			} else {
				showCategories();
			}
		} else {
			showSearchResults(text);
		}
	}

	private void onBackToCategories() {
		searchInput.setText("");
		showCategories();
	}

	@Override
	public boolean onBackPressed() {
		if (currentMode == MODE_LIST) {
			onBackToCategories();
			return true;
		}
		return false;
	}

	private void openGuideReader(@NonNull GuideEntry guide) {
		// Open guide reader as a dialog/overlay within the panel
		if (getActivity() != null) {
			GuideReaderDialogFragment.showInstance(
					getActivity().getSupportFragmentManager(), guide.getId());
		}
	}

	// ============================================================
	// Category Grid Adapter
	// ============================================================

	private class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

		private final GuideCategory[] categories = GuideCategory.values();

		@NonNull
		@Override
		public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_guide_category, parent, false);
			return new VH(view);
		}

		@Override
		public void onBindViewHolder(@NonNull VH holder, int position) {
			GuideCategory category = categories[position];
			holder.icon.setImageResource(category.getIconRes());
			holder.name.setText(category.getDisplayName());

			// Get guide count for this category
			OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
			int count = app.getGuideManager().getByCategory(category).size();
			holder.count.setText(count + (count == 1 ? " guide" : " guides"));

			holder.itemView.setOnClickListener(v -> showGuidesForCategory(category));
		}

		@Override
		public int getItemCount() {
			return categories.length;
		}

		class VH extends RecyclerView.ViewHolder {
			ImageView icon;
			TextView name;
			TextView count;

			VH(@NonNull View itemView) {
				super(itemView);
				icon = itemView.findViewById(R.id.category_icon);
				name = itemView.findViewById(R.id.category_name);
				count = itemView.findViewById(R.id.category_count);
			}
		}
	}

	// ============================================================
	// Guide List Adapter
	// ============================================================

	private class GuideListAdapter extends RecyclerView.Adapter<GuideListAdapter.VH> {

		private final List<GuideEntry> guides;

		GuideListAdapter(@NonNull List<GuideEntry> guides) {
			this.guides = new ArrayList<>(guides);
		}

		@NonNull
		@Override
		public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_guide_entry, parent, false);
			return new VH(view);
		}

		@Override
		public void onBindViewHolder(@NonNull VH holder, int position) {
			GuideEntry guide = guides.get(position);
			holder.title.setText(guide.getTitle());
			holder.summary.setText(guide.getSummary());

			// Color the importance strip
			int color;
			switch (guide.getImportance()) {
				case CRITICAL:
					color = Color.parseColor("#FF4444");
					break;
				case HIGH:
					color = Color.parseColor("#FF8800");
					break;
				default:
					color = Color.parseColor("#4488FF");
					break;
			}
			holder.importanceStrip.setBackgroundColor(color);

			holder.itemView.setOnClickListener(v -> openGuideReader(guide));
		}

		@Override
		public int getItemCount() {
			return guides.size();
		}

		class VH extends RecyclerView.ViewHolder {
			TextView title;
			TextView summary;
			View importanceStrip;

			VH(@NonNull View itemView) {
				super(itemView);
				title = itemView.findViewById(R.id.guide_title);
				summary = itemView.findViewById(R.id.guide_summary);
				importanceStrip = itemView.findViewById(R.id.importance_strip);
			}
		}
	}
}
