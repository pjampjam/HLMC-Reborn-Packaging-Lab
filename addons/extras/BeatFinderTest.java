package holylois.boombox;

import java.util.Random;

public final class BeatFinderTest {
    private static void check(boolean v, String label) { if (!v) throw new IllegalStateException(label); }

    /** 20 s of 48 kHz audio in 20 ms frames: a kick every beat over a busy bed; counts the beats found and how many land on a kick. */
    private static int[] run(int bpm, double kick, double bed, double gain) {
        var finder = new BeatFinder();
        var random = new Random(7);
        int rate = 48000, frame = 960, found = 0, onKick = 0;
        double beatSeconds = 60.0 / bpm;
        for (int f = 0; f < 1000; f++) {
            short[] audio = new short[frame];
            boolean kickFrame = false;
            for (int i = 0; i < frame; i++) {
                double t = (f * frame + i) / (double) rate, since = t % beatSeconds;
                if (since < 0.02) kickFrame = true;
                double k = since < 0.15 ? kick * Math.exp(-since * 30) * Math.sin(2 * Math.PI * 55 * since) : 0;
                double hats = bed * (random.nextDouble() * 2 - 1) + bed * 0.6 * Math.sin(2 * Math.PI * 440 * t) + bed * 0.4 * Math.sin(2 * Math.PI * 180 * t);
                audio[i] = (short) Math.max(-32768, Math.min(32767, (k + hats) * gain * 32767));
            }
            if (finder.frame(audio, f * 20L)) { found++; if (kickFrame) onKick++; }
        }
        return new int[] {found, onKick};
    }

    public static void main(String[] args) {
        // 120 BPM for 20 s is 40 kicks.
        int[] loud = run(120, 0.6, 0.25, 1.0), quiet = run(120, 0.6, 0.25, 0.05), compressed = run(120, 0.3, 0.45, 1.0);
        check(loud[0] >= 32 && loud[1] >= loud[0] * 0.9, "loud track: most kicks found and on the beat " + loud[0] + "/" + loud[1]);
        check(quiet[0] >= 32 && quiet[1] >= quiet[0] * 0.9, "quiet station still gets its beats " + quiet[0] + "/" + quiet[1]);
        check(compressed[0] >= 24 && compressed[1] >= compressed[0] * 0.85, "compressed radio: beats still found " + compressed[0] + "/" + compressed[1]);
        int[] silence = run(120, 0, 0, 0);
        check(silence[0] == 0, "no notes in silence");
        int[] flat = run(120, 0, 0.3, 1.0);
        check(flat[0] < 15, "a bed without kicks gives few notes " + flat[0]);
        System.out.println("Beat finder: loud " + loud[0] + ", quiet " + quiet[0] + ", compressed " + compressed[0] + ", flat " + flat[0] + " of 40 kicks.");
    }
}
