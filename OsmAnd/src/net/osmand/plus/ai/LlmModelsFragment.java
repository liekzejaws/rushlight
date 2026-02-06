package net.osmand.plus.ai;

import android.app.AlertDialog;
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
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.wikivoyage.WikiBaseDialogFragment;

import org.apache.commons.logging.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LAMPP: Fragment for managing LLM models - download, delete, view info.
 *
 * Supports GGUF format models from HuggingFace:
 * - TinyLlama (smallest, for testing)
 * - Phi-3-mini (good balance)
 * - Qwen2.5-3B (multilingual)
 * - Deepseek-R1-Distill (reasoning)
 */
public class LlmModelsFragment extends WikiBaseDialogFragment {

	public static final String TAG = "LlmModelsFragment";
	private static final Log LOG = PlatformUtil.getLog(LlmModelsFragment.class);

	// Model definitions - using Q4_K_M quantization for good quality/size balance
	// TinyLlama - smallest model for testing/low-end devices
	private static final String TINYLLAMA_URL = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf";
	private static final String TINYLLAMA_FILENAME = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf";
	private static final long TINYLLAMA_SIZE = 669_000_000L; // ~669 MB

	// Phi-3-mini - good balance of quality and size
	private static final String PHI3_URL = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf";
	private static final String PHI3_FILENAME = "Phi-3-mini-4k-instruct-q4.gguf";
	private static final long PHI3_SIZE = 2_400_000_000L; // ~2.4 GB

	// Currently selected model for download
	private String currentDownloadUrl;
	private String currentDownloadFilename;
	private long currentDownloadSize;

	// UI Components
	private RecyclerView downloadedModelsList;
	private TextView noDownloadedModels;
	private Button tinyLlamaButton;
	private ProgressBar tinyLlamaProgress;
	private TextView tinyLlamaProgressText;
	private Button phi3Button;
	private ProgressBar phi3Progress;
	private TextView phi3ProgressText;
	private TextView storageInfo;

	// State
	private LlmManager llmManager;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private boolean isDownloading = false;
	private String downloadingModel = null;

	public static void showInstance(@NonNull FragmentManager fragmentManager) {
		if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
			LlmModelsFragment fragment = new LlmModelsFragment();
			fragment.show(fragmentManager, TAG);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		updateNightMode();
		View view = inflater.inflate(R.layout.fragment_llm_models, container, false);

		llmManager = new LlmManager(app);

		setupToolbar(view.findViewById(R.id.toolbar));
		initViews(view);
		updateUI();

		return view;
	}

	@Override
	protected void setupToolbar(Toolbar toolbar) {
		super.setupToolbar(toolbar);
		toolbar.setTitle("AI Models");
	}

	private void initViews(View view) {
		downloadedModelsList = view.findViewById(R.id.downloaded_models_list);
		noDownloadedModels = view.findViewById(R.id.no_downloaded_models);

		// TinyLlama card (reusing gemma_2b IDs for now)
		tinyLlamaButton = view.findViewById(R.id.gemma_2b_button);
		tinyLlamaProgress = view.findViewById(R.id.gemma_2b_progress);
		tinyLlamaProgressText = view.findViewById(R.id.gemma_2b_progress_text);

		storageInfo = view.findViewById(R.id.storage_info);

		downloadedModelsList.setLayoutManager(new LinearLayoutManager(getContext()));

		// TinyLlama download button
		tinyLlamaButton.setOnClickListener(v -> {
			File modelFile = new File(llmManager.getModelsDirectory(), TINYLLAMA_FILENAME);
			if (modelFile.exists()) {
				showDeleteDialog(modelFile);
			} else if (!isDownloading) {
				downloadModel(TINYLLAMA_URL, TINYLLAMA_FILENAME, TINYLLAMA_SIZE,
					tinyLlamaProgress, tinyLlamaProgressText);
			}
		});
	}

	private void updateUI() {
		// Update downloaded models list
		File[] downloadedModels = llmManager.getDownloadedModels();
		if (downloadedModels.length == 0) {
			downloadedModelsList.setVisibility(View.GONE);
			noDownloadedModels.setVisibility(View.VISIBLE);
		} else {
			downloadedModelsList.setVisibility(View.VISIBLE);
			noDownloadedModels.setVisibility(View.GONE);
			downloadedModelsList.setAdapter(new DownloadedModelsAdapter(downloadedModels));
		}

		// Update TinyLlama button
		File tinyLlamaFile = new File(llmManager.getModelsDirectory(), TINYLLAMA_FILENAME);
		if (tinyLlamaFile.exists()) {
			tinyLlamaButton.setText(R.string.shared_string_delete);
		} else if (isDownloading && TINYLLAMA_FILENAME.equals(downloadingModel)) {
			tinyLlamaButton.setText(R.string.shared_string_cancel);
		} else {
			tinyLlamaButton.setText(R.string.shared_string_download);
		}

		// Update storage info
		long totalSize = llmManager.getTotalModelsSize();
		storageInfo.setText("Storage used: " + formatSize(totalSize));
	}

