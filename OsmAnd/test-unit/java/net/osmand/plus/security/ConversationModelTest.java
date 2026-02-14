package net.osmand.plus.security;

import net.osmand.plus.ai.ChatMessage;
import net.osmand.plus.security.EncryptedChatStorage.Conversation;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Phase 14: Tests for conversation model and chat message association.
 * Tests the data model used by EncryptedChatStorage v2.
 *
 * Note: Actual database operations can't be tested in JVM unit tests
 * because EncryptedChatStorage requires SQLCipher native libraries.
 * These tests verify the model structure and constants.
 */
public class ConversationModelTest {

	@Test
	public void testDefaultConversationIdIsOne() {
		assertEquals(1L, EncryptedChatStorage.DEFAULT_CONVERSATION_ID);
	}

	@Test
	public void testConversationConstructor() {
		Conversation conv = new Conversation(1L, "Test", null, 1000L, 2000L);
		assertEquals(1L, conv.id);
		assertEquals("Test", conv.title);
		assertNull(conv.systemPrompt);
		assertEquals(1000L, conv.createdAt);
		assertEquals(2000L, conv.updatedAt);
		assertEquals(0, conv.messageCount); // Default
	}

	@Test
	public void testConversationWithSystemPrompt() {
		Conversation conv = new Conversation(2L, "Emergency Plans",
				"You are a survival expert.", 1000L, 2000L);
		assertEquals(2L, conv.id);
		assertEquals("Emergency Plans", conv.title);
		assertEquals("You are a survival expert.", conv.systemPrompt);
	}

	@Test
	public void testConversationMessageCount() {
		Conversation conv = new Conversation(1L, "Test", null, 1000L, 2000L);
		conv.messageCount = 42;
		assertEquals(42, conv.messageCount);
	}

	@Test
	public void testConversationWithNullSystemPrompt() {
		Conversation conv = new Conversation(1L, "Test", null, 1000L, 2000L);
		assertNull(conv.systemPrompt);
	}

	@Test
	public void testConversationWithEmptySystemPrompt() {
		Conversation conv = new Conversation(1L, "Test", "", 1000L, 2000L);
		assertEquals("", conv.systemPrompt);
	}

	// ---- ChatMessage association ----

	@Test
	public void testChatMessageRoles() {
		assertEquals(0, ChatMessage.ROLE_USER);
		assertEquals(1, ChatMessage.ROLE_AI);
		assertEquals(2, ChatMessage.ROLE_SYSTEM);
	}

	@Test
	public void testChatMessageTimestamp() {
		long before = System.currentTimeMillis();
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_USER, "Hello");
		long after = System.currentTimeMillis();

		assertTrue("Timestamp should be set on creation",
				msg.timestamp >= before && msg.timestamp <= after);
	}

	@Test
	public void testChatMessageExplicitTimestamp() {
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_AI, "Response", 12345L);
		assertEquals(12345L, msg.timestamp);
	}

	@Test
	public void testChatMessageSources() {
		ChatMessage msg = new ChatMessage(ChatMessage.ROLE_AI, "Test");
		assertFalse(msg.hasSources());
		assertFalse(msg.hasPoiSources());
		assertFalse(msg.hasAnySources());
		assertEquals("", msg.getSourcesText());
	}

	@Test
	public void testMultipleConversationsDistinct() {
		Conversation conv1 = new Conversation(1L, "Default", null, 1000L, 2000L);
		Conversation conv2 = new Conversation(2L, "Emergency Plans", "Expert mode", 1500L, 2500L);

		assertNotEquals(conv1.id, conv2.id);
		assertNotEquals(conv1.title, conv2.title);
		assertNull(conv1.systemPrompt);
		assertNotNull(conv2.systemPrompt);
	}
}
