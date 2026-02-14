package net.osmand.plus.guides;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Immutable data model representing a single survival guide.
 * Guides are loaded from JSON files (bundled in assets or user-provided).
 */
public class GuideEntry {

	public enum Importance {
		CRITICAL(0),
		HIGH(1),
		MEDIUM(2);

		private final int sortOrder;

		Importance(int sortOrder) {
			this.sortOrder = sortOrder;
		}

		public int getSortOrder() {
			return sortOrder;
		}

		@NonNull
		public static Importance fromString(@Nullable String value) {
			if (value == null) return MEDIUM;
			try {
				return valueOf(value.toUpperCase());
			} catch (IllegalArgumentException e) {
				return MEDIUM;
			}
		}
	}

	private final String id;
	private final String title;
	private final GuideCategory category;
	private final List<String> tags;
	private final Importance importance;
	private final String summary;
	private final String body;
	private final long lastUpdated;
	private final boolean bundled;

	private GuideEntry(Builder builder) {
		this.id = builder.id;
		this.title = builder.title;
		this.category = builder.category;
		this.tags = builder.tags != null
				? Collections.unmodifiableList(new java.util.ArrayList<>(Arrays.asList(builder.tags)))
				: Collections.emptyList();
		this.importance = builder.importance;
		this.summary = builder.summary;
		this.body = builder.body;
		this.lastUpdated = builder.lastUpdated;
		this.bundled = builder.bundled;
	}

	@NonNull
	public String getId() { return id; }

	@NonNull
	public String getTitle() { return title; }

	@NonNull
	public GuideCategory getCategory() { return category; }

	@NonNull
	public List<String> getTags() { return tags; }

	@NonNull
	public Importance getImportance() { return importance; }

	@NonNull
	public String getSummary() { return summary; }

	@Nullable
	public String getBody() { return body; }

	public long getLastUpdated() { return lastUpdated; }

	public boolean isBundled() { return bundled; }

	public boolean hasBody() { return body != null && !body.isEmpty(); }

	public static class Builder {
		private String id;
		private String title;
		private GuideCategory category = GuideCategory.FIRST_AID;
		private String[] tags;
		private Importance importance = Importance.MEDIUM;
		private String summary = "";
		private String body;
		private long lastUpdated;
		private boolean bundled = true;

		public Builder(@NonNull String id, @NonNull String title) {
			this.id = id;
			this.title = title;
		}

		public Builder setCategory(@NonNull GuideCategory category) {
			this.category = category;
			return this;
		}

		public Builder setTags(@Nullable String[] tags) {
			this.tags = tags;
			return this;
		}

		public Builder setImportance(@NonNull Importance importance) {
			this.importance = importance;
			return this;
		}

		public Builder setSummary(@NonNull String summary) {
			this.summary = summary;
			return this;
		}

		public Builder setBody(@Nullable String body) {
			this.body = body;
			return this;
		}

		public Builder setLastUpdated(long lastUpdated) {
			this.lastUpdated = lastUpdated;
			return this;
		}

		public Builder setBundled(boolean bundled) {
			this.bundled = bundled;
			return this;
		}

		@NonNull
		public GuideEntry build() {
			if (id == null || id.isEmpty()) {
				throw new IllegalArgumentException("Guide id cannot be null or empty");
			}
			if (title == null || title.isEmpty()) {
				throw new IllegalArgumentException("Guide title cannot be null or empty");
			}
			return new GuideEntry(this);
		}
	}
}
