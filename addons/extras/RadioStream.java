package holylois.boombox;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Decodes an internet radio MP3 stream into 20 ms mono 48 kHz frames for Simple Voice Chat.
 * The bounded queue paces the download to real time; the voice thread takes one frame every 20 ms.
 */
final class RadioStream implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger("HolyLoisBoombox");
    static final int FRAME = 960, RATE = 48000;

    private final String url;
    /** Changed live by the volume control; applies from the next decoded frame. */
    volatile float gain;
    private final BlockingQueue<short[]> frames = new ArrayBlockingQueue<>(75);
    private volatile boolean running = true;
    volatile String nowPlaying = "";
    volatile boolean failed;

    RadioStream(String url, float gain) { this.url = url; this.gain = gain; }

    void start() { Thread.ofVirtual().name("holylois-boombox").start(this); }
    void stop() { running = false; frames.clear(); }
    boolean running() { return running; }

    /** Next 20 ms frame; silence while buffering so the voice channel stays open. */
    short[] next() {
        var frame = frames.poll();
        return frame != null ? frame : new short[FRAME];
    }

    @Override public void run() {
        int attempts = 0;
        while (running && attempts < 4) {
            attempts++;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setRequestProperty("User-Agent", "HolyLoisBoombox/1.0");
                connection.setRequestProperty("Icy-MetaData", "1");
                connection.setConnectTimeout(8000); connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
                int metaInt = connection.getHeaderFieldInt("icy-metaint", 0);
                InputStream raw = new BufferedInputStream(connection.getInputStream(), 32 * 1024);
                InputStream audio = metaInt > 0 ? new IcyStream(raw, metaInt, this) : raw;
                decode(audio);
                attempts = 0;
            } catch (Exception error) {
                if (running) LOG.warn("Boombox stream {} interrupted ({}), attempt {}", url, error.getMessage(), attempts);
                try { Thread.sleep(1500L * attempts); } catch (InterruptedException ignored) { return; }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        if (running) failed = true;
    }

    private void decode(InputStream input) throws Exception {
        var bitstream = new Bitstream(input);
        var decoder = new Decoder();
        short[] out = new short[FRAME];
        int filled = 0;
        double position = 0;
        float previous = 0;
        try {
            while (running) {
                Header header = bitstream.readFrame();
                if (header == null) return;
                var buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                int channels = buffer.getChannelCount(), length = buffer.getBufferLength() / channels;
                // MP3 needs a few frames of bit reservoir before it produces samples.
                if (length == 0) { bitstream.closeFrame(); continue; }
                double step = (double) buffer.getSampleFrequency() / RATE;
                short[] samples = buffer.getBuffer();
                // Linear resampling of the mono mix to 48 kHz.
                while (position < length) {
                    int index = (int) position;
                    float current = mono(samples, index, channels);
                    float sample = index == 0 ? previous + (current - previous) * (float) (position - index)
                        : mono(samples, index - 1, channels) + (current - mono(samples, index - 1, channels)) * (float) (position - index);
                    out[filled++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample * gain));
                    if (filled == FRAME) {
                        if (!frames.offer(out, 5, TimeUnit.SECONDS)) return;
                        out = new short[FRAME]; filled = 0;
                    }
                    position += step;
                }
                position -= length;
                previous = mono(samples, length - 1, channels);
                bitstream.closeFrame();
            }
        } finally {
            bitstream.close();
        }
    }

    private static float mono(short[] samples, int frame, int channels) {
        if (channels == 1) return samples[frame];
        return (samples[frame * 2] + samples[frame * 2 + 1]) * 0.5f;
    }

    /** Removes Shoutcast/Icecast metadata blocks and keeps the current song title. */
    static final class IcyStream extends FilterInputStream {
        private final int metaInt;
        private final RadioStream owner;
        private int untilMeta;

        IcyStream(InputStream in, int metaInt, RadioStream owner) { super(in); this.metaInt = metaInt; this.owner = owner; untilMeta = metaInt; }

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (untilMeta == 0) { readMeta(); untilMeta = metaInt; }
            int n = super.read(b, off, Math.min(len, untilMeta));
            if (n > 0) untilMeta -= n;
            return n;
        }

        private void readMeta() throws IOException {
            int size = in.read();
            if (size < 0) throw new EOFException();
            if (size == 0) return;
            byte[] meta = in.readNBytes(size * 16);
            owner.nowPlaying = title(new String(meta, StandardCharsets.UTF_8));
        }

        static String title(String meta) {
            int start = meta.indexOf("StreamTitle='");
            if (start < 0) return "";
            int end = meta.indexOf("';", start + 13);
            String value = end < 0 ? meta.substring(start + 13) : meta.substring(start + 13, end);
            value = value.replaceAll("[^\\p{L}\\p{N} .,'&()!?:/+-]", "").strip();
            return value.length() > 60 ? value.substring(0, 60) : value;
        }
    }
}
