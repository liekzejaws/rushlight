/*
 * This file is part of Rushlight, licensed under the GNU Affero General
 * Public License v3.0 or later. See the LICENSE file in the project root.
 */

package net.osmand.plus.morse;

import androidx.annotation.NonNull;

/**
 * LAMPP Morse Code: Data model for a Morse message.
 *
 * Stores the text content, timestamp, and direction (sent/received).
 * Used for message history logging (Phase 2) but defined now for structural completeness.
 */
public class MorseMessage {

    /**
     * Direction of the message.
     */
    public enum MessageType {
        SENT,
        RECEIVED
    }

    private final String text;
    private final long timestamp;
    private final MessageType type;

    /**
     * Create a new Morse message.
     *
     * @param text      the plain text content
     * @param timestamp creation time in milliseconds (System.currentTimeMillis())
     * @param type      SENT or RECEIVED
     */
    public MorseMessage(@NonNull String text, long timestamp, @NonNull MessageType type) {
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
    }

    /**
     * Create a SENT message with the current timestamp.
     */
    public static MorseMessage sent(@NonNull String text) {
        return new MorseMessage(text, System.currentTimeMillis(), MessageType.SENT);
    }

    /**
     * Create a RECEIVED message with the current timestamp.
     */
    public static MorseMessage received(@NonNull String text) {
        return new MorseMessage(text, System.currentTimeMillis(), MessageType.RECEIVED);
    }

    @NonNull
    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @NonNull
    public MessageType getType() {
        return type;
    }

    public boolean isSent() {
        return type == MessageType.SENT;
    }

    public boolean isReceived() {
        return type == MessageType.RECEIVED;
    }

    @NonNull
    @Override
    public String toString() {
        return "MorseMessage{" +
                "type=" + type +
                ", text='" + text + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
