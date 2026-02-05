package net.osmand.plus.wikipedia;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;

import org.apache.commons.logging.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * LAMPP: Parser for Kiwix OPDS catalog feed.
 *
 * Fetches and parses the Kiwix library catalog to get available ZIM files.
 * Uses OPDS (Open Publication Distribution System) Atom Feed format.
 */
public class KiwixCatalogParser {

	private static final Log LOG = PlatformUtil.getLog(KiwixCatalogParser.class);

	private static final String CATALOG_URL = "https://library.kiwix.org/catalog/root.xml";
	private static final String CACHE_FILENAME = "kiwix_catalog.xml";
	private static final long CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000; // 24 hours

	private static final String NS_ATOM = "http://www.w3.org/2005/Atom";
	private static final String NS_DC = "http://purl.org/dc/terms/";
	private static final String NS_OPDS = "http://opds-spec.org/2010/catalog";

	private final OsmandApplication app;

	public KiwixCatalogParser(@NonNull OsmandApplication app) {
		this.app = app;
	}

	/**
	 * Fetch and parse the Kiwix catalog.
	 * Uses cached version if available and fresh.
	 *
	 * @param forceRefresh If true, ignore cache and fetch fresh data
	 * @return List of available ZIM files
	 */
	@NonNull
	public List<ZimCatalogItem> fetchCatalog(boolean forceRefresh) {
		List<ZimCatalogItem> items = new ArrayList<>();

		try {
			InputStream inputStream = getCatalogStream(forceRefresh);
			if (inputStream != null) {
				items = parseCatalog(inputStream);
				inputStream.close();
			}
		} catch (IOException | XmlPullParserException e) {
			LOG.error("Error fetching/parsing Kiwix catalog", e);
		}

		return items;
	}

	/**
	 * Filter catalog items by category (e.g., "wikipedia").
	 */
	@NonNull
	public List<ZimCatalogItem> filterByCategory(@NonNull List<ZimCatalogItem> items, @NonNull String category) {
		List<ZimCatalogItem> filtered = new ArrayList<>();
		for (ZimCatalogItem item : items) {
			if (category.equalsIgnoreCase(item.getCategory())) {
				filtered.add(item);
			}
		}
		return filtered;
	}

	/**
	 * Filter catalog items by language.
	 */
	@NonNull
	public List<ZimCatalogItem> filterByLanguage(@NonNull List<ZimCatalogItem> items, @NonNull String language) {
		List<ZimCatalogItem> filtered = new ArrayList<>();
		for (ZimCatalogItem item : items) {
			if (language.equalsIgnoreCase(item.getLanguage())) {
				filtered.add(item);
			}
		}
		return filtered;
	}

	/**
	 * Filter catalog items by maximum file size.
	 */
	@NonNull
	public List<ZimCatalogItem> filterByMaxSize(@NonNull List<ZimCatalogItem> items, long maxSizeBytes) {
		List<ZimCatalogItem> filtered = new ArrayList<>();
		for (ZimCatalogItem item : items) {
			if (item.getFileSize() <= maxSizeBytes) {
				filtered.add(item);
			}
		}
		return filtered;
	}

