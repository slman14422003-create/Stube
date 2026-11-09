package com.nova.browser;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * تنظيف الملفات المؤقتة وكاش المتصفح (Java / NIO).
 * يجب استدعاؤه قبل إنشاء أي WebView حتى لا تكون ملفات الكاش مفتوحة.
 * لا يمسّ: الكوكيز، التنزيلات، التبويبات المحفوظة، الإعدادات.
 */
public final class CacheCleaner {
    private CacheCleaner() {}

    /** مجلدات الكاش داخل بيانات الـ WebView (آمنة الحذف عند بدء التطبيق). */
    private static final String[] WEBVIEW_CACHE_DIRS = {
            "HTTP Cache", "Code Cache", "GPUCache", "GrShaderCache", "ShaderCache",
            "Service Worker/CacheStorage", "Service Worker/ScriptCache"
    };

    /** يحذف الكاش ويعيد عدد البايتات المحررة. */
    public static long clean(Context ctx) {
        long freed = 0;
        freed += deleteContents(ctx.getCacheDir());
        File ext = ctx.getExternalCacheDir();
        if (ext != null) freed += deleteContents(ext);
        File base = new File(ctx.getApplicationInfo().dataDir, "app_webview/Default");
        for (String d : WEBVIEW_CACHE_DIRS) freed += deleteContents(new File(base, d));
        return freed;
    }

    /**
     * مسح كلي للكاش عند كل تشغيل (قبل إنشاء أي WebView): HTTP/Code/GPU/Shader/ServiceWorker + cacheDir.
     * لا يحذف: الكوكيز وتسجيلات الدخول، التخزين المحلي للمواقع، أذونات المواقع، كلمات المرور (Vault)، nova.conf.
     */
    public static long cleanAll(Context ctx) { return clean(ctx); }

    /** الحدّ الذي يُنظَّف عنده الكاش تلقائياً. */
    private static final long LIMIT = 300L * 1024 * 1024;

    private static File webBase(Context ctx) { return new File(ctx.getApplicationInfo().dataDir, "app_webview/Default"); }

    /** هل يحتاج الكاش تنظيفاً عند هذا التشغيل؟ (قراءة علم محفوظ، فورية). */
    public static boolean pending(Context ctx) {
        return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("cleanpending", false);
    }

    /** يُستدعى عند الخروج من التطبيق: يقيس الكاش ويضع علماً إن تجاوز الحد. */
    public static void markIfLarge(Context ctx) {
        long total = sizeOf(ctx.getCacheDir()) + sizeOf(ctx.getExternalCacheDir())
                + sizeOf(new File(webBase(ctx), "HTTP Cache")) + sizeOf(new File(webBase(ctx), "Service Worker/CacheStorage"));
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("cleanpending", total > LIMIT).apply();
    }

    /** تنظيف خفيف: ملفات التحميل المؤقتة فقط، ويُبقي كاش الشيفرة والرسوميات (Code/GPU/Shader) لسرعة التشغيل. */
    public static long cleanLarge(Context ctx) {
        long freed = deleteContents(ctx.getCacheDir());
        File ext = ctx.getExternalCacheDir();
        if (ext != null) freed += deleteContents(ext);
        freed += deleteContents(new File(webBase(ctx), "HTTP Cache"));
        freed += deleteContents(new File(webBase(ctx), "Service Worker/CacheStorage"));
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("cleanpending", false).apply();
        return freed;
    }

    /** حجم الكاش الحالي بالبايت (للعرض في الإعدادات). */
    public static long size(Context ctx) {
        long total = sizeOf(ctx.getCacheDir());
        File ext = ctx.getExternalCacheDir();
        if (ext != null) total += sizeOf(ext);
        File base = new File(ctx.getApplicationInfo().dataDir, "app_webview/Default");
        for (String d : WEBVIEW_CACHE_DIRS) total += sizeOf(new File(base, d));
        return total;
    }

    private static long sizeOf(File dir) {
        if (dir == null || !dir.exists()) return 0;
        final long[] sum = {0};
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    sum[0] += a.size();
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException ignored) { }
        return sum[0];
    }

    /** يحذف محتويات المجلد ويُبقي المجلد نفسه. */
    private static long deleteContents(File dir) {
        if (dir == null || !dir.exists()) return 0;
        final long[] freed = {0};
        final Path root = dir.toPath();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    try { long s = a.size(); Files.deleteIfExists(f); freed[0] += s; } catch (IOException ignored) { }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) {
                    if (!d.equals(root)) { try { Files.deleteIfExists(d); } catch (IOException ignored) { } }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException ignored) { }
        return freed[0];
    }

    /** تنسيق الحجم للعرض. */
    public static String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }
}
