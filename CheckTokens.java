public class CheckTokens {
    public static void main(String[] args) {
        System.out.println("ACCESSOR = " + org.mozilla.javascript.Token.ACCESSOR);
        System.out.println("NAME = " + org.mozilla.javascript.Token.NAME);
        System.out.println("CLASS = " + org.mozilla.javascript.Token.CLASS);
        System.out.println("DECORATOR = " + org.mozilla.javascript.Token.DECORATOR);
        System.out.println("LAST_TOKEN = " + org.mozilla.javascript.Token.LAST_TOKEN);
        System.out.println("Token name(102) = " + org.mozilla.javascript.Token.typeToName(102));
        System.out.println("Token name(127) = " + org.mozilla.javascript.Token.typeToName(127));
        
        // Check ES2022 bytecode tokens
        System.out.println("NEW_CLASS = " + org.mozilla.javascript.Token.NEW_CLASS);
        System.out.println("GET_PRIVATE_FIELD = " + org.mozilla.javascript.Token.GET_PRIVATE_FIELD);
        System.out.println("SET_PRIVATE_FIELD = " + org.mozilla.javascript.Token.SET_PRIVATE_FIELD);
        System.out.println("SET_PRIVATE_FIELD_OP = " + org.mozilla.javascript.Token.SET_PRIVATE_FIELD_OP);
        System.out.println("LAST_BYTECODE_TOKEN = " + org.mozilla.javascript.Token.LAST_BYTECODE_TOKEN);
    }
}