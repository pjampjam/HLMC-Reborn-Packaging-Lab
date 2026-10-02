package holylois.auth;

/** Pure validation. EasyAuth independently enforces authentication on the server. */
public final class AuthPolicy {
    private AuthPolicy() {}
    public static int mode(boolean authenticated, boolean registered, boolean placing) {
        return authenticated ? (placing ? 3 : 0) : (registered ? 1 : 2);
    }
    public static String validate(String password, String confirmation, boolean registering, int minimum) {
        if (password.isEmpty()) return "Enter your server password.";
        if (registering && password.length() < minimum) return "Use at least " + minimum + " characters.";
        if (password.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
            return "Use a password without spaces.";
        if (registering && !password.equals(confirmation)) return "The passwords do not match.";
        return "";
    }
    public static String argument(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    public static boolean randomRespawn(boolean alive, boolean usableSpawnPoint) {
        return !alive && !usableSpawnPoint;
    }
}
