/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 12: SQLite database for persisting Morse code message history.
 * Messages survive app restarts — critical for survival comms.
 *
 * Schema: id, text, timestamp, type (SENT/RECEIVED), raw_morse (reserved)
 * Follows the TransferHistoryDatabase singleton pattern.
 */
public class MorseHistoryDatabase extends SQLiteOpenHelper {

	private static final Log LOG = PlatformUtil.getLog(MorseHistoryDatabase.class);

	private static final String DB_NAME = "morse_history.db";
	private static final int DB_VERSION = 1;

	private static final String TABLE = "morse_history";
	private static final String COL_ID = "id";
	private static final String COL_TEXT = "text";
	private static final String COL_TIMESTAMP = "timestamp";
	private static final String COL_TYPE = "type";
	private static final String COL_RAW_MORSE = "raw_morse";

	private static MorseHistoryDatabase instance;

	public static synchronized MorseHistoryDatabase getInstance(@NonNull Context context) {
		if (instance == null) {
			instance = new MorseHistoryDatabase(context.getApplicationContext());
		}
		return instance;
	}

	private MorseHistoryDatabase(@NonNull Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE + " ("
				+ COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COL_TEXT + " TEXT NOT NULL, "
				+ COL_TIMESTAMP + " INTEGER NOT NULL, "
				+ COL_TYPE + " TEXT NOT NULL, "
				+ COL_RAW_MORSE + " TEXT"
				+ ")");
		LOG.info("Morse history database created");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TABLE);
		onCreate(db);
	}

	/**
	 * Insert a Morse message into the database.
	 */
	public long insertMessage(@NonNull MorseMessage message) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(COL_TEXT, message.getText());
			values.put(COL_TIMESTAMP, message.getTimestamp());
			values.put(COL_TYPE, message.getType().name());
			long id = db.insert(TABLE, null, values);
			return id;
		} catch (Exception e) {
			LOG.error("Failed to insert Morse message: " + e.getMessage());
			return -1;
		}
	}

	/**
	 * Load the most recent messages (newest first in DB, returned in chronological order).
	 *
	 * @param limit Maximum number of messages to load
	 * @return Messages in chronological order (oldest first)
	 */
	@NonNull
	public List<MorseMessage> loadMessages(int limit) {
		List<MorseMessage> messages = new ArrayList<>();
		try {
			SQLiteDatabase db = getReadableDatabase();
			// Select newest N, then reverse for chronological order
			Cursor cursor = db.query(TABLE, null, null, null, null, null,
					COL_TIMESTAMP + " DESC", String.valueOf(limit));

			while (cursor.moveToNext()) {
				String text = cursor.getString(cursor.getColumnIndexOrThrow(COL_TEXT));
				long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
				String typeName = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE));

				MorseMessage.MessageType type;
				try {
					type = MorseMessage.MessageType.valueOf(typeName);
				} catch (IllegalArgumentException e) {
					type = MorseMessage.MessageType.RECEIVED;
				}

				messages.add(0, new MorseMessage(text, timestamp, type));
			}
			cursor.close();
		} catch (Exception e) {
			LOG.error("Failed to load Morse messages: " + e.getMessage());
		}
		return messages;
	}

	/**
	 * Get total message count.
	 */
	public int getMessageCount() {
		try {
			SQLiteDatabase db = getReadableDatabase();
			Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
			if (cursor.moveToFirst()) {
				int count = cursor.getInt(0);
				cursor.close();
				return count;
			}
			cursor.close();
		} catch (Exception e) {
			LOG.error("Failed to count Morse messages: " + e.getMessage());
		}
		return 0;
	}

	/**
	 * Clear all message history.
	 */
	public void clearAll() {
		try {
			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE, null, null);
			LOG.info("Morse history cleared");
		} catch (Exception e) {
			LOG.error("Failed to clear Morse history: " + e.getMessage());
		}
	}

	/**
	 * Delete messages older than the given timestamp.
	 */
	public int deleteOlderThan(long timestampMs) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			return db.delete(TABLE, COL_TIMESTAMP + " < ?",
					new String[]{String.valueOf(timestampMs)});
		} catch (Exception e) {
			LOG.error("Failed to delete old Morse messages: " + e.getMessage());
			return 0;
		}
	}
}
