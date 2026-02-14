package net.osmand.plus.ai;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Phase 16: Tests for ChatMessage role constants used as RecyclerView view types.
 * Validates that view type values are distinct and match expected constants.
 */
public class ChatAdapterViewTypeTest {

	@Test
	public void testRoleUser_isZero() {
		assertEquals(0, ChatMessage.ROLE_USER);
	}

	@Test
	public void testRoleAi_isOne() {
		assertEquals(1, ChatMessage.ROLE_AI);
	}

	@Test
	public void testRoleSystem_isTwo() {
		assertEquals(2, ChatMessage.ROLE_SYSTEM);
	}

	@Test
	public void testRoles_areDistinct() {
		assertNotEquals(ChatMessage.ROLE_USER, ChatMessage.ROLE_AI);
		assertNotEquals(ChatMessage.ROLE_USER, ChatMessage.ROLE_SYSTEM);
		assertNotEquals(ChatMessage.ROLE_AI, ChatMessage.ROLE_SYSTEM);
	}

	@Test
	public void testUserMessage_roleMatchesViewType() {
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_USER, "Hello");
		assertEquals(ChatMessage.ROLE_USER, msg.role);
	}

	@Test
	public void testAiMessage_roleMatchesViewType() {
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_AI, "Response");
		assertEquals(ChatMessage.ROLE_AI, msg.role);
	}

	@Test
	public void testSystemMessage_roleMatchesViewType() {
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_SYSTEM, "Error");
		assertEquals(ChatMessage.ROLE_SYSTEM, msg.role);
	}

	@Test
	public void testChatMessage_preservesContent() {
		String text = "Test message content";
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_USER, text);
		assertEquals(text, msg.content);
	}

	@Test
	public void testChatMessage_emptyContent() {
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_AI, "");
		assertEquals("", msg.content);
	}

	@Test
	public void testChatMessage_defaultSources() {
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_AI, "Response");
		assertNull(msg.sources);
		assertNull(msg.poiSources);
	}

	@Test
	public void testRoleValues_nonNegative() {
		assertTrue(ChatMessage.ROLE_USER >= 0);
		assertTrue(ChatMessage.ROLE_AI >= 0);
		assertTrue(ChatMessage.ROLE_SYSTEM >= 0);
	}

	@Test
	public void testRoleValues_consecutive() {
		assertEquals(ChatMessage.ROLE_USER + 1, ChatMessage.ROLE_AI);
		assertEquals(ChatMessage.ROLE_AI + 1, ChatMessage.ROLE_SYSTEM);
	}
}
