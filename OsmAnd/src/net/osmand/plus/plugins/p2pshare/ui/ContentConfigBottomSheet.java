/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.p2pshare.ContentManifest;
import net.osmand.plus.plugins.p2pshare.P2pShareManager;
import net.osmand.plus.plugins.p2pshare.P2pSharePlugin;
import net.osmand.plus.plugins.p2pshare.ShareableContent;
import net.osmand.plus.plugins.p2pshare.ui.adapters.ContentListAdapter;
import net.osmand.plus.utils.AndroidUtils;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bottom sheet dialog for configuring which content to share via P2P.
 * Shows all available maps, ZIMs, models, and the app itself with checkboxes.
 */
public class ContentConfigBottomSheet extends BottomSheetDialogFragment
        implements ContentListAdapter.OnContentToggleListener {

    public static final String TAG = "ContentConfigBottomSheet";

    private OsmandApplication app;
    private P2pShareManager shareManager;

    // UI
    private RecyclerView contentRecycler;
    private ContentListAdapter adapter;
    private TextView summaryText;
    private Button selectAllButton;
    private Button deselectAllButton;

    // Data
    private final List<ShareableContent> contentList = new ArrayList<>();

    // Callback for when dialog closes
    private OnContentConfigChangedListener configChangedListener;

    public interface OnContentConfigChangedListener {
        void onContentConfigChanged();
    }

    public static void showInstance(@NonNull FragmentManager fragmentManager,
                                    @Nullable OnContentConfigChangedListener listener) {
        if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
            ContentConfigBottomSheet fragment = new ContentConfigBottomSheet();
            fragment.setConfigChangedListener(listener);
            fragment.show(fragmentManager, TAG);
        }
    }

    public void setConfigChangedListener(@Nullable OnContentConfigChangedListener listener) {
        this.configChangedListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (OsmandApplication) requireActivity().getApplication();

        // Get manager from plugin
        P2pSharePlugin plugin = PluginsHelper.getPlugin(P2pSharePlugin.class);
        if (plugin != null) {
            shareManager = plugin.getShareManager();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), getTheme());

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);

                // Set max height to 80% of screen using proper LayoutParams type
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
        View view = inflater.inflate(R.layout.bottom_sheet_content_config, container, false);

        initViews(view);
        loadContent();
        setupListeners();
        updateSummary();

        return view;
    }

    private void initViews(View view) {
        contentRecycler = view.findViewById(R.id.content_recycler);
        summaryText = view.findViewById(R.id.summary_text);
        selectAllButton = view.findViewById(R.id.select_all_button);
        deselectAllButton = view.findViewById(R.id.deselect_all_button);

        ImageButton closeButton = view.findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> dismiss());

        Button doneButton = view.findViewById(R.id.done_button);
        doneButton.setOnClickListener(v -> {
            saveAndDismiss();
        });

        // Setup RecyclerView
        adapter = new ContentListAdapter(contentList, this);
        contentRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        contentRecycler.setAdapter(adapter);
    }

    private void loadContent() {
        contentList.clear();

        if (shareManager != null) {
            // Make sure manifest is up to date
            shareManager.refreshLocalManifest();

            ContentManifest manifest = shareManager.getLocalManifest();
            contentList.addAll(manifest.getAllContent());
        }

        adapter.notifyDataSetChanged();
    }

    private void setupListeners() {
        selectAllButton.setOnClickListener(v -> {
            adapter.selectAll();
            updateSummary();
        });

        deselectAllButton.setOnClickListener(v -> {
            adapter.deselectAll();
            updateSummary();
        });
    }

    private void updateSummary() {
        int selected = adapter.getSelectedCount();
        int total = contentList.size();

        // Calculate total size of selected items
        long totalSize = 0;
        for (ShareableContent content : contentList) {
            if (content.isShared()) {
                totalSize += content.getFileSize();
            }
        }

        String sizeStr = formatSize(totalSize);
        String summary = getString(R.string.p2p_share_items_selected, selected) + " (" + sizeStr + ")";
        summaryText.setText(summary);
    }

    private String formatSize(long bytes) {
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

    private void saveAndDismiss() {
        // Persist excluded filenames to SharedPreferences
        if (app != null) {
            Set<String> excludedFiles = new HashSet<>();
            for (ShareableContent content : contentList) {
                if (!content.isShared()) {
                    excludedFiles.add(content.getFilename());
                }
            }
            app.getSharedPreferences("p2p_share_config", Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet("excluded_files", excludedFiles)
                    .apply();
        }

        // Notify listener
        if (configChangedListener != null) {
            configChangedListener.onContentConfigChanged();
        }

        dismiss();
    }

    // ContentListAdapter.OnContentToggleListener

    @Override
    public void onContentToggled(@NonNull ShareableContent content, boolean isShared) {
        updateSummary();
    }
}
