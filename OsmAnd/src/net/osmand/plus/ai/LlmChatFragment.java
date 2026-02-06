package net.osmand.plus.ai;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.wikivoyage.WikiBaseDialogFragment;

import org.apache.commons.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * LAMPP: Chat interface for local LLM AI assistant.
 *
 * Provides a conversational UI for interacting with locally-running
 * AI models for offline assistance with survival, navigation, and general knowledge.
 */
public class LlmChatFragment extends WikiBaseDialogFragment {

	public static final String TAG = "LlmChatFragment";
	private static final Log LOG = PlatformUtil.getLog(LlmChatFragment.class);

	// UI Components
	private Toolbar toolbar;
	private LinearLayout modelStatusBar;
	private ImageView modelStatusIcon;
	private TextView modelStatusText;
	private Button modelActionButton;
	private RecyclerView chatMessagesRecycler;
	private View emptyState;
	private View generatingIndicator;
	private EditText messageInput;
	private ImageButton sendButton;

	// State
	private LlmManager llmManager;
	private ChatAdapter chatAdapter;
	private final List<ChatMessage> messages = new ArrayList<>();

	public static void showInstance(@NonNull FragmentManager fragmentManager) {
		if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
			LlmChatFragment fragment = new LlmChatFragment();
			fragment.show(fragmentManager, TAG);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		updateNightMode();
		View view = inflater.inflate(R.layout.fragment_llm_chat, container, false);

		// Initialize manager
		llmManager = new LlmManager(app);

		// Setup UI
		setupToolbar(view.findViewById(R.id.toolbar));
		initViews(view);
		setupListeners();
		updateModelStatus();
		updateUI();

		return view;
	}

	@Override
	protected void setupToolbar(Toolbar toolbar) {
		super.setupToolbar(toolbar);
		this.toolbar = toolbar;
		toolbar.setTitle("AI Assistant");

		// Add menu for model management
		toolbar.inflateMenu(R.menu.menu_llm_chat);
		toolbar.setOnMenuItemClickListener(item -> {
			if (item.getItemId() == R.id.action_models) {
				showModelManagement();
				return true;
			}
			return false;
		});
	}

	private void initViews(View view) {
		modelStatusBar = view.findViewById(R.id.model_status_bar);
		modelStatusIcon = view.findViewById(R.id.model_status_icon);
		modelStatusText = view.findViewById(R.id.model_status_text);
		modelActionButton = view.findViewById(R.id.model_action_button);
		chatMessagesRecycler = view.findViewById(R.id.chat_messages);
		emptyState = view.findViewById(R.id.empty_state);
		generatingIndicator = view.findViewById(R.id.generating_indicator);
		messageInput = view.findViewById(R.id.message_input);
		sendButton = view.findViewById(R.id.send_button);

		// Setup RecyclerView
		chatAdapter = new ChatAdapter();
		chatMessagesRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
		chatMessagesRecycler.setAdapter(chatAdapter);
	}

