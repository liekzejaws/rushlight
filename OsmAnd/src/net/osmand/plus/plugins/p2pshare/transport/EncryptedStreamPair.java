package net.osmand.plus.plugins.p2pshare.transport;

import androidx.annotation.NonNull;

import net.osmand.PlatformUtil;

import org.apache.commons.logging.Log;
import org.bouncycastle.crypto.engines.ChaCha7539Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Phase 12: ChaCha20-Poly1305 authenticated encryption wrapping for P2P streams.
 *
 * Wraps raw InputStream/OutputStream with transparent encryption/decryption.
 * Uses counter-based nonces to prevent replay attacks.
 *
 * Wire format for each encrypted frame:
 * [4 bytes: ciphertext length (big-endian)]
 * [16 bytes: Poly1305 MAC tag]
 * [N bytes: ciphertext]
 *
 * The nonce is derived from a monotonically increasing counter (not sent on wire —
 * both sides maintain their own counters that stay in sync because the protocol
 * is strictly sequential request/response).
 */
public class EncryptedStreamPair {

	private static final Log LOG = PlatformUtil.getLog(EncryptedStreamPair.class);

	/** Maximum frame size: 1 MB to prevent memory exhaustion */
	private static final int MAX_FRAME_SIZE = 1024 * 1024;

	/** Poly1305 MAC tag length */
	private static final int MAC_LENGTH = 16;

	/** ChaCha20 nonce length (96-bit / 12 bytes for IETF variant) */
	private static final int NONCE_LENGTH = 12;

	@NonNull private final byte[] sendKey;
	@NonNull private final byte[] receiveKey;

	private long sendCounter = 0;
	private long receiveCounter = 0;

	/**
	 * Create an encrypted stream pair with directional keys from the handshake.
	 *
	 * @param sendKey    32-byte ChaCha20 key for outgoing data
	 * @param receiveKey 32-byte ChaCha20 key for incoming data
	 */
	public EncryptedStreamPair(@NonNull byte[] sendKey, @NonNull byte[] receiveKey) {
		if (sendKey.length != 32 || receiveKey.length != 32) {
			throw new IllegalArgumentException("Keys must be 32 bytes");
		}
		this.sendKey = sendKey.clone();
		this.receiveKey = receiveKey.clone();
	}

	/**
	 * Get an OutputStream that encrypts data before writing to the underlying stream.
	 * The returned stream buffers writes and encrypts them as frames when flush() is called.
	 */
	@NonNull
	public OutputStream wrapOutput(@NonNull OutputStream rawOut) {
		return new EncryptedOutputStream(rawOut);
	}

	/**
	 * Get an InputStream that decrypts data read from the underlying stream.
	 */
	@NonNull
	public InputStream wrapInput(@NonNull InputStream rawIn) {
		return new EncryptedInputStream(rawIn);
	}

	/**
	 * Derive a nonce from a counter value.
	 * Format: 4 bytes zero + 8 bytes counter (little-endian)
	 */
	private byte[] deriveNonce(long counter) {
		byte[] nonce = new byte[NONCE_LENGTH];
		ByteBuffer.wrap(nonce, 4, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter);
		return nonce;
	}

	/**
	 * Encrypt plaintext using ChaCha20 and compute Poly1305 MAC.
	 */
	private byte[] encrypt(byte[] plaintext, byte[] nonce) {
		// ChaCha20 encryption
		ChaCha7539Engine chacha = new ChaCha7539Engine();
		chacha.init(true, new ParametersWithIV(new KeyParameter(sendKey), nonce));

		byte[] ciphertext = new byte[plaintext.length];
		chacha.processBytes(plaintext, 0, plaintext.length, ciphertext, 0);

		// Poly1305 MAC over ciphertext
		// Generate Poly1305 key: first 32 bytes of ChaCha20 keystream with counter=0
		ChaCha7539Engine macKeyGen = new ChaCha7539Engine();
		macKeyGen.init(true, new ParametersWithIV(new KeyParameter(sendKey), nonce));
		byte[] poly1305Key = new byte[64]; // Generate 64 bytes, use first 32
		byte[] zeros = new byte[64];
		macKeyGen.processBytes(zeros, 0, 64, poly1305Key, 0);

		Poly1305 mac = new Poly1305();
		mac.init(new KeyParameter(poly1305Key, 0, 32));
		mac.update(ciphertext, 0, ciphertext.length);

		byte[] tag = new byte[MAC_LENGTH];
		mac.doFinal(tag, 0);

		return concatArrays(tag, ciphertext);
	}

	/**
	 * Decrypt ciphertext using ChaCha20 and verify Poly1305 MAC.
	 */
	private byte[] decrypt(byte[] tagAndCiphertext, byte[] nonce) throws IOException {
		if (tagAndCiphertext.length < MAC_LENGTH) {
			throw new IOException("Encrypted frame too short for MAC");
		}

		byte[] receivedTag = new byte[MAC_LENGTH];
		System.arraycopy(tagAndCiphertext, 0, receivedTag, 0, MAC_LENGTH);

		byte[] ciphertext = new byte[tagAndCiphertext.length - MAC_LENGTH];
		System.arraycopy(tagAndCiphertext, MAC_LENGTH, ciphertext, 0, ciphertext.length);

		// Verify Poly1305 MAC
		ChaCha7539Engine macKeyGen = new ChaCha7539Engine();
		macKeyGen.init(true, new ParametersWithIV(new KeyParameter(receiveKey), nonce));
		byte[] poly1305Key = new byte[64];
		byte[] zeros = new byte[64];
		macKeyGen.processBytes(zeros, 0, 64, poly1305Key, 0);

		Poly1305 mac = new Poly1305();
		mac.init(new KeyParameter(poly1305Key, 0, 32));
		mac.update(ciphertext, 0, ciphertext.length);

		byte[] computedTag = new byte[MAC_LENGTH];
		mac.doFinal(computedTag, 0);

		if (!constantTimeEquals(receivedTag, computedTag)) {
			throw new IOException("Encrypted frame: MAC verification failed — data may be tampered");
		}

		// ChaCha20 decryption
		ChaCha7539Engine chacha = new ChaCha7539Engine();
		chacha.init(false, new ParametersWithIV(new KeyParameter(receiveKey), nonce));

		byte[] plaintext = new byte[ciphertext.length];
		chacha.processBytes(ciphertext, 0, ciphertext.length, plaintext, 0);

		return plaintext;
	}

