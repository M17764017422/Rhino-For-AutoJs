import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class test_debug {
    public static void main(String[] args) {
        // Test 1: Simple class
        test("1. Simple class", "class A { x = 10; }");
        
        // Test 2: Class with private field
        test("2. Class with private field", "class B { #x = 10; }");
        
        // Test 3: Class with auto-accessor (parse only)
        test("3. Class with auto-accessor", "class C { accessor x = 10; }");
        
        // Test 4: Create instance of class with auto-accessor
        test("4. Create instance", "class D { accessor x = 10; } var d = new D();");
        
        // Test 5: Access auto-accessor property
        // This is where the error should occur
        test("5. Access property", "class E { accessor x = 10; } var e = new E(); e.x;");
    }
    
    static void test(String name, String script) {
        System.out.println("\n=== " + name + " ===");
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "test", 1, null);
            System.out.println("Result: " + result);
            System.out.println("SUCCESS");
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            // Print first 5 stack frames
            StackTraceElement[] stack = e.getStackTrace();
            for (int i = 0; i < Math.min(5, stack.length); i++) {
                System.out.println("  at " + stack[i]);
            }
        }
    }
}
