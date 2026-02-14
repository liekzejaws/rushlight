package net.osmand.plus.security;

import android.content.ContentValues;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.plus.ai.ChatMessage;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Rushlight: SQLCipher-encrypted storage for AI chat messages and conversations.
 * Uses AES-256 encryption with passphrase derived from Android Keystore.
 *
 * DB Version History:
 * v1 - Single messages table (Phase 9B)
 * v2 - Multi-conversation support: conversations table + conversation_id on messages (Phase 14)
 */
public class EncryptedChatStorage extends SQLiteOpenHelper {

	private static final Log LOG = PlatformUtil.getLog(EncryptedChatStorage.class);

	private static final String DB_NAME = "rushlight_chat.db";
	private static final int DB_VERSION = 2;

	// Messages table
	private static final String TABLE_MESSAGES = "messages";
	private static final String COL_ID = "id";
	private static final String COL_ROLE = "role";
	private static final String COL_CONTENT = "content";
	private static final String COL_TIMESTAMP = "timestamp";
	private static final String COL_CONVERSATION_ID = "conversation_id";

	// Conversations table (v2)
	private static final String TABLE_CONVERSATIONS = "conversations";
	private static final String COL_CONV_ID = "id";
	private static final String COL_CONV_TITLE = "title";
	private static final String COL_CONV_SYSTEM_PROMPT = "system_prompt";
	private static final String COL_CONV_CREATED_AT = "created_at";
	private static final String COL_CONV_UPDATED_AT = "updated_at";

	/** Default conversation ID for legacy messages migrated from v1 */
	public static final long DEFAULT_CONVERSATION_ID = 1;

	private final Context context;

	public EncryptedChatStorage(@NonNull Context context, @NonNull String passphrase) {
		super(context, DB_NAME, passphrase, null, DB_VERSION, 0, null, null, true);
		this.context = context;
		// Load native SQLCipher libs
		System.loadLibrary("sqlcipher");
	}

	@Override
	public void onConfigure(SQLiteDatabase db) {
		super.onConfigure(db);
		// Phase 16: Enable foreign key enforcement (SQLite does not enforce FKs by default)
		db.execSQL("PRAGMA foreign_keys = ON");
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// Create conversations table first (v2 schema from scratch)
		db.execSQL("CREATE TABLE " + TABLE_CONVERSATIONS + " ("
				+ COL_CONV_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COL_CONV_TITLE + " TEXT NOT NULL, "
				+ COL_CONV_SYSTEM_PROMPT + " TEXT, "
				+ COL_CONV_CREATED_AT + " INTEGER NOT NULL, "
				+ COL_CONV_UPDATED_AT + " INTEGER NOT NULL"
				+ ")");

		// Create messages table with conversation_id foreign key + cascade delete (Phase 16)
		db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
				+ COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COL_ROLE + " INTEGER NOT NULL, "
				+ COL_CONTENT + " TEXT NOT NULL, "
				+ COL_TIMESTAMP + " INTEGER NOT NULL, "
				+ COL_CONVERSATION_ID + " INTEGER DEFAULT " + DEFAULT_CONVERSATION_ID
				+ " REFERENCES " + TABLE_CONVERSATIONS + "(" + COL_CONV_ID + ") ON DELETE CASCADE"
				+ ")");

		db.execSQL("CREATE INDEX idx_messages_conversation ON "
				+ TABLE_MESSAGES + "(" + COL_CONVERSATION_ID + ")");

		// Insert default conversation
		long now = System.currentTimeMillis();
		ContentValues defaultConv = new ContentValues();
		defaultConv.put(COL_CONV_TITLE, "Default");
		defaultConv.put(COL_CONV_CREATED_AT, now);
		defaultConv.put(COL_CONV_UPDATED_AT, now);
		db.insert(TABLE_CONVERSATIONS, null, defaultConv);

