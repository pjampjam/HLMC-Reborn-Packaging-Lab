package holylois;
public final class OnboardingTest {
    public static void main(String[] args) {
        check(!HolyLois.ready(true,false,100,0),"Unauthenticated players must never move");
        check(!HolyLois.ready(false,true,100,0),"Returning players must never move");
        check(!HolyLois.ready(true,true,2,0),"Authentication restoration grace period");
        check(HolyLois.ready(true,true,3,0),"Authenticated new player is eligible");
        check(!HolyLois.ready(true,true,100,3),"Failed placement retries are bounded");
        check(HolyLois.greeting(true,"Lois").contains("Welcome to")&&HolyLois.greeting(false,"Lois").startsWith("Welcome back"),"Greeting varies");
        var days=NameDays.parse(java.util.List.of("# comment","10-03: Elza, Ilizana","06-24: Jānis","bad line","02-29:"));
        check(days.size()==2,"Name day parser skips comments, bad lines and empty days");
        check(NameDays.on(days,java.time.LocalDate.of(2026,10,3)).equals(java.util.List.of("Elza","Ilizana")),"Name day lookup by date");
        check(NameDays.join(java.util.List.of("A","B","C")).equals("A, B and C")&&NameDays.join(java.util.List.of("Elza")).equals("Elza"),"Name list reads naturally");
        check(NameDays.celebrating(days.get("06-24"),"janis_LV").orElse("").equals("Jānis"),"Username matches a name without diacritics");
        check(NameDays.celebrating(java.util.List.of("Ivo"),"Steve").isEmpty()&&NameDays.celebrating(java.util.List.of("Ivo"),"xIvo").isEmpty(),"Unrelated usernames are not congratulated");
        var wall=BotWall.parse("{\"today\":1647,\"allTime\":5000,\"latest\":[\"admin\",\"§cevil\",\"ubuntu\",\"pi\"],\"top\":[{\"name\":\"root\",\"count\":9,\"lastSeen\":\"06:10\"}]}");
        check(wall.today==1647&&wall.top.get(0).count==9,"Bot wall snapshot parses");
        check(BotWall.latest(wall).equals("admin, cevil, ubuntu"),"Bot names are cleaned and limited to three");
        check(BotWall.clean("aaaaaaaaaaaaaaaaaaaaaaaa").length()==16&&BotWall.latest(BotWall.parse("{}")).equals("nobody yet"),"Long or missing bot names are safe");
        check(Discoveries.name("nova_structures:tavern_spruce").equals("Tavern")&&Discoveries.name("minecraft:ancient_city").equals("Ancient City"),"Notable structures get friendly names");
        check(Discoveries.name("towns_and_towers:exclusives/pillager_outpost_tudor").equals("Pillager Outpost")&&Discoveries.name("nova_structures:illager_manor").equals("Illager Manor"),"Modded names are cleaned");
        check(Discoveries.name("nova_structures:village_birch")==null&&Discoveries.name("minecraft:village_plains")==null&&Discoveries.name("nova_structures:well_oak")==null
            &&Discoveries.name("nova_structures:remnant_graveyard")==null&&Discoveries.name("minecraft:shipwreck")==null&&Discoveries.name("nova_structures:stray_camp")==null,"Villages and small structures stay quiet");
        check(Discoveries.name("nova_structures:remnant_taiga_castle").equals("Ruined Taiga Castle")&&Discoveries.article("Ancient City").equals("an"),"Big ruins and articles");
        var stats=new java.util.HashMap<String,com.google.gson.JsonObject>();
        stats.put("u1",com.google.gson.JsonParser.parseString("{\"minecraft:mined\":{\"minecraft:diamond_ore\":3,\"minecraft:deepslate_diamond_ore\":4},\"minecraft:custom\":{\"minecraft:play_time\":144000}}").getAsJsonObject());
        stats.put("u2",com.google.gson.JsonParser.parseString("{\"minecraft:mined\":{\"minecraft:diamond_ore\":9}}").getAsJsonObject());
        stats.put("u3",com.google.gson.JsonParser.parseString("{\"minecraft:mined\":{\"minecraft:diamond_ore\":99}}").getAsJsonObject());
        var boards=Leaderboards.compute(java.util.Map.of("u1","Elza","u2","pjampjam"),stats);
        var diamonds=boards.get(Leaderboards.CATEGORIES.get(1));
        check(diamonds.size()==2&&diamonds.get(0).name().equals("pjampjam")&&diamonds.get(1).value()==7,"Diamond board sums ores and skips unknown players");
        check(boards.get(Leaderboards.CATEGORIES.get(0)).get(0).value()==2,"Playtime is shown in hours");
        var viewer=java.util.UUID.randomUUID(); var first=Leaderboards.view(viewer,100);
        check(Leaderboards.view(viewer,120)==first&&Leaderboards.view(viewer,400)!=first,"Board changes only when the page comes back");
        check(HolyLois.deathText(1,-2,3," in the Nether").equals("You died at 1, -2, 3 in the Nether."),"Death coordinates read naturally");
        var today=java.time.LocalDate.of(2026,10,3);
        check(DailyRewards.nextStreak("2026-10-02",4,today)==5&&DailyRewards.nextStreak("2026-09-30",4,today)==1&&DailyRewards.nextStreak("",0,today)==1,"Streak continues only on consecutive days");
        check(DailyRewards.nextStreak("2026-10-03",4,today)==-1,"One gift per Riga day");
        check(DailyRewards.dayOfWeek(7)==7&&DailyRewards.dayOfWeek(8)==1&&DailyRewards.tier(7)==1&&DailyRewards.tier(14)==2&&DailyRewards.tier(99)==3,"Lootbox every 7th day, tier grows weekly up to 3");
        check(Achievements.chairStyle("mcwfurnitures:stripped_oak_modern_chair").equals("modern_chair")&&Achievements.chairStyle("mcwfurnitures:red_couch").equals("couch")
            &&Achievements.chairStyle("mcwfurnitures:oak_chair").equals("chair")&&Achievements.chairStyle("mcwfurnitures:oak_table")==null&&Achievements.chairStyle("minecraft:oak_stairs")==null,"Chair styles are recognised");
        check(ServerEvents.holiday(java.time.LocalDate.of(2026,11,18)).key().equals("independence")&&ServerEvents.holiday(java.time.LocalDate.of(2026,6,23)).key().equals("ligo")
            &&ServerEvents.holiday(java.time.LocalDate.of(2026,12,25)).lootbox()&&ServerEvents.holiday(java.time.LocalDate.of(2026,10,4))==null,"Latvian holidays by Riga date");
        check(DailyRewards.bar(3).getString().equals("■■■■■■✦")&&DailyRewards.bar(3).getSiblings().size()==7,"Streak bar has seven boxes ending in the lootbox star");
        var support=SupportCommand.message().getString();
        check(SupportCommand.WALLETS.size()==5&&SupportCommand.WALLETS.stream().allMatch(w->support.contains(w.address()))&&support.contains("never buys anything"),"Support lists every address and promises no perks");
        System.out.println("Passed 29 onboarding, greeting, name day, bot wall, leaderboard, discovery, death, daily reward, chair, holiday and support checks");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
