package net.osmand.plus.lampp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 12: Manages notification badge counts per tab.
 *
 * Used to show small badge dots on tab icons when events occur on inactive tabs
 * (e.g., Morse message received while on AI Chat tab, peer discovered while on Wiki tab).
 *
 * The LamppSideTabBar reads badge counts from this manager and renders them.
 */
public class TabBadgeManager {

	private static final Log LOG = PlatformUtil.getLog(TabBadgeManager.class);

	/**
	 * Listener for badge count changes.
	 */
	public interface BadgeChangeListener {
		void onBadgeChanged(@NonNull LamppTab tab, int newCount);
	}

	private final Map<LamppTab, Integer> badgeCounts = new HashMap<>();
	@Nullable
	private BadgeChangeListener listener;

	/**
	 * Set the listener for badge change events.
	 */
	public void setListener(@Nullable BadgeChangeListener listener) {
		this.listener = listener;
	}

	/**
	 * Increment the badge count for a tab.
	 */
	public void incrementBadge(@NonNull LamppTab tab) {
		int current = getBadgeCount(tab);
		int newCount = current + 1;
		badgeCounts.put(tab, newCount);
		LOG.info("Badge incremented: " + tab.getTitle() + " = " + newCount);
		notifyListener(tab, newCount);
	}

	/**
	 * Clear the badge for a tab (set to 0).
	 */
	public void clearBadge(@NonNull LamppTab tab) {
		if (getBadgeCount(tab) > 0) {
			badgeCounts.put(tab, 0);
			LOG.info("Badge cleared: " + tab.getTitle());
			notifyListener(tab, 0);
		}
	}

	/**
	 * Get the current badge count for a tab.
	 */
	public int getBadgeCount(@NonNull LamppTab tab) {
		Integer count = badgeCounts.get(tab);
		return count != null ? count : 0;
	}

	/**
	 * Clear all badges.
	 */
	public void clearAll() {
		for (LamppTab tab : LamppTab.visibleTabs()) {
			if (getBadgeCount(tab) > 0) {
				badgeCounts.put(tab, 0);
				notifyListener(tab, 0);
			}
		}
	}

	private void notifyListener(@NonNull LamppTab tab, int newCount) {
		if (listener != null) {
			listener.onBadgeChanged(tab, newCount);
		}
	}
}