		LOG.info("Rushlight: Created encrypted chat database v2 with conversations");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2) {
			migrateV1toV2(db);
		}
	}

	/**
	 * Migrate from v1 (messages only) to v2 (multi-conversation).
	 */
	private void migrateV1toV2(SQLiteDatabase db) {
		LOG.info("Rushlight: Migrating chat database v1 → v2");

		// Phase 16: Wrap migration in transaction for atomicity
		db.beginTransaction();
		try {
			// Create conversations table
			db.execSQL("CREATE TABLE " + TABLE_CONVERSATIONS + " ("
					+ COL_CONV_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ COL_CONV_TITLE + " TEXT NOT NULL, "
					+ COL_CONV_SYSTEM_PROMPT + " TEXT, "
					+ COL_CONV_CREATED_AT + " INTEGER NOT NULL, "
					+ COL_CONV_UPDATED_AT + " INTEGER NOT NULL"
					+ ")");

			// Compute timestamps from existing messages
			long minTimestamp = System.currentTimeMillis();
			long maxTimestamp = minTimestamp;
			try {
				android.database.Cursor cursor = db.rawQuery(
						"SELECT MIN(" + COL_TIMESTAMP + "), MAX(" + COL_TIMESTAMP + ") FROM " + TABLE_MESSAGES, null);
				if (cursor.moveToFirst()) {
					long min = cursor.getLong(0);
					long max = cursor.getLong(1);
					if (min > 0) minTimestamp = min;
					if (max > 0) maxTimestamp = max;
				}
				cursor.close();
			} catch (Exception e) {
				LOG.error("Failed to get message timestamps for migration: " + e.getMessage());
			}

			// Insert default conversation with ID 1
			ContentValues defaultConv = new ContentValues();
			defaultConv.put(COL_CONV_TITLE, "Default");
			defaultConv.put(COL_CONV_CREATED_AT, minTimestamp);
			defaultConv.put(COL_CONV_UPDATED_AT, maxTimestamp);
			db.insert(TABLE_CONVERSATIONS, null, defaultConv);

			// Add conversation_id column to messages, defaulting to 1
			db.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN "
					+ COL_CONVERSATION_ID + " INTEGER DEFAULT " + DEFAULT_CONVERSATION_ID
					+ " REFERENCES " + TABLE_CONVERSATIONS + "(" + COL_CONV_ID + ")");

			// Create index
			db.execSQL("CREATE INDEX idx_messages_conversation ON "
					+ TABLE_MESSAGES + "(" + COL_CONVERSATION_ID + ")");

			db.setTransactionSuccessful();
			LOG.info("Rushlight: Migration v1 → v2 complete");
		} finally {
			db.endTransaction();
		}
	}

	// ==================== Conversation CRUD (v2) ====================

	/**
	 * Data class for conversation metadata.
	 */
	public static class Conversation {
		public final long id;
		public final String title;
		@Nullable
		public final String systemPrompt;
		public final long createdAt;
		public final long updatedAt;
		public int messageCount;

		public Conversation(long id, String title, @Nullable String systemPrompt,
		                    long createdAt, long updatedAt) {
			this.id = id;
			this.title = title;
			this.systemPrompt = systemPrompt;
			this.createdAt = createdAt;
			this.updatedAt = updatedAt;
		}
	}

	/**
	 * Create a new conversation and return its ID.
	 */
	public long createConversation(@NonNull String title) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			long now = System.currentTimeMillis();
			ContentValues values = new ContentValues();
			values.put(COL_CONV_TITLE, title);
			values.put(COL_CONV_CREATED_AT, now);
			values.put(COL_CONV_UPDATED_AT, now);
			long id = db.insert(TABLE_CONVERSATIONS, null, values);
			LOG.info("Rushlight: Created conversation '" + title + "' with id=" + id);
			return id;
		} catch (Exception e) {
			LOG.error("Failed to create conversation: " + e.getMessage());
			return -1;
		}
	}

	/**
	 * Get all conversations, sorted by last updated (most recent first).
	 */
	@NonNull
	public List<Conversation> getConversations() {
		List<Conversation> conversations = new ArrayList<>();
		try {
			SQLiteDatabase db = getReadableDatabase();
			android.database.Cursor cursor = db.query(
					TABLE_CONVERSATIONS,
					new String[]{COL_CONV_ID, COL_CONV_TITLE, COL_CONV_SYSTEM_PROMPT,
							COL_CONV_CREATED_AT, COL_CONV_UPDATED_AT},
					null, null, null, null,
					COL_CONV_UPDATED_AT + " DESC"
			);

			while (cursor.moveToNext()) {
				long id = cursor.getLong(0);
				String title = cursor.getString(1);
				String systemPrompt = cursor.isNull(2) ? null : cursor.getString(2);
				long createdAt = cursor.getLong(3);
				long updatedAt = cursor.getLong(4);
				Conversation conv = new Conversation(id, title, systemPrompt, createdAt, updatedAt);
				conversations.add(conv);
			}
			cursor.close();

			// Load message counts
			for (Conversation conv : conversations) {
				conv.messageCount = getMessageCountForConversation(conv.id);
			}

		} catch (Exception e) {
			LOG.error("Failed to load conversations: " + e.getMessage());
		}
		return conversations;
	}

	/**
	 * Get a single conversation by ID.
	 */
	@Nullable
	public Conversation getConversation(long conversationId) {
		try {
			SQLiteDatabase db = getReadableDatabase();
			android.database.Cursor cursor = db.query(
					TABLE_CONVERSATIONS,
					new String[]{COL_CONV_ID, COL_CONV_TITLE, COL_CONV_SYSTEM_PROMPT,
							COL_CONV_CREATED_AT, COL_CONV_UPDATED_AT},
					COL_CONV_ID + " = ?",
					new String[]{String.valueOf(conversationId)},
					null, null, null
			);

			Conversation conv = null;
			if (cursor.moveToFirst()) {
				conv = new Conversation(
						cursor.getLong(0),
						cursor.getString(1),
						cursor.isNull(2) ? null : cursor.getString(2),
						cursor.getLong(3),
						cursor.getLong(4)
				);
				conv.messageCount = getMessageCountForConversation(conversationId);
			}
			cursor.close();
			return conv;
		} catch (Exception e) {
			LOG.error("Failed to get conversation " + conversationId + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Rename a conversation.
	 */
	public boolean renameConversation(long conversationId, @NonNull String newTitle) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(COL_CONV_TITLE, newTitle);
			int rows = db.update(TABLE_CONVERSATIONS, values,
					COL_CONV_ID + " = ?", new String[]{String.valueOf(conversationId)});
			return rows > 0;
		} catch (Exception e) {
			LOG.error("Failed to rename conversation: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Delete a conversation and all its messages.
	 */
	public boolean deleteConversation(long conversationId) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			// Phase 16: Wrap in transaction for atomicity
			db.beginTransaction();
			try {
				// Delete messages first (FK constraint — also handled by CASCADE on fresh installs)
				db.delete(TABLE_MESSAGES, COL_CONVERSATION_ID + " = ?",
						new String[]{String.valueOf(conversationId)});
				// Delete conversation
				int rows = db.delete(TABLE_CONVERSATIONS, COL_CONV_ID + " = ?",
						new String[]{String.valueOf(conversationId)});
				db.setTransactionSuccessful();
				LOG.info("Rushlight: Deleted conversation " + conversationId);
				return rows > 0;
			} finally {
				db.endTransaction();
			}
		} catch (Exception e) {
			LOG.error("Failed to delete conversation: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Set the system prompt for a conversation.
	 */
	public void setConversationSystemPrompt(long conversationId, @Nullable String systemPrompt) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = new ContentValues();
			if (systemPrompt != null) {
				values.put(COL_CONV_SYSTEM_PROMPT, systemPrompt);
			} else {
				values.putNull(COL_CONV_SYSTEM_PROMPT);
			}
			db.update(TABLE_CONVERSATIONS, values,
					COL_CONV_ID + " = ?", new String[]{String.valueOf(conversationId)});
		} catch (Exception e) {
			LOG.error("Failed to set system prompt: " + e.getMessage());
		}
	}

	/**
	 * Get the system prompt for a conversation.
	 */
	@Nullable
	public String getConversationSystemPrompt(long conversationId) {
		try {
			SQLiteDatabase db = getReadableDatabase();
			android.database.Cursor cursor = db.query(
					TABLE_CONVERSATIONS,
					new String[]{COL_CONV_SYSTEM_PROMPT},
					COL_CONV_ID + " = ?",
					new String[]{String.valueOf(conversationId)},
					null, null, null
			);

			String prompt = null;
			if (cursor.moveToFirst() && !cursor.isNull(0)) {
				prompt = cursor.getString(0);
			}
			cursor.close();
			return prompt;
		} catch (Exception e) {
			LOG.error("Failed to get system prompt: " + e.getMessage());
			return null;
		}
	}

	// ==================== Message Operations ====================

	/**
	 * Save a chat message to the specified conversation.
	 */
	public void saveMessage(long conversationId, @NonNull ChatMessage message) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(COL_ROLE, message.role);
			values.put(COL_CONTENT, message.content);
			values.put(COL_TIMESTAMP, message.timestamp);
			values.put(COL_CONVERSATION_ID, conversationId);
			db.insert(TABLE_MESSAGES, null, values);

			// Update conversation's updated_at timestamp
			updateConversationTimestamp(db, conversationId);
		} catch (Exception e) {
			LOG.error("Failed to save chat message: " + e.getMessage());
		}
	}

	/**
	 * Save a chat message to the default conversation (backwards compatible).
	 */
	public void saveMessage(@NonNull ChatMessage message) {
		saveMessage(DEFAULT_CONVERSATION_ID, message);
	}

	/**
	 * Load messages for a specific conversation.
	 */
	@NonNull
	public List<ChatMessage> getMessagesForConversation(long conversationId) {
		List<ChatMessage> messages = new ArrayList<>();
		try {
			SQLiteDatabase db = getReadableDatabase();
			android.database.Cursor cursor = db.query(
					TABLE_MESSAGES,
					new String[]{COL_ROLE, COL_CONTENT, COL_TIMESTAMP},
					COL_CONVERSATION_ID + " = ?",
					new String[]{String.valueOf(conversationId)},
					null, null,
					COL_ID + " ASC"
			);

			while (cursor.moveToNext()) {
				int role = cursor.getInt(0);
				String content = cursor.getString(1);
				long timestamp = cursor.getLong(2);
				messages.add(new ChatMessage(role, content, timestamp));
			}
			cursor.close();
		} catch (Exception e) {
			LOG.error("Failed to load messages for conversation " + conversationId + ": " + e.getMessage());
		}
		return messages;
	}

	/**
	 * Load all chat messages from encrypted storage (default conversation).
	 * Backwards compatible with v1 callers.
	 */
	@NonNull
	public List<ChatMessage> loadAllMessages() {
		return getMessagesForConversation(DEFAULT_CONVERSATION_ID);
	}

	/**
	 * Get total message count across all conversations.
	 */
	public int getMessageCount() {
		try {
			SQLiteDatabase db = getReadableDatabase();
			android.database.Cursor cursor = db.rawQuery(
					"SELECT COUNT(*) FROM " + TABLE_MESSAGES, null);
			if (cursor.moveToFirst()) {
				int count = cursor.getInt(0);
				cursor.close();
				return count;
			}
			cursor.close();
		} catch (Exception e) {
			LOG.error("Failed to count messages: " + e.getMessage());
		}
		return 0;
	}

	/**
	 * Get message count for a specific conversation.
	 */
	public int getMessageCountForConversation(long conversationId) {
		try {
			SQLiteDatabase db = getReadableDatabase();
			android.database.Cursor cursor = db.rawQuery(
					"SELECT COUNT(*) FROM " + TABLE_MESSAGES
							+ " WHERE " + COL_CONVERSATION_ID + " = ?",
					new String[]{String.valueOf(conversationId)});
			if (cursor.moveToFirst()) {
				int count = cursor.getInt(0);
				cursor.close();
				return count;
			}
			cursor.close();
		} catch (Exception e) {
			LOG.error("Failed to count messages for conversation: " + e.getMessage());
		}
		return 0;
	}

	/**
	 * Get the conversation count.
	 */
	public int getConversationCount() {
		try {
			SQLiteDatabase db = getReadableDatabase();
			android.database.Cursor cursor = db.rawQuery(
					"SELECT COUNT(*) FROM " + TABLE_CONVERSATIONS, null);
			if (cursor.moveToFirst()) {
				int count = cursor.getInt(0);
				cursor.close();
				return count;
			}
			cursor.close();
		} catch (Exception e) {
			LOG.error("Failed to count conversations: " + e.getMessage());
		}
		return 0;
	}

	/**
	 * Update a conversation's updated_at timestamp.
	 */
	private void updateConversationTimestamp(SQLiteDatabase db, long conversationId) {
		ContentValues values = new ContentValues();
		values.put(COL_CONV_UPDATED_AT, System.currentTimeMillis());
		db.update(TABLE_CONVERSATIONS, values,
				COL_CONV_ID + " = ?", new String[]{String.valueOf(conversationId)});
	}

	// ==================== Wipe / Clear ====================

	/**
	 * Delete all messages and conversations, then destroy the database file.
	 * Called during panic wipe.
	 */
	public void wipeAll() {
		try {
			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE_MESSAGES, null, null);
			db.delete(TABLE_CONVERSATIONS, null, null);
			db.close();
		} catch (Exception e) {
			LOG.error("Error clearing data: " + e.getMessage());
		}

		// Delete the database file entirely
		context.deleteDatabase(DB_NAME);
		LOG.warn("Rushlight: Chat database wiped and deleted");
	}

	/**
	 * Clear all messages but keep the database file and conversations.
	 */
	public void clearMessages() {
		try {
			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE_MESSAGES, null, null);
			LOG.info("Rushlight: Cleared all chat messages");
		} catch (Exception e) {
			LOG.error("Failed to clear messages: " + e.getMessage());
		}
	}

	/**
	 * Clear messages for a specific conversation.
	 */
	public void clearMessagesForConversation(long conversationId) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE_MESSAGES, COL_CONVERSATION_ID + " = ?",
					new String[]{String.valueOf(conversationId)});
			LOG.info("Rushlight: Cleared messages for conversation " + conversationId);
		} catch (Exception e) {
			LOG.error("Failed to clear conversation messages: " + e.getMessage());
		}
	}
}
