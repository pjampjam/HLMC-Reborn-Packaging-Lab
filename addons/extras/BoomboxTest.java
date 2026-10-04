package holylois.boombox;

/** Streams a few configured stations for five seconds each and checks that real audio frames arrive. */
public final class BoomboxTest {
    public static void main(String[] args) throws Exception {
        if (!RadioStream.IcyStream.title("StreamTitle='Artist - Song';StreamUrl='';").equals("Artist - Song")) throw new AssertionError("ICY title");
        if (!PackCheck.older("1.7.4", "1.7.5") || PackCheck.older("1.7.5", "1.7.5") || PackCheck.older("1.10.0", "1.9.9") || !PackCheck.older("1.7", "1.7.1"))
            throw new AssertionError("pack version compare");
        if (Boombox.range(1) != 16f || Boombox.range(10) != 48f || Boombox.range(5) <= Boombox.range(4) || Boombox.range(99) != 48f) throw new AssertionError("boombox range 16 to 48");
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
