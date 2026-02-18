package net.osmand.plus.plugins.p2pshare.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.lampp.LamppPanelFragment;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.p2pshare.ContentManifest;
import net.osmand.plus.plugins.p2pshare.P2pPermissionHelper;
import net.osmand.plus.plugins.p2pshare.P2pShareManager;
import net.osmand.plus.plugins.p2pshare.P2pSharePlugin;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;
import net.osmand.plus.plugins.p2pshare.ui.adapters.NearbyPeersAdapter;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * LAMPP Phase 6: Main UI for P2P content sharing.
 *
 * Shows nearby peers, local content available for sharing,
 * and manages the sharing process.
 */
public class P2pShareFragment extends LamppPanelFragment implements P2pShareManager.P2pShareListener {

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

    // Transfer progress UI
    private View transferProgressCard;
    private TextView transferFilename;
    private ProgressBar transferProgressBar;
    private TextView transferProgressText;
    private Button transferCancelButton;
    private ImageButton historyButton;

    // State
    private P2pShareManager shareManager;
    private NearbyPeersAdapter peersAdapter;
    private final List<DiscoveredPeer> peers = new ArrayList<>();

    @Override
    protected int getPanelLayoutId() {
        return R.layout.fragment_p2p_share;
    }

    @NonNull
    @Override
    public String getPanelTag() {
        return TAG;
    }

    @Override
    protected void onPanelViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Get manager from plugin
        P2pSharePlugin plugin = PluginsHelper.getPlugin(P2pSharePlugin.class);
        if (plugin != null) {
            shareManager = plugin.getShareManager();
        }

