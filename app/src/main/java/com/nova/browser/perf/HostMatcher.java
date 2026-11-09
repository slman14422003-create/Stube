package com.nova.browser;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class HostMatcher {
    private final Set<String> hosts;
    private final Set<String> suffixes;

    public HostMatcher(String[] exact, String[] suffix) {
        hosts = new HashSet<>();
        suffixes = new HashSet<>();
        for (String h : exact) hosts.add(h.toLowerCase(Locale.ROOT));
        for (String s : suffix) suffixes.add(s.toLowerCase(Locale.ROOT));
    }

    public boolean matches(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) h = h.substring(4);
        if (hosts.contains(h)) return true;
        int i = h.indexOf('.');
        while (i >= 0) {
            String tail = h.substring(i + 1);
            if (hosts.contains(tail) || suffixes.contains(tail)) return true;
            i = h.indexOf('.', i + 1);
        }
        return suffixes.contains(h);
    }
}
