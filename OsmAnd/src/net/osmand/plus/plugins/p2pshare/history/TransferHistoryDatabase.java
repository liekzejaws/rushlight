/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.plugins.p2pshare.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database for logging P2P transfer history.
 * Uses standard (unencrypted) SQLite — transfer history contains no secrets.
 */
public class TransferHistoryDatabase extends SQLiteOpenHelper {

	private static final Log LOG = PlatformUtil.getLog(TransferHistoryDatabase.class);

	private static final String DB_NAME = "p2p_transfer_history.db";
	private static final int DB_VERSION = 1;

	private static final String TABLE = "transfer_history";
	private static final String COL_ID = "id";
	private static final String COL_FILENAME = "filename";
	private static final String COL_FILE_SIZE = "file_size";
	private static final String COL_BYTES_TRANSFERRED = "bytes_transferred";
	private static final String COL_PEER_NAME = "peer_name";
	private static final String COL_PEER_ID = "peer_id";
	private static final String COL_TRANSPORT = "transport";
	private static final String COL_DIRECTION = "direction";
	private static final String COL_STATUS = "status";
	private static final String COL_ERROR = "error";
	private static final String COL_START_TIME = "start_time";
	private static final String COL_END_TIME = "end_time";
	private static final String COL_SPEED_KBPS = "speed_kbps";
	private static final String COL_CHECKSUM_OK = "checksum_ok";
	private static final String COL_CONTENT_TYPE = "content_type";

	private static TransferHistoryDatabase instance;

	public static synchronized TransferHistoryDatabase getInstance(@NonNull Context context) {
		if (instance == null) {
			instance = new TransferHistoryDatabase(context.getApplicationContext());
		}
		return instance;
	}

