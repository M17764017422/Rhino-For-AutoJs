import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.*;

public class ParsePrint {
    public static void main(String[] args) throws Exception {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setLanguageVersion(Context.VERSION_ES6);
        Parser parser = new Parser(env);
        
        String script = "class A { accessor x = 10; }";
        try {
            AstRoot root = parser.parse(script, "test", 1);
            System.out.println("Parse successful!");
            System.out.println("First statement: " + root.getFirstChild().getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("Parse error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
