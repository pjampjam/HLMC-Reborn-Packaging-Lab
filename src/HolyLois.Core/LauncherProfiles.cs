using System.Text;
using System.Text.Json.Nodes;

namespace HolyLois.Core;

public static class LauncherProfiles
{
    public const string ProfileId = "holylois-reborn";
    public const string VersionId = "holylois-reborn-fabric-0.19.5-26.3";
    public const string PreviewProfileId = "holylois-reborn-preview";
    public static byte[] Upsert(byte[]? existing, string gameDirectory, byte[] icon, string profileId = ProfileId, string displayName = "Holy Lois: Reborn")
    {
        var root = existing is null ? new JsonObject() : JsonNode.Parse(existing) as JsonObject
            ?? throw new InvalidDataException("Launcher profile file was preserved because it could not be read.");
        if (root["profiles"] is null) root["profiles"] = new JsonObject();
        if (root["profiles"] is not JsonObject profiles) throw new InvalidDataException("Launcher profiles have an unexpected layout.");
        var entry = profiles[profileId] as JsonObject ?? new JsonObject();
        if (entry["gameDir"] is JsonValue dir && dir.TryGetValue<string>(out var existingDir)
            && !Path.GetFullPath(existingDir).Equals(Path.GetFullPath(gameDirectory), StringComparison.OrdinalIgnoreCase))
            throw new IOException("Another profile already uses the Holy Lois identifier with a different game folder.");
        entry["name"] = displayName;
        entry["type"] = "custom";
        entry["lastVersionId"] = VersionId;
        entry["gameDir"] = Path.GetFullPath(gameDirectory);
        entry["icon"] = "data:image/png;base64," + Convert.ToBase64String(icon);
        entry["created"] ??= DateTime.UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'");
        entry["javaArgs"] ??= "-Xmx4G -Xms1G";
        profiles[profileId] = entry;
        return Encoding.UTF8.GetBytes(root.ToJsonString(JsonSettings.Options));
    }

    public static async Task PrepareAsync(string minecraftRoot, string instanceRoot, PackManifest manifest,
        byte[] versionJson, byte[] icon, IFileDownloader downloader, CancellationToken cancellationToken, byte[]? vanillaJson = null, string profileId = ProfileId, string displayName = "Holy Lois: Reborn")
    {
        SafePaths.RejectLinks(minecraftRoot);
        var version = JsonNode.Parse(versionJson) as JsonObject ?? throw new InvalidDataException("Fabric profile is invalid.");
        if ((string?)version["id"] != VersionId || (string?)version["inheritsFrom"] != manifest.Minecraft)
            throw new InvalidDataException("Fabric profile does not match the approved pack.");
        if (vanillaJson is not null)
        {
            var baseVersion = JsonNode.Parse(vanillaJson) as JsonObject ?? throw new InvalidDataException("Minecraft base profile is invalid.");
            if ((string?)baseVersion["id"] != manifest.Minecraft || (string?)baseVersion["type"] != "release"
                || (int?)baseVersion["javaVersion"]?["majorVersion"] != manifest.Java)
                throw new InvalidDataException("Minecraft base profile does not match the approved game and Java versions.");
            var basePath = SafePaths.Resolve(minecraftRoot, "versions/" + manifest.Minecraft + "/" + manifest.Minecraft + ".json");
            // Existing vanilla metadata belongs to its launcher. Seed the exact publisher metadata only when missing.
            if (!File.Exists(basePath)) AtomicFiles.Write(basePath, vanillaJson);
        }
        foreach (var file in manifest.LoaderFiles)
        {
            var target = SafePaths.Resolve(minecraftRoot, file.Path);
            if (AtomicFiles.Matches(target, file)) continue;
            var staged = await downloader.GetAsync(file, null, cancellationToken);
            if (!AtomicFiles.Matches(staged, file)) throw new InvalidDataException("Fabric library checksum failed.");
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            // Fabric library coordinates are immutable. Do not overwrite a conflicting file from another launcher.
            if (File.Exists(target)) throw new IOException("An existing Fabric library differs from the tested release: " + file.Path);
            AtomicFiles.Write(target, File.ReadAllBytes(staged));
        }
        var profilesPath = SafePaths.Resolve(minecraftRoot, "launcher_profiles.json");
        var existing = File.Exists(profilesPath) ? File.ReadAllBytes(profilesPath) : null;
        var next = Upsert(existing, instanceRoot, icon, profileId, displayName);
        AtomicFiles.Write(SafePaths.Resolve(minecraftRoot, "versions/" + VersionId + "/" + VersionId + ".json"), versionJson);
        if (existing is not null) AtomicFiles.Write(SafePaths.Resolve(minecraftRoot, "launcher_profiles.holylois-backup.json"), existing);
        AtomicFiles.Write(profilesPath, next);
    }
}
