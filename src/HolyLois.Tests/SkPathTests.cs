using HolyLois.Core;
using System.Text.Json.Nodes;

internal static class SkPathTests
{
    public static IEnumerable<(string Name, Func<Task> Run)> Create(string testRoot)
    {
        yield return ("SK rejects a marked external folder and accepts only the current moved native library", () =>
        {
            var root = Path.Combine(testRoot, "sk-native-validation");
            var home = Path.Combine(root, "home");
            var external = Path.Combine(root, "external", "holy-lois-reborn");
            var oldNative = Path.Combine(home, "instances", "holy-lois-reborn");
            var moved = Path.Combine(root, "new-library");
            var newNative = Path.Combine(moved, "instances", "holy-lois-reborn");
            foreach (var path in new[] { external, oldNative, newNative })
            {
                Directory.CreateDirectory(path);
                File.WriteAllText(Path.Combine(path, "holylois-instance.json"), SkLauncherProfiles.Marker);
                File.WriteAllText(Path.Combine(path, "player-settings.txt"), "preserve");
            }
            Reject(() => SkLauncherProfiles.ValidateNativeDirectory(home, external));
            SkLauncherProfiles.ValidateNativeDirectory(home, oldNative);
            File.WriteAllText(Path.Combine(home, "location.json"), new JsonObject { ["dataDir"] = moved }.ToJsonString());
            Reject(() => SkLauncherProfiles.ValidateNativeDirectory(home, oldNative));
            SkLauncherProfiles.ValidateNativeDirectory(home, newNative);
            SkLauncherProfiles.ValidateNativeDirectory(home, newNative + Path.DirectorySeparatorChar);
            Reject(() => SkLauncherProfiles.ValidateNativeDirectory(home, Path.Combine("relative", "instances", "holy-lois-reborn")));
            foreach (var path in new[] { external, oldNative, newNative })
                if (File.ReadAllText(Path.Combine(path, "player-settings.txt")) != "preserve") throw new Exception("Native validation changed player files.");
            File.WriteAllText(Path.Combine(home, "location.json"), new JsonObject { ["dataDir"] = moved, ["prevDataDir"] = home }.ToJsonString());
            Reject(() => SkLauncherProfiles.ValidateNativeDirectory(home, newNative));
            return Task.CompletedTask;
        });
    }

    private static void Reject(Action action)
    {
        try { action(); }
        catch (IOException) { return; }
        throw new Exception("Stale or external SK folder was accepted.");
    }
}
