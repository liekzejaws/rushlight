/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.wikipedia;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;

import java.util.ArrayList;
import java.util.List;

/**
 * LAMPP: RecyclerView adapter for displaying ZIM catalog items.
 */
public class ZimCatalogAdapter extends RecyclerView.Adapter<ZimCatalogAdapter.ViewHolder> {

	private final OsmandApplication app;
	private final ZimDownloadManager downloadManager;
	private List<ZimCatalogItem> items = new ArrayList<>();
	private OnItemClickListener listener;

	public interface OnItemClickListener {
		void onDownloadClick(ZimCatalogItem item);
		void onCancelClick(ZimCatalogItem item);
		void onOpenClick(ZimCatalogItem item);
	}

	public ZimCatalogAdapter(@NonNull OsmandApplication app, @NonNull ZimDownloadManager downloadManager) {
		this.app = app;
		this.downloadManager = downloadManager;
	}

	public void setItems(@NonNull List<ZimCatalogItem> items) {
		this.items = new ArrayList<>(items);
		notifyDataSetChanged();
	}

	public void setOnItemClickListener(OnItemClickListener listener) {
		this.listener = listener;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.zim_catalog_item, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		ZimCatalogItem item = items.get(position);
		holder.bind(item);
	}

	@Override
	public int getItemCount() {
		return items.size();
	}

	class ViewHolder extends RecyclerView.ViewHolder {
		private final TextView titleText;
		private final TextView descriptionText;
		private final TextView sizeText;
		private final TextView languageText;
		private final ProgressBar progressBar;
		private final ImageButton actionButton;

		ViewHolder(@NonNull View itemView) {
			super(itemView);
			titleText = itemView.findViewById(R.id.title);
			descriptionText = itemView.findViewById(R.id.description);
			sizeText = itemView.findViewById(R.id.size);
			languageText = itemView.findViewById(R.id.language);
			progressBar = itemView.findViewById(R.id.progress_bar);
			actionButton = itemView.findViewById(R.id.action_button);
		}

		void bind(ZimCatalogItem item) {
			titleText.setText(item.getDisplayTitle());

			String desc = item.getDescription();
			if (desc != null && desc.length() > 100) {
				desc = desc.substring(0, 100) + "...";
			}
			descriptionText.setText(desc != null ? desc : "");

			sizeText.setText(item.getFileSizeString());

			String lang = item.getLanguage();
			languageText.setText(lang != null ? lang.toUpperCase() : "");

			// Determine state: downloaded, downloading, or available
			boolean isDownloaded = downloadManager.isDownloaded(item);
			boolean isDownloading = downloadManager.isDownloading(item);

			if (isDownloading) {
				// Show progress
				progressBar.setVisibility(View.VISIBLE);
				progressBar.setProgress(downloadManager.getDownloadProgress(item));
				actionButton.setImageResource(R.drawable.ic_action_remove_dark);
				actionButton.setOnClickListener(v -> {
					if (listener != null) {
						listener.onCancelClick(item);
					}
				});
			} else if (isDownloaded) {
				// Show open button
				progressBar.setVisibility(View.GONE);
				actionButton.setImageResource(R.drawable.ic_action_read_from_file);
				actionButton.setOnClickListener(v -> {
					if (listener != null) {
						listener.onOpenClick(item);
					}
				});
			} else {
				// Show download button
				progressBar.setVisibility(View.GONE);
				actionButton.setImageResource(R.drawable.ic_action_import);
				actionButton.setOnClickListener(v -> {
					if (listener != null) {
						listener.onDownloadClick(item);
					}
				});
			}

			// Item click opens if downloaded
			itemView.setOnClickListener(v -> {
				if (isDownloaded && listener != null) {
					listener.onOpenClick(item);
				}
			});
		}
	}
}
