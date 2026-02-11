package net.osmand.plus.morse;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * LAMPP Morse Code: Thread-safe in-memory message history store.
 *
 * Stores sent and received MorseMessage objects with a maximum capacity.
 * Notifies a listener on the main thread when messages are added or cleared.
 */
public class MorseHistoryManager {

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
     */
    public void clear() {
        synchronized (messages) {
            messages.clear();
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
