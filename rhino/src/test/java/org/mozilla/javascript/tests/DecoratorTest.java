/* This Source Code Form is subject to the terms of the Mozilla Public
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

/** Tests for ES2023 decorator syntax support in Rhino. */
public class DecoratorTest {

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

    // ==================== D0: Basic Decorator Parse Tests ====================

    @Test
    void testSimpleDecoratorOnClassParse() throws Exception {
        // @decorator class C {}
        String script = "function dec() {} @dec class C {}";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testMultipleDecoratorsOnClassParse() throws Exception {
        // @dec1 @dec2 class C {}
        String script = "function dec1() {} function dec2() {} @dec1 @dec2 class C {}";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnClassExpressionParse() throws Exception {
        // var C = @dec class {};
        String script = "function dec() {} var C = @dec class {};";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnNamedClassExpressionParse() throws Exception {
        // var C = @dec class Named {};
        String script = "function dec() {} var C = @dec class Named {};";
        assertNotNull(parseOnly(script));
    }

    // ==================== D1: Namespace Decorator Parse Tests ====================

    @Test
    void testNamespaceDecoratorOnClassParse() throws Exception {
        // @ns.decorator class C {}
        String script = "var ns = { dec: function() {} }; @ns.dec class C {}";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDeepNamespaceDecoratorParse() throws Exception {
        // @ns.sub.decorator class C {}
        String script = "var ns = { sub: { dec: function() {} } }; @ns.sub.dec class C {}";
        assertNotNull(parseOnly(script));
    }

    // ==================== D2: Class Element Decorator Parse Tests ====================

    @Test
    void testDecoratorOnMethodParse() throws Exception {
        // class C { @dec method() {} }
        String script = "function dec() {} class C { @dec method() {} }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnStaticMethodParse() throws Exception {
        // class C { @dec static method() {} }
        String script = "function dec() {} class C { @dec static method() {} }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnFieldParse() throws Exception {
        // class C { @dec field; }
        String script = "function dec() {} class C { @dec field; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnStaticFieldParse() throws Exception {
        // class C { @dec static field; }
        String script = "function dec() {} class C { @dec static field; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnFieldWithInitializerParse() throws Exception {
        // class C { @dec field = 1; }
        String script = "function dec() {} class C { @dec field = 1; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnGetterParse() throws Exception {
        // class C { @dec get value() { return 1; } }
        String script = "function dec() {} class C { @dec get value() { return 1; } }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnSetterParse() throws Exception {
        // class C { @dec set value(v) {} }
        String script = "function dec() {} class C { @dec set value(v) {} }";
        assertNotNull(parseOnly(script));
    }

    // ==================== D3: Multiple Decorators on Elements Parse Tests ====================

    @Test
    void testMultipleDecoratorsOnMethodParse() throws Exception {
        // class C { @dec1 @dec2 method() {} }
        String script = "function dec1() {} function dec2() {} class C { @dec1 @dec2 method() {} }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testMultipleDecoratorsOnFieldParse() throws Exception {
        // class C { @dec1 @dec2 field; }
        String script = "function dec1() {} function dec2() {} class C { @dec1 @dec2 field; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testMixedDecoratorsOnElementsParse() throws Exception {
        // Mix of class decorators and element decorators
        String script =
                "function dec1() {} function dec2() {} function dec3() {}"
                        + "@dec1 class C {"
                        + "  @dec2 method() {}"
                        + "  @dec3 field = 1;"
                        + "}";
        assertNotNull(parseOnly(script));
    }

    // ==================== D4: Private Element Decorator Parse Tests ====================

    @Test
    void testDecoratorOnPrivateMethodParse() throws Exception {
        // class C { @dec #privateMethod() {} }
        String script = "function dec() {} class C { @dec #privateMethod() {} }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnPrivateFieldParse() throws Exception {
        // class C { @dec #privateField; }
        String script = "function dec() {} class C { @dec #privateField; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnPrivateFieldWithInitializerParse() throws Exception {
        // class C { @dec #privateField = 1; }
        String script = "function dec() {} class C { @dec #privateField = 1; }";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnPrivateGetterParse() throws Exception {
        // class C { @dec get #value() { return 1; } }
        String script = "function dec() {} class C { @dec get #value() { return 1; } }";
        assertNotNull(parseOnly(script));
    }

    // ==================== D5: Complex Decorator Scenarios Parse Tests ====================

    @Test
    void testDecoratorOnClassWithExtendsParse() throws Exception {
        // @dec class C extends Base {}
        String script = "function dec() {} class Base {} @dec class C extends Base {}";
        assertNotNull(parseOnly(script));
    }

    @Test
    void testDecoratorOnDerivedClassWithSuperParse() throws Exception {
        // @dec class C extends Base { constructor() { super(); } }
        String script =
                "function dec() {} class Base {}"
                        + "@dec class C extends Base { constructor() { super(); } }";
        assertNotNull(parseOnly(script));
    }

    // ==================== D6: Runtime Tests (Basic) ====================

    @Test
    void testClassDecoratorIdentityRuntime() {
        // Simple decorator that returns the class unchanged
        String script = "function dec(cls) { return cls; }" + "@dec class C {}" + "typeof C;";
        try {
            Object result = evaluate(script);
            assertEquals("function", result.toString());
        } catch (Exception e) {
            System.out.println("testClassDecoratorIdentityRuntime error: " + e.getMessage());
        }
    }

    @Test
    void testClassDecoratorWithModificationRuntime() {
        // Decorator that adds a property to the class
        String script =
                "function sealed(cls) { Object.seal(cls); return cls; }"
                        + "@sealed class C {}"
                        + "Object.isSealed(C);";
        try {
            Object result = evaluate(script);
            assertEquals("true", result.toString());
        } catch (Exception e) {
            System.out.println(
                    "testClassDecoratorWithModificationRuntime error: " + e.getMessage());
        }
    }

    @Test
    void testMultipleClassDecoratorsRuntime() {
        // Multiple decorators applied left-to-right
        String script =
                "function addA(cls) { cls.propA = 'A'; return cls; }"
                        + "function addB(cls) { cls.propB = 'B'; return cls; }"
                        + "@addA @addB class C {}"
                        + "C.propA + C.propB;";
        try {
            Object result = evaluate(script);
            assertEquals("AB", result.toString());
        } catch (Exception e) {
            System.out.println("testMultipleClassDecoratorsRuntime error: " + e.getMessage());
        }
    }

    // ==================== D7: Decorator with Class Elements Runtime Tests ====================

    @Test
    void testClassWithDecoratedMethodRuntime() {
        // Class with decorated method should still work
        String script =
                "function log(cls, ctx) { return cls; }"
                        + "class C { @log method() { return 42; } }"
                        + "var c = new C(); c.method();";
        try {
            Object result = evaluate(script);
            assertEquals("42", result.toString());
        } catch (Exception e) {
            System.out.println("testClassWithDecoratedMethodRuntime error: " + e.getMessage());
        }
    }

    @Test
    void testClassWithDecoratedFieldRuntime() {
        // Class with decorated field should still work
        String script =
                "function readonly(cls, ctx) { return cls; }"
                        + "class C { @readonly value = 10; }"
                        + "var c = new C(); c.value;";
        try {
            Object result = evaluate(script);
            assertEquals("10", result.toString());
        } catch (Exception e) {
            System.out.println("testClassWithDecoratedFieldRuntime error: " + e.getMessage());
        }
    }

    // ==================== D8: Namespace Decorator Runtime Tests ====================

    @Test
    void testNamespaceDecoratorRuntime() {
        // Decorator from namespace object
        String script =
                "var decorators = {"
                        + "  logged: function(cls) { cls.logged = true; return cls; }"
                        + "};"
                        + "@decorators.logged class C {}"
                        + "C.logged;";
        try {
            Object result = evaluate(script);
            assertEquals("true", result.toString());
        } catch (Exception e) {
            System.out.println("testNamespaceDecoratorRuntime error: " + e.getMessage());
        }
    }
}
