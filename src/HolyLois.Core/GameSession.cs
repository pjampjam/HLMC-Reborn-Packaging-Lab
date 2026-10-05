using System.Diagnostics;
using System.Text;
using System.Text.RegularExpressions;

namespace HolyLois.Core;

/// <summary>
/// A running game started by fast start. Its console output goes to a log file (for reports) and is watched for the
/// Fabric "Loading N mods" line, so the start window can say what is happening. No console window is shown.
/// </summary>
public sealed partial class GameSession : IDisposable
{
    private readonly StreamWriter output;
    private readonly object gate = new();
    public Process Process { get; }
    public Task<int> Exited { get; }
    public int? ModCount { get; private set; }
    public event Action<string>? Line;

    [GeneratedRegex(@"Loading (\d+) mods")] private static partial Regex Mods();

    private GameSession(Process process, StreamWriter output)
    {
        Process = process; this.output = output;
        process.OutputDataReceived += (_, e) => Receive(e.Data);
        process.ErrorDataReceived += (_, e) => Receive(e.Data);
        process.BeginOutputReadLine(); process.BeginErrorReadLine();
        Exited = Watch();
    }

    public static GameSession Start(LaunchPlan plan, string outputLog)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(outputLog)!);
        var start = new ProcessStartInfo(plan.Java)
        {
            UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = plan.GameDirectory,
            RedirectStandardOutput = true, RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8
        };
        start.ArgumentList.Add("@" + plan.ArgumentFile);
        var log = new StreamWriter(new FileStream(outputLog, FileMode.Create, FileAccess.Write, FileShare.ReadWrite | FileShare.Delete), new UTF8Encoding(false)) { AutoFlush = true };
        try { return new GameSession(Process.Start(start) ?? throw new IOException("Java did not start."), log); }
        catch { log.Dispose(); throw; }
    }

    private void Receive(string? line)
    {
        if (line is null) return;
        lock (gate) { try { output.WriteLine(line); } catch (ObjectDisposedException) { } }
        if (ModCount is null && Mods().Match(line) is { Success: true } match) ModCount = int.Parse(match.Groups[1].Value);
        Line?.Invoke(line);
    }

    private async Task<int> Watch()
    {
        await Process.WaitForExitAsync();
        // Let the last output lines arrive before the log closes.
        Process.WaitForExit();
        lock (gate) { output.Flush(); }
        return Process.ExitCode;
    }

    /// <summary>True once the game has its own window (Windows only; the title starts with Minecraft or Holy Lois).</summary>
    public bool HasWindow()
    {
        try
        {
            Process.Refresh();
            if (Process.HasExited || Process.MainWindowHandle == IntPtr.Zero) return false;
            var title = Process.MainWindowTitle;
            return title.StartsWith("Minecraft", StringComparison.Ordinal) || title.StartsWith("Holy Lois", StringComparison.Ordinal);
        }
        catch (InvalidOperationException) { return false; }
    }

    public void Dispose()
    {
        lock (gate) { output.Dispose(); }
        Process.Dispose();
    }
}
