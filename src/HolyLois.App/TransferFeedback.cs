using System.Diagnostics;
namespace HolyLois.App;

// This estimates transfer time only; launcher preparation has no known duration.
public sealed class TransferFeedback
{
    private readonly Stopwatch watch = Stopwatch.StartNew();
    private long previous;
    private double previousSeconds;
    private double speed;
    public string Describe(long done, long total)
    {
        done = Math.Clamp(done,0,total);
        var seconds = watch.Elapsed.TotalSeconds;
        var delta = seconds - previousSeconds;
        if (delta >= .5 && done > previous)
        {
            var sample = (done - previous) / delta;
            speed = speed == 0 ? sample : speed * .7 + sample * .3;
            previous = done; previousSeconds = seconds;
        }
        var text = $"{done / 1048576.0:N1} / {total / 1048576.0:N1} MB";
        if (speed > 0 && seconds >= 3 && done < total)
            text += $"  -  {speed / 1048576.0:N1} MB/s  -  " + string.Format(Localize.Text("ApproxRemaining"),Math.Ceiling((total-done)/speed));
        return text;
    }
}
