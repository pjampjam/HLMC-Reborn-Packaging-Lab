package holylois;
public final class OnboardingTest {
    private static int checked;
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
        check(Discoveries.name("epic:small_plains_dungeon").equals("Small Plains Dungeon")&&Discoveries.name("epic:large_ice_dungeon").equals("Large Ice Dungeon")&&Discoveries.name("epic:sand_obelisk").equals("Sand Obelisk"),"Epic Dungeons are announced with their size");
        check(Discoveries.name("nova_structures:small_undead_crypt").equals("Small Undead Crypt")&&Discoveries.name("nova_structures:small_conduit_ruin_cold")==null,"Small crypts are announced, small ruins stay quiet");
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
        check(diamonds.size()==1&&diamonds.get(0).name().equals("Elza")&&diamonds.get(0).value()==7,"Diamond board sums ores and skips the owner and unknown players");
        String textureValue=java.util.Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"+"a".repeat(64)+"\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        check(SkinStats.texture(textureValue).equals("https://textures.minecraft.net/texture/"+"a".repeat(64)), "Skin texture export normalizes Mojang URL and drops other profile fields");
        check(SkinStats.texture("bad base64")==null, "Malformed skin texture stays private");
        check(!StatsVisibility.visible("PJAMPJAM")&&!StatsVisibility.visible("PJAMTEST")&&StatsVisibility.visible("Elza"),"Stats exclusion ignores capitals");
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
        var support=DonateCommand.message().getString();
        check(DonateCommand.WALLETS.size()==6&&DonateCommand.WALLETS.stream().anyMatch(w->w.address().startsWith("T")&&w.label().equals("TRON"))&&DonateCommand.WALLETS.stream().allMatch(w->support.contains(w.address()))&&support.contains("never buys anything"),"Support lists every address and promises no perks");
        var land=new Claims.Config();
        check(Claims.price(land,0)==500&&Claims.price(land,9)==1759&&Claims.price(land,19)==7116,"Chunk prices start at 500 and grow 15% each");
        check(Claims.cost(land,0,3)==500+575+661&&Claims.refund(land,3,1)==Math.round(661*0.5),"Buying several chunks adds up; selling refunds half of the last price");
        check(Claims.earned(land,0)==0&&Claims.earned(land,72000L*2)==1&&Claims.earned(land,72000L*1000)==48,"One free chunk per two hours played, capped at 48");
        check(Claims.ticksToNext(land,72000L*3)==72000&&Claims.ticksToNext(land,72000L*1000)==-1,"Time to the next earned chunk");
        var ring=new java.util.Random(7); boolean inRing=true;
        for(int i=0;i<2000;i++){int[] xz=RandomTeleport.spot(ring,250,1800);double d=Math.hypot(xz[0],xz[1]);inRing&=d>=249&&d<=1801;}
        check(inRing,"Random teleport stays between 250 and 1800 blocks from spawn");
        check(SupportCommand.category("grief someone broke my farm").equals("grief")&&SupportCommand.category("BUG chest eats items").equals("bug")&&SupportCommand.category("hello?").equals("other"),"Support request categories come from the first word");
        check(CombatTag.BLOCKED.contains("rtp")&&CombatTag.BLOCKED.contains("home")&&!CombatTag.BLOCKED.contains("support"),"Combat blocks teleports but never /support");
        for (String command : java.util.List.of("home tp base", "/Home", "essentialcommands:tpaccept Elza", "randomteleport", " /rtp ", "tpa\tElza", "spawn", "back", "warp tp town", "logout", "/LOGOUT", "easyauth:logout"))
            check(CombatTag.blocksCommand(command), "Combat recognizes " + command);
        for (String command : java.util.List.of("support help", "tpdeny Elza", "rules", "homework", "logouthelp", "tp Elza 0 80 0"))
            check(!CombatTag.blocksCommand(command), "Combat preserves " + command);
        check(Redeem.code("test-secret","2026-10-05").equals("HL-QZB2-QA3Q")&&Redeem.code("test-secret","2026-10-06").equals("HL-7AB3-Z0G0"),"Daily code matches the website's HMAC function");
        check(Redeem.normalize("hl-qzb2 qa3q").equals(Redeem.normalize("HL-QZB2-QA3Q"))&&Redeem.normalize("HL-0O1I").equals(Redeem.normalize("HL-001L")),"Typed codes ignore case, spaces, dashes and look-alikes");
        var noon=java.time.LocalDateTime.of(2026,10,6,12,0); var early=java.time.LocalDateTime.of(2026,10,6,0,30);
        check(Redeem.matches("test-secret","HL-7AB3-Z0G0",noon)&&!Redeem.matches("test-secret","HL-QZB2-QA3Q",noon)&&Redeem.matches("test-secret","HL-QZB2-QA3Q",early)&&!Redeem.matches("test-secret","HL-0000-0000",noon),"Today's code works, yesterday's only until 01:00 Riga time");
        check(Redeem.eligibility(3599,9999).contains("2 hours")&&Redeem.eligibility(7200,1199).contains("1 min more today")&&Redeem.eligibility(7200,1200)==null&&Redeem.eligibility(50000,0).contains("20 min more today"),"Codes need 2 h active play and 20 active minutes that day");
        check(Redeem.duration(4500).equals("1h 15m")&&Redeem.duration(30).equals("1 min"),"Redeem waits read as hours and minutes");
        var player=java.util.UUID.fromString("00000000-0000-0000-0000-000000000042");
        check(Redeem.roll("test-secret",player,"2026-10-05").equals(Redeem.roll("test-secret",player,"2026-10-05")),"The prize cannot be rerolled");
        var counts=new java.util.HashMap<String,Integer>();
        for(int i=0;i<4000;i++) counts.merge(Redeem.roll("test-secret",java.util.UUID.nameUUIDFromBytes(("p"+i).getBytes()),"2026-10-05"),1,Integer::sum);
        check(counts.getOrDefault("coins",0)>1700&&counts.getOrDefault("lootbox",0)>700&&counts.getOrDefault("legendary",0)<60&&counts.size()>=4,"Prize odds follow the weights (coins common, legendary very rare)");
        check(Afk.effective(72000,600)==60000&&Afk.effective(100,600)==0,"AFK seconds come off the played time and never below zero");
        check(!Quiet.allow("pjampjam left the game","multiplayer.player.left")&&!Quiet.allow("pjampjam has made the advancement [X]","chat.type.advancement.task")&&!Quiet.allow("pjampjam is now AFK.","")
            &&Quiet.allow("pjampjam fell from a high place","death.fell.accident.generic")&&Quiet.allow("Elza left the game","multiplayer.player.left")&&Quiet.allow("pjampjams cat left","")==true,"Quiet names hide joins, leaves, AFK and advancements but not deaths or other players");
        var loot=new DeathLoot.Tracker();var owner=java.util.UUID.randomUUID();var a=java.util.UUID.randomUUID();var b=java.util.UUID.randomUUID();var c=java.util.UUID.randomUUID();
        loot.track(owner,"minecraft:overworld",10,64,10,1000,java.util.List.of(a,b));loot.track(owner,"minecraft:overworld",500,64,500,2000,java.util.List.of());
        check(loot.state.active.size()==1,"A death without drops is not tracked");
        loot.merged(a,c);loot.removed(a);loot.removed(b);
        check(loot.state.gone.isEmpty()&&loot.state.active.size()==1,"Death loot merged into another stack keeps the marker");
        loot.removed(c);
        check(loot.state.gone.size()==1&&loot.state.active.isEmpty()&&loot.byItem.isEmpty(),"The marker goes once every drop is gone");
        var d=java.util.UUID.randomUUID();var e=java.util.UUID.randomUUID();
        loot.track(owner,"minecraft:overworld",10,64,10,3000,java.util.List.of(d));loot.track(owner,"minecraft:overworld",11,64,10,4000,java.util.List.of(e));loot.removed(d);
        check(loot.state.gone.size()==1,"A second death at the same spot with loot left keeps the marker");
        loot.state.active.get(0).time=0;loot.prune(DeathLoot.KEEP_ACTIVE_MS+1);
        check(loot.state.active.isEmpty()&&loot.byItem.isEmpty(),"Deaths in chunks nobody visits are forgotten after 30 days");
        check(!AudioUrlGuard.isPublic(ip("127.0.0.1"))&&!AudioUrlGuard.isPublic(ip("10.0.0.5"))&&!AudioUrlGuard.isPublic(ip("169.254.169.254"))&&!AudioUrlGuard.isPublic(ip("100.64.1.1"))&&!AudioUrlGuard.isPublic(ip("192.168.1.1"))&&!AudioUrlGuard.isPublic(ip("0.0.0.0")),"Audio links never reach local, private or cloud metadata IPv4 addresses");
        check(!AudioUrlGuard.isPublic(ip("::1"))&&!AudioUrlGuard.isPublic(ip("fd12::1"))&&!AudioUrlGuard.isPublic(ip("fe80::1"))&&!AudioUrlGuard.isPublic(ip("::ffff:10.0.0.1"))&&AudioUrlGuard.isPublic(ip("1.1.1.1"))&&AudioUrlGuard.isPublic(ip("2606:4700::1111")),"IPv6 private and mapped addresses are blocked, public ones pass");
        check(rejects("http://example.com/a.mp3")&&rejects("file:///etc/passwd")&&rejects("https://user:pw@example.com/a.mp3")&&!rejects("https://example.com/a.mp3"),"Only plain https links are accepted");
        check(AudioUrlGuard.waitMs(null,5000)==0&&AudioUrlGuard.waitMs(1000L,6000)==15000&&AudioUrlGuard.waitMs(1000L,30000)==0,"One audio link per player every 20 seconds");
        check(Seen.lastSeen("Bob",0,3*86_400_000L+5000).startsWith("Bob was last on 3 days ago")&&Seen.lastSeen("Bob",0,90*60_000L).contains("1h 30m ago"),"/seen says how long ago a player was on");
        System.out.println("Passed " + checked + " onboarding, AFK ledger, secret code and quiet name, greeting, name day, bot wall, leaderboard, discovery, death, daily reward, chair, holiday, donate, land, rtp, support and combat checks");
    }
    private static java.net.InetAddress ip(String text) {try{return java.net.InetAddress.getByName(text);}catch(Exception e){throw new AssertionError(e);}}
    private static boolean rejects(String url) {try{AudioUrlGuard.parse(url);return false;}catch(IllegalArgumentException e){return true;}}
    private static void check(boolean value,String message) {checked++;if(!value)throw new AssertionError(message);}
}