        // Setup toolbar if present
        toolbar = view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.p2p_share_name);
        }

        initViews(view);
        setupListeners();

        if (shareManager != null) {
            shareManager.addListener(this);
            shareManager.setCurrentActivity(getActivity());
            shareManager.refreshLocalManifest();
        }

        updateUI();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (shareManager != null) {
            shareManager.removeListener(this);
        }
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

        // Transfer progress views
        transferProgressCard = view.findViewById(R.id.transfer_progress_card);
        transferFilename = view.findViewById(R.id.transfer_filename);
        transferProgressBar = view.findViewById(R.id.transfer_progress_bar);
        transferProgressText = view.findViewById(R.id.transfer_progress_text);
        transferCancelButton = view.findViewById(R.id.transfer_cancel_button);
        transferCancelButton.setOnClickListener(v -> {
            if (shareManager != null) {
                shareManager.cancelTransfer();
            }
            transferProgressCard.setVisibility(View.GONE);
        });

        // History button
        historyButton = view.findViewById(R.id.history_button);
        if (historyButton != null) {
            historyButton.setOnClickListener(v ->
                    TransferHistoryBottomSheet.showInstance(getChildFragmentManager()));
        }

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
                    startScanWithPermissionCheck();
                }
            }
        });

        configureContentButton.setOnClickListener(v -> {
            showContentConfigDialog();
        });
    }

    /**
     * Check permissions before starting scan.
     * Shows rationale dialog if permissions aren't granted.
     */
    private void startScanWithPermissionCheck() {
        if (getActivity() == null || shareManager == null) return;

        if (P2pPermissionHelper.hasAllPermissions(requireContext())) {
            shareManager.startScanning();
        } else {
            P2pPermissionHelper.requestPermissionsWithRationale(
                    getActivity(),
                    getChildFragmentManager(),
                    new P2pPermissionHelper.PermissionCallback() {
                        @Override
                        public void onPermissionsGranted() {
                            shareManager.startScanning();
                        }

                        @Override
                        public void onPermissionsDenied(@NonNull String message) {
                            LOG.warn("P2P permissions denied: " + message);
                        }
                    });
        }
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

        // If already connected and manifest available, show content directly
        if (peer.getState() == DiscoveredPeer.PeerState.CONNECTED && peer.getRemoteManifest() != null) {
            PeerContentBottomSheet.showInstance(getChildFragmentManager(), peer);
            return;
        }

        // If already connecting, ignore duplicate taps
        if (peer.getState() == DiscoveredPeer.PeerState.CONNECTING) {
            return;
        }

        // Start connecting
        peer.setState(DiscoveredPeer.PeerState.CONNECTING);
        int index = peers.indexOf(peer);
        if (index >= 0) {
            peersAdapter.notifyItemChanged(index);
        }

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

    private void runOnUiIfAttached(@NonNull Runnable action) {
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(action);
        }
    }

    @Override
    public void onPeerDiscovered(@NonNull DiscoveredPeer peer) {
        runOnUiIfAttached(() -> {
            if (!isAdded() || peersAdapter == null) return;
            if (!peers.contains(peer)) {
                peers.add(peer);
                peersAdapter.notifyItemInserted(peers.size() - 1);
                updateEmptyState();
            }
        });
    }

    @Override
    public void onPeerLost(@NonNull DiscoveredPeer peer) {
        runOnUiIfAttached(() -> {
            if (!isAdded() || peersAdapter == null) return;
            int index = peers.indexOf(peer);
            if (index >= 0) {
                peers.remove(index);
                peersAdapter.notifyItemRemoved(index);
                updateEmptyState();
            }
        });
    }

    @Override
    public void onPeerConnected(@NonNull DiscoveredPeer peer) {
        runOnUiIfAttached(() -> {
            if (!isAdded() || peersAdapter == null) return;
            int index = peers.indexOf(peer);
            if (index >= 0) {
                peers.set(index, peer);
                peersAdapter.notifyItemChanged(index);
            }
        });
    }

    @Override
    public void onManifestReceived(@NonNull DiscoveredPeer peer) {
        runOnUiIfAttached(() -> {
            if (!isAdded()) return;
            PeerContentBottomSheet.showInstance(getChildFragmentManager(), peer);
        });
    }

    @Override
    public void onScanningStateChanged(boolean isScanning) {
        runOnUiIfAttached(() -> {
            if (!isAdded() || scanningIndicator == null) return;
            scanningIndicator.setVisibility(isScanning ? View.VISIBLE : View.GONE);
            startScanButton.setText(isScanning ? R.string.shared_string_cancel : R.string.p2p_share_scan);
        });
    }

    @Override
    public void onTransferProgress(@NonNull String filename, int progress, long bytesTransferred, long totalBytes) {
        runOnUiIfAttached(() -> {
            if (!isAdded() || transferProgressCard == null) return;
            transferProgressCard.setVisibility(View.VISIBLE);

            // Show queue status if multiple files
            String queueStatus = "";
            if (shareManager != null) {
                net.osmand.plus.plugins.p2pshare.TransferQueue queue = shareManager.getTransferQueue();
                if (queue != null && queue.getTotalJobs() > 1) {
                    queueStatus = queue.getStatusString() + " · ";
                }
            }

            transferFilename.setText(queueStatus + filename);
            transferProgressBar.setProgress(progress);

            String transferred = formatSize(bytesTransferred);
            String total = formatSize(totalBytes);
            transferProgressText.setText(getString(R.string.p2p_share_transfer_progress, transferred, total, progress));
        });
    }

    @Override
    public void onTransferComplete(@NonNull String filename, boolean success, @Nullable String error) {
        runOnUiIfAttached(() -> {
            if (!isAdded() || transferProgressCard == null) return;
            // Check if more files in queue
            boolean moreInQueue = shareManager != null && shareManager.getTransferQueue() != null
                    && !shareManager.getTransferQueue().isEmpty();

            if (success) {
                transferFilename.setText(getString(R.string.p2p_share_transfer_complete) + ": " + filename);
                transferProgressBar.setProgress(100);
                transferProgressText.setText("");

                if (!moreInQueue) {
                    transferProgressCard.postDelayed(() -> {
                        if (transferProgressCard != null) {
                            transferProgressCard.setVisibility(View.GONE);
                        }
                    }, 3000);
                }
            } else {
                String errorMsg = error != null ? error : "Unknown";
                transferFilename.setText(getString(R.string.p2p_share_transfer_failed, errorMsg));

                if (!moreInQueue) {
                    transferProgressCard.postDelayed(() -> {
                        if (transferProgressCard != null) {
                            transferProgressCard.setVisibility(View.GONE);
                        }
                    }, 5000);
                }
            }
        });
    }

    private void updateEmptyState() {
        boolean hasPeers = !peers.isEmpty();
        peersRecycler.setVisibility(hasPeers ? View.VISIBLE : View.GONE);
        emptyPeersState.setVisibility(hasPeers ? View.GONE : View.VISIBLE);
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
}
