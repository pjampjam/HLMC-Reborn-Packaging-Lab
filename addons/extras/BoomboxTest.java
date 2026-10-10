package holylois.boombox;

/** Streams a few configured stations for five seconds each and checks that real audio frames arrive. */
public final class BoomboxTest {
    public static void main(String[] args) throws Exception {
        if (!RadioStream.IcyStream.title("StreamTitle='Artist - Song';StreamUrl='';").equals("Artist - Song")) throw new AssertionError("ICY title");
        if (!PackCheck.older("1.7.4", "1.7.5") || PackCheck.older("1.7.5", "1.7.5") || PackCheck.older("1.10.0", "1.9.9") || !PackCheck.older("1.7", "1.7.1"))
            throw new AssertionError("pack version compare");
        if (Boombox.range(1) != 16f || Boombox.range(10) != 48f || Boombox.range(5) <= Boombox.range(4) || Boombox.range(99) != 48f) throw new AssertionError("boombox range 16 to 48");
        float fade = 0;
        for (int tick = 0; tick < BoomboxInEar.FADE_TICKS / 2; tick++) fade = BoomboxInEar.step(fade, true);
        float half = fade;
        for (int tick = 0; tick < BoomboxInEar.FADE_TICKS; tick++) fade = BoomboxInEar.step(fade, true);
        float full = fade;
        for (int tick = 0; tick < BoomboxInEar.FADE_TICKS; tick++) fade = BoomboxInEar.step(fade, false);
        if (Math.abs(half - 0.5f) > 1e-4 || full != 1f || fade > 1e-4f || BoomboxInEar.weight(0) != 0 || BoomboxInEar.weight(1) != 1
            || Math.abs(BoomboxInEar.weight(0.5f) - 0.5) > 1e-9 || BoomboxInEar.weight(0.25f) >= 0.25 || !BoomboxInEar.music("boombox")
            || !BoomboxInEar.music("music_discs") || BoomboxInEar.music("goat_horns") || BoomboxInEar.music(null))
            throw new AssertionError("boombox music fades into the ears over two seconds of cinematic and back out after it");
        var mods = ModCheck.parse("{\"mode\":\"enforce\",\"exempt\":[\"Owner\"],\"allowed\":[\"fabricloader\",\"sodium\",\"holylois-extras\"]}");
        var verdict = ModCheck.check(mods, java.util.List.of("fabricloader", "sodium", "xray", "meteor-client"));
        if (!verdict.unknown().equals(new java.util.TreeSet<>(java.util.List.of("meteor-client", "xray"))) || !verdict.missing().equals(new java.util.TreeSet<>(java.util.List.of("holylois-extras")))
            || !mods.legacy().equals("allow") || !mods.missing().equals("warn") || !mods.exempt().contains("Owner")) throw new AssertionError("mod check verdict");
        if (!ModCheck.check(mods, java.util.List.of("fabricloader", "sodium", "holylois-extras")).unknown().isEmpty()) throw new AssertionError("mod check clean client");
        int ok = 0;
        for (var station : new Boombox.Config().stations.subList(0, Math.min(args.length > 0 ? Integer.parseInt(args[0]) : 3, new Boombox.Config().stations.size()))) {
            var stream = new RadioStream(station.url, 0.55f);
            stream.start();
            Thread.sleep(5000);
            int frames = 0, loud = 0;
            for (int i = 0; i < 60; i++) {
                var frame = stream.next();
                frames++;
                long energy = 0; for (short s : frame) energy += Math.abs(s);
                if (energy / frame.length > 50) loud++;
            }
            stream.stop();
            System.out.println(station.name + ": " + loud + "/" + frames + " frames with sound, now playing: " + stream.nowPlaying);
            if (loud > 20) ok++;
        }
        System.out.println(ok + " stations delivered audio");
        if (ok == 0) throw new AssertionError("No station delivered audio");
    }
}
