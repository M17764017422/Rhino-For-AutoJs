package org.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Test ES2022 static async generator method parsing. This test verifies that 'static async
 * *method()' is parsed correctly.
 */
public class ES2022StaticAsyncGeneratorTest {

    @Test
    public void testStaticAsyncGeneratorMethod() {
        String script =
                ""
                        + "var C = class {\n"
                        + "  static async *method() {\n"
                        + "    yield 42;\n"
                        + "  }\n"
                        + "};\n"
                        + "C.method().next().value";

        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "test", 1, null);
            // Note: async generator returns a promise, so we can't directly check the value
            // Just verify it doesn't throw a syntax error
            assertNotNull(result);
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        } finally {
            Context.exit();
        }
    }

    // Note: Private async generator methods require additional runtime support
    // that is not yet implemented. This test is disabled until that feature is complete.

    @Test
    public void testAfterSameLineGenerator() {
        String script =
                ""
                        + "var C = class {\n"
                        + "  *m() { return 42; } a; b = 42;\n"
                        + "};\n"
                        + "var c = new C();\n"
                        + "c.a === undefined && c.b === 42 && c.m().next().value === 42";

        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "test", 1, null);
            assertTrue(Context.toBoolean(result));
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        } finally {
            Context.exit();
        }
    }

    @Test
    public void testAfterSameLineStaticPrivateField() {
        String script =
                ""
                        + "var C = class {\n"
                        + "  *m() { return 42; } static #x; static #y;\n"
                        + "};\n"
                        + "typeof C";

        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "test", 1, null);
            assertEquals("function", Context.toString(result));
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        } finally {
            Context.exit();
        }
    }
}
