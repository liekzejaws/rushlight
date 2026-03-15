/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.wikipedia;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.lampp.LamppPanelFragment;

import org.apache.commons.logging.Log;

import android.os.Environment;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LAMPP: Fragment for browsing offline Wikipedia via ZIM files.
 *
 * Features:
 * - Local tab: Show downloaded files, open ZIM files, search articles
 * - Download tab: Browse and download ZIM files from Kiwix catalog with language filter
 */
public class ZimBrowserFragment extends LamppPanelFragment implements ZimDownloadManager.DownloadListener {

	public static final String TAG = "ZimBrowserFragment";
	private static final Log LOG = PlatformUtil.getLog(ZimBrowserFragment.class);

	private static final int REQUEST_OPEN_ZIM = 1001;
	private static final int SEARCH_DELAY_MS = 300;
	private static final int MAX_SEARCH_RESULTS = 50;

	private static final int TAB_LOCAL = 0;
	private static final int TAB_DOWNLOAD = 1;

	// UI Components - Common
	private TabLayout tabLayout;
	private View localContainer;
	private View downloadContainer;

	// UI Components - Local tab - Downloaded files section
	private TextView downloadedFilesHeader;
	private RecyclerView downloadedFilesList;
	private View downloadedFilesDivider;
	private LocalFilesAdapter localFilesAdapter;

	// UI Components - Local tab - Current ZIM section
	private CardView zimInfoCard;
	private TextView zimTitle;
	private TextView zimArticleCount;
	private View searchContainer;
	private EditText searchEditText;
	private ProgressBar searchProgress;
	private ImageButton clearButton;
	private TextView searchStatus;
	private RecyclerView searchResults;
	private View localEmptyState;
	private Button openZimButton;

	// UI Components - Download tab
	private View languageFilterContainer;
	private Spinner languageSpinner;
	private RecyclerView catalogList;
	private ProgressBar catalogProgress;
	private View downloadEmptyState;
	private Button refreshCatalogButton;

	// State
	private ZimFileManager zimManager;
	private ZimDownloadManager downloadManager;
	private KiwixCatalogParser catalogParser;
	private SearchResultsAdapter searchAdapter;
	private ZimCatalogAdapter catalogAdapter;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private Runnable pendingSearch;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private List<ZimCatalogItem> catalogItems = new ArrayList<>();
	private List<ZimCatalogItem> filteredCatalogItems = new ArrayList<>();
	private List<String> availableLanguages = new ArrayList<>();
	private String selectedLanguage = null; // null = all languages
	private int currentTab = TAB_LOCAL;

	@Override
	protected int getPanelLayoutId() {
		return R.layout.fragment_zim_browser_tabbed;
	}

	@NonNull
	@Override
	public String getPanelTag() {
		return TAG;
	}

	@Override
	protected void onPanelViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		// Setup toolbar if present
		Toolbar toolbar = view.findViewById(R.id.toolbar);
		if (toolbar != null) {
			toolbar.setTitle(R.string.shared_string_wikipedia);
		}

		// Initialize managers
		WikipediaPlugin plugin = PluginsHelper.getPlugin(WikipediaPlugin.class);
		if (plugin != null) {
			zimManager = plugin.getZimFileManager();
		}
		downloadManager = new ZimDownloadManager(app);
		downloadManager.addListener(this);
		catalogParser = new KiwixCatalogParser(app);

		// Initialize UI components
		initTabs(view);
		initLocalTab(view);
		initDownloadTab(view);

		// Show local tab by default
		showTab(TAB_LOCAL);

