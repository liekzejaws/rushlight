package net.osmand.plus.security;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Method;

/**
 * Phase 16: Tests for EncryptedChatStorage schema and structure.
 * Validates the database helper has required methods for hardened operations.
 * Actual DB operations require SQLCipher + Android context (covered by integration tests).
 */
public class EncryptedChatStorageSchemaTest {

	@Test
	public void testClass_hasOnConfigureMethod() throws Exception {
		// Phase 16 added onConfigure() for PRAGMA foreign_keys = ON
		Method method = EncryptedChatStorage.class.getDeclaredMethod(
				"onConfigure", net.zetetic.database.sqlcipher.SQLiteDatabase.class);
		assertNotNull("Should have onConfigure() method for FK pragma", method);
	}

	@Test
	public void testClass_hasDeleteConversationMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod(
				"deleteConversation", long.class);
		assertNotNull(method);
		assertEquals("deleteConversation should return boolean",
				boolean.class, method.getReturnType());
	}

	@Test
	public void testClass_hasWipeAllMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod("wipeAll");
		assertNotNull(method);
	}

	@Test
	public void testClass_hasSaveMessageMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod(
				"saveMessage", long.class, net.osmand.plus.ai.ChatMessage.class);
		assertNotNull(method);
	}

	@Test
	public void testClass_hasGetMessagesForConversationMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod(
				"getMessagesForConversation", long.class);
		assertNotNull(method);
		assertEquals(java.util.List.class, method.getReturnType());
	}

	@Test
	public void testClass_hasClearMessagesForConversationMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod(
				"clearMessagesForConversation", long.class);
		assertNotNull(method);
	}

	@Test
	public void testClass_hasClearMessagesMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod("clearMessages");
		assertNotNull(method);
	}

	@Test
	public void testDefaultConversationId_isOne() {
		assertEquals(1L, EncryptedChatStorage.DEFAULT_CONVERSATION_ID);
	}

	@Test
	public void testClass_hasGetConversationsMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod("getConversations");
		assertNotNull(method);
	}

	@Test
	public void testClass_hasCreateConversationMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod(
				"createConversation", String.class);
		assertNotNull(method);
		assertEquals(long.class, method.getReturnType());
	}

	@Test
	public void testClass_hasOnUpgradeMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod(
				"onUpgrade",
				net.zetetic.database.sqlcipher.SQLiteDatabase.class,
				int.class,
				int.class);
		assertNotNull("Should have onUpgrade() for transaction-wrapped migration", method);
	}

	@Test
	public void testClass_hasOnCreateMethod() throws Exception {
		Method method = EncryptedChatStorage.class.getDeclaredMethod(
				"onCreate",
				net.zetetic.database.sqlcipher.SQLiteDatabase.class);
		assertNotNull("Should have onCreate() for CASCADE FK schema", method);
	}
}
