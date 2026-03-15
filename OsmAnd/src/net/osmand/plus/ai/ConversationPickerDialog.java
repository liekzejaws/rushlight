/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.ai;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.security.EncryptedChatStorage;
import net.osmand.plus.security.EncryptedChatStorage.Conversation;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 14: Bottom sheet dialog for managing conversations.
 * Allows users to create, select, rename, and delete conversations.
 */
public class ConversationPickerDialog extends BottomSheetDialogFragment {

	public static final String TAG = "ConversationPicker";

	private RecyclerView recyclerView;
	private View emptyView;
	private ConversationAdapter adapter;
	private final List<Conversation> conversations = new ArrayList<>();
	private long activeConversationId = EncryptedChatStorage.DEFAULT_CONVERSATION_ID;

	@Nullable
	private OnConversationSelectedListener listener;

	/**
	 * Callback interface for conversation selection events.
	 */
	public interface OnConversationSelectedListener {
		void onConversationSelected(long conversationId);
		void onNewConversation(long conversationId);
		void onConversationDeleted(long conversationId);
	}

	public static ConversationPickerDialog newInstance(long activeConversationId) {
		ConversationPickerDialog dialog = new ConversationPickerDialog();
		Bundle args = new Bundle();
		args.putLong("active_conversation_id", activeConversationId);
		dialog.setArguments(args);
		return dialog;
	}

	public void setListener(@Nullable OnConversationSelectedListener listener) {
		this.listener = listener;
	}

	public static void showInstance(@NonNull FragmentManager fragmentManager,
	                                long activeConversationId,
	                                @Nullable OnConversationSelectedListener listener) {
		if (fragmentManager.findFragmentByTag(TAG) != null) return;

		ConversationPickerDialog dialog = newInstance(activeConversationId);
		dialog.setListener(listener);
		dialog.show(fragmentManager, TAG);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getArguments() != null) {
			activeConversationId = getArguments().getLong("active_conversation_id",
					EncryptedChatStorage.DEFAULT_CONVERSATION_ID);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                          @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.dialog_conversation_picker, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		recyclerView = view.findViewById(R.id.conversations_list);
		emptyView = view.findViewById(R.id.empty_conversations);
		ImageButton newButton = view.findViewById(R.id.btn_new_conversation);

		adapter = new ConversationAdapter();
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
		recyclerView.setAdapter(adapter);

		newButton.setOnClickListener(v -> showNewConversationDialog());

		loadConversations();
	}

	private void loadConversations() {
		OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
		EncryptedChatStorage storage = app.getSecurityManager().getChatStorage();
		conversations.clear();
		conversations.addAll(storage.getConversations());

		if (conversations.isEmpty()) {
			recyclerView.setVisibility(View.GONE);
			emptyView.setVisibility(View.VISIBLE);
		} else {
			recyclerView.setVisibility(View.VISIBLE);
			emptyView.setVisibility(View.GONE);
		}
		adapter.notifyDataSetChanged();
	}

	private void showNewConversationDialog() {
		EditText input = new EditText(getContext());
		input.setHint(R.string.conversation_name_hint);
		input.setPadding(48, 32, 48, 16);

		new AlertDialog.Builder(requireContext())
				.setTitle(R.string.new_conversation)
				.setView(input)
				.setPositiveButton(R.string.shared_string_create, (dialog, which) -> {
					String title = input.getText().toString().trim();
					if (title.isEmpty()) {
						title = "Conversation " + (conversations.size() + 1);
					}
					createConversation(title);
				})
				.setNegativeButton(R.string.shared_string_cancel, null)
				.show();
	}

	private void createConversation(String title) {
		OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
		EncryptedChatStorage storage = app.getSecurityManager().getChatStorage();
		long newId = storage.createConversation(title);

		if (newId > 0) {
			activeConversationId = newId;
			if (listener != null) {
				listener.onNewConversation(newId);
			}
			loadConversations();
		}
	}

