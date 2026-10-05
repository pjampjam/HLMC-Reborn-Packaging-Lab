using System.Text.Json.Nodes;

namespace HolyLois.Core;

/// <summary>
/// Mojang's own Java build for the game (the one the Minecraft Launcher uses), placed in the same folder layout so a copy
/// that is already on the computer is reused. The file list is saved, so a ready Java starts without any network call.
/// </summary>
public static class JavaRuntime
{
    public const string ListUrl = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    public const string Platform = "windows-x64";

    public static string Folder(string component) => "runtime/" + component + "/" + Platform + "/" + component;

    /// <summary>Picks the newest build of the component for Windows from Mojang's Java list.</summary>
    public static GameFile ManifestFile(byte[] listJson, string component)
    {
        var root = JsonNode.Parse(listJson) as JsonObject ?? throw new InvalidDataException("Java list is invalid.");
        var builds = root[Platform]?[component] as JsonArray;
        var manifest = builds?.OfType<JsonObject>().FirstOrDefault()?["manifest"] as JsonObject
            ?? throw new InvalidDataException("Mojang has no Java " + component + " for Windows.");
        var sha1 = (string?)manifest["sha1"]; var url = (string?)manifest["url"]; var size = (long?)manifest["size"];
        if (sha1 is not { Length: 40 } || url is null || size is null or < 1 or > 16 * 1024 * 1024) throw new InvalidDataException("Java list entry is invalid.");
        GameDownloads.ValidateUri(url);
        return new GameFile(Folder(component) + ".json", url, size.Value, sha1.ToLowerInvariant());
    }

    /// <summary>The runtime files (raw downloads, no packed copies) below the runtime folder.</summary>
    public static IReadOnlyList<GameFile> Files(byte[] manifestJson, string component)
    {
        var root = JsonNode.Parse(manifestJson) as JsonObject ?? throw new InvalidDataException("Java file list is invalid.");
        var files = root["files"] as JsonObject ?? throw new InvalidDataException("Java file list is empty.");
        var result = new List<GameFile>();
        foreach (var (name, node) in files)
        {
            if (node is not JsonObject entry || (string?)entry["type"] != "file") continue;
            if (entry["downloads"]?["raw"] is not JsonObject raw) throw new InvalidDataException("Java file has no download: " + name);
            var path = Folder(component) + "/" + name;
            SafePaths.ValidateRelative(path);
            var sha1 = (string?)raw["sha1"]; var url = (string?)raw["url"]; var size = (long?)raw["size"];
            if (sha1 is not { Length: 40 } || !sha1.All(Uri.IsHexDigit) || url is null || size is null or < 0) throw new InvalidDataException("Java file entry is invalid: " + name);
            GameDownloads.ValidateUri(url);
            result.Add(new GameFile(path, url, size.Value, sha1.ToLowerInvariant()));
        }
        if (!result.Any(f => f.Path.EndsWith("/bin/java.exe", StringComparison.OrdinalIgnoreCase))) throw new InvalidDataException("Java file list has no java.exe.");
        return result;
    }

    /// <summary>Returns bin/java.exe of a checked runtime, downloading or reusing what is missing.</summary>
    public static async Task<string> EnsureAsync(GameDownloads downloads, string component, IProgress<GameProgress>? progress, CancellationToken token)
    {
        var saved = SafePaths.Resolve(downloads.Root, Folder(component) + ".files.json");
        // Fast path: the file list from the last good install and every file still matching it.
        if (File.Exists(saved))
        {
            try
            {
                var known = Files(File.ReadAllBytes(saved), component);
                if (known.All(downloads.IsReady)) { downloads.SaveStamps(); return Java(downloads, component); }
            }
            catch (InvalidDataException) { }
        }
        progress?.Report(new GameProgress("java", 0, 0, 0, 0));
        var list = await downloads.FetchAsync(ListUrl, 4 * 1024 * 1024, token);
        var manifest = ManifestFile(list, component);
        await downloads.EnsureAsync([manifest], "java", null, token);
        var bytes = File.ReadAllBytes(downloads.PathOf(manifest));
        var files = Files(bytes, component);
        await downloads.EnsureAsync(files, "java", progress, token);
        AtomicFiles.Write(saved, bytes);
        return Java(downloads, component);
    }

    private static string Java(GameDownloads downloads, string component) => SafePaths.Resolve(downloads.Root, Folder(component) + "/bin/java.exe");
}
