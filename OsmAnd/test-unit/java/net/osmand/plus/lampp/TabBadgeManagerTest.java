package net.osmand.plus.lampp;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for TabBadgeManager — notification badge system.
 */
public class TabBadgeManagerTest {

	private TabBadgeManager manager;

	@Before
	public void setUp() {
		manager = new TabBadgeManager();
	}

	@Test
	public void testInitialBadgeCountZero() {
		for (LamppTab tab : LamppTab.visibleTabs()) {
			assertEquals("Initial badge count should be 0 for " + tab.getTitle(),
					0, manager.getBadgeCount(tab));
		}
	}

	@Test
	public void testIncrementBadge() {
		manager.incrementBadge(LamppTab.P2P);
		assertEquals(1, manager.getBadgeCount(LamppTab.P2P));
	}

	@Test
	public void testMultipleIncrements() {
		manager.incrementBadge(LamppTab.MORSE);
		manager.incrementBadge(LamppTab.MORSE);
		manager.incrementBadge(LamppTab.MORSE);
		assertEquals(3, manager.getBadgeCount(LamppTab.MORSE));
	}

	@Test
	public void testClearBadge() {
		manager.incrementBadge(LamppTab.AI_CHAT);
		manager.incrementBadge(LamppTab.AI_CHAT);
		assertEquals(2, manager.getBadgeCount(LamppTab.AI_CHAT));

		manager.clearBadge(LamppTab.AI_CHAT);
		assertEquals(0, manager.getBadgeCount(LamppTab.AI_CHAT));
	}

	@Test
	public void testClearAlreadyZero() {
		// Should not throw or go negative
		manager.clearBadge(LamppTab.WIKI);
		assertEquals(0, manager.getBadgeCount(LamppTab.WIKI));
	}

	@Test
	public void testMultipleTabsIndependent() {
		manager.incrementBadge(LamppTab.P2P);
		manager.incrementBadge(LamppTab.P2P);
		manager.incrementBadge(LamppTab.MORSE);

		assertEquals(2, manager.getBadgeCount(LamppTab.P2P));
		assertEquals(1, manager.getBadgeCount(LamppTab.MORSE));
		assertEquals(0, manager.getBadgeCount(LamppTab.AI_CHAT));

		// Clearing one doesn't affect others
		manager.clearBadge(LamppTab.P2P);
		assertEquals(0, manager.getBadgeCount(LamppTab.P2P));
		assertEquals(1, manager.getBadgeCount(LamppTab.MORSE));
	}

	@Test
	public void testListenerNotifiedOnIncrement() {
		final List<Integer> counts = new ArrayList<>();
		manager.setListener((tab, newCount) -> counts.add(newCount));

		manager.incrementBadge(LamppTab.P2P);

		assertEquals(1, counts.size());
		assertEquals(Integer.valueOf(1), counts.get(0));
	}

	@Test
	public void testListenerNotifiedOnClear() {
		final List<Integer> counts = new ArrayList<>();

		manager.incrementBadge(LamppTab.MORSE);

		manager.setListener((tab, newCount) -> counts.add(newCount));
		manager.clearBadge(LamppTab.MORSE);

		assertEquals(1, counts.size());
		assertEquals(Integer.valueOf(0), counts.get(0));
	}

	@Test
	public void testNullListenerSafe() {
		manager.setListener(null);
		// Should not throw
		manager.incrementBadge(LamppTab.P2P);
		manager.clearBadge(LamppTab.P2P);
	}

	@Test
	public void testBadgeCountForAllVisibleTabs() {
		for (LamppTab tab : LamppTab.visibleTabs()) {
			manager.incrementBadge(tab);
			assertEquals(1, manager.getBadgeCount(tab));
		}
	}

	@Test
	public void testIncrementAfterClear() {
		manager.incrementBadge(LamppTab.TOOLS);
		manager.clearBadge(LamppTab.TOOLS);
		assertEquals(0, manager.getBadgeCount(LamppTab.TOOLS));

		manager.incrementBadge(LamppTab.TOOLS);
		assertEquals(1, manager.getBadgeCount(LamppTab.TOOLS));
	}

	@Test
	public void testClearAll() {
		manager.incrementBadge(LamppTab.P2P);
		manager.incrementBadge(LamppTab.MORSE);
		manager.incrementBadge(LamppTab.AI_CHAT);

		manager.clearAll();

		for (LamppTab tab : LamppTab.visibleTabs()) {
			assertEquals(0, manager.getBadgeCount(tab));
		}
	}

	@Test
	public void testListenerReceivesCorrectTab() {
		final List<LamppTab> tabs = new ArrayList<>();
		manager.setListener((tab, newCount) -> tabs.add(tab));

		manager.incrementBadge(LamppTab.P2P);
		manager.incrementBadge(LamppTab.MORSE);

		assertEquals(2, tabs.size());
		assertEquals(LamppTab.P2P, tabs.get(0));
		assertEquals(LamppTab.MORSE, tabs.get(1));
	}
}
