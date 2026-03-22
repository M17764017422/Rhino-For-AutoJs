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
import org.mozilla.javascript.ScriptableObject;

/**
 * Tests for {@link RhinoCompat}.
 *
 * <p>Verifies the main compatibility layer entry point:
 *
 * <ul>
 *   <li>Initialization
 *   <li>Function type detection
 *   <li>Function invocation
 *   <li>Type conversion
 * </ul>
 */
public class RhinoCompatTest {

    private Context cx;
    private Scriptable scope;

    @Before
    public void setUp() {
        RhinoCompat.reset();
        cx = Context.enter();
        scope = new ImporterTopLevel(cx);
    }

    @After
    public void tearDown() {
        RhinoCompat.reset();
        Context.exit();
    }

    // ========== Initialization Tests ==========

    @Test
    public void testInit() {
        assertFalse("Should not be initialized before init()", RhinoCompat.isInitialized());

        RhinoCompat.init(cx, scope);

        assertTrue("Should be initialized after init()", RhinoCompat.isInitialized());
    }

    @Test
    public void testInitIdempotent() {
        RhinoCompat.init(cx, scope);
        RhinoCompat.init(cx, scope); // Second call should be no-op

        assertTrue("Should still be initialized", RhinoCompat.isInitialized());
    }

    @Test
    public void testInitWithScopeOnly() {
        assertFalse("Should not be initialized before init()", RhinoCompat.isInitialized());

        RhinoCompat.init(scope);

        assertTrue("Should be initialized after init(scope)", RhinoCompat.isInitialized());
    }

    @Test
    public void testReset() {
        RhinoCompat.init(cx, scope);
        assertTrue("Should be initialized", RhinoCompat.isInitialized());

        RhinoCompat.reset();

        assertFalse("Should not be initialized after reset()", RhinoCompat.isInitialized());
    }

    // ========== extend Function Injection Tests ==========

    @Test
    public void testExtendFunctionInjected() {
        RhinoCompat.init(cx, scope);

        Object extend = ScriptableObject.getProperty(scope, "extend");
        assertNotNull("extend function should be injected", extend);
        assertTrue("extend should be callable", extend instanceof org.mozilla.javascript.Callable);
    }

    @Test
    public void testJavaExtendInjected() {
        RhinoCompat.init(cx, scope);

        Object javaObj = ScriptableObject.getProperty(scope, "Java");
        assertNotNull("Java object should be injected", javaObj);
        assertTrue("Java should be Scriptable", javaObj instanceof Scriptable);

        Object extend = ScriptableObject.getProperty((Scriptable) javaObj, "extend");
        assertNotNull("Java.extend should be injected", extend);
    }

    // ========== Function Type Detection Tests ==========

    @Test
    public void testIsFunctionWithRegularFunction() throws Exception {
        Object result =
                cx.evaluateString(
                        scope, "function test(a, b) { return a + b; } test", "test", 1, null);
        assertTrue("Regular function should be detected", RhinoCompat.isFunction(result));
    }

    @Test
    public void testIsFunctionWithArrowFunction() throws Exception {
        Object result = cx.evaluateString(scope, "var fn = (x) => x * 2; fn", "test", 1, null);
        assertTrue("Arrow function should be detected as function", RhinoCompat.isFunction(result));
    }

    @Test
    public void testIsFunctionWithObject() throws Exception {
        Object result = cx.evaluateString(scope, "var obj = {x: 1}; obj", "test", 1, null);
        assertFalse("Object should not be detected as function", RhinoCompat.isFunction(result));
    }

    @Test
    public void testIsFunctionWithNull() {
        assertFalse("null should not be detected as function", RhinoCompat.isFunction(null));
    }

    @Test
    public void testIsCallableWithFunction() throws Exception {
        Object result = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        assertTrue("Function should be callable", RhinoCompat.isCallable(result));
    }

    @Test
    public void testIsCallableWithObject() throws Exception {
        Object result = cx.evaluateString(scope, "var obj = {}; obj", "test", 1, null);
        assertFalse("Object should not be callable", RhinoCompat.isCallable(result));
    }

