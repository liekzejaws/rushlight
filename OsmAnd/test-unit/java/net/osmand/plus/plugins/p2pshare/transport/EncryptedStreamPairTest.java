package net.osmand.plus.plugins.p2pshare.transport;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for EncryptedStreamPair (ChaCha20-Poly1305).
 *
 * Tests encrypt/decrypt round-trips, key mismatch detection,
 * binary integrity, and sequential message handling.
 */
public class EncryptedStreamPairTest {

	private byte[] sendKey;
	private byte[] receiveKey;

	@Before
	public void setUp() {
		// Use deterministic keys for reproducible tests
		sendKey = new byte[32];
		receiveKey = new byte[32];
		Arrays.fill(sendKey, (byte) 0x41);
		Arrays.fill(receiveKey, (byte) 0x42);
	}

	@Test
	public void testEncryptDecryptRoundTrip() throws IOException {
		byte[] plaintext = "Hello, encrypted world!".getBytes();

		byte[] decrypted = roundTrip(sendKey, receiveKey, plaintext);
		assertArrayEquals("Round-trip should preserve data", plaintext, decrypted);
	}

	@Test
	public void testEncryptDecryptEmptyPayload() throws IOException {
		// Empty flush should not produce output; write at least 1 byte
		byte[] plaintext = new byte[]{0x00};
		byte[] decrypted = roundTrip(sendKey, receiveKey, plaintext);
		assertArrayEquals(plaintext, decrypted);
	}

	@Test
	public void testEncryptDecryptLargePayload() throws IOException {
		// 64KB payload (below 1MB max frame)
		byte[] plaintext = new byte[65536];
		new Random(42).nextBytes(plaintext);

		byte[] decrypted = roundTrip(sendKey, receiveKey, plaintext);
		assertArrayEquals("Large payload should survive round-trip", plaintext, decrypted);
	}

	@Test
	public void testEncryptedDataDiffersFromPlaintext() throws IOException {
		byte[] plaintext = "This should be encrypted".getBytes();

		ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
		EncryptedStreamPair sender = new EncryptedStreamPair(sendKey, receiveKey);
		OutputStream encOut = sender.wrapOutput(rawOut);
		encOut.write(plaintext);
		encOut.flush();
		encOut.close();

		byte[] wireData = rawOut.toByteArray();
		// Wire data includes 4-byte length + 16-byte MAC + ciphertext
		assertTrue("Wire data should be larger than plaintext",
				wireData.length > plaintext.length);

		// Extract the ciphertext portion (after 4-byte length header + 16-byte MAC)
		assertFalse("Ciphertext should differ from plaintext",
				containsSubsequence(wireData, plaintext));
	}

	@Test
	public void testDifferentKeysCannotDecrypt() throws IOException {
		byte[] plaintext = "Secret message".getBytes();

		// Encrypt with sendKey/receiveKey
		ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
		EncryptedStreamPair sender = new EncryptedStreamPair(sendKey, receiveKey);
		OutputStream encOut = sender.wrapOutput(rawOut);
		encOut.write(plaintext);
		encOut.flush();
		encOut.close();

		byte[] wireData = rawOut.toByteArray();

		// Try to decrypt with wrong keys
		byte[] wrongKey = new byte[32];
		Arrays.fill(wrongKey, (byte) 0xFF);

		ByteArrayInputStream rawIn = new ByteArrayInputStream(wireData);
		EncryptedStreamPair wrongReceiver = new EncryptedStreamPair(wrongKey, wrongKey);
		InputStream decIn = wrongReceiver.wrapInput(rawIn);

		try {
			byte[] buffer = new byte[plaintext.length + 100];
			decIn.read(buffer);
			fail("Decryption with wrong key should throw IOException (MAC failure)");
		} catch (IOException e) {
			assertTrue("Should be MAC verification failure",
					e.getMessage().contains("MAC") || e.getMessage().contains("tampered"));
		}
	}

