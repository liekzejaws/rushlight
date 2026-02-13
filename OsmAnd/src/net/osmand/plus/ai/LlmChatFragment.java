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
import net.osmand.plus.ai.rag.ArticleSource;
import net.osmand.plus.ai.rag.PoiSource;
import net.osmand.plus.ai.rag.RagCallback;
import net.osmand.plus.ai.rag.RagManager;
import net.osmand.plus.ai.rag.RagResponse;
import net.osmand.plus.security.EncryptedChatStorage;
import net.osmand.plus.security.SecurityManager;
import net.osmand.plus.lampp.LamppPanelFragment;
import net.osmand.plus.lampp.LamppStylePreset;
import net.osmand.plus.lampp.LamppThemeUtils;
import net.osmand.plus.lampp.effects.TerminalCursorBlinker;
import net.osmand.plus.utils.AndroidUtils;

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
public class LlmChatFragment extends LamppPanelFragment {

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
	private RagManager ragManager;
	private ChatAdapter chatAdapter;
	private final List<ChatMessage> messages = new ArrayList<>();
	@Nullable
	private TerminalCursorBlinker cursorBlinker;
	@Nullable
	private EncryptedChatStorage chatStorage;

	@Override
	protected int getPanelLayoutId() {
		return R.layout.fragment_llm_chat;
	}

	@NonNull
	@Override
	public String getPanelTag() {
		return TAG;
	}

	@Override
	protected void onPanelViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		// Initialize managers
		llmManager = new LlmManager(app);
		ragManager = new RagManager(app, llmManager);
		cursorBlinker = new TerminalCursorBlinker();

		// Initialize encrypted chat storage and load persisted messages
		try {
			SecurityManager secMgr = app.getSecurityManager();
			chatStorage = secMgr.getChatStorage();
			List<ChatMessage> saved = chatStorage.loadAllMessages();
			if (!saved.isEmpty()) {
				messages.addAll(saved);
			}
		} catch (Exception e) {
			LOG.error("Failed to initialize chat storage: " + e.getMessage());
		}

