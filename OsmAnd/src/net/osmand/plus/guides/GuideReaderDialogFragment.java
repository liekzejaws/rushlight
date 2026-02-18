package net.osmand.plus.guides;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.lampp.LamppPanelManager;
import net.osmand.plus.lampp.LamppTab;
import net.osmand.plus.lampp.LamppThemeUtils;

import io.noties.markwon.Markwon;

/**
 * Full-screen dialog fragment for reading a survival guide.
 * Uses Markwon to render Markdown body content.
 * Phase 17: Includes "Ask AI" FAB to send guide title as AI query.
 */
public class GuideReaderDialogFragment extends DialogFragment {

	public static final String TAG = "GuideReaderDialog";
	private static final String ARG_GUIDE_ID = "guide_id";

	public static void showInstance(@NonNull FragmentManager fragmentManager, @NonNull String guideId) {
		if (fragmentManager.findFragmentByTag(TAG) != null) return;

		GuideReaderDialogFragment fragment = new GuideReaderDialogFragment();
		Bundle args = new Bundle();
		args.putString(ARG_GUIDE_ID, guideId);
		fragment.setArguments(args);
		fragment.show(fragmentManager, TAG);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		if (dialog.getWindow() != null) {
			dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		}
		return dialog;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                          @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.activity_guide_reader, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ImageView backButton = view.findViewById(R.id.reader_back_button);
		TextView titleView = view.findViewById(R.id.reader_title);
		TextView categoryView = view.findViewById(R.id.reader_category);
		TextView bodyView = view.findViewById(R.id.reader_body);
		ExtendedFloatingActionButton askAiFab = view.findViewById(R.id.ask_ai_fab);

		backButton.setOnClickListener(v -> dismiss());

		String guideId = getArguments() != null ? getArguments().getString(ARG_GUIDE_ID) : null;
		if (guideId == null) {
			dismiss();
			return;
		}

		OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
		GuideEntry guide = app.getGuideManager().getGuide(guideId);
		if (guide == null) {
			dismiss();
			return;
		}

		titleView.setText(guide.getTitle());
		categoryView.setText(guide.getCategory().getDisplayName());

		// Render Markdown body using shared themed Markwon instance (Phase 17)
		if (guide.hasBody()) {
			Markwon markwon = LamppThemeUtils.getMarkwon(requireContext(), app);
			markwon.setMarkdown(bodyView, guide.getBody());
		} else {
			bodyView.setText(guide.getSummary());
		}

		// Phase 17: "Ask AI" FAB — taps switch to AI Chat with prefilled query
		if (askAiFab != null) {
			askAiFab.setOnClickListener(v -> {
				String query = "Tell me more about " + guide.getTitle();
				dismiss(); // Close the reader dialog first

				if (getActivity() instanceof MapActivity) {
					MapActivity mapActivity = (MapActivity) getActivity();
					LamppPanelManager panelManager = mapActivity.getLamppPanelManager();
					panelManager.openPanelWithQuery(LamppTab.AI_CHAT, query);
				}
			});
		}
	}
}
