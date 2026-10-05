using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace HolyLois.Core;

/// <summary>The computer the game will run on, as Mojang's version rules see it.</summary>
public sealed record GamePlatform(string Os, string Arch, Version OsVersion)
{
    public static GamePlatform Windows(Version? osVersion = null) => new("windows", "x64", osVersion ?? new Version(10, 0, 22631));
    public static GamePlatform Current => Windows(OperatingSystem.IsWindows() ? Environment.OSVersion.Version : null);
}

/// <summary>A file the game needs, checked by its hash: SHA-1 (40 hex digits) from Mojang's files, SHA-256 (64) from the signed pack.</summary>
public sealed record GameFile(string Path, string Url, long Size, string Hash);

/// <summary>
/// Reads the embedded Minecraft and Fabric version files (both shipped inside the signed launcher) and turns them into the
/// files to download and the exact command line, the same way the Minecraft Launcher merges a Fabric profile over vanilla.
/// </summary>
public sealed class GameVersion
{
    private readonly JsonObject vanilla, fabric;
    public string Id { get; }
    public string Minecraft { get; }
    public string MainClass { get; }
    public string AssetIndexId { get; }
    public GameFile AssetIndex { get; }
    public GameFile Client { get; }
    public GameFile? LogConfig { get; }
    public string? LogArgument { get; }
    public string JavaComponent { get; }

    public GameVersion(byte[] vanillaJson, byte[] fabricJson)
    {
        vanilla = JsonNode.Parse(vanillaJson) as JsonObject ?? throw new InvalidDataException("Minecraft version file is invalid.");
        fabric = JsonNode.Parse(fabricJson) as JsonObject ?? throw new InvalidDataException("Fabric version file is invalid.");
        Minecraft = (string?)vanilla["id"] ?? throw new InvalidDataException("Minecraft version has no id.");
        if ((string?)fabric["inheritsFrom"] != Minecraft) throw new InvalidDataException("Fabric profile does not match the Minecraft version.");
        Id = (string?)fabric["id"] ?? throw new InvalidDataException("Fabric profile has no id.");
        MainClass = (string?)fabric["mainClass"] ?? (string?)vanilla["mainClass"] ?? throw new InvalidDataException("Version has no main class.");
        var index = vanilla["assetIndex"] as JsonObject ?? throw new InvalidDataException("Minecraft version has no asset index.");
        AssetIndexId = Safe((string?)index["id"]);
        AssetIndex = Remote("assets/indexes/" + AssetIndexId + ".json", index);
        Client = Remote("versions/" + Minecraft + "/" + Minecraft + ".jar", vanilla["downloads"]?["client"] as JsonObject
            ?? throw new InvalidDataException("Minecraft version has no client download."));
        if (vanilla["logging"]?["client"] is JsonObject logging && logging["file"] is JsonObject logFile)
        {
            LogConfig = Remote("assets/log_configs/" + Safe((string?)logFile["id"]), logFile);
            LogArgument = (string?)logging["argument"];
        }
        JavaComponent = Safe((string?)vanilla["javaVersion"]?["component"] ?? "java-runtime-epsilon");
    }

    private static string Safe(string? name) => name is not null && Regex.IsMatch(name, @"^[A-Za-z0-9._-]{1,80}$") && !name.Contains("..")
        ? name : throw new InvalidDataException("Version file contains an unsafe name.");

    private static GameFile Remote(string path, JsonObject node)
    {
        SafePaths.ValidateRelative(path);
        var sha1 = (string?)node["sha1"]; var url = (string?)node["url"]; var size = (long?)node["size"];
        if (sha1 is not { Length: 40 } || !sha1.All(Uri.IsHexDigit) || url is null || size is null or < 1)
            throw new InvalidDataException("Version file has an invalid download for " + path + ".");
        GameDownloads.ValidateUri(url);
        return new GameFile(path, url, size.Value, sha1.ToLowerInvariant());
    }

