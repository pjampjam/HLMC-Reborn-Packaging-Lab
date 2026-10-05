namespace HolyLois.Core;

/// <summary>
/// What Play does. Fast start (launcher 1.3.0) starts Minecraft from this app for player-name accounts ("name", and SKlauncher
/// players unless they turn it off). A bought Minecraft account still opens the Minecraft Launcher, because signing in to
/// Minecraft from another app needs Mojang's approval; its profile joins Holy Lois on start instead.
/// Launcher 1.2.6 had a Quick Play mode that left a join note for the mod. It never joined reliably and was removed in 1.2.7:
/// saved "quick" settings fall back to Standard and a leftover note is deleted on Play.
/// </summary>
public static class PlayMode
{
    public const string Standard = "standard", Integrated = "integrated";
    public const string OldQuickPlayNote = "holylois-quickplay.json";

    public static string Normalize(string? mode) => Standard;

    /// <summary>Player-name accounts always use fast start; SKlauncher players can switch back to opening SKlauncher.</summary>
    public static bool UsesFastStart(string launcher, bool? fastStart) => launcher == "name" || launcher == "sk" && fastStart != false;

    public static void RemoveOldQuickPlayNote(string instanceRoot)
    {
        try { var path = SafePaths.Resolve(instanceRoot, OldQuickPlayNote); if (File.Exists(path)) File.Delete(path); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { /* a locked note is harmless: the mod that read it is gone */ }
    }
}
