package net.osmand.plus.security;

import android.content.ContentValues;
import android.content.Context;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;
import net.osmand.plus.ai.ChatMessage;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Rushlight: SQLCipher-encrypted storage for AI chat messages.
 * Uses AES-256 encryption with passphrase derived from Android Keystore.
 */
public class EncryptedChatStorage extends SQLiteOpenHelper {

	private static final Log LOG = PlatformUtil.getLog(EncryptedChatStorage.class);

	private static final String DB_NAME = "rushlight_chat.db";
	private static final int DB_VERSION = 1;

	private static final String TABLE_MESSAGES = "messages";
	private static final String COL_ID = "id";
	private static final String COL_ROLE = "role";
	private static final String COL_CONTENT = "content";
	private static final String COL_TIMESTAMP = "timestamp";

	private final Context context;

	public EncryptedChatStorage(@NonNull Context context, @NonNull String passphrase) {
		super(context, DB_NAME, passphrase, null, DB_VERSION, 0, null, null, true);
		this.context = context;
		// Load native SQLCipher libs
		System.loadLibrary("sqlcipher");
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
				+ COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COL_ROLE + " INTEGER NOT NULL, "
				+ COL_CONTENT + " TEXT NOT NULL, "
				+ COL_TIMESTAMP + " INTEGER NOT NULL"
				+ ")");
		LOG.info("Rushlight: Created encrypted chat database");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Future migrations go here
	}

	/**
	 * Save a chat message to encrypted storage.
	 */
	public void saveMessage(@NonNull ChatMessage message) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(COL_ROLE, message.role);
			values.put(COL_CONTENT, message.content);
			values.put(COL_TIMESTAMP, message.timestamp);
			db.insert(TABLE_MESSAGES, null, values);
		} catch (Exception e) {
			LOG.error("Failed to save chat message: " + e.getMessage());
		}
	}

	/**
	 * Load all chat messages from encrypted storage, ordered by timestamp.
	 */
	@NonNull
	public List<ChatMessage> loadAllMessages() {
		List<ChatMessage> messages = new ArrayList<>();
		try {
			SQLiteDatabase db = getReadableDatabase();
			android.database.Cursor cursor = db.query(
					TABLE_MESSAGES,
					new String[]{COL_ROLE, COL_CONTENT, COL_TIMESTAMP},
					null, null, null, null,
					COL_ID + " ASC"
			);

			while (cursor.moveToNext()) {
				int role = cursor.getInt(0);
				String content = cursor.getString(1);
				long timestamp = cursor.getLong(2);
				messages.add(new ChatMessage(role, content, timestamp));
			}
			cursor.close();

			LOG.info("Rushlight: Loaded " + messages.size() + " messages from encrypted storage");
		} catch (Exception e) {
			LOG.error("Failed to load chat messages: " + e.getMessage());
		}
		return messages;
	}

	/**
	 * Get total message count.
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
	 * Delete all messages and destroy the database file.
	 * Called during panic wipe.
	 */
	public void wipeAll() {
		try {
			// Delete all rows
			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE_MESSAGES, null, null);
			db.close();
		} catch (Exception e) {
			LOG.error("Error clearing messages: " + e.getMessage());
		}

		// Delete the database file entirely
		context.deleteDatabase(DB_NAME);
		LOG.warn("Rushlight: Chat database wiped and deleted");
	}

	/**
	 * Clear all messages but keep the database file.
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
}