    /// <summary>Mojang's rule format: no rules allows, otherwise the last matching rule decides.</summary>
    public static bool Allowed(JsonNode? rules, GamePlatform platform, IReadOnlySet<string> features)
    {
        if (rules is not JsonArray list) return true;
        var allowed = false;
        foreach (var rule in list.OfType<JsonObject>())
        {
            var matches = true;
            if (rule["os"] is JsonObject os)
            {
                if (os["name"] is JsonValue name && (string?)name != platform.Os) matches = false;
                if (os["arch"] is JsonValue arch && (string?)arch != platform.Arch) matches = false;
                if (os["versionRange"] is JsonObject range)
                {
                    if (range["min"] is JsonValue min && Version.TryParse((string?)min, out var low) && platform.OsVersion < low) matches = false;
                    if (range["max"] is JsonValue max && Version.TryParse((string?)max, out var high) && platform.OsVersion >= high) matches = false;
                }
                if (os["version"] is JsonValue pattern && !Regex.IsMatch(platform.OsVersion.ToString(), (string?)pattern ?? "")) matches = false;
            }
            if (rule["features"] is JsonObject wanted)
                foreach (var (key, value) in wanted)
                    if ((value?.GetValue<bool>() ?? false) != features.Contains(key)) matches = false;
            if (matches) allowed = (string?)rule["action"] == "allow";
        }
        return allowed;
    }

    /// <summary>group:artifact:version[:classifier] as a Maven path below libraries/.</summary>
    public static string MavenPath(string name)
    {
        var parts = name.Split(':');
        if (parts.Length is < 3 or > 4 || parts.Any(p => !Regex.IsMatch(p, @"^[A-Za-z0-9._+-]{1,120}$") || p.Contains("..")))
            throw new InvalidDataException("Library name is invalid: " + name);
        var file = parts[1] + "-" + parts[2] + (parts.Length == 4 ? "-" + parts[3] : "") + ".jar";
        return parts[0].Replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + file;
    }

    // Libraries are the same artifact when group, name and classifier match; the version may differ.
    private static string LibraryKey(string name)
    {
        var parts = name.Split(':');
        return parts.Length >= 4 ? parts[0] + ":" + parts[1] + ":" + parts[3] : parts[0] + ":" + parts[1];
    }

    /// <summary>
    /// Fabric libraries first (they win over vanilla copies of the same artifact), then vanilla. Fabric's files are the signed pack's
    /// loader files (SHA-256), because the Fabric profile does not list a checksum for every library.
    /// </summary>
    public IReadOnlyList<GameFile> Libraries(GamePlatform platform, IReadOnlyList<PackFile> loaderFiles)
    {
        var seen = new HashSet<string>(StringComparer.Ordinal);
        var result = new List<GameFile>();
        foreach (var library in (fabric["libraries"] as JsonArray ?? []).OfType<JsonObject>())
        {
            var name = (string?)library["name"] ?? throw new InvalidDataException("Fabric library has no name.");
            if (!Allowed(library["rules"], platform, new HashSet<string>()) || !seen.Add(LibraryKey(name))) continue;
            var path = "libraries/" + MavenPath(name);
            var signed = loaderFiles.FirstOrDefault(f => f.Path == path) ?? throw new InvalidDataException("The pack does not list Fabric library " + name + ".");
            result.Add(Checked(new GameFile(path, signed.Url, signed.Size, signed.Sha256.ToLowerInvariant())));
        }
        foreach (var library in (vanilla["libraries"] as JsonArray ?? []).OfType<JsonObject>())
        {
            var name = (string?)library["name"] ?? throw new InvalidDataException("Minecraft library has no name.");
            if (!Allowed(library["rules"], platform, new HashSet<string>()) || library["downloads"]?["artifact"] is not JsonObject artifact) continue;
            if (!seen.Add(LibraryKey(name))) continue;
            var path = (string?)artifact["path"] ?? MavenPath(name);
            result.Add(Remote("libraries/" + path, artifact));
        }
        return result;
    }

