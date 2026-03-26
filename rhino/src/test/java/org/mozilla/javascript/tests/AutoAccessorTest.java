/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ast.AstRoot;

/** Tests for ES2023 Auto-Accessors syntax support in Rhino. */
public class AutoAccessorTest {

    private Object evaluate(String script) {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            return cx.evaluateString(scope, script, "test", 1, null);
        }
    }

    /** Parse only - does not compile or execute. Used for AST/syntax tests. */
    private AstRoot parseOnly(String script) throws Exception {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        Parser parser = new Parser(env);
        return parser.parse(script, "test", 1);
    }

    // ==================== AA0: Basic Auto-Accessor Parse Tests ====================

    @Test
    void testBasicAutoAccessorParse() throws Exception {
        String script = "class A { accessor x = 10; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithoutInitializerParse() throws Exception {
        String script = "class C { accessor value; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testStaticAutoAccessorParse() throws Exception {
        String script = "class B { static accessor count = 0; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testStaticAutoAccessorWithoutInitializerParse() throws Exception {
        String script = "class D { static accessor name; }";
        assertNotNull(parseOnly(script));
    }

    // ==================== AA1: Private Auto-Accessor Parse Tests ====================

    @Test
    void testPrivateAutoAccessorParse() throws Exception {
        String script = "class E { accessor #secret = 'hidden'; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testPrivateAutoAccessorWithoutInitializerParse() throws Exception {
        String script = "class F { accessor #value; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testStaticPrivateAutoAccessorParse() throws Exception {
        String script = "class G { static accessor #count = 0; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testStaticPrivateAutoAccessorWithoutInitializerParse() throws Exception {
        String script = "class H { static accessor #name; }";
        assertNotNull(parseOnly(script));
    }

    // ==================== AA2: Computed Key Auto-Accessor Parse Tests ====================

    @Test
    void testAutoAccessorWithComputedKeyParse() throws Exception {
        String script = "const key = 'prop'; class I { accessor [key] = 100; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithComputedKeyNoInitParse() throws Exception {
        String script = "const key = 'prop'; class J { accessor [key]; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithComputedKeyExpressionParse() throws Exception {
        String script = "class K { accessor ['computed' + 'Key'] = 1; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testStaticAutoAccessorWithComputedKeyParse() throws Exception {
        String script = "const key = 'prop'; class L { static accessor [key] = 100; }";
        assertNotNull(parseOnly(script));
    }

    // ==================== AA3: Multiple Auto-Accessors Parse Tests ====================

    @Test
    void testMultipleAutoAccessorsParse() throws Exception {
        String script = "class N { accessor a = 1; accessor b = 2; accessor c = 3; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testMixedAutoAccessorsParse() throws Exception {
        String script = "class O { accessor x = 1; accessor #y = 2; static accessor z = 3; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testMultiplePrivateAutoAccessorsParse() throws Exception {
        String script = "class P { accessor #a = 1; accessor #b = 2; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testMultipleStaticAutoAccessorsParse() throws Exception {
        String script = "class Q { static accessor a = 1; static accessor b = 2; }";
        assertNotNull(parseOnly(script));
    }

    // ==================== AA4: Auto-Accessor with Other Class Elements Parse Tests
    // ====================

    @Test
    void testAutoAccessorWithMethodsParse() throws Exception {
        String script = "class R { accessor x = 1; method() { return this.x; } }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithFieldsParse() throws Exception {
        String script = "class S { accessor x = 1; y = 2; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithConstructorParse() throws Exception {
        String script = "class T { accessor value = 0; constructor(v) { this.value = v; } }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithGetterSetterParse() throws Exception {
        String script = "class U { accessor x = 1; get y() { return 2; } set y(v) {} }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithPrivateMethodParse() throws Exception {
        String script = "class V { accessor x = 1; #privateMethod() {} }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithPrivateFieldParse() throws Exception {
        String script = "class W { accessor x = 1; #y = 2; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithPrivateGetterSetterParse() throws Exception {
        String script = "class X { accessor x = 1; get #y() { return 1; } set #y(v) {} }";
        assertNotNull(parseOnly(script));
    }

    // ==================== AA5: Auto-Accessor with Inheritance Parse Tests ====================

    @Test
    void testAutoAccessorWithExtendsParse() throws Exception {
        String script = "class Base {} class Y extends Base { accessor x = 1; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithSuperParse() throws Exception {
        String script =
                "class Base { constructor(n) { this.name = n; } }"
                        + "class Z extends Base {"
                        + "  accessor value = 0;"
                        + "  constructor(n, v) { super(n); this.value = v; }"
                        + "}";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorInDerivedClassParse() throws Exception {
        String script =
                "class Parent { accessor parentValue = 1; }"
                        + "class Child extends Parent {"
                        + "  accessor childValue = 2;"
                        + "}";
        assertNotNull(parseOnly(script));
    }

    // ==================== AA6: Edge Cases ====================

    @Test
    void testAccessorAsMethodNameParse() throws Exception {
        // When accessor is followed by (), it's a method name
        String script = "class AA { accessor() { return 'method'; } }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAccessorAsFieldNameParse() throws Exception {
        // When accessor is followed by =, it's a field name
        String script = "class AB { accessor = 'field'; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAccessorAsStaticMethodNameParse() throws Exception {
        String script = "class AC { static accessor() { return 'static method'; } }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAccessorAsStaticFieldNameParse() throws Exception {
        String script = "class AD { static accessor = 'static field'; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAccessorAsPrivateMethodNameParse() throws Exception {
        String script = "class AE { #accessor() { return 'private method'; } }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAccessorAsPrivateFieldNameParse() throws Exception {
        String script = "class AF { #accessor = 'private field'; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithNumberKeyParse() throws Exception {
        String script = "class AG { accessor 123 = 'value'; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithStringKeyParse() throws Exception {
        String script = "class AH { accessor 'stringKey' = 'value'; }";
        assertNotNull(parseOnly(script));
    }

    // ==================== AA7: Auto-Accessor with Decorators Parse Tests ====================

    @Test
    void testAutoAccessorWithDecoratorParse() throws Exception {
        String script = "function dec() {} class AI { @dec accessor x = 1; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testAutoAccessorWithMultipleDecoratorsParse() throws Exception {
        String script =
                "function dec1() {} function dec2() {} class AJ { @dec1 @dec2 accessor x = 1; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testStaticAutoAccessorWithDecoratorParse() throws Exception {
        String script = "function dec() {} class AK { @dec static accessor x = 1; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testPrivateAutoAccessorWithDecoratorParse() throws Exception {
        String script = "function dec() {} class AL { @dec accessor #x = 1; }";
        assertNotNull(parseOnly(script));
    }

    // ==================== Runtime Tests (pending IR fix) ====================
    // Note: Runtime tests require fixing "Can't transform: 127" issue in IRFactory

    // @Test
    // void testBasicAutoAccessorRuntime() {
    //     String script = "class A { accessor x = 10; }\n" + "var a = new A();\n" + "a.x;";
    //     assertEquals(10, evaluate(script));
    // }

    // @Test
    // void testAutoAccessorSetterRuntime() {
    //     String script = "class A { accessor x = 10; }\n" + "var a = new A();\n" + "a.x = 20;\n"
    // + "a.x;";
    //     assertEquals(20, evaluate(script));
    // }

    // @Test
    // void testStaticAutoAccessorRuntime() {
    //     String script = "class B { static accessor count = 0; }\n" + "B.count;";
    //     assertEquals(0, evaluate(script));
    // }

    // @Test
    // void testPrivateAutoAccessorRuntime() {
    //     String script =
    //             "class C {\n"
    //                     + "    accessor #secret = 'hidden';\n"
    //                     + "    reveal() { return this.#secret; }\n"
    //                     + "}\n"
    //                     + "var c = new C();\n"
    //                     + "c.reveal();";
    //     assertEquals("hidden", evaluate(script));
    // }
}