	private void downloadModel(String url, String filename, long estimatedSize,
							   ProgressBar progressBar, TextView progressText) {
		isDownloading = true;
		downloadingModel = filename;
		progressBar.setVisibility(View.VISIBLE);
		progressBar.setIndeterminate(false);
		progressBar.setMax(100);
		progressText.setVisibility(View.VISIBLE);
		updateUI();

		executor.execute(() -> {
			File outputFile = new File(llmManager.getModelsDirectory(), filename);
			File tempFile = new File(outputFile.getParentFile(), filename + ".tmp");

			try {
				URL downloadUrl = new URL(url);
				HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(30000);
				connection.setReadTimeout(60000);
				// Follow redirects (HuggingFace uses them)
				connection.setInstanceFollowRedirects(true);
				connection.connect();

				int responseCode = connection.getResponseCode();
				if (responseCode != HttpURLConnection.HTTP_OK) {
					throw new Exception("Server returned HTTP " + responseCode);
				}

				long contentLength = connection.getContentLengthLong();
				if (contentLength <= 0) {
					contentLength = estimatedSize;
				}

				InputStream inputStream = connection.getInputStream();
				FileOutputStream outputStream = new FileOutputStream(tempFile);

				byte[] buffer = new byte[8192];
				long downloaded = 0;
				int bytesRead;

				while ((bytesRead = inputStream.read(buffer)) != -1) {
					if (!isDownloading) {
						// Cancelled
						inputStream.close();
						outputStream.close();
						tempFile.delete();
						return;
					}

					outputStream.write(buffer, 0, bytesRead);
					downloaded += bytesRead;

					// Update progress
					final int progress = (int) (downloaded * 100 / contentLength);
					final long downloadedFinal = downloaded;
					final long totalFinal = contentLength;

					requireActivity().runOnUiThread(() -> {
						progressBar.setProgress(progress);
						progressText.setText(formatSize(downloadedFinal) + " / " + formatSize(totalFinal));
					});
				}

				inputStream.close();
				outputStream.close();

				// Rename temp file to final name
				if (tempFile.renameTo(outputFile)) {
					requireActivity().runOnUiThread(() -> {
						isDownloading = false;
						downloadingModel = null;
						progressBar.setVisibility(View.GONE);
						progressText.setVisibility(View.GONE);
						updateUI();
						app.showShortToastMessage("Model downloaded: " + filename);
					});
				} else {
					throw new Exception("Failed to save model file");
				}

			} catch (Exception e) {
				LOG.error("Failed to download model: " + filename, e);
				tempFile.delete();

				requireActivity().runOnUiThread(() -> {
					isDownloading = false;
					downloadingModel = null;
					progressBar.setVisibility(View.GONE);
					progressText.setVisibility(View.GONE);
					updateUI();
					app.showShortToastMessage("Download failed: " + e.getMessage());
				});
			}
		});
	}

	private void showDeleteDialog(File modelFile) {
		new AlertDialog.Builder(getContext())
			.setTitle("Delete Model")
			.setMessage("Delete " + modelFile.getName() + "? This will free " + formatSize(modelFile.length()) + " of storage.")
			.setPositiveButton(R.string.shared_string_delete, (dialog, which) -> {
				llmManager.deleteModel(modelFile);
				updateUI();
				app.showShortToastMessage("Model deleted");
			})
			.setNegativeButton(R.string.shared_string_cancel, null)
			.show();
	}

	private String formatSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return new DecimalFormat("#.#").format(bytes / 1024.0) + " KB";
		} else if (bytes < 1024 * 1024 * 1024) {
			return new DecimalFormat("#.#").format(bytes / (1024.0 * 1024.0)) + " MB";
		} else {
			return new DecimalFormat("#.##").format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		// Cancel any ongoing download
		isDownloading = false;
	}

	// Adapter for downloaded models
	class DownloadedModelsAdapter extends RecyclerView.Adapter<DownloadedModelsAdapter.ModelViewHolder> {
		private final File[] models;

		DownloadedModelsAdapter(File[] models) {
			this.models = models;
		}

		@NonNull
		@Override
		public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
				.inflate(android.R.layout.simple_list_item_2, parent, false);
			return new ModelViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
			File model = models[position];
			holder.bind(model);
		}

		@Override
		public int getItemCount() {
			return models.length;
		}

		class ModelViewHolder extends RecyclerView.ViewHolder {
			TextView text1;
			TextView text2;

			ModelViewHolder(@NonNull View itemView) {
				super(itemView);
				text1 = itemView.findViewById(android.R.id.text1);
				text2 = itemView.findViewById(android.R.id.text2);
			}

			void bind(File model) {
				String name = model.getName().replace(".gguf", "");
				text1.setText(name);
				text2.setText(formatSize(model.length()));

				itemView.setOnClickListener(v -> {
					showDeleteDialog(model);
				});
			}
		}
	}
}
