import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class test_simple {
    public static void main(String[] args) {
        // Test 1: Simple class
        String script1 = "class A { x = 10; }";
        test("Simple class with field", script1);
        
        // Test 2: Class with private field
        String script2 = "class B { #x = 10; }";
        test("Class with private field", script2);
        
        // Test 3: Class with auto-accessor (parse only)
        String script3 = "class C { accessor x = 10; }";
        test("Class with auto-accessor", script3);
        
        // Test 4: Auto-accessor runtime
        String script4 = "class D { accessor x = 10; } var d = new D();";
        test("Auto-accessor instance creation", script4);
        
        // Test 5: Auto-accessor property access
        String script5 = "class E { accessor x = 10; } var e = new E(); e.x;";
        test("Auto-accessor property read", script5);
    }
    
    static void test(String name, String script) {
        System.out.println("\n=== " + name + " ===");
        System.out.println("Script: " + script);
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "test", 1, null);
            System.out.println("Result: " + result);
            System.out.println("SUCCESS");
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }
}
