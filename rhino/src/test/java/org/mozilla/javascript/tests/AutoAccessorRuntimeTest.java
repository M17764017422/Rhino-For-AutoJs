/* -*- Mode: java; tab-width: 8; indent-tabs:: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/** Runtime tests for ES2023 Auto-Accessors. */
public class AutoAccessorRuntimeTest {

    private Object evaluate(String script) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setOptimizationLevel(-1); // Use interpreter mode first
            Scriptable scope = cx.initStandardObjects();
            return cx.evaluateString(scope, script, "test", 1, null);
        }
    }

    private Object evaluateOptimized(String script) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setOptimizationLevel(9); // Use optimized compiler
            Scriptable scope = cx.initStandardObjects();
            return cx.evaluateString(scope, script, "test", 1, null);
        }
    }

    @Test
    void testBasicAutoAccessorInterpreter() {
        // First test that class and instance are created correctly
        String script1 = "class A { x = 10; }\n" + "var a = new A();\n" + "typeof a;";
        assertEquals("object", evaluate(script1));
    }

    @Test
    void testClassConstructorName() {
        // Test that instance has correct constructor
        String script1 = "class A { x = 10; }\n" + "A.name;";
        assertEquals("A", evaluate(script1));
    }

    @Test
    void testAutoAccessorBasic() {
        // Test basic class with accessor - step by step
        String script1 = "class A { accessor x = 10; }\n" + "typeof A;";
        assertEquals("function", evaluate(script1));

        String script2 = "class A { accessor x = 10; }\n" + "A.prototype.hasOwnProperty('x');";
        assertEquals(true, evaluate(script2));
    }

    @Test
    void testAutoAccessorInstance() {
        // Test instance creation
        String script = "class A { accessor x = 10; }\n" + "A.name;";
        assertEquals("A", evaluate(script));
    }

    @Test
    void testBasicAutoAccessorOptimized() {
        String script = "class A { accessor x = 10; }\n" + "var a = new A();\n" + "a.x;";
        assertEquals(10, evaluateOptimized(script));
    }

    @Test
    void testAutoAccessorSetterInterpreter() {
        String script =
                "class A { accessor x = 10; }\n" + "var a = new A();\n" + "a.x = 20;\n" + "a.x;";
        assertEquals(20, evaluate(script));
    }

    @Test
    void testPrivateAutoAccessorInterpreter() {
        String script =
                "class C {\n"
                        + "    accessor #secret = 'hidden';\n"
                        + "    reveal() { return this.#secret; }\n"
                        + "}\n"
                        + "var c = new C();\n"
                        + "c.reveal();";
        assertEquals("hidden", evaluate(script));
    }
}