    @Test
    public void testIsArrowFunctionWithArrow() throws Exception {
        Object result = cx.evaluateString(scope, "var fn = (x) => x; fn", "test", 1, null);
        assertTrue("Arrow function should be detected", RhinoCompat.isArrowFunction(result));
    }

    @Test
    public void testIsArrowFunctionWithRegular() throws Exception {
        Object result = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        assertFalse(
                "Regular function should not be detected as arrow",
                RhinoCompat.isArrowFunction(result));
    }

    @Test
    public void testIsGeneratorFunction() throws Exception {
        Object result =
                cx.evaluateString(scope, "function* gen() { yield 1; } gen", "test", 1, null);
        assertTrue(
                "Generator function should be detected", RhinoCompat.isGeneratorFunction(result));
    }

    @Test
    public void testIsBoundFunction() throws Exception {
        Object result =
                cx.evaluateString(scope, "function test() {} test.bind(null)", "test", 1, null);
        assertTrue("Bound function should be detected", RhinoCompat.isBoundFunction(result));
    }

    // ========== Function Invocation Tests ==========

    @Test
    public void testCall() throws Exception {
        Object fn =
                cx.evaluateString(
                        scope, "function add(a, b) { return a + b; } add", "test", 1, null);

        Object result = RhinoCompat.call(fn, cx, scope, null, new Object[] {3, 5});

        assertEquals(8, ((Number) result).intValue());
    }

    @Test
    public void testCallWithArrowFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (a, b) => a * b; fn", "test", 1, null);

        Object result = RhinoCompat.call(fn, cx, scope, null, new Object[] {4, 5});

        assertEquals(20, ((Number) result).intValue());
    }

    @Test
    public void testCallWithNonFunction() {
        try {
            RhinoCompat.call("not a function", cx, scope, null, new Object[] {});
            fail("Should throw exception for non-function");
        } catch (Exception e) {
            // EcmaError is thrown in Rhino 2.0.0
            assertTrue("Should be an error", e instanceof RuntimeException);
        }
    }

    @Test
    public void testConstruct() throws Exception {
        Object fn =
                cx.evaluateString(
                        scope,
                        "function Person(name) { this.name = name; } Person",
                        "test",
                        1,
                        null);

        Scriptable result = RhinoCompat.construct(fn, cx, scope, new Object[] {"Alice"});

        assertNotNull(result);
        assertEquals("Alice", ScriptableObject.getProperty(result, "name"));
    }

    // ========== Function Info Tests ==========

    @Test
    public void testGetParamCount() throws Exception {
        Object fn = cx.evaluateString(scope, "function test(a, b, c) {} test", "test", 1, null);
        assertEquals(3, RhinoCompat.getParamCount(fn));
    }

    @Test
    public void testGetParamCountWithArrow() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (x, y) => x + y; fn", "test", 1, null);
        assertEquals(2, RhinoCompat.getParamCount(fn));
    }

    @Test
    public void testGetFunctionName() throws Exception {
        Object fn = cx.evaluateString(scope, "function myFunc() {} myFunc", "test", 1, null);
        assertEquals("myFunc", RhinoCompat.getFunctionName(fn));
    }

    @Test
    public void testGetFunctionNameWithArrow() throws Exception {
        Object fn = cx.evaluateString(scope, "var myArrow = (x) => x; myArrow", "test", 1, null);
        assertEquals("myArrow", RhinoCompat.getFunctionName(fn));
    }

    // ========== Type Conversion Tests ==========

    @Test
    public void testWrapFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);

        Object wrapped = RhinoCompat.wrapFunction(fn);

        assertNotNull(wrapped);
        // Original should be preserved
        assertSame(fn, NativeFunctionAdapter.unwrap(wrapped));
    }

    @Test
    public void testUnwrapFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        Object wrapped = RhinoCompat.wrapFunction(fn);

        Object unwrapped = RhinoCompat.unwrapFunction(wrapped);

        assertSame(fn, unwrapped);
    }
}
