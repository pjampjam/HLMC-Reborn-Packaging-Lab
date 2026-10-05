namespace HolyLois.Core;

/// <summary>
/// What Play does. Standard opens the player's Minecraft launcher; Integrated (starting the game from this app) comes later.
/// Launcher 1.2.6 had a Quick Play mode that left a join note for the mod. It never joined reliably and was removed in 1.2.7:
/// saved "quick" settings fall back to Standard and a leftover note is deleted on Play.
/// </summary>
public static class PlayMode
{
    public const string Standard = "standard", Integrated = "integrated";
    public const string OldQuickPlayNote = "holylois-quickplay.json";

    public static string Normalize(string? mode) => Standard;

    public static void RemoveOldQuickPlayNote(string instanceRoot)
    {
        try { var path = SafePaths.Resolve(instanceRoot, OldQuickPlayNote); if (File.Exists(path)) File.Delete(path); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { /* a locked note is harmless: the mod that read it is gone */ }
    }
}
