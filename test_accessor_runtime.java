import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class test_accessor_runtime {
    public static void main(String[] args) {
        String script = "class A { accessor x = 10; }\n" +
                        "var a = new A();\n" +
                        "a.x;";
        
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            Object result = cx.evaluateString(scope, script, "test", 1, null);
            System.out.println("Result: " + result);
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
