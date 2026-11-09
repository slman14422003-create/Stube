package com.nova.browser;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SmartCalc {
    private SmartCalc() {}

    private static final Pattern DATE_LIKE = Pattern.compile("^\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}$");
    private static final Pattern THOUSANDS = Pattern.compile("(?<=\\d),(?=\\d{3}(?!\\d))");
    private static final Pattern TIMES = Pattern.compile("(?<=[\\d)])\\s*[xX]\\s*(?=[\\d(])");
    private static final String ALLOWED = "+-*/^%(). ";

    public static String evaluate(String input) {
        if (input == null) return null;
        String s = clean(input);
        if (s.length() < 3 || s.length() > 120) return null;
        if (DATE_LIKE.matcher(s).matches()) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9') && ALLOWED.indexOf(c) < 0) return null;
        }
        try {
            Parser p = new Parser(s);
            double v = p.parse();
            if (p.ops == 0) return null;
            return format(v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String clean(String in) {
        StringBuilder b = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') b.append((char) ('0' + (c - '\u0660')));
            else if (c >= '\u06F0' && c <= '\u06F9') b.append((char) ('0' + (c - '\u06F0')));
            else if (c == '\u066B') b.append('.');
            else if (c == '\u066C') continue;
            else if (c == '\u00D7') b.append('*');
            else if (c == '\u00F7') b.append('/');
            else if (c == '\u2212' || c == '\u2013') b.append('-');
            else b.append(c);
        }
        String t = b.toString().trim();
        t = THOUSANDS.matcher(t).replaceAll("");
        t = TIMES.matcher(t).replaceAll("*");
        return t;
    }

    static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        double a = Math.abs(v);
        if (a >= 1e15 || (a != 0 && a < 1e-9)) return String.format(Locale.ROOT, "%.6e", v);
        BigDecimal d = new BigDecimal(v).round(new MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros();
        if (d.scale() < 0) d = d.setScale(0);
        return d.toPlainString();
    }

    private static final class Parser {
        private final String s;
        private int pos;
        private int depth;
        int ops;

        Parser(String s) { this.s = s; }

        double parse() {
            double v = expr();
            skip();
            if (pos != s.length()) throw new IllegalStateException();
            return v;
        }

        private void skip() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        private boolean eat(char c) {
            skip();
            if (pos < s.length() && s.charAt(pos) == c) { pos++; return true; }
            return false;
        }

        private double expr() {
            double v = term();
            while (true) {
                if (eat('+')) { ops++; v += term(); }
                else if (eat('-')) { ops++; v -= term(); }
                else return v;
            }
        }

        private double term() {
            double v = unary();
            while (true) {
                if (eat('*')) { ops++; v *= unary(); }
                else if (eat('/')) {
                    ops++;
                    double d = unary();
                    if (d == 0) throw new ArithmeticException();
                    v /= d;
                }
                else return v;
            }
        }

        private double unary() {
            if (eat('-')) return -unary();
            if (eat('+')) return unary();
            return power();
        }

        private double power() {
            double base = postfix();
            if (eat('^')) { ops++; return Math.pow(base, unary()); }
            return base;
        }

        private double postfix() {
            double v = primary();
            while (eat('%')) { ops++; v /= 100.0; }
            return v;
        }

        private double primary() {
            skip();
            if (eat('(')) {
                if (++depth > 30) throw new IllegalStateException();
                double v = expr();
                if (!eat(')')) throw new IllegalStateException();
                depth--;
                return v;
            }
            int start = pos;
            while (pos < s.length() && (s.charAt(pos) == '.' || (s.charAt(pos) >= '0' && s.charAt(pos) <= '9'))) pos++;
            if (start == pos) throw new IllegalStateException();
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
