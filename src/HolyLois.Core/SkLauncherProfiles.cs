using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Nodes;

namespace HolyLois.Core;

// SKlauncher 4 keeps its instance registry in the home folder even when game data is moved.
public static class SkLauncherProfiles
{
    public const string Marker = "{\"instance\":\"holylois-reborn-26.3\"}";
    public static string DataRoot(string home)
    {
        SafePaths.RejectLinks(home);
        var location = SafePaths.Resolve(home, "location.json");
        if (!File.Exists(location)) return home;
        var node = ReadObject(File.ReadAllBytes(location));
        if (node["prevDataDir"] is JsonValue previous && previous.TryGetValue<string>(out var pending) && !string.IsNullOrWhiteSpace(pending))
            throw new IOException("Finish moving the SKlauncher library before installing Holy Lois.");
        var value = (string?)node["dataDir"];
        if (string.IsNullOrWhiteSpace(value)) return home;
        if (!Path.IsPathFullyQualified(value)) throw new IOException("SKlauncher's game data folder is invalid.");
        SafePaths.RejectLinks(value);
        return Path.GetFullPath(value);
    }
    private static JsonObject ReadObject(byte[] bytes)
    {
        if (bytes.Length > 8 * 1024 * 1024) throw new IOException("SKlauncher metadata is too large.");
        try { return JsonNode.Parse(bytes) as JsonObject ?? throw new IOException("SKlauncher metadata could not be read."); }
        catch (System.Text.Json.JsonException ex) { throw new IOException("SKlauncher metadata could not be read. Open SKlauncher once to recover it, then close it and retry.", ex); }
    }
    private static JsonArray Instances(JsonObject root)
    {
        if (root["instances"] is null) root["instances"] = new JsonArray();
        return root["instances"] as JsonArray ?? throw new IOException("SKlauncher's instance registry has an unexpected format.");
    }
    private static bool SamePath(string? first, string second) => first is not null && Path.IsPathFullyQualified(first)
        && Path.GetFullPath(first).Equals(Path.GetFullPath(second), StringComparison.OrdinalIgnoreCase);
    private static string? NativeDirectory(string home, JsonObject entry)
    {
        var id = (string?)entry["id"];
        return id is not null && System.Text.RegularExpressions.Regex.IsMatch(id, @"^[a-zA-Z0-9_-]{1,128}$")
            ? SafePaths.Resolve(DataRoot(home), "instances/" + id) : null;
    }
    public static string InstallDirectory(string home)
    {
        var existing = FindOwnedInstance(home);
        if (existing is not null) return existing;
        var registry = SafePaths.Resolve(home, "instances.json");
        var entries = File.Exists(registry) ? Instances(ReadObject(File.ReadAllBytes(registry))).OfType<JsonObject>().ToArray() : [];
        for (var suffix = 1; ; suffix++)
        {
            var id = suffix == 1 ? "holy-lois-reborn" : "holy-lois-reborn-" + suffix;
            var target = SafePaths.Resolve(DataRoot(home), "instances/" + id);
            var occupant = entries.FirstOrDefault(entry => (string?)entry["id"] == id);
            var empty = !Directory.Exists(target) || !Directory.EnumerateFileSystemEntries(target).Any();
            if (occupant is null && (empty || IsOwned(target))) return target;
            // A deleted owned pack can be recreated without replacing another instance's files.
            if (empty && occupant is not null && (string?)occupant["name"] == "Holy Lois: Reborn"
                && (string?)occupant["versionId"] == LauncherProfiles.VersionId) return target;
        }
    }
    public static bool IsOwned(string path)
    {
        try { return File.Exists(SafePaths.Resolve(path, "holylois-instance.json")) && File.ReadAllText(SafePaths.Resolve(path, "holylois-instance.json")) == Marker; }
        catch (Exception ex) when (ex is IOException or ArgumentException) { return false; }
    }
    public static string? FindOwnedInstance(string home)
    {
        var path = SafePaths.Resolve(home, "instances.json");
        if (!File.Exists(path)) return null;
        foreach (var entry in Instances(ReadObject(File.ReadAllBytes(path))).OfType<JsonObject>())
            if ((string?)entry["versionId"] == LauncherProfiles.VersionId && NativeDirectory(home, entry) is { } directory && IsOwned(directory)) return directory;
        return null;
    }
    public static bool IsRegistered(string home, string gameDirectory)
    {
        try {
            var path = SafePaths.Resolve(home, "instances.json");
            return File.Exists(path) && Instances(ReadObject(File.ReadAllBytes(path))).OfType<JsonObject>().Any(entry =>
                (string?)entry["versionId"] == LauncherProfiles.VersionId && SamePath(NativeDirectory(home,entry), gameDirectory));
        }
        catch (Exception ex) when (ex is IOException or ArgumentException) { return false; }
    }
    public static byte[] Upsert(byte[]? existing, string gameDirectory, PackManifest manifest, string icon)
    {
        if (!IsOwned(gameDirectory)) throw new IOException("Only a verified Holy Lois game folder can be registered.");
        var root = existing is null ? new JsonObject() : ReadObject(existing);
        var instances = Instances(root);
        var nativeId = Path.GetFileName(Path.GetFullPath(gameDirectory));
        var entry = instances.OfType<JsonObject>().FirstOrDefault(item => (string?)item["id"] == nativeId
            && (string?)item["versionId"] == LauncherProfiles.VersionId);
        if (entry is null)
        {
            var id = Path.GetFileName(Path.GetFullPath(gameDirectory));
            if (!System.Text.RegularExpressions.Regex.IsMatch(id, @"^holy-lois-reborn(-[0-9]+)?$")
                || instances.OfType<JsonObject>().Any(item => (string?)item["id"] == id))
                throw new IOException("This SKlauncher instance identifier is already in use. Existing instances were preserved.");
            entry = new JsonObject { ["id"] = id, ["createdAt"] = DateTime.UtcNow.ToString("O"), ["playTime"] = 0,
                ["sessionCount"] = 0, ["memoryMax"] = 4096, ["installComplete"] = false };
            instances.Add(entry);
        }
        entry["name"] = "Holy Lois: Reborn"; entry["type"] = "custom"; entry["icon"] = icon;
        entry["versionId"] = LauncherProfiles.VersionId; entry["gameType"] = "fabric";
        entry["loaderVersion"] = manifest.Fabric; entry["minecraftVersion"] = manifest.Minecraft;
        entry["directory"] = Path.GetFullPath(gameDirectory);
        // Java and base-game installation remain managed by SKlauncher, like its own imported instances.
        entry["javaComponent"] = "java-runtime-epsilon";
        return Encoding.UTF8.GetBytes(root.ToJsonString(JsonSettings.Options));
    }
    public static void Register(string home, string gameDirectory, PackManifest manifest, byte[] icon)
    {
        SafePaths.RejectLinks(home);
        if (!SamePath(gameDirectory, SafePaths.Resolve(DataRoot(home), "instances/" + Path.GetFileName(gameDirectory))))
            throw new IOException("Holy Lois must be installed in SKlauncher's native instance folder.");
        var path = SafePaths.Resolve(home, "instances.json");
        var existing = File.Exists(path) ? File.ReadAllBytes(path) : null;
        var hash = Convert.ToHexStringLower(SHA1.HashData(icon));
        var iconPath = "icons/holy-lois-reborn/" + hash + ".png";
        var next = Upsert(existing, gameDirectory, manifest, "starship-icon://" + iconPath);
        AtomicFiles.Write(SafePaths.Resolve(home, iconPath), icon);
        // A launcher opened during download must not have its newer registry silently replaced.
        if (existing is null ? File.Exists(path) : !File.Exists(path) || !File.ReadAllBytes(path).AsSpan().SequenceEqual(existing))
            throw new IOException("SKlauncher's library changed during setup. Close it and click Repair / check files again.");
        if (existing is not null) AtomicFiles.Write(SafePaths.Resolve(home, "instances.holylois-backup.json"), existing);
        AtomicFiles.Write(path, next);
    }
}