	@Nullable
	private InputStream getCatalogStream(boolean forceRefresh) throws IOException {
		File cacheFile = new File(app.getCacheDir(), CACHE_FILENAME);

		// Check if cache is valid
		if (!forceRefresh && cacheFile.exists()) {
			long age = System.currentTimeMillis() - cacheFile.lastModified();
			if (age < CACHE_VALIDITY_MS) {
				LOG.info("Using cached Kiwix catalog (age: " + (age / 1000 / 60) + " minutes)");
				return new FileInputStream(cacheFile);
			}
		}

		// Fetch fresh catalog
		LOG.info("Fetching Kiwix catalog from " + CATALOG_URL);
		HttpURLConnection connection = null;
		try {
			URL url = new URL(CATALOG_URL);
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(30000);
			connection.setReadTimeout(60000);
			connection.setRequestProperty("User-Agent", "Lampp/1.0");

			int responseCode = connection.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				LOG.error("Kiwix catalog fetch failed: HTTP " + responseCode);
				// Fall back to cache if available
				if (cacheFile.exists()) {
					return new FileInputStream(cacheFile);
				}
				return null;
			}

			// Save to cache
			InputStream inputStream = new BufferedInputStream(connection.getInputStream());
			try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					fos.write(buffer, 0, bytesRead);
				}
			}
			inputStream.close();

			LOG.info("Kiwix catalog cached successfully");
			return new FileInputStream(cacheFile);

		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	@NonNull
	private List<ZimCatalogItem> parseCatalog(@NonNull InputStream inputStream)
			throws XmlPullParserException, IOException {
		List<ZimCatalogItem> items = new ArrayList<>();

		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		factory.setNamespaceAware(true);
		XmlPullParser parser = factory.newPullParser();
		parser.setInput(inputStream, "UTF-8");

		ZimCatalogItem currentItem = null;
		String currentTag = null;

		int eventType = parser.getEventType();
		while (eventType != XmlPullParser.END_DOCUMENT) {
			String tagName = parser.getName();

			switch (eventType) {
				case XmlPullParser.START_TAG:
					currentTag = tagName;
					if ("entry".equals(tagName)) {
						currentItem = new ZimCatalogItem();
					} else if ("link".equals(tagName) && currentItem != null) {
						parseLink(parser, currentItem);
					}
					break;

				case XmlPullParser.TEXT:
					if (currentItem != null && currentTag != null) {
						String text = parser.getText().trim();
						if (!text.isEmpty()) {
							parseField(currentItem, currentTag, text);
						}
					}
					break;

				case XmlPullParser.END_TAG:
					if ("entry".equals(tagName) && currentItem != null) {
						// Only add items with valid download URL
						if (currentItem.getDownloadUrl() != null) {
							items.add(currentItem);
						}
						currentItem = null;
					}
					currentTag = null;
					break;
			}

			eventType = parser.next();
		}

		LOG.info("Parsed " + items.size() + " ZIM entries from catalog");
		return items;
	}

	private void parseField(@NonNull ZimCatalogItem item, @NonNull String tag, @NonNull String text) {
		switch (tag) {
			case "id":
				item.setId(text);
				break;
			case "title":
				item.setTitle(text);
				break;
			case "summary":
				item.setDescription(text);
				break;
			case "language":
				item.setLanguage(text);
				break;
			case "category":
				item.setCategory(text);
				break;
			case "flavour":
				item.setFlavour(text);
				break;
			case "issued":
				item.setPublishDate(parseDate(text));
				break;
		}
	}

	private void parseLink(@NonNull XmlPullParser parser, @NonNull ZimCatalogItem item) {
		String rel = parser.getAttributeValue(null, "rel");
		String href = parser.getAttributeValue(null, "href");
		String type = parser.getAttributeValue(null, "type");
		String lengthStr = parser.getAttributeValue(null, "length");

		if (href == null) {
			return;
		}

		// Look for the actual ZIM download link
		// The catalog uses .meta4 links, we strip that to get direct ZIM URL
		if (href.endsWith(".meta4")) {
			String zimUrl = href.substring(0, href.length() - 6); // Remove .meta4
			item.setDownloadUrl(zimUrl);
			if (lengthStr != null) {
				try {
					item.setFileSize(Long.parseLong(lengthStr));
				} catch (NumberFormatException e) {
					// Ignore
				}
			}
		} else if ("http://opds-spec.org/image/thumbnail".equals(rel)) {
			item.setThumbnailUrl(href);
		} else if (type != null && type.contains("application/x-zim") && item.getDownloadUrl() == null) {
			// Fallback: direct ZIM link
			item.setDownloadUrl(href);
			if (lengthStr != null) {
				try {
					item.setFileSize(Long.parseLong(lengthStr));
				} catch (NumberFormatException e) {
					// Ignore
				}
			}
		}
	}

	@Nullable
	private Date parseDate(@NonNull String dateStr) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
			return sdf.parse(dateStr);
		} catch (ParseException e) {
			return null;
		}
	}

	/**
	 * Clear the cached catalog.
	 */
	public void clearCache() {
		File cacheFile = new File(app.getCacheDir(), CACHE_FILENAME);
		if (cacheFile.exists()) {
			cacheFile.delete();
		}
	}
}