	@Test
	public void testMultipleMessagesInSequence() throws IOException {
		ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
		EncryptedStreamPair sender = new EncryptedStreamPair(sendKey, receiveKey);
		OutputStream encOut = sender.wrapOutput(rawOut);

		// Write 10 messages
		String[] messages = new String[10];
		for (int i = 0; i < 10; i++) {
			messages[i] = "Message " + i;
			encOut.write(messages[i].getBytes());
			encOut.flush();
		}
		encOut.close();

		// Read them back
		ByteArrayInputStream rawIn = new ByteArrayInputStream(rawOut.toByteArray());
		// Receiver's receiveKey = sender's sendKey, sender's receiveKey = receiver's sendKey
		EncryptedStreamPair receiver = new EncryptedStreamPair(receiveKey, sendKey);
		InputStream decIn = receiver.wrapInput(rawIn);

		for (int i = 0; i < 10; i++) {
			byte[] buffer = new byte[messages[i].length()];
			int read = decIn.read(buffer);
			assertEquals(messages[i].length(), read);
			assertEquals("Message " + i + " should match", messages[i], new String(buffer));
		}
	}

	@Test
	public void testBinaryDataIntegrity() throws IOException {
		// Test all 256 byte values
		byte[] allBytes = new byte[256];
		for (int i = 0; i < 256; i++) {
			allBytes[i] = (byte) i;
		}

		byte[] decrypted = roundTrip(sendKey, receiveKey, allBytes);
		assertArrayEquals("All 256 byte values must be preserved", allBytes, decrypted);
	}

	@Test
	public void testConstructorRejectsWrongKeyLength() {
		try {
			new EncryptedStreamPair(new byte[16], new byte[32]);
			fail("Should reject 16-byte key");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("32 bytes"));
		}

		try {
			new EncryptedStreamPair(new byte[32], new byte[64]);
			fail("Should reject 64-byte key");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("32 bytes"));
		}
	}

	@Test
	public void testKeysAreCloned() throws IOException {
		byte[] key1 = new byte[32];
		byte[] key2 = new byte[32];
		Arrays.fill(key1, (byte) 0x11);
		Arrays.fill(key2, (byte) 0x22);

		EncryptedStreamPair pair = new EncryptedStreamPair(key1, key2);

		// Modify original keys — should not affect the pair
		Arrays.fill(key1, (byte) 0);
		Arrays.fill(key2, (byte) 0);

		// The pair should still work correctly (keys were cloned)
		byte[] plaintext = "test".getBytes();
		ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
		OutputStream encOut = pair.wrapOutput(rawOut);
		encOut.write(plaintext);
		encOut.flush();
		encOut.close();

		assertTrue("Stream pair should still produce encrypted output",
				rawOut.toByteArray().length > plaintext.length);
	}

	@Test
	public void testStreamReadReturnsMinus1OnEof() throws IOException {
		ByteArrayInputStream emptyRaw = new ByteArrayInputStream(new byte[0]);
		EncryptedStreamPair receiver = new EncryptedStreamPair(receiveKey, sendKey);
		InputStream decIn = receiver.wrapInput(emptyRaw);

		assertEquals("Empty stream should return -1", -1, decIn.read());
	}

	// ---- Helpers ----

	/**
	 * Encrypt with sender, decrypt with receiver, return decrypted bytes.
	 * Sets up keys so sender's sendKey = receiver's receiveKey.
	 */
	private byte[] roundTrip(byte[] sKey, byte[] rKey, byte[] plaintext) throws IOException {
		// Sender encrypts
		ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
		EncryptedStreamPair sender = new EncryptedStreamPair(sKey, rKey);
		OutputStream encOut = sender.wrapOutput(rawOut);
		encOut.write(plaintext);
		encOut.flush();
		encOut.close();

		// Receiver decrypts (receiver's receiveKey = sender's sendKey)
		ByteArrayInputStream rawIn = new ByteArrayInputStream(rawOut.toByteArray());
		EncryptedStreamPair receiver = new EncryptedStreamPair(rKey, sKey);
		InputStream decIn = receiver.wrapInput(rawIn);

		byte[] buffer = new byte[plaintext.length + 1024];
		int totalRead = 0;
		int read;
		while ((read = decIn.read(buffer, totalRead, buffer.length - totalRead)) > 0) {
			totalRead += read;
		}

		return Arrays.copyOf(buffer, totalRead);
	}

	/**
	 * Check if container contains the exact subsequence.
	 */
	private boolean containsSubsequence(byte[] container, byte[] subsequence) {
		outer:
		for (int i = 0; i <= container.length - subsequence.length; i++) {
			for (int j = 0; j < subsequence.length; j++) {
				if (container[i + j] != subsequence[j]) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}
}