	private TransferHistoryDatabase(@NonNull Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE + " ("
				+ COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ COL_FILENAME + " TEXT NOT NULL, "
				+ COL_FILE_SIZE + " INTEGER NOT NULL, "
				+ COL_BYTES_TRANSFERRED + " INTEGER NOT NULL DEFAULT 0, "
				+ COL_PEER_NAME + " TEXT, "
				+ COL_PEER_ID + " TEXT, "
				+ COL_TRANSPORT + " TEXT, "
				+ COL_DIRECTION + " TEXT NOT NULL, "
				+ COL_STATUS + " TEXT NOT NULL, "
				+ COL_ERROR + " TEXT, "
				+ COL_START_TIME + " INTEGER NOT NULL, "
				+ COL_END_TIME + " INTEGER, "
				+ COL_SPEED_KBPS + " REAL, "
				+ COL_CHECKSUM_OK + " INTEGER DEFAULT -1, "
				+ COL_CONTENT_TYPE + " TEXT"
				+ ")");
		LOG.info("P2P transfer history database created");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Future migrations go here
	}

	/**
	 * Insert a new transfer record. Returns the new row ID.
	 */
	public long insertRecord(@NonNull TransferRecord record) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = toContentValues(record);
			long id = db.insert(TABLE, null, values);
			record.setId(id);
			LOG.debug("Transfer history: inserted record #" + id + " " + record.getFilename());
			return id;
		} catch (Exception e) {
			LOG.error("Failed to insert transfer record: " + e.getMessage());
			return -1;
		}
	}

	/**
	 * Update an existing transfer record (e.g., progress, completion).
	 */
	public void updateRecord(@NonNull TransferRecord record) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = toContentValues(record);
			db.update(TABLE, values, COL_ID + " = ?",
					new String[]{String.valueOf(record.getId())});
		} catch (Exception e) {
			LOG.error("Failed to update transfer record: " + e.getMessage());
		}
	}

	/**
	 * Get all transfer records, most recent first.
	 */
	@NonNull
	public List<TransferRecord> getAllRecords() {
		return getRecords(null, null, 200);
	}

	/**
	 * Get recent transfer records with a limit.
	 */
	@NonNull
	public List<TransferRecord> getRecentRecords(int limit) {
		return getRecords(null, null, limit);
	}

	/**
	 * Get records filtered by status.
	 */
	@NonNull
	public List<TransferRecord> getRecordsByStatus(@NonNull String status) {
		return getRecords(COL_STATUS + " = ?", new String[]{status}, 200);
	}

	/**
	 * Get records filtered by direction.
	 */
	@NonNull
	public List<TransferRecord> getRecordsByDirection(@NonNull String direction) {
		return getRecords(COL_DIRECTION + " = ?", new String[]{direction}, 200);
	}

	/**
	 * Get a single record by ID.
	 */
	@Nullable
	public TransferRecord getRecord(long id) {
		List<TransferRecord> records = getRecords(
				COL_ID + " = ?", new String[]{String.valueOf(id)}, 1);
		return records.isEmpty() ? null : records.get(0);
	}

	/**
	 * Get total number of transfer records.
	 */
	public int getRecordCount() {
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
			LOG.error("Failed to count transfer records: " + e.getMessage());
		}
		return 0;
	}

	/**
	 * Get total bytes transferred (successful transfers only).
	 */
	public long getTotalBytesTransferred() {
		try {
			SQLiteDatabase db = getReadableDatabase();
			Cursor cursor = db.rawQuery(
					"SELECT SUM(" + COL_BYTES_TRANSFERRED + ") FROM " + TABLE
							+ " WHERE " + COL_STATUS + " = ?",
					new String[]{TransferRecord.STATUS_SUCCESS});
			if (cursor.moveToFirst()) {
				long total = cursor.getLong(0);
				cursor.close();
				return total;
			}
			cursor.close();
		} catch (Exception e) {
			LOG.error("Failed to sum bytes transferred: " + e.getMessage());
		}
		return 0;
	}

	/**
	 * Delete all transfer history.
	 */
	public void clearHistory() {
		try {
			SQLiteDatabase db = getWritableDatabase();
			db.delete(TABLE, null, null);
			LOG.info("Transfer history cleared");
		} catch (Exception e) {
			LOG.error("Failed to clear transfer history: " + e.getMessage());
		}
	}

	/**
	 * Delete records older than the specified timestamp.
	 */
	public int deleteOlderThan(long timestampMs) {
		try {
			SQLiteDatabase db = getWritableDatabase();
			int deleted = db.delete(TABLE, COL_START_TIME + " < ?",
					new String[]{String.valueOf(timestampMs)});
			LOG.info("Deleted " + deleted + " old transfer records");
			return deleted;
		} catch (Exception e) {
			LOG.error("Failed to delete old records: " + e.getMessage());
			return 0;
		}
	}

	// Private helpers

	@NonNull
	private List<TransferRecord> getRecords(@Nullable String selection,
	                                         @Nullable String[] selectionArgs,
	                                         int limit) {
		List<TransferRecord> records = new ArrayList<>();
		try {
			SQLiteDatabase db = getReadableDatabase();
			Cursor cursor = db.query(TABLE,
					null, // all columns
					selection,
					selectionArgs,
					null, null,
					COL_START_TIME + " DESC",
					String.valueOf(limit));

			while (cursor.moveToNext()) {
				records.add(fromCursor(cursor));
			}
			cursor.close();
		} catch (Exception e) {
			LOG.error("Failed to query transfer records: " + e.getMessage());
		}
		return records;
	}

	@NonNull
	private ContentValues toContentValues(@NonNull TransferRecord record) {
		ContentValues values = new ContentValues();
		values.put(COL_FILENAME, record.getFilename());
		values.put(COL_FILE_SIZE, record.getFileSize());
		values.put(COL_BYTES_TRANSFERRED, record.getBytesTransferred());
		values.put(COL_PEER_NAME, record.getPeerName());
		values.put(COL_PEER_ID, record.getPeerId());
		values.put(COL_TRANSPORT, record.getTransport());
		values.put(COL_DIRECTION, record.getDirection());
		values.put(COL_STATUS, record.getStatus());
		values.put(COL_ERROR, record.getError());
		values.put(COL_START_TIME, record.getStartTime());
		values.put(COL_END_TIME, record.getEndTime());
		values.put(COL_SPEED_KBPS, record.getSpeedKbps());
		values.put(COL_CHECKSUM_OK, record.getChecksumOk());
		values.put(COL_CONTENT_TYPE, record.getContentType());
		return values;
	}

	@NonNull
	private TransferRecord fromCursor(@NonNull Cursor cursor) {
		return new TransferRecord(
				cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
				cursor.getString(cursor.getColumnIndexOrThrow(COL_FILENAME)),
				cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILE_SIZE)),
				cursor.getLong(cursor.getColumnIndexOrThrow(COL_BYTES_TRANSFERRED)),
				cursor.getString(cursor.getColumnIndexOrThrow(COL_PEER_NAME)),
				cursor.getString(cursor.getColumnIndexOrThrow(COL_PEER_ID)),
				cursor.getString(cursor.getColumnIndexOrThrow(COL_TRANSPORT)),
				cursor.getString(cursor.getColumnIndexOrThrow(COL_DIRECTION)),
				cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)),
				cursor.getString(cursor.getColumnIndexOrThrow(COL_ERROR)),
				cursor.getLong(cursor.getColumnIndexOrThrow(COL_START_TIME)),
				cursor.getLong(cursor.getColumnIndexOrThrow(COL_END_TIME)),
				cursor.getFloat(cursor.getColumnIndexOrThrow(COL_SPEED_KBPS)),
				cursor.getInt(cursor.getColumnIndexOrThrow(COL_CHECKSUM_OK)),
				cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT_TYPE))
		);
	}
}
