namespace HolyLois.Core;

/// <summary>
/// Quick Play: right before the player's Minecraft launcher opens, the launcher leaves a small note in the Holy Lois game folder.
/// The Holy Lois mod (extras, QuickPlayClient) reads it once on the title screen and joins the server, which works for premium and
/// offline accounts alike. A note older than a few minutes is ignored by the mod, so a forgotten one never joins by surprise.
/// </summary>
public static class QuickPlay
{
    public const string FileName = "holylois-quickplay.json";
    public const string Standard = "standard", Quick = "quick";

    public static string Normalize(string? mode) => mode == Standard ? Standard : Quick;

    public static void Arm(string instanceRoot, string address, DateTimeOffset now) =>
        AtomicFiles.WriteJson(SafePaths.Resolve(instanceRoot, FileName), new QuickPlayNote(address, now.ToUnixTimeSeconds()));

    public static void Disarm(string instanceRoot)
    {
        try { var path = SafePaths.Resolve(instanceRoot, FileName); if (File.Exists(path)) File.Delete(path); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { /* a locked note expires on its own */ }
    }
}

public sealed record QuickPlayNote(string Address, long Created);
