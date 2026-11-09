package com.nova.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostMatcherTest {
    private final HostMatcher m = new HostMatcher(new String[] {"exact.test"}, new String[] {"suffix.test"});

    @Test
    public void exactAndSubdomains() {
        assertTrue(m.matches("exact.test"));
        assertTrue(m.matches("www.exact.test"));
        assertTrue(m.matches("a.b.exact.test"));
        assertFalse(m.matches("notexact.test"));
    }

    @Test
    public void suffixes() {
        assertTrue(m.matches("suffix.test"));
        assertTrue(m.matches("x.suffix.test"));
        assertFalse(m.matches("evilsuffix.test"));
        assertFalse(m.matches(""));
        assertFalse(m.matches(null));
    }
}
