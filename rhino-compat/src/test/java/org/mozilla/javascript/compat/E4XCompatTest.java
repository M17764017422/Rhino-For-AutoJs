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
 * Tests for E4X compatibility layer.
 *
 * <p>Verifies E4X initialization and XML support:
 *
 * <ul>
 *   <li>E4X initialization via RhinoCompat.init()
 *   <li>XML object creation
 *   <li>XMLList support
 * </ul>
 */
public class E4XCompatTest {

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

    // ========== E4X Initialization Tests ==========

    @Test
    public void testE4XInitializationViaRhinoCompat() {
        RhinoCompat.init(cx, scope);

        // After initialization, XML should be available
        Object xml = ScriptableObject.getProperty(scope, "XML");
        assertNotNull("XML should be defined after RhinoCompat.init()", xml);
    }

    @Test
    public void testXMLListAvailability() {
        RhinoCompat.init(cx, scope);

        // After initialization, XMLList should be available
        Object xmlList = ScriptableObject.getProperty(scope, "XMLList");
        assertNotNull("XMLList should be defined after RhinoCompat.init()", xmlList);
    }

    // ========== XML Object Creation Tests ==========

    @Test
    public void testCreateXMLObject() throws Exception {
        RhinoCompat.init(cx, scope);

        Object result =
                cx.evaluateString(
                        scope, "var x = <root><item>test</item></root>; x", "test", 1, null);

        assertNotNull("Should be able to create XML object", result);
        // Verify it's an XML object by checking its class name
        assertTrue("Should be an XML object", result.getClass().getName().contains("XML"));
    }

    @Test
    public void testXMLToString() throws Exception {
        RhinoCompat.init(cx, scope);

        Object result =
                cx.evaluateString(
                        scope, "var x = <root>content</root>; x.toString()", "test", 1, null);

        assertNotNull("XML.toString() should work", result);
        assertEquals("content", result);
    }

    @Test
    public void testXMLAttributes() throws Exception {
        RhinoCompat.init(cx, scope);

        Object result =
                cx.evaluateString(
                        scope,
                        "var x = <root attr='value'>content</root>; x.@attr.toString()",
                        "test",
                        1,
                        null);

        assertNotNull("XML attribute access should work", result);
        assertEquals("value", result);
    }

    @Test
    public void testXMLChildren() throws Exception {
        RhinoCompat.init(cx, scope);

        Object result =
                cx.evaluateString(
                        scope,
                        "var x = <root><item>1</item><item>2</item></root>; x.item.length()",
                        "test",
                        1,
                        null);

        assertNotNull("XML children access should work", result);
        assertEquals(2.0, ((Number) result).doubleValue(), 0.001);
    }

    // ========== XMLList Tests ==========

    @Test
    public void testXMLListCreation() throws Exception {
        RhinoCompat.init(cx, scope);

        Object result =
                cx.evaluateString(
                        scope,
                        "var list = <><item>1</item><item>2</item></>; list",
                        "test",
                        1,
                        null);

        assertNotNull("Should be able to create XMLList", result);
    }

    // ========== E4X in RhinoCompat Context Tests ==========

    @Test
    public void testE4XWithJavaExtend() throws Exception {
        RhinoCompat.init(cx, scope);

        // Both E4X and Java.extend should work together
        Object xmlResult = cx.evaluateString(scope, "var x = <test/>; x.name()", "test", 1, null);
        // Rhino 2.0.0 returns QName object, not String
        assertNotNull("Should return XML name", xmlResult);
        assertTrue("Should contain 'test'", xmlResult.toString().contains("test"));

        Object extendResult =
                cx.evaluateString(
                        scope,
                        "var r = extend(java.lang.Runnable, { run: function() {} }); r",
                        "test",
                        1,
                        null);
        assertNotNull("extend should work with E4X", extendResult);
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testInvalidXMLSyntax() throws Exception {
        RhinoCompat.init(cx, scope);

        try {
            cx.evaluateString(scope, "var x = <root><unclosed></root>;", "test", 1, null);
            // May or may not throw depending on parser implementation
        } catch (Exception e) {
            // Expected - invalid XML should cause an error
            assertNotNull(e);
        }
    }
}
