package net.osmand.plus.wikipedia;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.osmand.IndexConstants;
import net.osmand.plus.R;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.development.OsmandDevelopmentPlugin;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.InsetTarget;
import net.osmand.plus.utils.InsetTargetsCollection;
import net.osmand.plus.utils.InsetsUtils.InsetSide;

import java.io.File;

/**
 * LAMPP: Fragment for displaying articles from ZIM files.
 *
 * This is similar to WikipediaDialogFragment but optimized for ZIM content
 * which doesn't have the same structure as OsmAnd's built-in Wikipedia POIs.
 */
public class ZimArticleDialogFragment extends WikiArticleBaseDialogFragment {

	public static final String TAG = "ZimArticleDialogFragment";

	private static final String KEY_TITLE = "title";
	private static final String KEY_HTML = "html";

	private TextView backToSearchButton;

	private String title;
	private String htmlContent;

	public void setTitle(String title) {
		this.title = title;
	}

	public void setHtmlContent(String htmlContent) {
		this.htmlContent = htmlContent;
	}

	@SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		updateNightMode();

		// Restore state if available
		if (savedInstanceState != null) {
			title = savedInstanceState.getString(KEY_TITLE, title);
			htmlContent = savedInstanceState.getString(KEY_HTML, htmlContent);
		}

		View mainView = inflate(R.layout.wikipedia_dialog_fragment, container, false);

		setupToolbar(mainView.findViewById(R.id.toolbar));

		articleToolbarText = mainView.findViewById(R.id.title_text_view);
		ImageView options = mainView.findViewById(R.id.options_button);
		// Hide options button - not needed for ZIM articles
		options.setVisibility(View.GONE);

		ColorStateList buttonColorStateList = AndroidUtils.createPressedColorStateList(getContext(), nightMode,
				R.color.ctx_menu_controller_button_text_color_light_n, R.color.ctx_menu_controller_button_text_color_light_p,
				R.color.ctx_menu_controller_button_text_color_dark_n, R.color.ctx_menu_controller_button_text_color_dark_p);

		// Reuse the "read full article" button as "back to search"
		backToSearchButton = mainView.findViewById(R.id.read_full_article);
		backToSearchButton.setText(R.string.shared_string_back);
		backToSearchButton.setBackgroundResource(nightMode ? R.drawable.bt_round_long_night : R.drawable.bt_round_long_day);
		backToSearchButton.setTextColor(buttonColorStateList);
		backToSearchButton.setCompoundDrawablesWithIntrinsicBounds(getIcon(R.drawable.ic_arrow_back), null, null, null);
		backToSearchButton.setCompoundDrawablePadding((int) getResources().getDimension(R.dimen.content_padding_small));
		int paddingLeft = (int) getResources().getDimension(R.dimen.wikipedia_button_left_padding);
		int paddingRight = (int) getResources().getDimension(R.dimen.dialog_content_margin);
		backToSearchButton.setPadding(paddingLeft, 0, paddingRight, 0);
		backToSearchButton.setOnClickListener(v -> dismiss());

		// Hide language selector - ZIM files have single language
		selectedLangTv = mainView.findViewById(R.id.select_language_text_view);
		selectedLangTv.setVisibility(View.GONE);

		contentWebView = mainView.findViewById(R.id.content_web_view);
		contentWebView.setOnTouchListener(new View.OnTouchListener() {
			float initialY, finalY;
			boolean isScrollingUp;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int action = event.getAction();

				switch (action) {
					case (MotionEvent.ACTION_DOWN):
						initialY = event.getY();
					case (MotionEvent.ACTION_UP):
						finalY = event.getY();
						if (initialY < finalY) {
							isScrollingUp = true;
						} else if (initialY > finalY) {
							isScrollingUp = false;
						}
					default:
				}

				if (isScrollingUp) {
					backToSearchButton.setVisibility(View.VISIBLE);
				} else {
					backToSearchButton.setVisibility(View.GONE);
				}

				return false;
			}
		});

		WebSettings webSettings = contentWebView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setTextZoom((int) (getResources().getConfiguration().fontScale * 100f));
		// Allow loading local images from ZIM
		webSettings.setAllowFileAccess(true);
		updateWebSettings();
		contentWebView.setBackgroundColor(ContextCompat.getColor(app, nightMode ? R.color.list_background_color_dark : R.color.list_background_color_light));

		return mainView;
	}

	@Override
	public InsetTargetsCollection getInsetTargets() {
		InsetTargetsCollection collection = super.getInsetTargets();
		collection.add(InsetTarget.createCustomBuilder(R.id.read_full_article).portraitSides(InsetSide.BOTTOM).preferMargin(true).build());
		return collection;
	}

	@Override
	@NonNull
	protected String createHtmlContent() {
		StringBuilder sb = new StringBuilder(HEADER_INNER);
		sb.append("<body>\n");
		String nightModeClass = nightMode ? " nightmode" : "";
		sb.append("<div class=\"main");
		sb.append(nightModeClass);
		sb.append("\">\n");

		if (title != null && !title.isEmpty()) {
			sb.append("<h1>").append(title).append("</h1>");
		}

		if (htmlContent != null) {
			sb.append(htmlContent);
		} else {
			sb.append("<p>Article content not available.</p>");
		}

		sb.append(FOOTER_INNER);

		if (PluginsHelper.isActive(OsmandDevelopmentPlugin.class)) {
			writeOutHTML(sb, new File(app.getAppPath(IndexConstants.WIKIVOYAGE_INDEX_DIR), "zim_page.html"));
		}
		return sb.toString();
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		populateArticle();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(KEY_TITLE, title);
		// Note: For large articles, this could exceed Bundle size limits
		// In production, consider saving to a file instead
		if (htmlContent != null && htmlContent.length() < 500000) {
			outState.putString(KEY_HTML, htmlContent);
		}
	}

	@Override
	public void onDestroyView() {
		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.setDismissMessage(null);
		}
		super.onDestroyView();
	}

	@Override
	protected void populateArticle() {
		if (articleToolbarText != null && title != null) {
			articleToolbarText.setText(title);
		}
		if (contentWebView != null) {
			contentWebView.loadDataWithBaseURL(getBaseUrl(), createHtmlContent(), "text/html", "UTF-8", null);
		}
	}

	@Override
	protected void showPopupLangMenu(View view, String langSelected) {
		// No language menu for ZIM articles - they are single-language
	}

	@Override
	@NonNull
	public Drawable getIcon(@DrawableRes int resId) {
		int colorId = nightMode ? R.color.ctx_menu_controller_button_text_color_dark_n : R.color.ctx_menu_controller_button_text_color_light_n;
		return requireIcon(resId, colorId);
	}

	/**
	 * Show a ZIM article in the dialog.
	 *
	 * @param activity The activity context
	 * @param title The article title
	 * @param htmlContent The HTML content from the ZIM file
	 */
	public static void showInstance(@NonNull FragmentActivity activity,
	                                @NonNull String title, @NonNull String htmlContent) {
		FragmentManager fragmentManager = activity.getSupportFragmentManager();
		if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG, true)) {
			ZimArticleDialogFragment fragment = new ZimArticleDialogFragment();
			fragment.setTitle(title);
			fragment.setHtmlContent(htmlContent);
			fragment.setRetainInstance(true);
			fragment.show(fragmentManager, TAG);
		}
	}
}
