package net.osmand.plus.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import io.noties.markwon.Markwon;

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
	private com.google.android.material.card.MaterialCardView modelStatusCard; // Phase 17
	private LinearLayout modelStatusBar;
	private ImageView modelStatusIcon;
	private ProgressBar modelLoadingProgress; // Phase 17: loading spinner
	private TextView modelStatusText;
	private TextView modelStatusDetail; // Phase 17: secondary detail line
	private Button modelActionButton;
	private RecyclerView chatMessagesRecycler;
	private View emptyState;
	private ChipGroup suggestionChipGroup; // Phase 17: suggestion chips in empty state
	private View generatingIndicator;
	private TextView generatingStatusText;
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
	@Nullable
	private Markwon markwon;
	@Nullable
	private TokenBatcher tokenBatcher; // Phase 17: batched streaming renderer

	// Active conversation (Phase 14)
	private long activeConversationId = EncryptedChatStorage.DEFAULT_CONVERSATION_ID;

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

		// Initialize Markwon with shared theme-aware instance (Phase 17)
		markwon = LamppThemeUtils.getMarkwon(view.getContext(), app);

		// Phase 17: Initialize token batcher for smooth streaming
		tokenBatcher = new TokenBatcher(markwon, null);
		tokenBatcher.setCallback(() -> {
			// Scroll to bottom after each batch render
			if (chatMessagesRecycler != null && !messages.isEmpty()) {
				chatMessagesRecycler.scrollToPosition(messages.size() - 1);
			}
		});

		// Initialize encrypted chat storage and load persisted messages
		try {
			SecurityManager secMgr = app.getSecurityManager();
			chatStorage = secMgr.getChatStorage();
			// Restore last active conversation (default=1)
			activeConversationId = app.getSettings().LAMPP_ACTIVE_CONVERSATION_ID.get();
			loadConversationMessages(activeConversationId);
		} catch (Exception e) {
			LOG.error("Failed to initialize chat storage: " + e.getMessage());
			app.showShortToastMessage(R.string.chat_storage_unavailable);
		}

		// Setup UI
		toolbar = view.findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setTitle("AI Assistant");
			toolbar.inflateMenu(R.menu.menu_llm_chat);
			toolbar.setOnMenuItemClickListener(item -> {
				int id = item.getItemId();
				if (id == R.id.action_models) {
					showModelManagement();
					return true;
				} else if (id == R.id.action_conversations) {
					showConversationPicker();
					return true;
				} else if (id == R.id.action_system_prompt) {
					showSystemPromptEditor();
					return true;
				} else if (id == R.id.action_clear_chat) {
					clearActiveConversation();
					return true;
				}
				return false;
			});
		}
		initViews(view);
		setupListeners();
		updateModelStatus();
		updateUI();

		// Phase 19: Auto-load model if available but not loaded (reduces demo friction)
		if (!llmManager.isModelLoaded() && !llmManager.isLoading() && llmManager.hasDownloadedModels()) {
			llmManager.preWarmIfNeeded(new LlmManager.ModelLoadCallback() {
				@Override
				public void onLoadStarted() {
					if (isAdded()) updateModelStatus();
				}

				@Override
				public void onLoadComplete(String name) {
					if (isAdded()) updateModelStatus();
				}

				@Override
				public void onLoadError(String error) {
					if (isAdded()) updateModelStatus();
				}
			});
			updateModelStatus(); // Show loading state immediately
		}

		// Phase 17: Check for pending query from "Ask AI" button
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			String pendingQuery = mapActivity.getLamppPanelManager().consumePendingQuery();
			if (pendingQuery != null && !pendingQuery.isEmpty()) {
				// Delay slightly to let the panel animate in
				view.postDelayed(() -> prefillQuery(pendingQuery), 300);
			}
		}
	}

	private void initViews(View view) {
		modelStatusCard = view.findViewById(R.id.model_status_card);
		modelStatusBar = view.findViewById(R.id.model_status_bar);
		modelStatusIcon = view.findViewById(R.id.model_status_icon);
		modelLoadingProgress = view.findViewById(R.id.model_loading_progress);
		modelStatusText = view.findViewById(R.id.model_status_text);
		modelStatusDetail = view.findViewById(R.id.model_status_detail);
		modelActionButton = view.findViewById(R.id.model_action_button);
		chatMessagesRecycler = view.findViewById(R.id.chat_messages);
		emptyState = view.findViewById(R.id.empty_state);
		suggestionChipGroup = view.findViewById(R.id.suggestion_chip_group);
		generatingIndicator = view.findViewById(R.id.generating_indicator);
		generatingStatusText = view.findViewById(R.id.generating_status_text);
		messageInput = view.findViewById(R.id.message_input);
		sendButton = view.findViewById(R.id.send_button);

		// Setup RecyclerView
		chatAdapter = new ChatAdapter();
		chatMessagesRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
		chatMessagesRecycler.setAdapter(chatAdapter);

		// Phase 17: Populate suggestion chips
		buildSuggestionChips();
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

		// Make both the button AND the entire status card clickable
		modelActionButton.setOnClickListener(modelClickListener);
		if (modelStatusCard != null) {
			modelStatusCard.setOnClickListener(modelClickListener);
		}
		modelStatusBar.setOnClickListener(modelClickListener);

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

	/**
	 * Phase 17: Redesigned model status bar with distinct visual states.
	 * - Red card: No model installed → download CTA
	 * - Amber card: Model available → tap to load
	 * - Loading: Progress ring animation
	 * - Green card: Model loaded → compact status with data sources
	 */
	private void updateModelStatus() {
		LamppStylePreset preset = LamppThemeUtils.getActivePreset(app);
		Context ctx = modelStatusText.getContext();

		if (llmManager.isLoading()) {
			// Loading state: progress ring + amber card
			modelStatusIcon.setVisibility(View.GONE);
			modelLoadingProgress.setVisibility(View.VISIBLE);
			modelStatusText.setText("Loading model\u2026");
			modelStatusText.setTextColor(preset.getStatusAvailableTextColor(ctx, nightMode));
			modelStatusDetail.setVisibility(View.GONE);
			modelActionButton.setVisibility(View.GONE);
			if (modelStatusCard != null) {
				modelStatusCard.setCardBackgroundColor(preset.getStatusAvailableBgColor(ctx, nightMode));
			}
		} else if (llmManager.isModelLoaded()) {
			// Loaded state: green card with compact model + data source info
			modelStatusIcon.setVisibility(View.VISIBLE);
			modelLoadingProgress.setVisibility(View.GONE);
			modelStatusIcon.setImageResource(R.drawable.ic_action_done);
			modelStatusIcon.setColorFilter(preset.getStatusLoadedTextColor(ctx, nightMode));
			modelStatusText.setText(llmManager.getCurrentModelName());
			modelStatusText.setTextColor(preset.getStatusLoadedTextColor(ctx, nightMode));

			// Build data source detail line
			StringBuilder detail = new StringBuilder();
			if (ragManager.isWikipediaAvailable()) {
				detail.append("Wiki: ").append(ragManager.getWikipediaTitle());
			} else {
				detail.append(getString(R.string.lampp_model_status_wiki_not_loaded));
			}
			if (ragManager.isMapDataAvailable()) {
				if (detail.length() > 0) detail.append("  \u2022  ");
				detail.append("POI: On");
			}
			int guideCount = app.getGuideManager().getGuideCount();
			if (guideCount > 0) {
				if (detail.length() > 0) detail.append("  \u2022  ");
				detail.append("Guides: ").append(guideCount);
			}
			modelStatusDetail.setText(detail.toString());
			modelStatusDetail.setTextColor(preset.getStatusDetailTextColor(ctx, nightMode));
			modelStatusDetail.setVisibility(View.VISIBLE);

			modelActionButton.setText("Unload");
			modelActionButton.setVisibility(View.VISIBLE);
			if (modelStatusCard != null) {
				modelStatusCard.setCardBackgroundColor(preset.getStatusLoadedBgColor(ctx, nightMode));
			}
		} else if (llmManager.hasDownloadedModels()) {
			// Available state: amber card — tap to load
			modelStatusIcon.setVisibility(View.VISIBLE);
			modelLoadingProgress.setVisibility(View.GONE);
			modelStatusIcon.setImageResource(R.drawable.ic_action_info_outlined);
			modelStatusIcon.setColorFilter(preset.getStatusAvailableTextColor(ctx, nightMode));
			modelStatusText.setText("Model ready");
			modelStatusText.setTextColor(preset.getStatusAvailableTextColor(ctx, nightMode));
			modelStatusDetail.setText("Tap to load into memory");
			modelStatusDetail.setTextColor(preset.getStatusDetailTextColor(ctx, nightMode));
			modelStatusDetail.setVisibility(View.VISIBLE);
			modelActionButton.setText("Load");
			modelActionButton.setVisibility(View.VISIBLE);
			if (modelStatusCard != null) {
				modelStatusCard.setCardBackgroundColor(preset.getStatusAvailableBgColor(ctx, nightMode));
			}
		} else {
			// No model state: red card — download CTA
			modelStatusIcon.setVisibility(View.VISIBLE);
			modelLoadingProgress.setVisibility(View.GONE);
			modelStatusIcon.setImageResource(R.drawable.ic_action_alert);
			modelStatusIcon.setColorFilter(preset.getStatusNoModelTextColor(ctx, nightMode));
			modelStatusText.setText("No AI model");
			modelStatusText.setTextColor(preset.getStatusNoModelTextColor(ctx, nightMode));
			modelStatusDetail.setText("Download a model to enable AI");
			modelStatusDetail.setTextColor(preset.getStatusDetailTextColor(ctx, nightMode));
			modelStatusDetail.setVisibility(View.VISIBLE);
			modelActionButton.setText(R.string.shared_string_download);
			modelActionButton.setVisibility(View.VISIBLE);
			if (modelStatusCard != null) {
				modelStatusCard.setCardBackgroundColor(preset.getStatusNoModelBgColor(ctx, nightMode));
			}
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

	/**
	 * Phase 17: Build suggestion chips in the empty state from SuggestionChipProvider.
	 */
	private void buildSuggestionChips() {
		if (suggestionChipGroup == null || ragManager == null) return;
		suggestionChipGroup.removeAllViews();

		List<SuggestionChipProvider.Suggestion> suggestions =
				SuggestionChipProvider.getSuggestions(app, ragManager);

		LamppStylePreset preset = LamppThemeUtils.getActivePreset(app);
		Context ctx = suggestionChipGroup.getContext();
		int chipTextColor = preset.getTextPrimaryColor(ctx, nightMode);
		int chipIconColor = preset.getPrimaryColor(ctx, nightMode);

		for (SuggestionChipProvider.Suggestion suggestion : suggestions) {
			Chip chip = new Chip(ctx);
			chip.setText(suggestion.text);
			chip.setChipIconResource(suggestion.iconRes);
			chip.setChipIconVisible(true);
			chip.setTextSize(13);
			chip.setChipIconTint(android.content.res.ColorStateList.valueOf(chipIconColor));
			chip.setTextColor(chipTextColor);
			chip.setChipBackgroundColorResource(android.R.color.transparent);
			chip.setChipStrokeColorResource(R.color.lampp_text_secondary);
			chip.setChipStrokeWidth(1f);
			chip.setClickable(true);

			chip.setOnClickListener(v -> {
				// Prefill the message input and send
				messageInput.setText(suggestion.text);
				if (llmManager.isModelLoaded()) {
					sendMessage();
				}
			});

			suggestionChipGroup.addView(chip);
		}
	}

	/**
	 * Phase 16: Update the generating indicator text with a RAG progress state.
	 */
	private void setGeneratingStatus(int stringResId) {
		if (generatingStatusText != null) {
			generatingStatusText.setText(stringResId);
		}
		if (generatingIndicator != null) {
			generatingIndicator.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * Phase 16: Update the generating indicator text with a pre-formatted string.
	 */
	private void setGeneratingStatus(@NonNull String text) {
		if (generatingStatusText != null) {
			generatingStatusText.setText(text);
		}
		if (generatingIndicator != null) {
			generatingIndicator.setVisibility(View.VISIBLE);
		}
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
		if (messageInput == null) return;
		String text = messageInput.getText().toString().trim();
		if (text.isEmpty() || llmManager == null || !llmManager.isModelLoaded()) {
			return;
		}

		// Add user message
		ChatMessage userMessage = new ChatMessage(ChatMessage.ROLE_USER, text);
		messages.add(userMessage);
		persistMessage(userMessage);
		if (chatAdapter != null) chatAdapter.notifyItemInserted(messages.size() - 1);
		if (chatMessagesRecycler != null) chatMessagesRecycler.scrollToPosition(messages.size() - 1);

		// Clear input
		messageInput.setText("");

		// Update UI
		updateUI();
		updateSendButton();

		// Generate response using RAG pipeline
		// Phase 16: Show searching state immediately
		setGeneratingStatus(R.string.rag_status_searching);

		// Phase 17: Reset token batcher for new generation
		if (tokenBatcher != null) {
			tokenBatcher.reset();
		}

		ragManager.queryAsync(text, new RagCallback() {
			@Override
			public void onSearchStarted(@NonNull List<String> searchTerms) {
				LOG.debug("RAG: Searching for " + searchTerms);
				if (getActivity() != null) {
					getActivity().runOnUiThread(() -> setGeneratingStatus(R.string.rag_status_searching));
				}
			}

			@Override
			public void onSearchComplete(@NonNull List<ArticleSource> sources, long timeMs) {
				LOG.debug("RAG: Found " + sources.size() + " sources in " + timeMs + "ms");
				if (getActivity() != null) {
					getActivity().runOnUiThread(() ->
						setGeneratingStatus(getString(R.string.rag_status_sources_found, sources.size())));
				}
			}

			@Override
			public void onGenerationStarted(boolean usesWikipedia) {
				LOG.debug("RAG: Generation started, usesWikipedia=" + usesWikipedia);
				if (getActivity() != null) {
					getActivity().runOnUiThread(() -> setGeneratingStatus(R.string.rag_status_generating));
				}
			}

			@Override
			public void onPartialResult(@NonNull String partialText) {
				if (!isAdded()) return;
				// Update the data model
				int lastIndex = messages.size() - 1;
				if (lastIndex >= 0 && messages.get(lastIndex).role == ChatMessage.ROLE_AI) {
					messages.get(lastIndex).content = partialText;
				} else {
					// Add new AI message placeholder
					ChatMessage aiMessage = new ChatMessage(ChatMessage.ROLE_AI, partialText);
					messages.add(aiMessage);
					if (chatAdapter != null) chatAdapter.notifyItemInserted(messages.size() - 1);
				}

				// Phase 17: Use TokenBatcher for efficient rendering
				// The batcher renders every 5 tokens or 100ms instead of every token
				if (tokenBatcher != null) {
					tokenBatcher.onToken(partialText);
				} else if (chatAdapter != null) {
					// Fallback: direct Markwon render (pre-Phase 17 behavior)
					chatAdapter.notifyItemChanged(messages.size() - 1);
				}
				if (chatMessagesRecycler != null) {
					chatMessagesRecycler.scrollToPosition(messages.size() - 1);
				}

				// Update cursor blinker base text for Pip-Boy effect
				if (cursorBlinker != null && cursorBlinker.isRunning()) {
					cursorBlinker.updateBaseText(partialText);
				}
			}

			@Override
			public void onComplete(@NonNull RagResponse response) {
				// Phase 17: Flush batcher for final rich render
				if (tokenBatcher != null) {
					tokenBatcher.flush();
					tokenBatcher.stop();
				}

				// Stop cursor blinker
				if (cursorBlinker != null) {
					cursorBlinker.stop();
				}

				if (!isAdded()) return;

				// Update the final message with sources if available
				int lastIndex = messages.size() - 1;
				if (lastIndex >= 0 && messages.get(lastIndex).role == ChatMessage.ROLE_AI) {
					ChatMessage aiMessage = messages.get(lastIndex);
					if (response.hasSources()) {
						aiMessage.sources = response.getSources();
					}
					if (response.hasPoiSources()) {
						aiMessage.poiSources = response.getPoiSources();
					}
					// Always do a final full render to ensure Markwon formatting
					if (chatAdapter != null) chatAdapter.notifyItemChanged(lastIndex);
					// Persist completed AI response
					persistMessage(aiMessage);
				}

				LOG.info("RAG complete: " + response.getPerformanceSummary());
				updateUI();
				updateSendButton();
			}

			@Override
			public void onError(@NonNull String error) {
				// Phase 17: Stop batcher on error
				if (tokenBatcher != null) {
					tokenBatcher.stop();
				}

				if (!isAdded()) return;

				// Add error message
				ChatMessage errorMessage = new ChatMessage(ChatMessage.ROLE_SYSTEM, "Error: " + error);
				messages.add(errorMessage);
				if (chatAdapter != null) chatAdapter.notifyItemInserted(messages.size() - 1);
				if (chatMessagesRecycler != null) chatMessagesRecycler.scrollToPosition(messages.size() - 1);

				updateUI();
				updateSendButton();
			}
		});

		// Show generating indicator
		updateUI();
	}

	private void showModelManagement() {
		// Use activity's fragment manager for full-screen model management dialog
		if (getActivity() != null) {
			LlmModelsFragment.showInstance(getActivity().getSupportFragmentManager());
		}
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

		// Phase 17: Stop token batcher
		if (tokenBatcher != null) {
			tokenBatcher.stop();
			tokenBatcher = null;
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
				chatStorage.saveMessage(activeConversationId, message);
			} catch (Exception e) {
				LOG.error("Failed to persist message: " + e.getMessage());
			}
		}
	}

	/**
	 * Load messages for a conversation and refresh the UI.
	 */
	private void loadConversationMessages(long conversationId) {
		messages.clear();
		if (chatStorage != null) {
			List<ChatMessage> saved = chatStorage.getMessagesForConversation(conversationId);
			if (!saved.isEmpty()) {
				messages.addAll(saved);
			}
		}
		if (chatAdapter != null) {
			chatAdapter.notifyDataSetChanged();
			if (!messages.isEmpty()) {
				chatMessagesRecycler.scrollToPosition(messages.size() - 1);
			}
		}
	}

	/**
	 * Switch to a different conversation.
	 */
	private void switchConversation(long conversationId) {
		activeConversationId = conversationId;
		// Persist the active conversation preference
		if (app != null) {
			app.getSettings().LAMPP_ACTIVE_CONVERSATION_ID.set(conversationId);
		}
		loadConversationMessages(conversationId);

		// Apply conversation's system prompt to RAG pipeline if available
		if (chatStorage != null && ragManager != null) {
			String systemPrompt = chatStorage.getConversationSystemPrompt(conversationId);
			if (systemPrompt != null && !systemPrompt.isEmpty()) {
				ragManager.setCustomSystemPrompt(systemPrompt);
			} else {
				ragManager.setCustomSystemPrompt(null);
			}
		}

		updateUI();
	}

	/**
	 * Show the conversation picker dialog.
	 */
	private void showConversationPicker() {
		ConversationPickerDialog.showInstance(
				getParentFragmentManager(),
				activeConversationId,
				new ConversationPickerDialog.OnConversationSelectedListener() {
					@Override
					public void onConversationSelected(long conversationId) {
						switchConversation(conversationId);
					}

					@Override
					public void onNewConversation(long conversationId) {
						switchConversation(conversationId);
					}

					@Override
					public void onConversationDeleted(long conversationId) {
						if (conversationId == activeConversationId) {
							switchConversation(EncryptedChatStorage.DEFAULT_CONVERSATION_ID);
						}
					}
				}
		);
	}

	/**
	 * Show the system prompt editor for the active conversation.
	 */
	private void showSystemPromptEditor() {
		SystemPromptEditorDialog.showInstance(
				getParentFragmentManager(),
				activeConversationId,
				prompt -> {
					// Apply the new system prompt to the RAG pipeline
					if (ragManager != null) {
						ragManager.setCustomSystemPrompt(prompt);
					}
				}
		);
	}

	/**
	 * Clear messages in the active conversation.
	 */
	private void clearActiveConversation() {
		messages.clear();
		if (chatAdapter != null) {
			chatAdapter.notifyDataSetChanged();
		}
		if (chatStorage != null) {
			chatStorage.clearMessagesForConversation(activeConversationId);
		}
		updateUI();
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

	/**
	 * Phase 17: Prefill the message input with a query from external source
	 * (e.g., "Ask AI" button from Guides or Wikipedia).
	 * If a model is loaded, sends the message immediately.
	 */
	public void prefillQuery(@NonNull String query) {
		if (messageInput != null) {
			messageInput.setText(query);
			messageInput.setSelection(query.length());
			if (llmManager != null && llmManager.isModelLoaded()) {
				sendMessage();
			}
		}
	}

	// buildMarkwon() removed in Phase 17 — uses LamppThemeUtils.getMarkwon() shared instance

	/**
	 * Rebuild Markwon when theme changes (Phase 17: uses shared instance).
	 */
	private void rebuildMarkwon() {
		View view = getView();
		if (view != null) {
			LamppThemeUtils.invalidateMarkwon();
			markwon = LamppThemeUtils.getMarkwon(view.getContext(), app);
			if (chatAdapter != null) {
				chatAdapter.notifyDataSetChanged();
			}
		}
	}

	/**
	 * Copy text to clipboard and show toast.
	 */
	private void copyToClipboard(@NonNull String text) {
		Context context = getContext();
		if (context != null) {
			ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
			if (clipboard != null) {
				clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", text));
				Toast.makeText(context, R.string.rushlight_copied_to_clipboard, Toast.LENGTH_SHORT).show();
			}
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
		public int getItemViewType(int position) {
			return messages.get(position).role;
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
			ImageButton copyButton;

			MessageViewHolder(@NonNull View itemView) {
				super(itemView);
				messageCard = itemView.findViewById(R.id.message_card);
				roleText = itemView.findViewById(R.id.message_role);
				contentText = itemView.findViewById(R.id.message_content);
				sourcesText = itemView.findViewById(R.id.message_sources);
				copyButton = itemView.findViewById(R.id.copy_button);
			}

			void bind(ChatMessage message) {
				boolean isAiMessage = message.role == ChatMessage.ROLE_AI;
				boolean isStreaming = llmManager != null && llmManager.isGenerating();
				int position = getAdapterPosition();
				boolean isLastMessage = position == messages.size() - 1;
				boolean isStreamingThisMessage = isAiMessage && isLastMessage && isStreaming;

				// Phase 17: Wire the TokenBatcher to the streaming message's TextView
				if (isStreamingThisMessage && tokenBatcher != null) {
					tokenBatcher.setTextView(contentText);
					tokenBatcher.setMarkwon(markwon);
					// Don't do a full Markwon render here — the batcher handles it
					contentText.setText(message.content);
				} else if (isAiMessage && markwon != null) {
					// Non-streaming AI messages: full Markwon render
					markwon.setMarkdown(contentText, message.content);
				} else {
					contentText.setText(message.content);
				}

				// Pip-Boy terminal cursor blink effect on last AI message during generation
				if (isStreamingThisMessage && isPipBoyCursorEnabled()) {
					cursorBlinker.attachTo(contentText, message.content);
				}

				// Show sources if available (for AI messages with Wikipedia/POI context)
				if (isAiMessage && message.hasAnySources()) {
					if (sourcesText != null) {
						sourcesText.setVisibility(View.VISIBLE);
						sourcesText.setText(message.getSourcesText().trim());
					}
				} else {
					if (sourcesText != null) {
						sourcesText.setVisibility(View.GONE);
					}
				}

				// Copy button: visible only for completed AI messages
				if (copyButton != null) {
					if (isAiMessage && !isStreamingThisMessage) {
						copyButton.setVisibility(View.VISIBLE);
						copyButton.setOnClickListener(v -> copyToClipboard(message.content));
					} else {
						copyButton.setVisibility(View.GONE);
					}
				}

				// Long-press to copy for all messages
				itemView.setOnLongClickListener(v -> {
					copyToClipboard(message.content);
					return true;
				});

				// Style based on role — use preset-aware colors
				LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) messageCard.getLayoutParams();
				LamppStylePreset preset = LamppThemeUtils.getActivePreset(app);
				Context ctx = itemView.getContext();

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

				// Tint copy button to match theme
				if (copyButton != null && isAiMessage) {
					copyButton.setColorFilter(preset.getTextSecondaryColor(ctx, nightMode));
				}
			}
		}
	}
}
