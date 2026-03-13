package net.osmand.plus.guides;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for GuideCategory enum.
 */
public class GuideCategoryTest {

	@Test
	public void testAllCategoriesHaveDisplayNames() {
		for (GuideCategory cat : GuideCategory.values()) {
			assertNotNull(cat.getDisplayName());
			assertFalse(cat.getDisplayName().isEmpty());
		}
	}

	@Test
	public void testCategoryCount() {
		assertEquals(11, GuideCategory.values().length);
	}

	@Test
	public void testSortOrderIsUnique() {
		int[] orders = new int[GuideCategory.values().length];
		for (int i = 0; i < GuideCategory.values().length; i++) {
			orders[i] = GuideCategory.values()[i].getSortOrder();
		}
		for (int i = 0; i < orders.length; i++) {
			for (int j = i + 1; j < orders.length; j++) {
				assertNotEquals("Duplicate sort order at indices " + i + " and " + j,
						orders[i], orders[j]);
			}
		}
	}

	@Test
	public void testFromStringExactMatch() {
		assertEquals(GuideCategory.FIRST_AID, GuideCategory.fromString("FIRST_AID"));
		assertEquals(GuideCategory.WATER, GuideCategory.fromString("WATER"));
		assertEquals(GuideCategory.FIRE, GuideCategory.fromString("FIRE"));
		assertEquals(GuideCategory.SHELTER, GuideCategory.fromString("SHELTER"));
		assertEquals(GuideCategory.NAVIGATION, GuideCategory.fromString("NAVIGATION"));
		assertEquals(GuideCategory.SIGNALING, GuideCategory.fromString("SIGNALING"));
		assertEquals(GuideCategory.FOOD, GuideCategory.fromString("FOOD"));
		assertEquals(GuideCategory.SECURITY, GuideCategory.fromString("SECURITY"));
	}

	@Test
	public void testFromStringCaseInsensitive() {
		assertEquals(GuideCategory.WATER, GuideCategory.fromString("water"));
		assertEquals(GuideCategory.FIRE, GuideCategory.fromString("Fire"));
		assertEquals(GuideCategory.SHELTER, GuideCategory.fromString("shelter"));
	}

	@Test
	public void testFromStringByDisplayName() {
		assertEquals(GuideCategory.FIRST_AID, GuideCategory.fromString("First Aid"));
		assertEquals(GuideCategory.FIRE, GuideCategory.fromString("Fire & Heat"));
	}

	@Test
	public void testFromStringNull() {
		assertEquals(GuideCategory.FIRST_AID, GuideCategory.fromString(null));
	}

	@Test
	public void testFromStringInvalid() {
		assertEquals(GuideCategory.FIRST_AID, GuideCategory.fromString("nonexistent"));
	}

	@Test
	public void testIconResIsNonZero() {
		for (GuideCategory cat : GuideCategory.values()) {
			assertNotEquals("Icon resource should be non-zero for " + cat.name(),
					0, cat.getIconRes());
		}
	}
}
