/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * LAMPP Morse Code: Thread-safe in-memory message history store.
 *
 * Phase 12: Now backed by SQLite via MorseHistoryDatabase.
 * Messages persist across app restarts — critical for survival comms.
 *
 * In-memory list is kept at MAX_MESSAGES for UI performance.
 * All messages are stored in SQLite for long-term retention.
 */
public class MorseHistoryManager {

    private static final Log LOG = PlatformUtil.getLog(MorseHistoryManager.class);
    private static final int MAX_MESSAGES = 100;

    /**
     * Listener for history changes.
     * Callbacks are always invoked on the main (UI) thread.
     */
    public interface HistoryListener {
        void onMessageAdded(@NonNull MorseMessage message);
        void onHistoryCleared();
    }

    private final List<MorseMessage> messages = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private HistoryListener listener;
    @Nullable
    private MorseHistoryDatabase historyDb;

    /**
     * Create a history manager without database backing (legacy behavior).
     */
    public MorseHistoryManager() {
        this.historyDb = null;
    }

    /**
     * Create a history manager with SQLite database persistence.
     * Loads existing messages from the database on construction.
     */
    public MorseHistoryManager(@NonNull Context context) {
        this.historyDb = MorseHistoryDatabase.getInstance(context);
        loadFromDatabase();
    }

    /**
     * Load messages from the database into the in-memory list.
     */
    private void loadFromDatabase() {
        if (historyDb != null) {
            List<MorseMessage> dbMessages = historyDb.loadMessages(MAX_MESSAGES);
            synchronized (messages) {
                messages.clear();
                messages.addAll(dbMessages);
            }
            LOG.info("Loaded " + dbMessages.size() + " Morse messages from database");
        }
    }

    /**
     * Set the listener for history change events.
     */
    public void setListener(@Nullable HistoryListener listener) {
        this.listener = listener;
    }

    /**
     * Add a message to the history. Thread-safe.
     * If at max capacity, the oldest message is removed.
     *
     * @param message the message to add
     */
    public void addMessage(@NonNull MorseMessage message) {
        synchronized (messages) {
            if (messages.size() >= MAX_MESSAGES) {
                messages.remove(0);
            }
            messages.add(message);
        }

        // Persist to database
        if (historyDb != null) {
            historyDb.insertMessage(message);
        }

        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onMessageAdded(message);
                }
            });
        }
    }

    /**
     * Get a copy of all messages. Thread-safe.
     *
     * @return new list containing all messages in chronological order
     */
    @NonNull
    public List<MorseMessage> getMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    /**
     * Get the number of messages in history.
     */
    public int getMessageCount() {
        synchronized (messages) {
            return messages.size();
        }
    }

    /**
     * Clear all messages. Thread-safe.
     * Clears both in-memory list and database.
     */
    public void clear() {
        synchronized (messages) {
            messages.clear();
        }

        if (historyDb != null) {
            historyDb.clearAll();
        }

        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onHistoryCleared();
                }
            });
        }
    }
}