		// v0.5: Auto-detect ZIM files if none currently open
		if (zimManager == null || !zimManager.isOpen()) {
			autoDetectAndOpenFirst();
		}
	}

	private void initTabs(View view) {
		tabLayout = view.findViewById(R.id.tab_layout);
		localContainer = view.findViewById(R.id.local_container);
		downloadContainer = view.findViewById(R.id.download_container);

		tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				showTab(tab.getPosition());
			}

			@Override
			public void onTabUnselected(TabLayout.Tab tab) {}

			@Override
			public void onTabReselected(TabLayout.Tab tab) {}
		});
	}

	private void initLocalTab(View view) {
		// Downloaded files section
		downloadedFilesHeader = view.findViewById(R.id.downloaded_files_header);
		downloadedFilesList = view.findViewById(R.id.downloaded_files_list);
		downloadedFilesDivider = view.findViewById(R.id.downloaded_files_divider);

		// Setup downloaded files adapter
		localFilesAdapter = new LocalFilesAdapter();
		downloadedFilesList.setLayoutManager(new LinearLayoutManager(getContext()));
		downloadedFilesList.setAdapter(localFilesAdapter);

		// Current ZIM section
		zimInfoCard = view.findViewById(R.id.zim_info_card);
		zimTitle = view.findViewById(R.id.zim_title);
		zimArticleCount = view.findViewById(R.id.zim_article_count);
		searchContainer = view.findViewById(R.id.search_container);
		searchEditText = view.findViewById(R.id.search_edit_text);
		searchProgress = view.findViewById(R.id.search_progress);
		clearButton = view.findViewById(R.id.clear_button);
		searchStatus = view.findViewById(R.id.search_status);
		searchResults = view.findViewById(R.id.search_results);
		localEmptyState = view.findViewById(R.id.local_empty_state);
		openZimButton = view.findViewById(R.id.open_zim_button);

		// Setup search adapter
		searchAdapter = new SearchResultsAdapter();
		searchResults.setLayoutManager(new LinearLayoutManager(getContext()));
		searchResults.setAdapter(searchAdapter);

		// Setup search
		searchEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

			@Override
			public void afterTextChanged(Editable s) {
				String query = s.toString().trim();
				clearButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

				if (pendingSearch != null) {
					mainHandler.removeCallbacks(pendingSearch);
				}

				if (query.length() >= 2) {
					pendingSearch = () -> performSearch(query);
					mainHandler.postDelayed(pendingSearch, SEARCH_DELAY_MS);
				} else {
					searchAdapter.setResults(new ArrayList<>());
					updateSearchStatus(0, false);
				}
			}
		});

		clearButton.setOnClickListener(v -> {
			searchEditText.setText("");
			searchAdapter.setResults(new ArrayList<>());
			updateSearchStatus(0, false);
		});

		openZimButton.setOnClickListener(v -> smartZimAcquire());
	}

	private void initDownloadTab(View view) {
		languageFilterContainer = view.findViewById(R.id.language_filter_container);
		languageSpinner = view.findViewById(R.id.language_spinner);
		catalogList = view.findViewById(R.id.catalog_list);
		catalogProgress = view.findViewById(R.id.catalog_progress);
		downloadEmptyState = view.findViewById(R.id.download_empty_state);
		refreshCatalogButton = view.findViewById(R.id.refresh_catalog_button);

		// Setup catalog adapter
		catalogAdapter = new ZimCatalogAdapter(app, downloadManager);
		catalogList.setLayoutManager(new LinearLayoutManager(getContext()));
		catalogList.setAdapter(catalogAdapter);

		catalogAdapter.setOnItemClickListener(new ZimCatalogAdapter.OnItemClickListener() {
			@Override
			public void onDownloadClick(ZimCatalogItem item) {
				downloadManager.startDownload(item);
				catalogAdapter.notifyDataSetChanged();
			}

			@Override
			public void onCancelClick(ZimCatalogItem item) {
				downloadManager.cancelDownload(item);
				catalogAdapter.notifyDataSetChanged();
			}

			@Override
			public void onOpenClick(ZimCatalogItem item) {
				openDownloadedZim(item);
			}
		});

		// Setup language spinner
		languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (position == 0) {
					selectedLanguage = null; // All languages
				} else {
					selectedLanguage = availableLanguages.get(position - 1);
				}
				applyLanguageFilter();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		refreshCatalogButton.setOnClickListener(v -> loadCatalog(true));
	}

	private void showTab(int tab) {
		currentTab = tab;

		if (tab == TAB_LOCAL) {
			localContainer.setVisibility(View.VISIBLE);
			downloadContainer.setVisibility(View.GONE);
			loadDownloadedFiles();
			updateLocalUI();
		} else {
			localContainer.setVisibility(View.GONE);
			downloadContainer.setVisibility(View.VISIBLE);
			if (catalogItems.isEmpty()) {
				loadCatalog(false);
			}
		}
	}

	private void loadDownloadedFiles() {
		List<File> files = downloadManager.getDownloadedFiles();
		localFilesAdapter.setFiles(files);

		boolean hasFiles = !files.isEmpty();
		downloadedFilesHeader.setVisibility(hasFiles ? View.VISIBLE : View.GONE);
		downloadedFilesList.setVisibility(hasFiles ? View.VISIBLE : View.GONE);

		// Show divider if we have both downloaded files AND an open ZIM
		boolean hasZim = zimManager != null && zimManager.isOpen();
		downloadedFilesDivider.setVisibility(hasFiles && hasZim ? View.VISIBLE : View.GONE);
	}

	private void updateLocalUI() {
		boolean hasZim = zimManager != null && zimManager.isOpen();
		boolean hasDownloadedFiles = localFilesAdapter.getItemCount() > 0;

		if (hasZim) {
			zimInfoCard.setVisibility(View.VISIBLE);
			zimTitle.setText(zimManager.getZimTitle());

			// Show file size instead of article count (since count is unreliable)
			String sizeInfo = formatFileSize(zimManager.getFileSize());
			zimArticleCount.setText(getString(R.string.ltr_or_rtl_combine_via_colon,
					getString(R.string.shared_string_size), sizeInfo));

			searchContainer.setVisibility(View.VISIBLE);
			searchResults.setVisibility(View.VISIBLE);
			localEmptyState.setVisibility(View.GONE);
		} else {
			zimInfoCard.setVisibility(View.GONE);
			searchContainer.setVisibility(View.GONE);
			searchResults.setVisibility(View.GONE);
			searchStatus.setVisibility(View.GONE);
			// Only show empty state if no downloaded files either
			localEmptyState.setVisibility(hasDownloadedFiles ? View.GONE : View.VISIBLE);
		}

		// Update divider visibility
		downloadedFilesDivider.setVisibility(hasDownloadedFiles && hasZim ? View.VISIBLE : View.GONE);
	}

	private void updateSearchStatus(int resultCount, boolean isSearchComplete) {
		if (!isSearchComplete) {
			searchStatus.setVisibility(View.GONE);
			return;
		}

		searchStatus.setVisibility(View.VISIBLE);
		if (resultCount == 0) {
			searchStatus.setText(R.string.search_no_results);
		} else {
			searchStatus.setText(getString(R.string.search_results_count, resultCount));
		}
	}

	private void loadCatalog(boolean forceRefresh) {
		catalogProgress.setVisibility(View.VISIBLE);
		downloadEmptyState.setVisibility(View.GONE);
		catalogList.setVisibility(View.GONE);
		languageFilterContainer.setVisibility(View.GONE);

		executor.execute(() -> {
			List<ZimCatalogItem> items = catalogParser.fetchCatalog(forceRefresh);
			// Filter to Wikipedia only and limit size to 5GB for mobile
			items = catalogParser.filterByCategory(items, "wikipedia");
			items = catalogParser.filterByMaxSize(items, 5L * 1024 * 1024 * 1024);

			// Extract unique languages
			Set<String> languages = new HashSet<>();
			for (ZimCatalogItem item : items) {
				if (item.getLanguage() != null) {
					languages.add(item.getLanguage());
				}
			}
			List<String> sortedLanguages = new ArrayList<>(languages);
			sortedLanguages.sort(String::compareToIgnoreCase);

			// v0.7b: Detect device language and prioritize it in the list
			String deviceLang = Locale.getDefault().getLanguage();
			if (sortedLanguages.contains(deviceLang)) {
				sortedLanguages.remove(deviceLang);
				sortedLanguages.add(0, deviceLang);
			}

			List<ZimCatalogItem> finalItems = items;
			String finalDeviceLang = deviceLang;
			mainHandler.post(() -> {
				catalogProgress.setVisibility(View.GONE);
				catalogItems = finalItems;
				availableLanguages = sortedLanguages;

				if (catalogItems.isEmpty()) {
					downloadEmptyState.setVisibility(View.VISIBLE);
					catalogList.setVisibility(View.GONE);
					languageFilterContainer.setVisibility(View.GONE);
				} else {
					downloadEmptyState.setVisibility(View.GONE);
					catalogList.setVisibility(View.VISIBLE);
					languageFilterContainer.setVisibility(View.VISIBLE);

					// Setup language spinner
					List<String> spinnerItems = new ArrayList<>();
					spinnerItems.add(getString(R.string.all_languages));
					spinnerItems.addAll(availableLanguages);
					ArrayAdapter<String> adapter = new ArrayAdapter<>(
							requireContext(),
							android.R.layout.simple_spinner_item,
							spinnerItems);
					adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
					languageSpinner.setAdapter(adapter);

					// v0.7b: Auto-select device language if available
					int deviceLangIndex = availableLanguages.indexOf(finalDeviceLang);
					if (deviceLangIndex >= 0) {
						languageSpinner.setSelection(deviceLangIndex + 1); // +1 for "All languages"
					}

					applyLanguageFilter();
				}
			});
		});
	}

	private void applyLanguageFilter() {
		if (selectedLanguage == null) {
			filteredCatalogItems = new ArrayList<>(catalogItems);
		} else {
			filteredCatalogItems = catalogParser.filterByLanguage(catalogItems, selectedLanguage);
		}
		catalogAdapter.setItems(filteredCatalogItems);
	}

	// ---- v0.5: Smart ZIM Acquisition ----

	/**
	 * Scan common storage locations for .zim files.
	 * Returns all found ZIM files (app dir, Downloads, external storage, SD card).
	 */
	@NonNull
	private List<File> autoDetectZimFiles() {
		List<File> found = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		// 1. App's own ZIM directory
		scanDirectoryForZim(downloadManager.getZimDirectory(), found, seen);

		// 2. External storage Downloads folder
		try {
			File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			scanDirectoryForZim(downloads, found, seen);
		} catch (Exception e) {
			LOG.warn("Could not scan Downloads: " + e.getMessage());
		}

		// 3. Root of external storage
		try {
			File extRoot = Environment.getExternalStorageDirectory();
			scanDirectoryForZim(extRoot, found, seen);
		} catch (Exception e) {
			LOG.warn("Could not scan external storage root: " + e.getMessage());
		}

		// 4. SD card (if present) via getExternalFilesDirs
		try {
			File[] extDirs = app.getExternalFilesDirs(null);
			if (extDirs != null) {
				for (File dir : extDirs) {
					if (dir != null) {
						scanDirectoryForZim(dir, found, seen);
						// Also scan parent (app-specific → generic area)
						File parent = dir.getParentFile();
						if (parent != null) {
							scanDirectoryForZim(parent, found, seen);
						}
					}
				}
			}
		} catch (Exception e) {
			LOG.warn("Could not scan external file dirs: " + e.getMessage());
		}

		LOG.info("v0.5: Auto-detected " + found.size() + " ZIM files");
		return found;
	}

	private void scanDirectoryForZim(@Nullable File dir, @NonNull List<File> found, @NonNull Set<String> seen) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) return;
		try {
			File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".zim"));
			if (files != null) {
				for (File file : files) {
					String path = file.getAbsolutePath();
					if (!seen.contains(path) && file.length() > 0) {
						seen.add(path);
						found.add(file);
					}
				}
			}
		} catch (Exception e) {
			LOG.warn("Error scanning " + dir.getAbsolutePath() + ": " + e.getMessage());
		}
	}

	/**
	 * v0.5: Auto-scan on fragment open. If exactly 1 file found, open it silently.
	 * Runs on background thread.
	 */
	private void autoDetectAndOpenFirst() {
		executor.execute(() -> {
			List<File> files = autoDetectZimFiles();
			mainHandler.post(() -> {
				if (!isAdded()) return;
				if (files.size() == 1) {
					// Auto-open the single found file
					openZimFile(files.get(0));
					app.showShortToastMessage(getString(R.string.zim_found_auto, files.get(0).getName()));
				} else if (files.size() > 1) {
					// Multiple found — update the local files list
					loadDownloadedFiles();
					updateLocalUI();
				}
				// If 0 found, do nothing — the empty state is already shown
			});
		});
	}

	/**
	 * v0.5: Smart ZIM acquisition — three-tier fallback.
	 * 1. Scan for local .zim files
	 * 2. If found 1 → auto-open; if found multiple → picker dialog
	 * 3. If found 0 → show options dialog (P2P, Browse Files, Download)
	 */
	private void smartZimAcquire() {
		// Show scanning toast
		app.showShortToastMessage(R.string.zim_scanning);

		executor.execute(() -> {
			List<File> files = autoDetectZimFiles();
			mainHandler.post(() -> {
				if (!isAdded()) return;
				if (files.size() == 1) {
					openZimFile(files.get(0));
					app.showShortToastMessage(getString(R.string.zim_found_auto, files.get(0).getName()));
				} else if (files.size() > 1) {
					showZimPickerDialog(files);
				} else {
					showZimNotFoundDialog();
				}
			});
		});
	}

	/**
	 * v0.5: Show a picker dialog when multiple ZIM files are found.
	 */
	private void showZimPickerDialog(@NonNull List<File> files) {
		FragmentActivity activity = getActivity();
		if (activity == null) return;

		String[] names = new String[files.size()];
		for (int i = 0; i < files.size(); i++) {
			names[i] = files.get(i).getName() + " (" + formatFileSize(files.get(i).length()) + ")";
		}

		new AlertDialog.Builder(activity)
				.setTitle(getString(R.string.zim_found_multiple, files.size()))
				.setItems(names, (dialog, which) -> openZimFile(files.get(which)))
				.setNegativeButton(R.string.shared_string_cancel, null)
				.show();
	}

	/**
	 * v0.5: Show options dialog when no ZIM files found on device.
	 */
	private void showZimNotFoundDialog() {
		FragmentActivity activity = getActivity();
		if (activity == null) return;

		new AlertDialog.Builder(activity)
				.setTitle(R.string.zim_not_found_title)
				.setMessage(R.string.zim_not_found_message)
				.setPositiveButton(R.string.zim_get_via_p2p, (dialog, which) -> {
					// Navigate to P2P tab
					net.osmand.plus.activities.MapActivity mapActivity = getMapActivity();
					if (mapActivity != null) {
						mapActivity.getLamppPanelManager().openPanel(
								net.osmand.plus.lampp.LamppTab.P2P);
					}
				})
				.setNeutralButton(R.string.zim_browse_files, (dialog, which) -> openFilePicker())
				.setNegativeButton(R.string.zim_download_online, (dialog, which) -> {
					// Switch to Download tab
					tabLayout.selectTab(tabLayout.getTabAt(TAB_DOWNLOAD));
				})
				.show();
	}

	private void openFilePicker() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		String[] mimeTypes = {"application/octet-stream", "application/zim", "*/*"};
		intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		startActivityForResult(intent, REQUEST_OPEN_ZIM);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_OPEN_ZIM && resultCode == Activity.RESULT_OK && data != null) {
			Uri uri = data.getData();
			if (uri != null) {
				openZimFromUri(uri);
			}
		}
	}

	private void openZimFromUri(Uri uri) {
		if (zimManager == null) {
			app.showShortToastMessage("Wikipedia plugin not available");
			return;
		}

		try {
			int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
			app.getContentResolver().takePersistableUriPermission(uri, takeFlags);
		} catch (SecurityException e) {
			LOG.warn("Could not take persistent permission for URI: " + uri, e);
		}

		if (zimManager.openZimFile(uri)) {
			app.showShortToastMessage("Opened: " + zimManager.getZimTitle());
			updateLocalUI();
		} else {
			app.showShortToastMessage("Failed to open ZIM file");
		}
	}

	private void openDownloadedZim(ZimCatalogItem item) {
		String fileName = item.getFileName();
		if (fileName == null) return;

		File file = new File(downloadManager.getZimDirectory(), fileName);
		openZimFile(file);
	}

	private void openZimFile(File file) {
		android.util.Log.i("ZimBrowserFragment", "openZimFile called with: " + file.getAbsolutePath());
		if (file.exists() && zimManager != null) {
			android.util.Log.i("ZimBrowserFragment", "File exists, calling zimManager.openZimFile");
			if (zimManager.openZimFile(file)) {
				android.util.Log.i("ZimBrowserFragment", "ZIM file opened successfully: " + zimManager.getZimTitle());
				app.showShortToastMessage("Opened: " + zimManager.getZimTitle());
				// Switch to local tab and refresh
				tabLayout.selectTab(tabLayout.getTabAt(TAB_LOCAL));
			} else {
				android.util.Log.e("ZimBrowserFragment", "Failed to open ZIM file");
				app.showShortToastMessage("Cannot open ZIM file. It may use an unsupported format.");
			}
		} else {
			android.util.Log.e("ZimBrowserFragment", "File doesn't exist or zimManager is null. exists=" + file.exists() + ", zimManager=" + zimManager);
		}
	}

	private void confirmDeleteFile(File file) {
		FragmentActivity activity = getActivity();
		if (activity == null) return;

		new AlertDialog.Builder(activity)
				.setTitle(R.string.delete_zim_file)
				.setMessage(R.string.delete_zim_file_descr)
				.setPositiveButton(R.string.shared_string_delete, (dialog, which) -> {
					// Close if this file is currently open
					if (zimManager != null && zimManager.isOpen()) {
						File currentFile = zimManager.getCurrentFile();
						if (currentFile != null && currentFile.equals(file)) {
							zimManager.close();
						}
					}

					// Delete the file
					if (file.delete()) {
						app.showShortToastMessage("File deleted");
						loadDownloadedFiles();
						updateLocalUI();
						// Also refresh catalog adapter to update downloaded states
						catalogAdapter.notifyDataSetChanged();
					} else {
						app.showShortToastMessage("Failed to delete file");
					}
				})
				.setNegativeButton(R.string.shared_string_cancel, null)
				.show();
	}

	private void performSearch(String query) {
		if (zimManager == null || !zimManager.isOpen()) {
			return;
		}

		searchProgress.setVisibility(View.VISIBLE);
		searchStatus.setVisibility(View.GONE);

		executor.execute(() -> {
			List<String> results = zimManager.searchArticles(query, MAX_SEARCH_RESULTS);
			mainHandler.post(() -> {
				searchProgress.setVisibility(View.GONE);
				searchAdapter.setResults(results);
				updateSearchStatus(results.size(), true);
			});
		});
	}

	private void openArticle(String title) {
		if (zimManager == null || !zimManager.isOpen()) {
			return;
		}

		executor.execute(() -> {
			String html = zimManager.getArticleHtml(title);
			mainHandler.post(() -> {
				FragmentActivity activity = getActivity();
				if (activity != null && html != null) {
					ZimArticleDialogFragment.showInstance(activity, title, html);
				} else if (html == null) {
					app.showShortToastMessage("Article not found: " + title);
				}
			});
		});
	}

	private String formatFileSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		DecimalFormat df = new DecimalFormat("#.##");
		if (bytes < 1024 * 1024) {
			return df.format(bytes / 1024.0) + " KB";
		}
		if (bytes < 1024 * 1024 * 1024) {
			return df.format(bytes / (1024.0 * 1024)) + " MB";
		}
		return df.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
	}

	// DownloadListener callbacks
	@Override
	public void onDownloadStarted(ZimCatalogItem item) {
		catalogAdapter.notifyDataSetChanged();
	}

	@Override
	public void onDownloadProgress(ZimCatalogItem item, int progress, long downloadedBytes, long totalBytes) {
		catalogAdapter.notifyDataSetChanged();
	}

	@Override
	public void onDownloadComplete(ZimCatalogItem item, File file) {
		app.showShortToastMessage("Download complete: " + item.getTitle());
		catalogAdapter.notifyDataSetChanged();
		// Refresh downloaded files list if on local tab
		if (currentTab == TAB_LOCAL) {
			loadDownloadedFiles();
		}
	}

	@Override
	public void onDownloadError(ZimCatalogItem item, String error) {
		app.showShortToastMessage("Download failed: " + error);
		catalogAdapter.notifyDataSetChanged();
	}

	@Override
	public void onDownloadCancelled(ZimCatalogItem item) {
		catalogAdapter.notifyDataSetChanged();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (downloadManager != null) {
			downloadManager.removeListener(this);
		}
		executor.shutdown();
	}

	/**
	 * Adapter for downloaded ZIM files in the Local tab.
	 */
	private class LocalFilesAdapter extends RecyclerView.Adapter<LocalFilesAdapter.ViewHolder> {

		private List<File> files = new ArrayList<>();

		void setFiles(List<File> files) {
			this.files = files != null ? files : new ArrayList<>();
			notifyDataSetChanged();
		}

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.zim_local_item, parent, false);
			return new ViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			File file = files.get(position);
			holder.fileName.setText(file.getName());
			holder.fileSize.setText(formatFileSize(file.length()));

			holder.openButton.setOnClickListener(v -> openZimFile(file));
			holder.deleteButton.setOnClickListener(v -> confirmDeleteFile(file));
			holder.itemView.setOnClickListener(v -> openZimFile(file));
		}

		@Override
		public int getItemCount() {
			return files.size();
		}

		class ViewHolder extends RecyclerView.ViewHolder {
			TextView fileName;
			TextView fileSize;
			ImageButton openButton;
			ImageButton deleteButton;

			ViewHolder(@NonNull View itemView) {
				super(itemView);
				fileName = itemView.findViewById(R.id.file_name);
				fileSize = itemView.findViewById(R.id.file_size);
				openButton = itemView.findViewById(R.id.open_button);
				deleteButton = itemView.findViewById(R.id.delete_button);
			}
		}
	}

	/**
	 * Simple RecyclerView adapter for search results.
	 */
	private class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {

		private List<String> results = new ArrayList<>();

		void setResults(List<String> results) {
			this.results = results != null ? results : new ArrayList<>();
			notifyDataSetChanged();
		}

		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
					.inflate(android.R.layout.simple_list_item_1, parent, false);
			return new ViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			String title = results.get(position);
			holder.textView.setText(title);
			holder.itemView.setOnClickListener(v -> openArticle(title));
		}

		@Override
		public int getItemCount() {
			return results.size();
		}

		class ViewHolder extends RecyclerView.ViewHolder {
			TextView textView;

			ViewHolder(@NonNull View itemView) {
				super(itemView);
				textView = itemView.findViewById(android.R.id.text1);
			}
		}
	}
}