		// Setup UI
		toolbar = view.findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setTitle("AI Assistant");
			toolbar.inflateMenu(R.menu.menu_llm_chat);
			toolbar.setOnMenuItemClickListener(item -> {
				if (item.getItemId() == R.id.action_models) {
					showModelManagement();
					return true;
				}
				return false;
			});
		}
		initViews(view);
		setupListeners();
		updateModelStatus();
		updateUI();
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
			// Show model name and data source status
			StringBuilder status = new StringBuilder();
			status.append("Model: ").append(llmManager.getCurrentModelName());
			if (ragManager.isWikipediaAvailable()) {
				status.append(" | Wiki: ").append(ragManager.getWikipediaTitle());
			}
			if (ragManager.isMapDataAvailable()) {
				status.append(" | POI: On");
			}
			modelStatusText.setText(status.toString());
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
		persistMessage(userMessage);
		chatAdapter.notifyItemInserted(messages.size() - 1);
		chatMessagesRecycler.scrollToPosition(messages.size() - 1);

		// Clear input
		messageInput.setText("");

		// Update UI
		updateUI();
		updateSendButton();

		// Generate response using RAG pipeline
		ragManager.queryAsync(text, new RagCallback() {
			@Override
			public void onSearchStarted(@NonNull List<String> searchTerms) {
				// Could show "Searching Wikipedia..." indicator
				LOG.debug("RAG: Searching for " + searchTerms);
			}

			@Override
			public void onSearchComplete(@NonNull List<ArticleSource> sources, long timeMs) {
				LOG.debug("RAG: Found " + sources.size() + " sources in " + timeMs + "ms");
			}

			@Override
			public void onGenerationStarted(boolean usesWikipedia) {
				LOG.debug("RAG: Generation started, usesWikipedia=" + usesWikipedia);
			}

			@Override
			public void onPartialResult(@NonNull String partialText) {
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

				// Update cursor blinker base text for Pip-Boy effect
				if (cursorBlinker != null && cursorBlinker.isRunning()) {
					cursorBlinker.updateBaseText(partialText);
				}
			}

			@Override
			public void onComplete(@NonNull RagResponse response) {
				// Stop cursor blinker
				if (cursorBlinker != null) {
					cursorBlinker.stop();
				}

				// Update the final message with sources if available
				int lastIndex = messages.size() - 1;
				if (lastIndex >= 0 && messages.get(lastIndex).role == ChatMessage.ROLE_AI) {
					ChatMessage aiMessage = messages.get(lastIndex);
					boolean changed = false;
					if (response.hasSources()) {
						aiMessage.sources = response.getSources();
						changed = true;
					}
					if (response.hasPoiSources()) {
						aiMessage.poiSources = response.getPoiSources();
						changed = true;
					}
					if (changed) {
						chatAdapter.notifyItemChanged(lastIndex);
					}
					// Persist completed AI response
					persistMessage(aiMessage);
				}

				LOG.info("RAG complete: " + response.getPerformanceSummary());
				updateUI();
				updateSendButton();
			}

			@Override
			public void onError(@NonNull String error) {
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

		// Shutdown RAG manager to release executor resources
		if (ragManager != null) {
			ragManager.shutdown();
		}

		// Stop cursor blinker
		if (cursorBlinker != null) {
			cursorBlinker.stop();
			cursorBlinker = null;
		}
	}

	private boolean isPipBoyCursorEnabled() {
		return app != null
				&& LamppThemeUtils.getActivePreset(app) == LamppStylePreset.PIP_BOY
				&& app.getSettings().LAMPP_PIPBOY_CURSOR_BLINK.get();
	}

	private void persistMessage(@NonNull ChatMessage message) {
		if (chatStorage != null) {
			try {
				chatStorage.saveMessage(message);
			} catch (Exception e) {
				LOG.error("Failed to persist message: " + e.getMessage());
			}
		}
	}

	/**
	 * Clear all messages from memory and storage.
	 * Called by SecurityManager during panic wipe.
	 */
	public void clearAllMessages() {
		messages.clear();
		if (chatAdapter != null) {
			chatAdapter.notifyDataSetChanged();
		}
		if (chatStorage != null) {
			chatStorage.clearMessages();
		}
		updateUI();
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
			TextView sourcesText;

			MessageViewHolder(@NonNull View itemView) {
				super(itemView);
				messageCard = itemView.findViewById(R.id.message_card);
				roleText = itemView.findViewById(R.id.message_role);
				contentText = itemView.findViewById(R.id.message_content);
				sourcesText = itemView.findViewById(R.id.message_sources);
			}

			void bind(ChatMessage message) {
				contentText.setText(message.content);

				// Pip-Boy terminal cursor blink effect on last AI message during generation
				int position = getAdapterPosition();
				if (message.role == ChatMessage.ROLE_AI
						&& position == messages.size() - 1
						&& llmManager != null && llmManager.isGenerating()
						&& isPipBoyCursorEnabled()) {
					cursorBlinker.attachTo(contentText, message.content);
				}

				// Show sources if available (for AI messages with Wikipedia/POI context)
				if (message.role == ChatMessage.ROLE_AI && message.hasAnySources()) {
					if (sourcesText != null) {
						sourcesText.setVisibility(View.VISIBLE);
						sourcesText.setText(message.getSourcesText().trim());
					}
				} else {
					if (sourcesText != null) {
						sourcesText.setVisibility(View.GONE);
					}
				}

				// Style based on role — use preset-aware colors
				LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();
				net.osmand.plus.lampp.LamppStylePreset preset =
						net.osmand.plus.lampp.LamppThemeUtils.getActivePreset(app);
				android.content.Context ctx = itemView.getContext();

				switch (message.role) {
					case ChatMessage.ROLE_USER:
						params.gravity = Gravity.END;
						messageCard.setCardBackgroundColor(preset.getUserMessageBgColor(ctx, nightMode));
						contentText.setTextColor(preset.getUserMessageTextColor(ctx, nightMode));
						roleText.setVisibility(View.GONE);
						break;

					case ChatMessage.ROLE_AI:
						params.gravity = Gravity.START;
						messageCard.setCardBackgroundColor(preset.getAiMessageBgColor(ctx, nightMode));
						contentText.setTextColor(preset.getAiMessageTextColor(ctx, nightMode));
						roleText.setVisibility(View.GONE);
						break;

					case ChatMessage.ROLE_SYSTEM:
						params.gravity = Gravity.CENTER;
						messageCard.setCardBackgroundColor(preset.getSystemMessageBgColor(ctx, nightMode));
						contentText.setTextColor(getResources().getColor(R.color.text_color_primary_dark));
						roleText.setVisibility(View.GONE);
						break;
				}

				messageCard.setLayoutParams(params);
			}
		}
	}
}
