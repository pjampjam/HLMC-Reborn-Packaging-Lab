package holylois;
public final class OnboardingTest {
    public static void main(String[] args) {
        check(!HolyLois.ready(true,false,100,0),"Unauthenticated players must never move");
        check(!HolyLois.ready(false,true,100,0),"Returning players must never move");
        check(!HolyLois.ready(true,true,2,0),"Authentication restoration grace period");
        check(HolyLois.ready(true,true,3,0),"Authenticated new player is eligible");
        check(!HolyLois.ready(true,true,100,3),"Failed placement retries are bounded");
        System.out.println("Passed 5 onboarding authentication and eligibility checks");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
