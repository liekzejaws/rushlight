/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.wikipedia;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

/**
 * LAMPP: Data model for a downloadable ZIM file from the Kiwix catalog.
 *
 * Represents an entry from the Kiwix OPDS catalog feed.
 */
public class ZimCatalogItem {

	private String id;
	private String title;
	private String description;
	private String language;
	private String downloadUrl;
	private long fileSize;
	private String category;      // wikipedia, wiktionary, wikivoyage, etc.
	private String flavour;       // maxi, mini, nopic
	private Date publishDate;
	private String thumbnailUrl;

	public ZimCatalogItem() {
	}

	@Nullable
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Nullable
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	@Nullable
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Nullable
	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	@Nullable
	public String getDownloadUrl() {
		return downloadUrl;
	}

	public void setDownloadUrl(String downloadUrl) {
		this.downloadUrl = downloadUrl;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	@Nullable
	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	@Nullable
	public String getFlavour() {
		return flavour;
	}

	public void setFlavour(String flavour) {
		this.flavour = flavour;
	}

	@Nullable
	public Date getPublishDate() {
		return publishDate;
	}

	public void setPublishDate(Date publishDate) {
		this.publishDate = publishDate;
	}

	@Nullable
	public String getThumbnailUrl() {
		return thumbnailUrl;
	}

	public void setThumbnailUrl(String thumbnailUrl) {
		this.thumbnailUrl = thumbnailUrl;
	}

	/**
	 * Get human-readable file size string.
	 */
	@NonNull
	public String getFileSizeString() {
		if (fileSize < 1024) {
			return fileSize + " B";
		} else if (fileSize < 1024 * 1024) {
			return String.format("%.1f KB", fileSize / 1024.0);
		} else if (fileSize < 1024 * 1024 * 1024) {
			return String.format("%.1f MB", fileSize / (1024.0 * 1024));
		} else {
			return String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024));
		}
	}

	/**
	 * Get a display title that includes language and flavour info.
	 */
	@NonNull
	public String getDisplayTitle() {
		StringBuilder sb = new StringBuilder();
		if (title != null) {
			sb.append(title);
		}
		if (flavour != null && !flavour.isEmpty()) {
			sb.append(" (").append(flavour).append(")");
		}
		return sb.toString();
	}

	/**
	 * Check if this is a Wikipedia ZIM file.
	 */
	public boolean isWikipedia() {
		return "wikipedia".equalsIgnoreCase(category);
	}

	/**
	 * Check if this is a mini/nopic variant (smaller download).
	 */
	public boolean isCompact() {
		return "mini".equalsIgnoreCase(flavour) || "nopic".equalsIgnoreCase(flavour);
	}

	/**
	 * Get the expected filename for this ZIM.
	 */
	@Nullable
	public String getFileName() {
		if (downloadUrl == null) {
			return null;
		}
		int lastSlash = downloadUrl.lastIndexOf('/');
		if (lastSlash >= 0 && lastSlash < downloadUrl.length() - 1) {
			return downloadUrl.substring(lastSlash + 1);
		}
		return null;
	}

	@NonNull
	@Override
	public String toString() {
		return "ZimCatalogItem{" +
				"title='" + title + '\'' +
				", language='" + language + '\'' +
				", size=" + getFileSizeString() +
				", category='" + category + '\'' +
				", flavour='" + flavour + '\'' +
				'}';
	}
}