	private void showRenameDialog(Conversation conv) {
		EditText input = new EditText(getContext());
		input.setText(conv.title);
		input.setSelection(conv.title.length());
		input.setPadding(48, 32, 48, 16);

		new AlertDialog.Builder(requireContext())
				.setTitle(R.string.rename_conversation)
				.setView(input)
				.setPositiveButton(R.string.shared_string_save, (dialog, which) -> {
					String newTitle = input.getText().toString().trim();
					if (!newTitle.isEmpty()) {
						OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
						app.getSecurityManager().getChatStorage().renameConversation(conv.id, newTitle);
						loadConversations();
					}
				})
				.setNegativeButton(R.string.shared_string_cancel, null)
				.show();
	}

	private void showDeleteConfirmation(Conversation conv) {
		new AlertDialog.Builder(requireContext())
				.setTitle(R.string.delete_conversation)
				.setMessage(getString(R.string.delete_conversation_confirm, conv.title))
				.setPositiveButton(R.string.shared_string_delete, (dialog, which) -> {
					OsmandApplication app = (OsmandApplication) requireActivity().getApplication();
					app.getSecurityManager().getChatStorage().deleteConversation(conv.id);

					if (listener != null) {
						listener.onConversationDeleted(conv.id);
					}

					// If we deleted the active conversation, switch to default
					if (conv.id == activeConversationId) {
						activeConversationId = EncryptedChatStorage.DEFAULT_CONVERSATION_ID;
						if (listener != null) {
							listener.onConversationSelected(activeConversationId);
						}
					}
					loadConversations();
				})
				.setNegativeButton(R.string.shared_string_cancel, null)
				.show();
	}

	// ==================== Adapter ====================

	class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_conversation, parent, false);
			return new ViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			Conversation conv = conversations.get(position);
			holder.bind(conv);
		}

		@Override
		public int getItemCount() {
			return conversations.size();
		}

		class ViewHolder extends RecyclerView.ViewHolder {
			TextView titleText;
			TextView infoText;
			ImageView activeIndicator;
			ImageButton deleteButton;

			ViewHolder(@NonNull View itemView) {
				super(itemView);
				titleText = itemView.findViewById(R.id.conv_title);
				infoText = itemView.findViewById(R.id.conv_info);
				activeIndicator = itemView.findViewById(R.id.conv_active_indicator);
				deleteButton = itemView.findViewById(R.id.conv_delete);
			}

			void bind(Conversation conv) {
				titleText.setText(conv.title);

				// Build info line: "X messages • last active Y ago"
				StringBuilder info = new StringBuilder();
				info.append(conv.messageCount).append(" message");
				if (conv.messageCount != 1) info.append("s");

				CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
						conv.updatedAt, System.currentTimeMillis(),
						DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
				info.append(" • ").append(relativeTime);

				if (conv.systemPrompt != null && !conv.systemPrompt.isEmpty()) {
					info.append(" • \uD83D\uDCDD"); // 📝 emoji for custom prompt
				}
				infoText.setText(info.toString());

				// Active indicator
				boolean isActive = conv.id == activeConversationId;
				activeIndicator.setVisibility(isActive ? View.VISIBLE : View.GONE);

				// Click to select
				itemView.setOnClickListener(v -> {
					activeConversationId = conv.id;
					if (listener != null) {
						listener.onConversationSelected(conv.id);
					}
					dismiss();
				});

				// Long press to rename
				itemView.setOnLongClickListener(v -> {
					showRenameDialog(conv);
					return true;
				});

				// Delete button (don't allow deleting the last conversation)
				deleteButton.setOnClickListener(v -> {
					if (conversations.size() <= 1) {
						// Don't delete the last conversation
						return;
					}
					showDeleteConfirmation(conv);
				});
				// Hide delete for the default conversation if it's the only one
				deleteButton.setVisibility(conversations.size() > 1 ? View.VISIBLE : View.GONE);
			}
		}
	}
}
