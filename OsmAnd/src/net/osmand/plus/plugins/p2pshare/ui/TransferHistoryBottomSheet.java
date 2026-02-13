package net.osmand.plus.plugins.p2pshare.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.plugins.p2pshare.history.TransferHistoryDatabase;
import net.osmand.plus.plugins.p2pshare.history.TransferRecord;
import net.osmand.plus.plugins.p2pshare.ui.adapters.TransferHistoryAdapter;
import net.osmand.plus.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet displaying the transfer history log.
 * Shows recent file transfers with status, speed, and peer info.
 */
public class TransferHistoryBottomSheet extends BottomSheetDialogFragment {

	public static final String TAG = "TransferHistoryBottomSheet";

	private OsmandApplication app;

	// UI
	private RecyclerView historyRecycler;
	private TextView emptyState;
	private TextView summaryText;
	private TransferHistoryAdapter adapter;

	// Data
	private final List<TransferRecord> records = new ArrayList<>();

	public static void showInstance(@NonNull FragmentManager fragmentManager) {
		if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
			TransferHistoryBottomSheet fragment = new TransferHistoryBottomSheet();
			fragment.show(fragmentManager, TAG);
		}
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = (OsmandApplication) requireActivity().getApplication();
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), getTheme());

		dialog.setOnShowListener(dialogInterface -> {
			BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
			FrameLayout bottomSheet = d.findViewById(
					com.google.android.material.R.id.design_bottom_sheet);
			if (bottomSheet != null) {
				BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
				behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
				behavior.setSkipCollapsed(true);

				// Set max height to 80% of screen
				int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
				ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
				layoutParams.height = maxHeight;
				bottomSheet.setLayoutParams(layoutParams);
			}
		});

		return dialog;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                          @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.bottom_sheet_transfer_history, container, false);
		initViews(view);
		loadHistory();
		return view;
	}

	private void initViews(@NonNull View view) {
		TextView titleText = view.findViewById(R.id.title_text);
		titleText.setText(R.string.p2p_share_transfer_history);

		ImageButton closeButton = view.findViewById(R.id.close_button);
		closeButton.setOnClickListener(v -> dismiss());

		ImageButton clearButton = view.findViewById(R.id.clear_button);
		clearButton.setOnClickListener(v -> clearHistory());

		historyRecycler = view.findViewById(R.id.history_recycler);
		emptyState = view.findViewById(R.id.empty_state);
		summaryText = view.findViewById(R.id.summary_text);

		adapter = new TransferHistoryAdapter(records);
		historyRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
		historyRecycler.setAdapter(adapter);
	}

	private void loadHistory() {
		records.clear();

		TransferHistoryDatabase db = TransferHistoryDatabase.getInstance(app);
		records.addAll(db.getRecentRecords(100));

		adapter.notifyDataSetChanged();

		// Show empty state if no records
		boolean hasRecords = !records.isEmpty();
		historyRecycler.setVisibility(hasRecords ? View.VISIBLE : View.GONE);
		emptyState.setVisibility(hasRecords ? View.GONE : View.VISIBLE);

		updateSummary();
	}

	private void updateSummary() {
		TransferHistoryDatabase db = TransferHistoryDatabase.getInstance(app);
		int count = db.getRecordCount();
		long totalBytes = db.getTotalBytesTransferred();

		String totalStr = formatBytes(totalBytes);
		summaryText.setText(getString(R.string.p2p_share_history_summary, count, totalStr));
	}

	private void clearHistory() {
		TransferHistoryDatabase db = TransferHistoryDatabase.getInstance(app);
		db.clearHistory();
		records.clear();
		adapter.notifyDataSetChanged();

		historyRecycler.setVisibility(View.GONE);
		emptyState.setVisibility(View.VISIBLE);
		updateSummary();

		app.showShortToastMessage(R.string.p2p_share_history_cleared);
	}

	private String formatBytes(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		} else if (bytes < 1024L * 1024 * 1024) {
			return String.format("%.1f MB", bytes / (1024.0 * 1024));
		} else {
			return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
		}
	}
}
