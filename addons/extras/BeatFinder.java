package holylois.boombox;

/**
 * Finds beats in a boombox stream, one 20 ms voice chat frame at a time: bass energy (a one-pole low-pass keeps the kick drum)
 * against the last ~0.9 s. A beat is a frame clearly above that average and its spread, so quiet and loud, compressed and
 * dynamic stations all get notes, at any frame rate (owner round 4: notes were often missing).
 */
public final class BeatFinder {
    private static final float BASS = 0.02f;
    private final float[] history = new float[43];
    private int filled, next;
    private float low;
    private long last = Long.MIN_VALUE / 2;
    /** Loudness of the last frame, 0..1. */
    public float rms;

    /** Feeds one frame; true when it holds a beat (at most one every 260 ms). */
    public boolean frame(short[] audio, long now) {
        double sum = 0, bass = 0;
        for (short sample : audio) {
            sum += (double) sample * sample;
            low += BASS * (sample - low);
            bass += (double) low * low;
        }
        rms = (float) Math.sqrt(sum / audio.length) / 32768f;
        float energy = (float) (bass / audio.length);
        boolean beat = false;
        if (filled >= 20) {
            float mean = 0, spread = 0;
            for (int i = 0; i < filled; i++) mean += history[i];
            mean /= filled;
            for (int i = 0; i < filled; i++) spread += (history[i] - mean) * (history[i] - mean);
            spread = (float) Math.sqrt(spread / filled);
            beat = energy > mean * 1.3f && energy > mean + spread * 0.9f && rms > 0.004f && now - last > 260;
            if (beat) last = now;
        }
        history[next] = energy;
        next = (next + 1) % history.length;
        filled = Math.min(filled + 1, history.length);
        return beat;
    }
}
