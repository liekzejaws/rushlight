package net.osmand.plus.plugins.p2pshare;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Phase 13: Unit tests for ContentType — P2P file type classification.
 */
public class ContentTypeTest {

	@Test
	public void testDetectMap() {
		assertEquals(ContentType.MAP, ContentType.fromFilename("world.obf"));
	}

	@Test
	public void testDetectZim() {
		assertEquals(ContentType.ZIM, ContentType.fromFilename("wikipedia_en.zim"));
	}

	@Test
	public void testDetectModel() {
		assertEquals(ContentType.MODEL, ContentType.fromFilename("phi-2.gguf"));
	}

	@Test
	public void testDetectApk() {
		assertEquals(ContentType.APK, ContentType.fromFilename("Rushlight-1.0.apk"));
	}

	@Test
	public void testCaseInsensitive() {
		assertEquals(ContentType.APK, ContentType.fromFilename("app.APK"));
		assertEquals(ContentType.MAP, ContentType.fromFilename("world.OBF"));
		assertEquals(ContentType.ZIM, ContentType.fromFilename("wiki.ZIM"));
		assertEquals(ContentType.MODEL, ContentType.fromFilename("model.GGUF"));
	}

	@Test
	public void testUnknownExtensionDefaultsToMap() {
		assertEquals(ContentType.MAP, ContentType.fromFilename("file.xyz"));
		assertEquals(ContentType.MAP, ContentType.fromFilename("readme.txt"));
		assertEquals(ContentType.MAP, ContentType.fromFilename("data.json"));
	}

	@Test
	public void testExtensionValues() {
		assertEquals(".obf", ContentType.MAP.getExtension());
		assertEquals(".zim", ContentType.ZIM.getExtension());
		assertEquals(".gguf", ContentType.MODEL.getExtension());
		assertEquals(".apk", ContentType.APK.getExtension());
	}

	@Test
	public void testDisplayNameValues() {
		assertEquals("Offline Map", ContentType.MAP.getDisplayName());
		assertEquals("Wikipedia", ContentType.ZIM.getDisplayName());
		assertEquals("LLM Model", ContentType.MODEL.getDisplayName());
		assertEquals("Lampp App", ContentType.APK.getDisplayName());
	}

	@Test
	public void testAllEnumValuesExist() {
		ContentType[] values = ContentType.values();
		assertEquals(4, values.length);
	}

	@Test
	public void testFilenameWithPath() {
		// fromFilename should work with full paths
		assertEquals(ContentType.APK, ContentType.fromFilename("/sdcard/downloads/app.apk"));
		assertEquals(ContentType.ZIM, ContentType.fromFilename("D:\\files\\wiki.zim"));
	}
}
