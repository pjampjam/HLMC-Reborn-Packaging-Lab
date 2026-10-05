using System.Net;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace HolyLois.Core;

public sealed record NameEntry(string Name, DateTimeOffset LastUsed);
/// <summary>The player's names on this computer: the current one, the ones used before, and when new ones were added.</summary>
public sealed record PlayerBook(string? Current = null, NameEntry[]? Names = null, DateTimeOffset[]? Added = null)
{
    public NameEntry[] History => Names ?? [];
}

/// <summary>
/// Player names for fast start. The server knows a player by name, so a new name is a new player (new password, empty
/// inventory). Changing is allowed but rate limited, and every name used before stays one click away. The list lives in the
/// app's data folder, which uninstalling keeps unless the player asks to forget it.
/// </summary>
public static partial class PlayerNames
{
    public const int NewNamesPerDay = 3;
    public const int HistoryLimit = 12;

    [GeneratedRegex("^[A-Za-z0-9_]{3,16}$")] private static partial Regex Valid();
    [GeneratedRegex(@"Setting user: ([A-Za-z0-9_]{3,16})\s*$", RegexOptions.Multiline)] private static partial Regex LogUser();

    public static bool IsValid(string? name) => name is not null && Valid().IsMatch(name);

    public static PlayerBook Load(string path)
    {
        try
        {
            if (!File.Exists(path) || new FileInfo(path).Length > 256 * 1024) return new();
            var book = JsonSerializer.Deserialize<PlayerBook>(File.ReadAllBytes(path), JsonSettings.Options) ?? new();
            return book with { Current = IsValid(book.Current) ? book.Current : null, Names = book.History.Where(n => IsValid(n.Name)).ToArray() };
        }
        catch (JsonException) { return new(); }
    }

    public static void Save(string path, PlayerBook book) => AtomicFiles.WriteJson(path, book);

    public static bool Known(PlayerBook book, string name) => book.History.Any(n => n.Name.Equals(name, StringComparison.OrdinalIgnoreCase));

    /// <summary>How many brand-new names can still be added today.</summary>
    public static int NewNamesLeft(PlayerBook book, DateTimeOffset now) =>
        Math.Max(0, NewNamesPerDay - (book.Added ?? []).Count(t => now - t < TimeSpan.FromDays(1)));

    /// <summary>Makes NAME the current name. Names used before are free; new names count toward the daily limit.</summary>
    public static PlayerBook Use(PlayerBook book, string name, DateTimeOffset now)
    {
        name = name.Trim();
        if (!IsValid(name)) throw new InvalidDataException("Use 3 to 16 letters, digits or _ (no spaces).");
        var known = book.History.FirstOrDefault(n => n.Name.Equals(name, StringComparison.OrdinalIgnoreCase));
        var added = (book.Added ?? []).Where(t => now - t < TimeSpan.FromDays(7)).ToList();
        if (known is null && book.Current is not null)
        {
            if (NewNamesLeft(book, now) == 0) throw new InvalidDataException("You added " + NewNamesPerDay + " new names today. Try again tomorrow, or pick a name you used before.");
            added.Add(now);
        }
        // Keep the spelling the player used before, so the server sees the same player.
        var spelled = known?.Name ?? name;
        var names = book.History.Where(n => !n.Name.Equals(spelled, StringComparison.OrdinalIgnoreCase))
            .Prepend(new NameEntry(spelled, now)).Take(HistoryLimit).ToArray();
        return new PlayerBook(spelled, names, added.ToArray());
    }

    /// <summary>The name the player last joined with in this game folder (SKlauncher or another launcher), read from the game log.</summary>
    public static string? FromGameLog(string gameDirectory)
    {
        try
        {
            var log = SafePaths.Resolve(gameDirectory, "logs/latest.log");
            if (!File.Exists(log)) return null;
            using var stream = new FileStream(log, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            var buffer = new byte[Math.Min(stream.Length, 512 * 1024)];
            stream.ReadExactly(buffer);
            var match = LogUser().Match(System.Text.Encoding.UTF8.GetString(buffer));
            return match.Success ? match.Groups[1].Value : null;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or InvalidDataException) { return null; }
    }

    /// <summary>True when the name belongs to a bought Minecraft account (the server would ask for that account), null when unknown.</summary>
    public static async Task<bool?> IsPremiumAsync(HttpClient http, string name, CancellationToken token)
    {
        if (!IsValid(name)) return null;
        try
        {
            using var response = await http.GetAsync("https://api.mojang.com/users/profiles/minecraft/" + name, token);
            return response.StatusCode switch { HttpStatusCode.OK => true, HttpStatusCode.NoContent or HttpStatusCode.NotFound => false, _ => null };
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException) { return null; }
    }
}
