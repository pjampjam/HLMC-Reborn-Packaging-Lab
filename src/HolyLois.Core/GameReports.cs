using System.IO.Compression;
using System.Text;
using System.Text.RegularExpressions;

namespace HolyLois.Core;

/// <summary>What the app knows about the last start, saved next to the logs so a report can be made later.</summary>
public sealed record StartRecord(string Mode, string Pack, string App, DateTimeOffset Started, int? ExitCode = null, DateTimeOffset? Ended = null, string? Error = null);

/// <summary>
/// Help and reports: gathers the last start record, the game output, the game log and the newest crash report into one text
/// a player can copy or save. Windows user names, login tokens and IP addresses are blanked out before anything leaves the app.
/// </summary>
public static partial class GameReports
{
    public const int TailLines = 400;

    [GeneratedRegex(@"\b(?:\d{1,3}\.){3}\d{1,3}\b")] private static partial Regex Ipv4();
    [GeneratedRegex(@"(--accessToken|accessToken[""=:]+|token:)\s*[^\s"",)]+", RegexOptions.IgnoreCase)] private static partial Regex Token();
    [GeneratedRegex(@"(--xuid|--clientId|--uuid)\s+\S+", RegexOptions.IgnoreCase)] private static partial Regex Ids();

    /// <summary>Removes personal details. The Holy Lois server address stays, it is public anyway.</summary>
    public static string Redact(string text, string? windowsUser)
    {
        if (!string.IsNullOrWhiteSpace(windowsUser) && windowsUser.Length >= 2)
            text = Regex.Replace(text, @"(?<=[\\/]Users[\\/])" + Regex.Escape(windowsUser) + @"(?=[\\/]|$)", "<user>", RegexOptions.IgnoreCase);
        text = Token().Replace(text, m => m.Groups[1].Value + " HIDDEN");
        text = Ids().Replace(text, m => m.Groups[1].Value + " HIDDEN");
        return Ipv4().Replace(text, m => m.Value == ServerAddress.Ip.Split(':')[0] || m.Value.StartsWith("127.", StringComparison.Ordinal) || m.Value == "0.0.0.0" ? m.Value : "IP-HIDDEN");
    }

    /// <summary>The last lines of a text file that may still be open by the game.</summary>
    public static string Tail(string path, int lines = TailLines)
    {
        if (!File.Exists(path)) return "";
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        var length = Math.Min(stream.Length, 2 * 1024 * 1024);
        stream.Seek(-length, SeekOrigin.End);
        var buffer = new byte[length]; stream.ReadExactly(buffer);
        var all = Encoding.UTF8.GetString(buffer).Replace("\r\n", "\n").Split('\n');
        return string.Join("\n", all.Skip(Math.Max(0, all.Length - lines)));
    }

    /// <summary>The newest crash report or JVM crash log written after the start time, if any.</summary>
    public static string? NewestCrash(string gameDirectory, DateTimeOffset since)
    {
        var candidates = new List<FileInfo>();
        try
        {
            var reports = Path.Combine(gameDirectory, "crash-reports");
            if (Directory.Exists(reports)) candidates.AddRange(new DirectoryInfo(reports).GetFiles("crash-*.txt"));
            if (Directory.Exists(gameDirectory)) candidates.AddRange(new DirectoryInfo(gameDirectory).GetFiles("hs_err_pid*.log"));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { return null; }
        return candidates.Where(f => f.LastWriteTimeUtc >= since.UtcDateTime.AddSeconds(-5)).OrderByDescending(f => f.LastWriteTimeUtc).FirstOrDefault()?.FullName;
    }

    public static string Build(StartRecord? record, string gameDirectory, string? outputLog, string windowsVersion, string? windowsUser)
    {
        var text = new StringBuilder();
        text.AppendLine("Holy Lois: Reborn report");
        text.AppendLine("Made: " + DateTimeOffset.Now.ToString("yyyy-MM-dd HH:mm"));
        text.AppendLine("Windows: " + windowsVersion);
        if (record is not null)
        {
            text.AppendLine($"App {record.App}, pack {record.Pack}, start mode {record.Mode}");
            text.AppendLine("Started: " + record.Started.ToLocalTime().ToString("yyyy-MM-dd HH:mm:ss")
                + (record.Ended is { } ended ? ", ended " + ended.ToLocalTime().ToString("HH:mm:ss") : ", still running or not recorded")
                + (record.ExitCode is { } code ? ", exit code " + code : ""));
            if (record.Error is not null) text.AppendLine("Start error: " + record.Error);
        }
        else text.AppendLine("No start recorded by this app yet.");
        void Section(string title, string? path)
        {
            if (path is null || !File.Exists(path)) return;
            text.AppendLine().AppendLine("===== " + title + " (" + Path.GetFileName(path) + ") =====");
            try { text.AppendLine(Tail(path)); }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { text.AppendLine("(could not read: " + ex.Message + ")"); }
        }
        var since = record?.Started ?? DateTimeOffset.UtcNow.AddDays(-2);
        Section("Crash", NewestCrash(gameDirectory, since));
        Section("Game output", outputLog);
        Section("Game log", Path.Combine(gameDirectory, "logs", "latest.log"));
        return Redact(text.ToString(), windowsUser);
    }

    /// <summary>The same report as a zip with one text file, for attaching in Discord.</summary>
    public static void SaveZip(string report, string path)
    {
        var temp = path + ".holylois-tmp";
        using (var zip = ZipFile.Open(temp, ZipArchiveMode.Create))
        {
            var entry = zip.CreateEntry("holylois-report.txt", CompressionLevel.Optimal);
            using var writer = new StreamWriter(entry.Open(), new UTF8Encoding(false));
            writer.Write(report);
        }
        File.Move(temp, path, true);
    }
}