	private void setupListeners() {
		// Model action handler - shared between button and status bar
		View.OnClickListener modelClickListener = v -> {
			android.util.Log.i(TAG, "Model action clicked! isLoaded=" + llmManager.isModelLoaded()
				+ " hasDownloaded=" + llmManager.hasDownloadedModels());
			if (llmManager.isModelLoaded()) {
				// Unload model to free memory
				llmManager.closeModel();
				updateModelStatus();
			} else if (llmManager.hasDownloadedModels()) {
				// Load first available model
				File[] models = llmManager.getDownloadedModels();
				android.util.Log.i(TAG, "Found " + models.length + " models to load");
				if (models.length > 0) {
					loadModel(models[0]);
				}
			} else {
				// Go to model download
				showModelManagement();
			}
		};

		// Make both the button AND the entire status bar clickable
		modelActionButton.setOnClickListener(modelClickListener);
		modelStatusBar.setOnClickListener(modelClickListener);
		modelStatusBar.setClickable(true);
		modelStatusBar.setFocusable(true);

		// Message input text watcher
		messageInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				updateSendButton();
			}
		});

		// Send button
		sendButton.setOnClickListener(v -> sendMessage());
	}

	private void updateModelStatus() {
		if (llmManager.isLoading()) {
			modelStatusIcon.setImageResource(R.drawable.ic_action_time);
			modelStatusText.setText("Loading model...");
			modelActionButton.setVisibility(View.GONE);
		} else if (llmManager.isModelLoaded()) {
			modelStatusIcon.setImageResource(R.drawable.ic_action_done);
			modelStatusText.setText("Model: " + llmManager.getCurrentModelName());
			modelActionButton.setText("Unload");
			modelActionButton.setVisibility(View.VISIBLE);
		} else if (llmManager.hasDownloadedModels()) {
			modelStatusIcon.setImageResource(R.drawable.ic_action_info_outlined);
			modelStatusText.setText("Model available - tap to load");
			modelActionButton.setText("Load");
			modelActionButton.setVisibility(View.VISIBLE);
		} else {
			modelStatusIcon.setImageResource(R.drawable.ic_action_alert);
			modelStatusText.setText("No model installed");
			modelActionButton.setText(R.string.shared_string_download);
			modelActionButton.setVisibility(View.VISIBLE);
		}

		updateSendButton();
	}

	private void updateSendButton() {
		boolean canSend = llmManager.isModelLoaded()
			&& !llmManager.isGenerating()
			&& messageInput.getText().length() > 0;
		sendButton.setEnabled(canSend);
	}

	private void updateUI() {
		if (messages.isEmpty()) {
			emptyState.setVisibility(View.VISIBLE);
			chatMessagesRecycler.setVisibility(View.GONE);
		} else {
			emptyState.setVisibility(View.GONE);
			chatMessagesRecycler.setVisibility(View.VISIBLE);
		}

		generatingIndicator.setVisibility(llmManager.isGenerating() ? View.VISIBLE : View.GONE);
	}

	private void loadModel(File modelFile) {
		llmManager.loadModel(modelFile, new LlmManager.ModelLoadCallback() {
			@Override
			public void onLoadStarted() {
				updateModelStatus();
			}

			@Override
			public void onLoadComplete(String modelName) {
				updateModelStatus();
				app.showShortToastMessage("Model loaded: " + modelName);
			}

			@Override
			public void onLoadError(String error) {
				updateModelStatus();
				app.showShortToastMessage("Failed to load model: " + error);
			}
		});
	}

	private void sendMessage() {
		String text = messageInput.getText().toString().trim();
		if (text.isEmpty() || !llmManager.isModelLoaded()) {
			return;
		}

		// Add user message
		ChatMessage userMessage = new ChatMessage(ChatMessage.ROLE_USER, text);
		messages.add(userMessage);
		chatAdapter.notifyItemInserted(messages.size() - 1);
		chatMessagesRecycler.scrollToPosition(messages.size() - 1);

		// Clear input
		messageInput.setText("");

		// Update UI
		updateUI();
		updateSendButton();

		// Generate response
		llmManager.generateResponseAsync(text, new LlmManager.LlmCallback() {
			@Override
			public void onPartialResult(String partialText) {
				// Update the last AI message with partial response
				int lastIndex = messages.size() - 1;
				if (lastIndex >= 0 && messages.get(lastIndex).role == ChatMessage.ROLE_AI) {
					messages.get(lastIndex).content = partialText;
					chatAdapter.notifyItemChanged(lastIndex);
				} else {
					// Add new AI message
					ChatMessage aiMessage = new ChatMessage(ChatMessage.ROLE_AI, partialText);
					messages.add(aiMessage);
					chatAdapter.notifyItemInserted(messages.size() - 1);
				}
				chatMessagesRecycler.scrollToPosition(messages.size() - 1);
			}

			@Override
			public void onComplete(String fullResponse) {
				updateUI();
				updateSendButton();
			}

			@Override
			public void onError(String error) {
				// Add error message
				ChatMessage errorMessage = new ChatMessage(ChatMessage.ROLE_SYSTEM, "Error: " + error);
				messages.add(errorMessage);
				chatAdapter.notifyItemInserted(messages.size() - 1);
				chatMessagesRecycler.scrollToPosition(messages.size() - 1);

				updateUI();
				updateSendButton();
			}
		});

		// Show generating indicator
		updateUI();
	}

	private void showModelManagement() {
		LlmModelsFragment.showInstance(getParentFragmentManager());
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		// Don't close the model - keep it loaded for quick access
		// User can manually unload from status bar if needed
	}

	// Chat message data class
	static class ChatMessage {
		static final int ROLE_USER = 0;
		static final int ROLE_AI = 1;
		static final int ROLE_SYSTEM = 2;

		int role;
		String content;

		ChatMessage(int role, String content) {
			this.role = role;
			this.content = content;
		}
	}

	// RecyclerView Adapter
	class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

		@NonNull
		@Override
		public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.item_llm_message, parent, false);
			return new MessageViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
			ChatMessage message = messages.get(position);
			holder.bind(message);
		}

		@Override
		public int getItemCount() {
			return messages.size();
		}

		class MessageViewHolder extends RecyclerView.ViewHolder {
			CardView messageCard;
			TextView roleText;
			TextView contentText;

			MessageViewHolder(@NonNull View itemView) {
				super(itemView);
				messageCard = itemView.findViewById(R.id.message_card);
				roleText = itemView.findViewById(R.id.message_role);
				contentText = itemView.findViewById(R.id.message_content);
			}

			void bind(ChatMessage message) {
				contentText.setText(message.content);

				// Style based on role
				LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();

				switch (message.role) {
					case ChatMessage.ROLE_USER:
						params.gravity = Gravity.END;
						messageCard.setCardBackgroundColor(getResources().getColor(
							nightMode ? R.color.active_color_primary_dark : R.color.active_color_primary_light));
						contentText.setTextColor(getResources().getColor(R.color.text_color_primary_dark));
						roleText.setVisibility(View.GONE);
						break;

					case ChatMessage.ROLE_AI:
						params.gravity = Gravity.START;
						messageCard.setCardBackgroundColor(getResources().getColor(
							nightMode ? R.color.card_and_list_background_dark : R.color.card_and_list_background_light));
						contentText.setTextColor(getResources().getColor(
							nightMode ? R.color.text_color_primary_dark : R.color.text_color_primary_light));
						roleText.setVisibility(View.GONE);
						break;

					case ChatMessage.ROLE_SYSTEM:
						params.gravity = Gravity.CENTER;
						messageCard.setCardBackgroundColor(getResources().getColor(R.color.color_warning));
						contentText.setTextColor(getResources().getColor(R.color.text_color_primary_dark));
						roleText.setVisibility(View.GONE);
						break;
				}

				messageCard.setLayoutParams(params);
			}
		}
	}
}