    private static GameFile Checked(GameFile file)
    {
        SafePaths.ValidateRelative(file.Path);
        if (file.Hash.Length is not (40 or 64) || !file.Hash.All(Uri.IsHexDigit) || file.Size < 1) throw new InvalidDataException("Library checksum is invalid: " + file.Path);
        GameDownloads.ValidateUri(file.Url);
        return file;
    }

    /// <summary>The JVM flags Mojang recommends for this version (garbage collector and friends), without its heap sizes.</summary>
    public IReadOnlyList<string> RecommendedJvmFlags(GamePlatform platform)
    {
        var flags = Expand(vanilla["arguments"]?["default-user-jvm"], platform, new HashSet<string>());
        return flags.Where(f => !f.StartsWith("-Xmx", StringComparison.Ordinal) && !f.StartsWith("-Xms", StringComparison.Ordinal)).ToArray();
    }

    /// <summary>JVM arguments, main class and game arguments with every ${placeholder} filled in.</summary>
    public IReadOnlyList<string> CommandLine(GamePlatform platform, IReadOnlySet<string> features, IReadOnlyDictionary<string, string> values, IEnumerable<string> extraJvm)
    {
        var jvm = Expand(vanilla["arguments"]?["jvm"], platform, features).Concat(Expand(fabric["arguments"]?["jvm"], platform, features));
        var game = Expand(vanilla["arguments"]?["game"], platform, features).Concat(Expand(fabric["arguments"]?["game"], platform, features));
        // Mojang's launcher log setup turns the console into XML for its own log viewer; the game's built-in setup keeps
        // plain text in the console and in logs/latest.log, which is what reports need.
        var args = extraJvm.Concat(jvm).Select(a => Fill(a, values)).ToList();
        args.Add(MainClass);
        var filled = game.Select(a => Fill(a, values)).ToList();
        // Offline players have no Xbox id or client id: drop such options instead of passing an empty value.
        for (var i = 0; i < filled.Count; i++)
        {
            if (filled[i].StartsWith("--", StringComparison.Ordinal) && i + 1 < filled.Count && filled[i + 1].Length == 0) { i++; continue; }
            args.Add(filled[i]);
        }
        return args;
    }

    private static List<string> Expand(JsonNode? node, GamePlatform platform, IReadOnlySet<string> features)
    {
        var result = new List<string>();
        foreach (var item in node as JsonArray ?? [])
        {
            if (item is JsonValue plain) { result.Add((string?)plain ?? ""); continue; }
            if (item is not JsonObject entry || !Allowed(entry["rules"], platform, features)) continue;
            if (entry["value"] is JsonValue single) result.Add((string?)single ?? "");
            else foreach (var value in entry["value"] as JsonArray ?? []) result.Add((string?)value ?? "");
        }
        return result;
    }

    private static string Fill(string argument, IReadOnlyDictionary<string, string> values) =>
        Regex.Replace(argument, @"\$\{([a-zA-Z0-9_]+)\}", m => values.TryGetValue(m.Groups[1].Value, out var value) ? value : "");

    /// <summary>The same Fabric profile with a "join this server on start" option, used when the player's own launcher starts the game.</summary>
    public static byte[] WithJoin(byte[] fabricJson, string? server)
    {
        var root = JsonNode.Parse(fabricJson) as JsonObject ?? throw new InvalidDataException("Fabric profile is invalid.");
        var arguments = root["arguments"] as JsonObject ?? new JsonObject();
        root["arguments"] = arguments;
        // Drop an earlier join option together with its server value, then add the current one.
        var cleaned = new JsonArray();
        var skip = false;
        foreach (var item in arguments["game"] as JsonArray ?? [])
        {
            if (skip) { skip = false; continue; }
            if (item is JsonValue value && value.TryGetValue<string>(out var text) && text == "--quickPlayMultiplayer") { skip = true; continue; }
            cleaned.Add(item?.DeepClone());
        }
        if (server is not null) { cleaned.Add("--quickPlayMultiplayer"); cleaned.Add(server); }
        arguments["game"] = cleaned;
        return System.Text.Encoding.UTF8.GetBytes(root.ToJsonString(JsonSettings.Options));
    }
}
