import org.mozilla.javascript.Token;

public class print_tokens {
    public static void main(String[] args) {
        System.out.println("Token.DOT = " + Token.DOT);
        System.out.println("Token.GETPROP = " + Token.GETPROP);
        System.out.println("Token.THIS = " + Token.THIS);
        System.out.println("Token.STRING = " + Token.STRING);
        System.out.println("Token.NAME = " + Token.NAME);
        System.out.println("Token.BLOCK = " + Token.BLOCK);
        System.out.println("Token.RETURN = " + Token.RETURN);
        System.out.println("Token.GET_PRIVATE_FIELD = " + Token.GET_PRIVATE_FIELD);
        System.out.println("Token.SET_PRIVATE_FIELD = " + Token.SET_PRIVATE_FIELD);
    }
}
