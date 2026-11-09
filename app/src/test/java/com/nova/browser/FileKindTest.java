package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class FileKindTest {
    private static byte[] zip(String... names) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (String n : names) {
                z.putNextEntry(new ZipEntry(n));
                z.write(new byte[] {1, 2, 3, 4});
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    private static String sniff(byte[] b) {
        return FileKind.INSTANCE.sniff(b, b, b.length);
    }

    @Test
    public void headerNames() {
        assertEquals("app release.apk", FileKind.INSTANCE.nameFromHeader("attachment; filename=\"app release.apk\""));
        assertEquals("app.apk", FileKind.INSTANCE.nameFromHeader("attachment; filename=app.apk; size=10"));
        assertEquals("\u0645\u0644\u0641.apk", FileKind.INSTANCE.nameFromHeader("attachment; filename*=UTF-8''%D9%85%D9%84%D9%81.apk"));
        assertEquals("a.apk", FileKind.INSTANCE.nameFromHeader("attachment; filename=\"../../a.apk\""));
        assertNull(FileKind.INSTANCE.nameFromHeader("inline"));
        assertNull(FileKind.INSTANCE.nameFromHeader(null));
    }

    @Test
    public void urlNames() {
        assertEquals("app-release.apk", FileKind.INSTANCE.nameFromUrl(
            "https://objects.githubusercontent.com/x/abc?response-content-disposition=attachment%3B%20filename%3Dapp-release.apk&x=1"));
        assertEquals("tool.apk", FileKind.INSTANCE.nameFromUrl("https://example.org/get?file=tool.apk"));
        assertEquals("setup.exe", FileKind.INSTANCE.nameFromUrl("https://example.org/dl/setup.exe?x=1"));
        assertNull(FileKind.INSTANCE.nameFromUrl("https://example.org/download/12345"));
        assertNull(FileKind.INSTANCE.nameFromUrl("https://example.org/v1.2"));
    }

    @Test
    public void sniffsApkAndFriends() throws Exception {
        assertEquals("apk", sniff(zip("META-INF/MANIFEST.MF", "AndroidManifest.xml", "classes.dex", "resources.arsc")));
        assertEquals("xapk", sniff(zip("manifest.json", "app.apk", "icon.png")));
        assertEquals("apks", sniff(zip("toc.pb", "base.apk", "split_config.arm64_v8a.apk")));
        assertEquals("docx", sniff(zip("[Content_Types].xml", "word/document.xml")));
        assertEquals("xlsx", sniff(zip("[Content_Types].xml", "xl/workbook.xml")));
        assertEquals("jar", sniff(zip("META-INF/MANIFEST.MF", "a/B.class")));
        assertEquals("zip", sniff(zip("a.txt", "b.txt")));
    }

    @Test
    public void sniffsPlainMagic() {
        assertEquals("pdf", sniff("%PDF-1.7 rest".getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals("png", sniff(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("jpg", sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0}));
        assertEquals("7z", sniff(new byte[] {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C, 0, 0}));
        assertEquals("mp4", sniff(new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'}));
        assertEquals("html", sniff("  <!DOCTYPE html><html>".getBytes(StandardCharsets.ISO_8859_1)));
        assertNull(sniff(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
    }

    @Test
    public void renameRules() {
        assertTrue(FileKind.INSTANCE.needsSniff("abc123.bin"));
        assertTrue(FileKind.INSTANCE.needsSniff("download"));
        assertTrue(FileKind.INSTANCE.needsSniff("get.php"));
        assertFalse(FileKind.INSTANCE.needsSniff("movie.mp4"));
        assertEquals("abc123.apk", FileKind.INSTANCE.withExt("abc123.bin", "apk"));
        assertEquals("download.apk", FileKind.INSTANCE.withExt("download", "apk"));
        assertEquals("get.apk", FileKind.INSTANCE.withExt("get.php", "apk"));
        assertEquals(Arrays.asList("a", "b").size(), 2);
    }
}
