using System.Text.Json;

namespace HolyLois.Core;

public sealed record SetupChoices(string Launcher, bool DesktopShortcut, bool StartMenuShortcut);

public static class LauncherSetup
{
    // The old marker is a migration boundary, including shortcuts the player deleted.
    public static bool NeedsSetup(string baseFolder, bool existingPlayerSettings) =>
        !File.Exists(SafePaths.Resolve(baseFolder, "setup-completed.json")) &&
        !File.Exists(SafePaths.Resolve(baseFolder, "shortcuts-initialized.txt")) && !existingPlayerSettings;

    public static void Complete(string baseFolder, SetupChoices choices, Action<bool> createShortcut)
    {
        if (choices.Launcher is not ("official" or "sk")) throw new InvalidDataException("Choose Minecraft Launcher or SKlauncher.");
        var receipt = SafePaths.Resolve(baseFolder, "setup-completed.json");
        if (File.Exists(receipt)) return;
        if (choices.DesktopShortcut) createShortcut(true);
        if (choices.StartMenuShortcut) createShortcut(false);
        AtomicFiles.WriteJson(receipt, choices);
        AtomicFiles.Write(SafePaths.Resolve(baseFolder, "shortcuts-initialized.txt"), "User selected shortcuts during setup."u8.ToArray());
    }

    public static void Migrate(string baseFolder, string launcher)
    {
        var receipt = SafePaths.Resolve(baseFolder, "setup-completed.json");
        if (!File.Exists(receipt)) AtomicFiles.WriteJson(receipt, new SetupChoices(launcher, false, false));
    }
}
