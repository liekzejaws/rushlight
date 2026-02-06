package net.osmand.plus.plugins.p2pshare.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.plus.R;
import net.osmand.plus.plugins.p2pshare.discovery.DiscoveredPeer;

import java.util.List;

/**
 * Adapter for displaying discovered peers in a RecyclerView.
 */
public class NearbyPeersAdapter extends RecyclerView.Adapter<NearbyPeersAdapter.PeerViewHolder> {

    private final List<DiscoveredPeer> peers;
    private final OnPeerClickListener clickListener;

    public interface OnPeerClickListener {
        void onPeerClicked(@NonNull DiscoveredPeer peer);
    }

    public NearbyPeersAdapter(@NonNull List<DiscoveredPeer> peers, @NonNull OnPeerClickListener clickListener) {
        this.peers = peers;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nearby_peer, parent, false);
        return new PeerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        DiscoveredPeer peer = peers.get(position);
        holder.bind(peer);
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }

    class PeerViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView nameView;
        private final TextView summaryView;
        private final TextView distanceView;

        PeerViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.peer_icon);
            nameView = itemView.findViewById(R.id.peer_name);
            summaryView = itemView.findViewById(R.id.peer_summary);
            distanceView = itemView.findViewById(R.id.peer_distance);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    clickListener.onPeerClicked(peers.get(pos));
                }
            });
        }

        void bind(@NonNull DiscoveredPeer peer) {
            nameView.setText(peer.getDeviceName());

            // Show content summary if available
            String summary = peer.getManifestSummary();
            if (summary != null && !summary.isEmpty()) {
                summaryView.setText(summary);
                summaryView.setVisibility(View.VISIBLE);
            } else {
                summaryView.setText(R.string.p2p_share_tap_to_connect);
                summaryView.setVisibility(View.VISIBLE);
            }

            // Show distance estimate
            distanceView.setText(peer.getDistanceEstimate());

            // Icon based on state
            switch (peer.getState()) {
                case CONNECTING:
                    iconView.setImageResource(R.drawable.ic_action_time);
                    break;
                case CONNECTED:
                    iconView.setImageResource(R.drawable.ic_action_done);
                    break;
                case TRANSFERRING:
                    iconView.setImageResource(R.drawable.ic_action_import);
                    break;
                default:
                    iconView.setImageResource(R.drawable.ic_action_device_top);
                    break;
            }
        }
    }
}
