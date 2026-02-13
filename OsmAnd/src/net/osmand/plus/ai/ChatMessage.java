package net.osmand.plus.ai;

import net.osmand.plus.ai.rag.ArticleSource;
import net.osmand.plus.ai.rag.PoiSource;

import java.util.List;

/**
 * Rushlight: Data model for chat messages between user and AI assistant.
 * Extracted from LlmChatFragment inner class to support persistence via EncryptedChatStorage.
 */
public class ChatMessage {
	public static final int ROLE_USER = 0;
	public static final int ROLE_AI = 1;
	public static final int ROLE_SYSTEM = 2;

	public int role;
	public String content;
	public long timestamp;
	public List<ArticleSource> sources;    // Wikipedia sources used (for AI messages)
	public List<PoiSource> poiSources;     // POI sources used

	public ChatMessage(int role, String content) {
		this.role = role;
		this.content = content;
		this.timestamp = System.currentTimeMillis();
	}

	public ChatMessage(int role, String content, long timestamp) {
		this.role = role;
		this.content = content;
		this.timestamp = timestamp;
	}

	public boolean hasSources() {
		return sources != null && !sources.isEmpty();
	}

	public boolean hasPoiSources() {
		return poiSources != null && !poiSources.isEmpty();
	}

	public boolean hasAnySources() {
		return hasSources() || hasPoiSources();
	}

	public String getSourcesText() {
		StringBuilder sb = new StringBuilder();

		if (hasSources()) {
			sb.append("Sources: ");
			for (int i = 0; i < sources.size(); i++) {
				if (i > 0) sb.append(", ");
				sb.append(sources.get(i).getTitle());
			}
		}

		if (hasPoiSources()) {
			if (sb.length() > 0) sb.append("\n");
			sb.append("Nearby: ");
			for (int i = 0; i < poiSources.size(); i++) {
				if (i > 0) sb.append(", ");
				sb.append(poiSources.get(i).toChatString());
			}
		}

		return sb.length() > 0 ? sb.toString() : "";
	}
}
