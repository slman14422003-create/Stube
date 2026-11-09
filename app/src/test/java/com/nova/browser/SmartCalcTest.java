package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SmartCalcTest {
    @Test public void basicArithmetic() {
        assertEquals("4", SmartCalc.evaluate("2+2"));
        assertEquals("8", SmartCalc.evaluate("2 + 2 * 3"));
        assertEquals("20", SmartCalc.evaluate("(2+3)*4"));
        assertEquals("2.5", SmartCalc.evaluate("10/4"));
        assertEquals("0.3", SmartCalc.evaluate("0.1+0.2"));
    }

    @Test public void powersAndSigns() {
        assertEquals("1024", SmartCalc.evaluate("2^10"));
        assertEquals("512", SmartCalc.evaluate("2^3^2"));
        assertEquals("-4", SmartCalc.evaluate("-2^2"));
        assertEquals("8", SmartCalc.evaluate("5--3"));
        assertEquals("0.125", SmartCalc.evaluate("2^-3"));
    }

    @Test public void percent() {
        assertEquals("0.5", SmartCalc.evaluate("50%"));
        assertEquals("20", SmartCalc.evaluate("200*10%"));
    }

    @Test public void localizedInput() {
        assertEquals("15", SmartCalc.evaluate("\u0661\u0662+\u0663"));
        assertEquals("12", SmartCalc.evaluate("3\u00D74"));
        assertEquals("3", SmartCalc.evaluate("12\u00F74"));
        assertEquals("1005", SmartCalc.evaluate("1,000+5"));
        assertEquals("6", SmartCalc.evaluate("2x3"));
        assertEquals("2.5", SmartCalc.evaluate("5\u00F72"));
    }

    @Test public void rejectsNonMath() {
        assertNull(SmartCalc.evaluate(null));
        assertNull(SmartCalc.evaluate("abc"));
        assertNull(SmartCalc.evaluate("123"));
        assertNull(SmartCalc.evaluate("2024-10-16"));
        assertNull(SmartCalc.evaluate("5 6"));
        assertNull(SmartCalc.evaluate("(2+3"));
        assertNull(SmartCalc.evaluate("1/0"));
        assertNull(SmartCalc.evaluate("example.com"));
        assertNull(SmartCalc.evaluate("(3)"));
        assertNull(SmartCalc.evaluate("1.2.3+4"));
    }

    @Test public void largeValues() {
        assertEquals("1.000000e+22", SmartCalc.evaluate("99999999999*99999999999"));
        assertEquals("0.333333333333", SmartCalc.evaluate("1/3"));
    }
}
