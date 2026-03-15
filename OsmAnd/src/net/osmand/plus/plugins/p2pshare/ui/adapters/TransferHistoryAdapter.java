/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.plus.R;
import net.osmand.plus.plugins.p2pshare.history.TransferRecord;

import java.util.List;

/**
 * RecyclerView adapter for displaying transfer history records.
 */
public class TransferHistoryAdapter extends RecyclerView.Adapter<TransferHistoryAdapter.ViewHolder> {

	private final List<TransferRecord> records;

	public TransferHistoryAdapter(@NonNull List<TransferRecord> records) {
		this.records = records;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_transfer_history, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		TransferRecord record = records.get(position);
		holder.bind(record);
	}

	@Override
	public int getItemCount() {
		return records.size();
	}

	static class ViewHolder extends RecyclerView.ViewHolder {

		private final ImageView directionIcon;
		private final TextView filenameText;
		private final TextView statusText;
		private final TextView detailsText;
		private final TextView timeText;

		ViewHolder(@NonNull View itemView) {
			super(itemView);
			directionIcon = itemView.findViewById(R.id.direction_icon);
			filenameText = itemView.findViewById(R.id.filename_text);
			statusText = itemView.findViewById(R.id.status_text);
			detailsText = itemView.findViewById(R.id.details_text);
			timeText = itemView.findViewById(R.id.time_text);
		}

		void bind(@NonNull TransferRecord record) {
			// Filename
			filenameText.setText(record.getFilename());

			// Direction icon
			boolean isReceived = TransferRecord.DIRECTION_RECEIVED.equals(record.getDirection());
			directionIcon.setImageResource(isReceived
					? R.drawable.ic_action_import
					: R.drawable.ic_action_export);

			// Status text with color
			statusText.setText(record.getStatusDisplay());
			switch (record.getStatus()) {
				case TransferRecord.STATUS_SUCCESS:
					statusText.setTextColor(Color.parseColor("#4CAF50")); // Green
					break;
				case TransferRecord.STATUS_FAILED:
					statusText.setTextColor(Color.parseColor("#F44336")); // Red
					break;
				case TransferRecord.STATUS_CANCELLED:
					statusText.setTextColor(Color.parseColor("#FF9800")); // Orange
					break;
				default:
					statusText.setTextColor(Color.parseColor("#2196F3")); // Blue
					break;
			}

			// Details: size, speed, peer
			StringBuilder details = new StringBuilder();
			details.append(record.getFormattedSize());

			String speed = record.getFormattedSpeed();
			if (!speed.isEmpty()) {
				details.append(" · ").append(speed);
			}

			details.append(" · ").append(record.getFormattedDuration());

			String peerName = record.getPeerName();
			if (peerName != null && !peerName.isEmpty()) {
				details.append(" · ").append(peerName);
			}

			// Checksum status
			if (record.getChecksumOk() == TransferRecord.CHECKSUM_PASSED) {
				details.append(" ✓");
			} else if (record.getChecksumOk() == TransferRecord.CHECKSUM_FAILED) {
				details.append(" ✗");
			}

			detailsText.setText(details.toString());

			// Time
			timeText.setText(record.getFormattedTime());

			// Error tooltip on failed items
			if (TransferRecord.STATUS_FAILED.equals(record.getStatus())
					&& record.getError() != null) {
				itemView.setOnClickListener(v -> {
					// Toggle error display
					if (detailsText.getMaxLines() == 1) {
						detailsText.setMaxLines(3);
						detailsText.setText(record.getError());
					} else {
						detailsText.setMaxLines(1);
						detailsText.setText(details.toString());
					}
				});
			} else {
				itemView.setOnClickListener(null);
				itemView.setClickable(false);
			}
		}
	}
}