	/**
	 * Constant-time comparison to prevent timing attacks on MAC verification.
	 */
	private static boolean constantTimeEquals(byte[] a, byte[] b) {
		if (a.length != b.length) return false;
		int result = 0;
		for (int i = 0; i < a.length; i++) {
			result |= a[i] ^ b[i];
		}
		return result == 0;
	}

	private static byte[] concatArrays(byte[] a, byte[] b) {
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	/**
	 * OutputStream that encrypts data into frames.
	 * Each write() call becomes an encrypted frame when flush() is called by DataOutputStream.
	 */
	private class EncryptedOutputStream extends OutputStream {
		private final OutputStream rawOut;
		private byte[] buffer = new byte[0];

		EncryptedOutputStream(OutputStream rawOut) {
			this.rawOut = rawOut;
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[]{(byte) b}, 0, 1);
		}

		@Override
		public void write(@NonNull byte[] data, int off, int len) throws IOException {
			// Accumulate data until flush
			byte[] newBuffer = new byte[buffer.length + len];
			System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
			System.arraycopy(data, off, newBuffer, buffer.length, len);
			buffer = newBuffer;
		}

		@Override
		public void flush() throws IOException {
			if (buffer.length == 0) {
				rawOut.flush();
				return;
			}

			byte[] nonce = deriveNonce(sendCounter++);
			byte[] encrypted = encrypt(buffer, nonce);

			// Write frame: [4 bytes length][tag + ciphertext]
			int frameLength = encrypted.length;
			rawOut.write((frameLength >> 24) & 0xFF);
			rawOut.write((frameLength >> 16) & 0xFF);
			rawOut.write((frameLength >> 8) & 0xFF);
			rawOut.write(frameLength & 0xFF);
			rawOut.write(encrypted);
			rawOut.flush();

			buffer = new byte[0];
		}

		@Override
		public void close() throws IOException {
			flush();
			rawOut.close();
		}
	}

	/**
	 * InputStream that reads encrypted frames and decrypts them.
	 */
	private class EncryptedInputStream extends InputStream {
		private final InputStream rawIn;
		private byte[] decryptedBuffer = new byte[0];
		private int bufferPos = 0;

		EncryptedInputStream(InputStream rawIn) {
			this.rawIn = rawIn;
		}

		@Override
		public int read() throws IOException {
			if (bufferPos >= decryptedBuffer.length) {
				if (!readNextFrame()) {
					return -1;
				}
			}
			return decryptedBuffer[bufferPos++] & 0xFF;
		}

		@Override
		public int read(@NonNull byte[] b, int off, int len) throws IOException {
			if (bufferPos >= decryptedBuffer.length) {
				if (!readNextFrame()) {
					return -1;
				}
			}

			int available = decryptedBuffer.length - bufferPos;
			int toRead = Math.min(available, len);
			System.arraycopy(decryptedBuffer, bufferPos, b, off, toRead);
			bufferPos += toRead;
			return toRead;
		}

		@Override
		public int available() throws IOException {
			return decryptedBuffer.length - bufferPos;
		}

		private boolean readNextFrame() throws IOException {
			// Read 4-byte frame length
			byte[] lenBytes = new byte[4];
			if (!readFullyOrEof(rawIn, lenBytes)) {
				return false;
			}

			int frameLength = ((lenBytes[0] & 0xFF) << 24)
					| ((lenBytes[1] & 0xFF) << 16)
					| ((lenBytes[2] & 0xFF) << 8)
					| (lenBytes[3] & 0xFF);

			if (frameLength <= 0 || frameLength > MAX_FRAME_SIZE) {
				throw new IOException("Invalid encrypted frame length: " + frameLength);
			}

			// Read encrypted frame
			byte[] encrypted = new byte[frameLength];
			if (!readFullyOrEof(rawIn, encrypted)) {
				throw new IOException("Unexpected end of encrypted stream mid-frame");
			}

			// Decrypt
			byte[] nonce = deriveNonce(receiveCounter++);
			decryptedBuffer = decrypt(encrypted, nonce);
			bufferPos = 0;

			return true;
		}

		/**
		 * Read exactly buffer.length bytes. Returns false on clean EOF (first byte).
		 */
		private boolean readFullyOrEof(InputStream in, byte[] buffer) throws IOException {
			int offset = 0;
			while (offset < buffer.length) {
				int read = in.read(buffer, offset, buffer.length - offset);
				if (read < 0) {
					if (offset == 0) return false; // Clean EOF
					throw new IOException("Unexpected end of stream after " + offset + " bytes");
				}
				offset += read;
			}
			return true;
		}

		@Override
		public void close() throws IOException {
			rawIn.close();
		}
	}
}
