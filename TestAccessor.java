import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.*;

public class TestAccessor {
    public static void main(String[] args) throws Exception {
        Context cx = Context.enter();
        try {
            cx.setLanguageVersion(Context.VERSION_ES6);
            Scriptable scope = cx.initStandardObjects();
            
            String script = "class A { accessor x = 10; }; var a = new A(); a.x;";
            Object result = cx.evaluateString(scope, script, "test", 1, null);
            System.out.println("Result: " + result);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Context.exit();
        }
    }
}
