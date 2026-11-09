package com.nova.browser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** أدوات اللغة (Java): اتجاه الكتابة وترويسة Accept-Language. */
public final class LangUtil {
    private LangUtil() {}

    private static final Set<String> RTL = new HashSet<>(Arrays.asList("ar", "fa", "ur", "he", "ps", "ku"));

    /** هل اللغة تُكتب من اليمين لليسار؟ */
    public static boolean isRtl(String code) {
        if (code == null || code.isEmpty()) return false;
        return RTL.contains(code.toLowerCase(Locale.ROOT).split("[-_]")[0]);
    }

    /** ترويسة Accept-Language للغة مفضّلة (مع الإنجليزية كاحتياط). */
    public static String acceptLanguage(String code) {
        if (code == null || code.isEmpty()) return "";
        String c = code.toLowerCase(Locale.ROOT).split("[-_]")[0];
        return "en".equals(c) ? "en" : c + ",en;q=0.8";
    }
}
