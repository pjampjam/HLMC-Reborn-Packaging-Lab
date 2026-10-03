package holylois;
public final class OnboardingTest {
    public static void main(String[] args) {
        check(!HolyLois.ready(true,false,100,0),"Unauthenticated players must never move");
        check(!HolyLois.ready(false,true,100,0),"Returning players must never move");
        check(!HolyLois.ready(true,true,2,0),"Authentication restoration grace period");
        check(HolyLois.ready(true,true,3,0),"Authenticated new player is eligible");
        check(!HolyLois.ready(true,true,100,3),"Failed placement retries are bounded");
        var quotes=Quotes.parse(java.util.List.of("# comment","","First | A","Second without author","  Third | C  "));
        check(quotes.size()==3,"Quote parser skips comments and blanks");
        check(Quotes.pick(quotes,0).startsWith("First")&&Quotes.pick(quotes,4).startsWith("Second"),"Quote rotates by day");
        check(Quotes.pick(quotes,-1).startsWith("Third"),"Quote index is safe for any day");
        check(Quotes.text("A | B").equals("A")&&Quotes.author("A | B").equals("B")&&Quotes.author("A").isEmpty(),"Quote fields split");
        check(HolyLois.greeting(true,"Lois").contains("Welcome to")&&HolyLois.greeting(false,"Lois").startsWith("Welcome back"),"Greeting varies");
        var days=NameDays.parse(java.util.List.of("# comment","10-03: Elza, Ilizana","06-24: Jānis","bad line","02-29:"));
        check(days.size()==2,"Name day parser skips comments, bad lines and empty days");
        check(NameDays.on(days,java.time.LocalDate.of(2026,10,3)).equals(java.util.List.of("Elza","Ilizana")),"Name day lookup by date");
        check(NameDays.join(java.util.List.of("A","B","C")).equals("A, B and C")&&NameDays.join(java.util.List.of("Elza")).equals("Elza"),"Name list reads naturally");
        check(NameDays.celebrating(days.get("06-24"),"janis_LV").orElse("").equals("Jānis"),"Username matches a name without diacritics");
        check(NameDays.celebrating(java.util.List.of("Ivo"),"Steve").isEmpty()&&NameDays.celebrating(java.util.List.of("Ivo"),"xIvo").isEmpty(),"Unrelated usernames are not congratulated");
        System.out.println("Passed 15 onboarding, quote, greeting and name day checks");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
