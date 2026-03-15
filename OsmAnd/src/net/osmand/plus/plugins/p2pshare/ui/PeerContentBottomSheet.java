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
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.plugins.p2pshare.ui.adapters.ContentListAdapter;
import net.osmand.plus.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet dialog for browsing a connected peer's available content.
 * Shows the peer's remote manifest with checkboxes for selecting files to download.
 */
public class PeerContentBottomSheet extends BottomSheetDialogFragment
        implements ContentListAdapter.OnContentToggleListener {

    public static final String TAG = "PeerContentBottomSheet";

    private OsmandApplication app;
    private P2pShareManager shareManager;

    // The peer whose content we're browsing
    @Nullable
    private DiscoveredPeer peer;

    // UI
    private TextView titleText;
    private RecyclerView contentRecycler;
    private ContentListAdapter adapter;
    private TextView summaryText;
    private TextView emptyState;
    private Button downloadButton;

    // Data
    private final List<ShareableContent> contentList = new ArrayList<>();

    // Static peer reference for fragment recreation
    @Nullable
    private static DiscoveredPeer pendingPeer;

    public static void showInstance(@NonNull FragmentManager fragmentManager,
                                    @NonNull DiscoveredPeer peer) {
        if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
            pendingPeer = peer;
            PeerContentBottomSheet fragment = new PeerContentBottomSheet();
            fragment.show(fragmentManager, TAG);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (OsmandApplication) requireActivity().getApplication();
        peer = pendingPeer;
        pendingPeer = null;

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
        View view = inflater.inflate(R.layout.bottom_sheet_peer_content, container, false);

        initViews(view);
        loadPeerContent();
        updateSummary();

        return view;
    }

    private void initViews(View view) {
        titleText = view.findViewById(R.id.title_text);
        contentRecycler = view.findViewById(R.id.content_recycler);
        summaryText = view.findViewById(R.id.summary_text);
        emptyState = view.findViewById(R.id.empty_state);
        downloadButton = view.findViewById(R.id.download_button);

        ImageButton closeButton = view.findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> dismiss());

        downloadButton.setOnClickListener(v -> downloadSelected());

        // Set title to peer's name
        if (peer != null) {
            titleText.setText(getString(R.string.p2p_share_peer_content, peer.getDeviceName()));
        }

        // Setup RecyclerView
        adapter = new ContentListAdapter(contentList, this);
        contentRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        contentRecycler.setAdapter(adapter);
    }

    private void loadPeerContent() {
        contentList.clear();

        if (peer != null) {
            ContentManifest remoteManifest = peer.getRemoteManifest();
            if (remoteManifest != null) {
                List<ShareableContent> remoteContent = remoteManifest.getAllContent();
                // Default all to unchecked — user opts IN to download
                for (ShareableContent content : remoteContent) {
                    content.setShared(false);
                    contentList.add(content);
                }
            }
        }

        adapter.notifyDataSetChanged();

        // Show empty state if no content
        boolean hasContent = !contentList.isEmpty();
        contentRecycler.setVisibility(hasContent ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(hasContent ? View.GONE : View.VISIBLE);
        downloadButton.setEnabled(hasContent);
    }

    private void updateSummary() {
        int selected = 0;
        long totalSize = 0;
        for (ShareableContent content : contentList) {
            if (content.isShared()) {
                selected++;
                totalSize += content.getFileSize();
            }
        }

        String sizeStr = formatSize(totalSize);
        String summary = getString(R.string.p2p_share_items_selected, selected) + " (" + sizeStr + ")";
        summaryText.setText(summary);

        downloadButton.setEnabled(selected > 0);
    }

    private void downloadSelected() {
        if (shareManager == null) return;

        // Collect all selected files and enqueue them
        List<ShareableContent> selectedFiles = new ArrayList<>();
        for (ShareableContent content : contentList) {
            if (content.isShared()) {
                selectedFiles.add(content);
            }
        }

        if (!selectedFiles.isEmpty()) {
            shareManager.enqueueFiles(selectedFiles);
        }
        dismiss();
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

    // ContentListAdapter.OnContentToggleListener

    @Override
    public void onContentToggled(@NonNull ShareableContent content, boolean isShared) {
        updateSummary();
    }
}
