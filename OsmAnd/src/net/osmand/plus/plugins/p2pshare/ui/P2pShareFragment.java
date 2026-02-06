package net.osmand.plus.plugins.p2pshare.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.p2pshare.ContentManifest;
import net.osmand.plus.plugins.p2pshare.P2pShareManager;
import net.osmand.plus.plugins.p2pshare.P2pSharePlugin;
import net.osmand.plus.plugins.p2pshare.ShareableContent;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.plugins.p2pshare.ui.adapters.NearbyPeersAdapter;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.wikivoyage.WikiBaseDialogFragment;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * LAMPP Phase 6: Main UI for P2P content sharing.
 *
 * Shows nearby peers, local content available for sharing,
 * and manages the sharing process.
 */
public class P2pShareFragment extends WikiBaseDialogFragment implements P2pShareManager.P2pShareListener {

    public static final String TAG = "P2pShareFragment";
    private static final Log LOG = PlatformUtil.getLog(P2pShareFragment.class);

    // UI Components
    private Toolbar toolbar;
    private View scanningIndicator;
    private TextView scanningText;
    private ProgressBar scanningProgress;
    private RecyclerView peersRecycler;
    private View emptyPeersState;
    private TextView myContentSummary;
    private Button configureContentButton;
    private Button startScanButton;

    // State
    private P2pShareManager shareManager;
    private NearbyPeersAdapter peersAdapter;
    private final List<DiscoveredPeer> peers = new ArrayList<>();

    public static void showInstance(@NonNull FragmentManager fragmentManager) {
        if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
            P2pShareFragment fragment = new P2pShareFragment();
            fragment.show(fragmentManager, TAG);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        updateNightMode();
        View view = inflater.inflate(R.layout.fragment_p2p_share, container, false);

        // Get manager from plugin
        P2pSharePlugin plugin = PluginsHelper.getPlugin(P2pSharePlugin.class);
        if (plugin != null) {
            shareManager = plugin.getShareManager();
        }

        setupToolbar(view.findViewById(R.id.toolbar));
        initViews(view);
        setupListeners();

        if (shareManager != null) {
            shareManager.addListener(this);
            shareManager.refreshLocalManifest();
        }

        updateUI();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (shareManager != null) {
            shareManager.removeListener(this);
        }
    }

    @Override
    protected void setupToolbar(Toolbar toolbar) {
        super.setupToolbar(toolbar);
        this.toolbar = toolbar;
        toolbar.setTitle(R.string.p2p_share_name);
    }

    private void initViews(View view) {
        scanningIndicator = view.findViewById(R.id.scanning_indicator);
        scanningText = view.findViewById(R.id.scanning_text);
        scanningProgress = view.findViewById(R.id.scanning_progress);
        peersRecycler = view.findViewById(R.id.peers_recycler);
        emptyPeersState = view.findViewById(R.id.empty_peers_state);
        myContentSummary = view.findViewById(R.id.my_content_summary);
        configureContentButton = view.findViewById(R.id.configure_content_button);
        startScanButton = view.findViewById(R.id.start_scan_button);

        // Setup RecyclerView
        peersAdapter = new NearbyPeersAdapter(peers, this::onPeerClicked);
        peersRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        peersRecycler.setAdapter(peersAdapter);
    }

    private void setupListeners() {
        startScanButton.setOnClickListener(v -> {
            if (shareManager != null) {
                if (shareManager.isScanning()) {
                    shareManager.stopScanning();
                } else {
                    shareManager.startScanning();
                }
            }
        });

        configureContentButton.setOnClickListener(v -> {
            // TODO Phase 6.2: Show content configuration dialog
            showContentConfigDialog();
        });
    }

    private void updateUI() {
        // Update scanning state
        boolean scanning = shareManager != null && shareManager.isScanning();
        scanningIndicator.setVisibility(scanning ? View.VISIBLE : View.GONE);
        startScanButton.setText(scanning ? R.string.shared_string_cancel : R.string.p2p_share_scan);

        // Update peers list
        peers.clear();
        if (shareManager != null) {
            peers.addAll(shareManager.getDiscoveredPeers());
        }
        peersAdapter.notifyDataSetChanged();

        // Show empty state if no peers
        boolean hasPeers = !peers.isEmpty();
        peersRecycler.setVisibility(hasPeers ? View.VISIBLE : View.GONE);
        emptyPeersState.setVisibility(hasPeers ? View.GONE : View.VISIBLE);

        // Update content summary
        if (shareManager != null) {
            ContentManifest manifest = shareManager.getLocalManifest();
            myContentSummary.setText(manifest.getSummary());
        } else {
            myContentSummary.setText(R.string.p2p_share_no_content);
        }
    }

    private void onPeerClicked(@NonNull DiscoveredPeer peer) {
        LOG.info("Peer clicked: " + peer.getDeviceName());

        // TODO Phase 6.4: Connect to peer and show their content
        // For now, just show a toast
        app.showShortToastMessage("Connecting to " + peer.getDeviceName() + "...");

        if (shareManager != null) {
            shareManager.connectToPeer(peer);
        }
    }

    private void showContentConfigDialog() {
        ContentConfigBottomSheet.showInstance(
                getChildFragmentManager(),
                this::onContentConfigChanged
        );
    }

    private void onContentConfigChanged() {
        // Refresh the content summary after configuration changes
        updateUI();
    }

    // P2pShareListener callbacks

    @Override
    public void onPeerDiscovered(@NonNull DiscoveredPeer peer) {
        requireActivity().runOnUiThread(() -> {
            if (!peers.contains(peer)) {
                peers.add(peer);
                peersAdapter.notifyItemInserted(peers.size() - 1);
                updateEmptyState();
            }
        });
    }

    @Override
    public void onPeerLost(@NonNull DiscoveredPeer peer) {
        requireActivity().runOnUiThread(() -> {
            int index = peers.indexOf(peer);
            if (index >= 0) {
                peers.remove(index);
                peersAdapter.notifyItemRemoved(index);
                updateEmptyState();
            }
        });
    }

    @Override
    public void onScanningStateChanged(boolean isScanning) {
        requireActivity().runOnUiThread(() -> {
            scanningIndicator.setVisibility(isScanning ? View.VISIBLE : View.GONE);
            startScanButton.setText(isScanning ? R.string.shared_string_cancel : R.string.p2p_share_scan);
        });
    }

    @Override
    public void onTransferProgress(@NonNull String filename, int progress, long bytesTransferred, long totalBytes) {
        // TODO Phase 6.4: Update transfer progress UI
    }

    @Override
    public void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error) {
        requireActivity().runOnUiThread(() -> {
            if (success) {
                app.showShortToastMessage("Transfer complete: " + filename);
            } else {
                app.showShortToastMessage("Transfer failed: " + (error != null ? error : "Unknown error"));
            }
        });
    }

    private void updateEmptyState() {
        boolean hasPeers = !peers.isEmpty();
        peersRecycler.setVisibility(hasPeers ? View.VISIBLE : View.GONE);
        emptyPeersState.setVisibility(hasPeers ? View.GONE : View.VISIBLE);
    }
}
