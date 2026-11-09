package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LangUtilTest {
    @Test public void rtlLanguages() {
        assertTrue(LangUtil.isRtl("ar"));
        assertTrue(LangUtil.isRtl("fa-IR"));
        assertFalse(LangUtil.isRtl("en"));
        assertFalse(LangUtil.isRtl(""));
        assertFalse(LangUtil.isRtl(null));
    }

    @Test public void acceptLanguage() {
        assertEquals("", LangUtil.acceptLanguage(""));
        assertEquals("en", LangUtil.acceptLanguage("en-US"));
        assertEquals("ar,en;q=0.8", LangUtil.acceptLanguage("ar"));
    }
}
