package holylois.auth;
public final class AuthPolicyTest {
    private static int checked;
    private static void check(boolean result) { checked++; if (!result) throw new AssertionError("Check " + checked + " failed"); }
    public static void main(String[] args) {
        check(AuthPolicy.mode(false,false,false)==2);
        check(AuthPolicy.mode(false,true,false)==1);
        check(AuthPolicy.mode(false,true,true)==1);
        check(AuthPolicy.mode(true,true,false)==0);
        check(AuthPolicy.mode(true,false,true)==3);
        check(!AuthPolicy.validate("abcd","abcd",true,5).isEmpty());
        check(AuthPolicy.validate("abcde","abcde",true,5).isEmpty());
        check(!AuthPolicy.validate("abcde","abcdf",true,5).isEmpty());
        check(!AuthPolicy.validate("ab cd","ab cd",true,5).isEmpty());
        check(AuthPolicy.validate("old","",false,5).isEmpty());
        check(AuthPolicy.argument("a\"b\\c").equals("\"a\\\"b\\\\c\""));
        check(AuthPolicy.randomRespawn(false,false));
        check(!AuthPolicy.randomRespawn(false,true));
        check(!AuthPolicy.randomRespawn(true,false));
        check(AuthPolicy.routineAuthNotice("\u00a76Use /register <password> <password> to claim this account."));
        check(AuthPolicy.routineAuthNotice("You are not authenticated!\nUse /login or /l to authenticate."));
        check(AuthPolicy.routineAuthNotice("You have a valid session. No need to log in."));
        check(!AuthPolicy.routineAuthNotice("Incorrect password!"));
        check(!AuthPolicy.routineAuthNotice("Welcome to Holy Lois: Reborn!"));
        check(!AuthPolicy.routineAuthNotice("Ask an admin how to use /register on another server."));
        check(!AuthPolicy.quietWorldAudio(0));
        check(AuthPolicy.quietWorldAudio(1) && AuthPolicy.quietWorldAudio(2) && AuthPolicy.quietWorldAudio(3));
        check(!AuthPolicy.quietWorldAudio(-1) && !AuthPolicy.quietWorldAudio(4));
        System.out.println(checked + " auth mode, validation, escaping and respawn checks passed.");
    }
}
