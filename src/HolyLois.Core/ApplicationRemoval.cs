namespace HolyLois.Core;

public static class ApplicationRemoval
{
    public const string Marker = "holylois-reborn-app-v1";
    public static bool IsOwnedMarker(string marker) => marker is Marker or "holylois-packaging-preview-v1";
    public static void RemoveOwnedApp(string root, string expectedHash)
    {
        SafePaths.RejectLinks(root);
        var marker = SafePaths.Resolve(root,"holylois-app.txt");
        if (!File.Exists(marker) || !IsOwnedMarker(File.ReadAllText(marker))) throw new IOException("This folder is not an owned Holy Lois installation.");
        var target = SafePaths.Resolve(root,"HolyLoisReborn.exe");
        if (!File.Exists(target) || AtomicFiles.Hash(target) != expectedHash) throw new IOException("The installed app changed; removal was cancelled.");
        // Never recurse or touch game data, other launchers, downloaded copies or unknown files.
        var paths = new[] { "HolyLoisReborn.exe", "setup-completed.json", "shortcuts-initialized.txt", "app-release-state.json", "failed-app-update.txt", "holylois-app.txt" }.Select(name => SafePaths.Resolve(root,name)).ToArray();
        foreach (var path in paths) if (File.Exists(path)) File.Delete(path);
    }
}
