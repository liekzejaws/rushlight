/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.plus.R;
import net.osmand.plus.plugins.p2pshare.ContentType;
import net.osmand.plus.plugins.p2pshare.ShareableContent;

import java.util.List;

/**
 * Adapter for displaying shareable content items with checkboxes.
 * Used in the content configuration bottom sheet.
 */
public class ContentListAdapter extends RecyclerView.Adapter<ContentListAdapter.ContentViewHolder> {

    private final List<ShareableContent> contentList;
    private final OnContentToggleListener toggleListener;

    public interface OnContentToggleListener {
        void onContentToggled(@NonNull ShareableContent content, boolean isShared);
    }

    public ContentListAdapter(@NonNull List<ShareableContent> contentList,
                              @NonNull OnContentToggleListener toggleListener) {
        this.contentList = contentList;
        this.toggleListener = toggleListener;
    }

    @NonNull
    @Override
    public ContentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shareable_content, parent, false);
        return new ContentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContentViewHolder holder, int position) {
        ShareableContent content = contentList.get(position);
        holder.bind(content);
    }

    @Override
    public int getItemCount() {
        return contentList.size();
    }

    /**
     * Select all items in the list.
     */
    public void selectAll() {
        for (ShareableContent content : contentList) {
            content.setShared(true);
        }
        notifyDataSetChanged();
    }

    /**
     * Deselect all items in the list.
     */
    public void deselectAll() {
        for (ShareableContent content : contentList) {
            content.setShared(false);
        }
        notifyDataSetChanged();
    }

    /**
     * Get count of selected items.
     */
    public int getSelectedCount() {
        int count = 0;
        for (ShareableContent content : contentList) {
            if (content.isShared()) {
                count++;
            }
        }
        return count;
    }

    class ContentViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView nameView;
        private final TextView detailsView;
        private final CheckBox checkBox;

        ContentViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.content_icon);
            nameView = itemView.findViewById(R.id.content_name);
            detailsView = itemView.findViewById(R.id.content_details);
            checkBox = itemView.findViewById(R.id.content_checkbox);

            // Clicking anywhere on the row toggles the checkbox
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    ShareableContent content = contentList.get(pos);
                    boolean newState = !content.isShared();
                    content.setShared(newState);
                    checkBox.setChecked(newState);
                    toggleListener.onContentToggled(content, newState);
                }
            });
        }

        void bind(@NonNull ShareableContent content) {
            nameView.setText(content.getDisplayName());

            // Build details string: size + type
            String details = content.getFormattedSize() + " · " + content.getContentType().getDisplayName();
            detailsView.setText(details);

            // Set checkbox state
            checkBox.setChecked(content.isShared());

            // Set icon based on content type
            iconView.setImageResource(getIconForType(content.getContentType()));
        }

        private int getIconForType(@NonNull ContentType type) {
            switch (type) {
                case MAP:
                    return R.drawable.ic_world_globe_dark;
                case ZIM:
                    return R.drawable.ic_plugin_wikipedia;
                case MODEL:
                    return R.drawable.ic_action_help; // AI/brain icon
                case APK:
                    return R.drawable.ic_action_apk;
                default:
                    return R.drawable.ic_action_folder;
            }
        }
    }
}
