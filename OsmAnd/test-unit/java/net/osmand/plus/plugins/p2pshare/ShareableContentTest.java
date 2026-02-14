package net.osmand.plus.plugins.p2pshare;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for ShareableContent — P2P content data model.
 */
public class ShareableContentTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testConstructorSetsFields() {
		ShareableContent content = new ShareableContent(
				"world.obf", "/sdcard/maps/world.obf", 1024 * 1024,
				ContentType.MAP, "abc123");

		assertEquals("world.obf", content.getFilename());
		assertEquals("/sdcard/maps/world.obf", content.getFilePath());
		assertEquals(1024 * 1024, content.getFileSize());
		assertEquals(ContentType.MAP, content.getContentType());
		assertEquals(ContentType.MAP, content.getType()); // alias
		assertEquals("abc123", content.getChecksum());
	}

	@Test
	public void testFromFile() throws IOException {
		File file = tempFolder.newFile("test_map.obf");
		writeBytes(file, new byte[512]);

		ShareableContent content = ShareableContent.fromFile(file);

		assertEquals("test_map.obf", content.getFilename());
		assertEquals(file.getAbsolutePath(), content.getFilePath());
		assertEquals(512, content.getFileSize());
		assertEquals(ContentType.MAP, content.getContentType());
		assertNull("Checksum should be null initially", content.getChecksum());
	}

	@Test
	public void testDisplayNameStripsExtension() {
		ShareableContent content = new ShareableContent(
				"my_cool_map.obf", "/path/my_cool_map.obf", 100,
				ContentType.MAP, null);

		assertEquals("my cool map", content.getDisplayName());
	}

	@Test
	public void testIsSharedDefault() {
		ShareableContent content = new ShareableContent(
				"test.obf", "/test.obf", 100, ContentType.MAP, null);

		assertTrue("Default should be shared", content.isShared());
	}

	@Test
	public void testSetShared() {
		ShareableContent content = new ShareableContent(
				"test.obf", "/test.obf", 100, ContentType.MAP, null);

		content.setShared(false);
		assertFalse(content.isShared());

		content.setShared(true);
		assertTrue(content.isShared());
	}

	@Test
	public void testSetChecksum() {
		ShareableContent content = new ShareableContent(
				"test.obf", "/test.obf", 100, ContentType.MAP, null);

		assertNull(content.getChecksum());
		content.setChecksum("sha256_hash");
		assertEquals("sha256_hash", content.getChecksum());
	}

	@Test
	public void testFormattedSizeBytes() {
		ShareableContent content = new ShareableContent(
				"small.obf", "/small.obf", 512, ContentType.MAP, null);
		assertEquals("512 B", content.getFormattedSize());
	}

	@Test
	public void testFormattedSizeKB() {
		ShareableContent content = new ShareableContent(
				"medium.obf", "/medium.obf", 2048, ContentType.MAP, null);
		assertEquals("2.0 KB", content.getFormattedSize());
	}

	@Test
	public void testFormattedSizeMB() {
		ShareableContent content = new ShareableContent(
				"large.obf", "/large.obf", 5 * 1024 * 1024, ContentType.MAP, null);
		assertEquals("5.0 MB", content.getFormattedSize());
	}

	@Test
	public void testFormattedSizeGB() {
		ShareableContent content = new ShareableContent(
				"huge.zim", "/huge.zim", 2L * 1024 * 1024 * 1024, ContentType.ZIM, null);
		assertEquals("2.00 GB", content.getFormattedSize());
	}

	@Test
	public void testEqualityBasedOnFilePath() {
		ShareableContent a = new ShareableContent("a.obf", "/same/path.obf", 100, ContentType.MAP, null);
		ShareableContent b = new ShareableContent("b.obf", "/same/path.obf", 200, ContentType.ZIM, "hash");

		assertEquals("Same filePath should be equal", a, b);
		assertEquals("Same filePath should have same hashCode", a.hashCode(), b.hashCode());
	}

	@Test
	public void testInequalityDifferentPaths() {
		ShareableContent a = new ShareableContent("a.obf", "/path/a.obf", 100, ContentType.MAP, null);
		ShareableContent b = new ShareableContent("b.obf", "/path/b.obf", 100, ContentType.MAP, null);

		assertNotEquals(a, b);
	}

	@Test
	public void testToStringContainsInfo() {
		ShareableContent content = new ShareableContent(
				"test.apk", "/test.apk", 1024 * 1024, ContentType.APK, null);

		String str = content.toString();
		assertTrue(str.contains("test.apk"));
		assertTrue(str.contains("APK"));
	}

	@Test
	public void testComputeSha256() throws IOException {
		File file = tempFolder.newFile("hashtest.txt");
		writeBytes(file, "Hello, World!".getBytes());

		String hash = ShareableContent.computeSha256(file);

		assertNotNull(hash);
		assertEquals("SHA-256 should be 64 hex chars", 64, hash.length());
		// Known SHA-256 of "Hello, World!"
		assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", hash);
	}

	@Test
	public void testComputeSha256EmptyFile() throws IOException {
		File file = tempFolder.newFile("empty.txt");

		String hash = ShareableContent.computeSha256(file);

		assertNotNull(hash);
		assertEquals(64, hash.length());
		// Known SHA-256 of empty input
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
	}

	private void writeBytes(File file, byte[] data) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(data);
		}
	}
}
