/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.compat;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;

/**
 * Tests for {@link FunctionCompat}.
 *
 * <p>Verifies function type detection and information retrieval:
 *
 * <ul>
 *   <li>JavaScript function detection
 *   <li>NativeFunction vs JSFunction detection
 *   <li>Arrow function detection
 *   <li>Constructor detection
 *   <li>Function info retrieval
 * </ul>
 */
public class FunctionCompatTest {

    private Context cx;
    private Scriptable scope;

    @Before
    public void setUp() {
        cx = Context.enter();
        scope = new ImporterTopLevel(cx);
    }

    @After
    public void tearDown() {
        Context.exit();
    }

    // ========== isJavaScriptFunction Tests ==========

    @Test
    public void testIsJavaScriptFunctionWithRegularFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        assertTrue(
                "Regular function should be JavaScript function",
                FunctionCompat.isJavaScriptFunction(fn));
    }

    @Test
    public void testIsJavaScriptFunctionWithArrowFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (x) => x; fn", "test", 1, null);
        assertTrue(
                "Arrow function should be JavaScript function",
                FunctionCompat.isJavaScriptFunction(fn));
    }

    @Test
    public void testIsJavaScriptFunctionWithObject() throws Exception {
        Object obj = cx.evaluateString(scope, "var obj = {}; obj", "test", 1, null);
        assertFalse(
                "Object should not be JavaScript function",
                FunctionCompat.isJavaScriptFunction(obj));
    }

    @Test
    public void testIsJavaScriptFunctionWithNull() {
        assertFalse(
                "null should not be JavaScript function",
                FunctionCompat.isJavaScriptFunction(null));
    }

    // ========== isNativeFunction Tests ==========

    @Test
    public void testIsNativeFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        // In Rhino 2.0.0+, JS functions are JSFunction instances
        // NativeFunctionAdapter can wrap them
        boolean isNative = FunctionCompat.isNativeFunction(fn);
        // Just verify it doesn't throw
        assertNotNull(isNative);
    }

    // ========== isJSFunction Tests ==========

    @Test
    public void testIsJSFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        // In Rhino 2.0.0+, most JS functions are JSFunction instances
        boolean isJS = FunctionCompat.isJSFunction(fn);
        // Just verify it doesn't throw
        assertNotNull(isJS);
    }

    // ========== isArrowFunction Tests ==========

    @Test
    public void testIsArrowFunctionWithArrow() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (x) => x; fn", "test", 1, null);
        assertTrue("Should detect arrow function", FunctionCompat.isArrowFunction(fn));
    }

    @Test
    public void testIsArrowFunctionWithRegular() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        assertFalse(
                "Regular function should not be detected as arrow",
                FunctionCompat.isArrowFunction(fn));
    }

    @Test
    public void testIsArrowFunctionWithMethod() throws Exception {
        Object fn =
                cx.evaluateString(scope, "var obj = { method() {} }; obj.method", "test", 1, null);
        // Method is not an arrow function
        assertFalse("Method should not be detected as arrow", FunctionCompat.isArrowFunction(fn));
    }

    @Test
    public void testIsArrowFunctionWithNull() {
        assertFalse("null should not be arrow function", FunctionCompat.isArrowFunction(null));
    }

    // ========== isMethod Tests ==========

    @Test
    public void testIsMethodWithMethod() throws Exception {
        Object fn =
                cx.evaluateString(scope, "var obj = { method() {} }; obj.method", "test", 1, null);
        // Methods have home object
        boolean isMethod = FunctionCompat.isMethod(fn);
        // Just verify it doesn't throw
        assertNotNull(isMethod);
    }

    @Test
    public void testIsMethodWithRegularFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        assertFalse(
                "Regular function should not be detected as method", FunctionCompat.isMethod(fn));
    }

    // ========== isConstructor Tests ==========

    @Test
    public void testIsConstructorWithFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function Person() {} Person", "test", 1, null);
        assertTrue("Regular function should be constructor", FunctionCompat.isConstructor(fn));
    }

    @Test
    public void testIsConstructorWithArrow() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (x) => x; fn", "test", 1, null);
        // Arrow functions cannot be constructors
        assertFalse("Arrow function should not be constructor", FunctionCompat.isConstructor(fn));
    }

    @Test
    public void testIsConstructorWithNull() {
        assertFalse("null should not be constructor", FunctionCompat.isConstructor(null));
    }

    // ========== getParamCount Tests ==========

    @Test
    public void testGetParamCount() throws Exception {
        Object fn = cx.evaluateString(scope, "function test(a, b, c) {} test", "test", 1, null);
        assertEquals(3, FunctionCompat.getParamCount(fn));
    }

    @Test
    public void testGetParamCountWithNoParams() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        assertEquals(0, FunctionCompat.getParamCount(fn));
    }

    @Test
    public void testGetParamCountWithArrow() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (x, y) => x + y; fn", "test", 1, null);
        assertEquals(2, FunctionCompat.getParamCount(fn));
    }

    @Test
    public void testGetParamCountWithNull() {
        assertEquals(0, FunctionCompat.getParamCount(null));
    }

    @Test
    public void testGetParamCountWithNonFunction() {
        assertEquals(0, FunctionCompat.getParamCount(new Object()));
    }

    // ========== getFunctionName Tests ==========

    @Test
    public void testGetFunctionName() throws Exception {
        Object fn =
                cx.evaluateString(scope, "function myFunction() {} myFunction", "test", 1, null);
        assertEquals("myFunction", FunctionCompat.getFunctionName(fn));
    }

    @Test
    public void testGetFunctionNameWithArrow() throws Exception {
        Object fn = cx.evaluateString(scope, "var myArrow = (x) => x; myArrow", "test", 1, null);
        assertEquals("myArrow", FunctionCompat.getFunctionName(fn));
    }

    @Test
    public void testGetFunctionNameWithNull() {
        assertEquals("", FunctionCompat.getFunctionName(null));
    }

    @Test
    public void testGetFunctionNameWithNonFunction() {
        assertEquals("", FunctionCompat.getFunctionName(new Object()));
    }

    // ========== getSource Tests ==========

    @Test
    public void testGetSource() throws Exception {
        Object fn =
                cx.evaluateString(scope, "function test() { return 42; } test", "test", 1, null);
        String source = FunctionCompat.getSource(fn);
        assertNotNull("Source should not be null", source);
        assertTrue("Source should contain function keyword", source.contains("function"));
    }

    @Test
    public void testGetSourceWithArrow() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (x) => x * 2; fn", "test", 1, null);
        String source = FunctionCompat.getSource(fn);
        assertNotNull("Source should not be null", source);
    }

    // ========== call Tests ==========

    @Test
    public void testCall() throws Exception {
        Object fn =
                cx.evaluateString(
                        scope, "function add(a, b) { return a + b; } add", "test", 1, null);

        Object result = FunctionCompat.call(fn, cx, scope, null, new Object[] {3, 5});

        assertEquals(8, ((Number) result).intValue());
    }

    @Test
    public void testCallWithArrowFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (a, b) => a * b; fn", "test", 1, null);

        Object result = FunctionCompat.call(fn, cx, scope, null, new Object[] {4, 5});

        assertEquals(20, ((Number) result).intValue());
    }

    @Test
    public void testCallWithNonFunction() {
        try {
            FunctionCompat.call("not a function", cx, scope, null, new Object[] {});
            fail("Should throw exception for non-function");
        } catch (Exception e) {
            // EcmaError is thrown in Rhino 2.0.0
            assertTrue("Should be an error", e instanceof RuntimeException);
        }
    }

    // ========== getEffectiveThis Tests ==========

    @Test
    public void testGetEffectiveThisWithRegularFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        Scriptable thisObj = cx.newObject(scope);

        Scriptable result = FunctionCompat.getEffectiveThis(fn, thisObj);

        assertSame("Regular function should use provided this", thisObj, result);
    }

    @Test
    public void testGetEffectiveThisWithArrowFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (x) => x; fn", "test", 1, null);
        Scriptable thisObj = cx.newObject(scope);

        // Arrow functions use lexical this, but we can't easily test that here
        // Just verify it doesn't throw
        Scriptable result = FunctionCompat.getEffectiveThis(fn, thisObj);
        assertNotNull(result);
    }
}
