/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.compat;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;

/**
 * Tests for {@link NativeFunctionAdapter}.
 *
 * <p>Verifies the JSFunction to NativeFunction adapter:
 *
 * <ul>
 *   <li>wrap() creates adapter for JSFunction
 *   <li>unwrap() returns original function
 *   <li>isAdapter() detects adapter instances
 *   <li>Delegation methods work correctly
 * </ul>
 */
public class NativeFunctionAdapterTest {

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

    // ========== wrap() Tests ==========

    @Test
    public void testWrapJSFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);

        Object wrapped = NativeFunctionAdapter.wrap(fn);

        assertTrue("Wrapped object should be an adapter", NativeFunctionAdapter.isAdapter(wrapped));
        assertNotSame("Wrapped should be different from original", fn, wrapped);
    }

    @Test
    public void testWrapArrowFunction() throws Exception {
        Object fn = cx.evaluateString(scope, "var fn = (x) => x; fn", "test", 1, null);

        Object wrapped = NativeFunctionAdapter.wrap(fn);

        assertTrue(
                "Wrapped arrow function should be an adapter",
                NativeFunctionAdapter.isAdapter(wrapped));
    }

    @Test
    public void testWrapNonFunction() {
        Object obj = new Object();

        Object result = NativeFunctionAdapter.wrap(obj);

        assertSame("Non-function should be returned as-is", obj, result);
        assertFalse(
                "Non-function should not become adapter", NativeFunctionAdapter.isAdapter(result));
    }

    @Test
    public void testWrapNull() {
        Object result = NativeFunctionAdapter.wrap(null);
        assertNull("null should be returned as-is", result);
    }

    // ========== unwrap() Tests ==========

    @Test
    public void testUnwrapAdapter() throws Exception {
        Object original = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        Object wrapped = NativeFunctionAdapter.wrap(original);

        Object unwrapped = NativeFunctionAdapter.unwrap(wrapped);

        assertSame("Unwrapped should be original", original, unwrapped);
    }

    @Test
    public void testUnwrapNonAdapter() {
        Object obj = new Object();

        Object result = NativeFunctionAdapter.unwrap(obj);

        assertSame("Non-adapter should be returned as-is", obj, result);
    }

    @Test
    public void testUnwrapNull() {
        Object result = NativeFunctionAdapter.unwrap(null);
        assertNull("null should be returned as-is", result);
    }

    // ========== isAdapter() Tests ==========

    @Test
    public void testIsAdapterWithAdapter() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        Object wrapped = NativeFunctionAdapter.wrap(fn);

        assertTrue("Should detect adapter", NativeFunctionAdapter.isAdapter(wrapped));
    }

    @Test
    public void testIsAdapterWithOriginal() throws Exception {
        Object fn = cx.evaluateString(scope, "function test() {} test", "test", 1, null);

        assertFalse("Original should not be adapter", NativeFunctionAdapter.isAdapter(fn));
    }

    @Test
    public void testIsAdapterWithNull() {
        assertFalse("null should not be adapter", NativeFunctionAdapter.isAdapter(null));
    }

    @Test
    public void testIsAdapterWithNonFunction() {
        assertFalse(
                "Non-function should not be adapter",
                NativeFunctionAdapter.isAdapter(new Object()));
    }

    // ========== getDelegate() Tests ==========

    @Test
    public void testGetDelegate() throws Exception {
        Object original = cx.evaluateString(scope, "function test() {} test", "test", 1, null);
        Object wrapped = NativeFunctionAdapter.wrap(original);

        assertTrue(
                "wrapped should be NativeFunctionAdapter",
                wrapped instanceof NativeFunctionAdapter);

        BaseFunction delegate = ((NativeFunctionAdapter) wrapped).getDelegate();

        assertSame("Delegate should be original", original, delegate);
    }

    // ========== Delegation Tests ==========

    @Test
    public void testCallDelegation() throws Exception {
        Object original =
                cx.evaluateString(
                        scope, "function add(a, b) { return a + b; } add", "test", 1, null);
        Object wrapped = NativeFunctionAdapter.wrap(original);

        assertTrue(
                "Wrapped should be callable", wrapped instanceof org.mozilla.javascript.Callable);

        Object result =
                ((org.mozilla.javascript.Callable) wrapped)
                        .call(cx, scope, null, new Object[] {3, 5});

        assertEquals(8, ((Number) result).intValue());
    }

    @Test
    public void testGetFunctionNameDelegation() throws Exception {
        Object original = cx.evaluateString(scope, "function myFunc() {} myFunc", "test", 1, null);
        Object wrapped = NativeFunctionAdapter.wrap(original);

        assertTrue("Wrapped should be BaseFunction", wrapped instanceof BaseFunction);

        String name = ((BaseFunction) wrapped).getFunctionName();

        assertEquals("myFunc", name);
    }

    @Test
    public void testGetLengthDelegation() throws Exception {
        Object original =
                cx.evaluateString(scope, "function test(a, b, c) {} test", "test", 1, null);
        Object wrapped = NativeFunctionAdapter.wrap(original);

        assertTrue("Wrapped should be BaseFunction", wrapped instanceof BaseFunction);

        int length = ((BaseFunction) wrapped).getLength();

        assertEquals(3, length);
    }

    @Test
    public void testConstructDelegation() throws Exception {
        Object original =
                cx.evaluateString(
                        scope,
                        "function Person(name) { this.name = name; } Person",
                        "test",
                        1,
                        null);
        Object wrapped = NativeFunctionAdapter.wrap(original);

        assertTrue(
                "Wrapped should be org.mozilla.javascript.Function",
                wrapped instanceof org.mozilla.javascript.Function);

        Scriptable result =
                ((org.mozilla.javascript.Function) wrapped)
                        .construct(cx, scope, new Object[] {"Alice"});

        assertNotNull(result);
        assertEquals("Alice", org.mozilla.javascript.ScriptableObject.getProperty(result, "name"));
    }

    @Test
    public void testIsConstructorDelegation() throws Exception {
        Object original = cx.evaluateString(scope, "function Person() {} Person", "test", 1, null);
        Object wrapped = NativeFunctionAdapter.wrap(original);

        assertTrue("Wrapped should be BaseFunction", wrapped instanceof BaseFunction);

        boolean isCtor = ((BaseFunction) wrapped).isConstructor();

        assertTrue("Function should be constructor", isCtor);
    }

    // ========== Round-trip Tests ==========

    @Test
    public void testRoundTrip() throws Exception {
        Object original =
                cx.evaluateString(
                        scope, "function test(x) { return x * 2; } test", "test", 1, null);

        Object wrapped = NativeFunctionAdapter.wrap(original);
        Object unwrapped = NativeFunctionAdapter.unwrap(wrapped);

        assertSame("Round-trip should return original", original, unwrapped);

        // Double wrap
        Object wrappedAgain = NativeFunctionAdapter.wrap(wrapped);
        assertTrue(
                "Double wrap should still be adapter",
                NativeFunctionAdapter.isAdapter(wrappedAgain));
    }
}
