using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Nodes;

namespace HolyLois.Core;

public sealed record FastStartOptions(string PlayerName, string? JoinServer, string LauncherVersion, int MemoryMb);

/// <summary>Everything needed to start the game: Java, its argument file and the game folder.</summary>
public sealed record LaunchPlan(string Java, string ArgumentFile, IReadOnlyList<string> Arguments, string GameDirectory);

/// <summary>
/// Fast start: the Holy Lois app starts Minecraft itself, the way SKlauncher starts it for player-name accounts.
/// Java, Minecraft and Fabric come from Mojang and Fabric, checked by checksum; mods stay in the pack's game folder.
/// </summary>
public static class FastStart
{
    public const string LauncherName = "holylois-reborn";
    public const string AssetHost = "https://resources.download.minecraft.net/";

    /// <summary>The UUID Minecraft servers give a player-name account: a name-based UUID of "OfflinePlayer:NAME".</summary>
    public static string OfflineUuid(string name)
    {
        var hash = MD5.HashData(Encoding.UTF8.GetBytes("OfflinePlayer:" + name));
        hash[6] = (byte)(hash[6] & 0x0f | 0x30);
        hash[8] = (byte)(hash[8] & 0x3f | 0x80);
        return Convert.ToHexStringLower(hash);
    }

    /// <summary>Heap size from the computer's memory: enough for the pack, never so much that Windows starts swapping.</summary>
    public static int MemoryMb(long totalBytes)
    {
        var gb = totalBytes / (1024.0 * 1024 * 1024);
        return gb >= 30 ? 8192 : gb >= 15 ? 6144 : gb >= 11 ? 5120 : gb >= 7 ? 4096 : 3072;
    }

    public static IReadOnlyList<GameFile> AssetObjects(byte[] indexJson)
    {
        var root = JsonNode.Parse(indexJson) as JsonObject ?? throw new InvalidDataException("Asset index is invalid.");
        var result = new List<GameFile>();
        foreach (var (_, node) in root["objects"] as JsonObject ?? throw new InvalidDataException("Asset index is empty."))
        {
            var hash = (string?)node?["hash"]; var size = (long?)node?["size"];
            if (hash is not { Length: 40 } || !hash.All(Uri.IsHexDigit) || size is null or < 0) throw new InvalidDataException("Asset index entry is invalid.");
            hash = hash.ToLowerInvariant();
            result.Add(new GameFile("assets/objects/" + hash[..2] + "/" + hash, AssetHost + hash[..2] + "/" + hash, size.Value, hash));
        }
        return result;
    }

    /// <summary>Checks or fetches Java, Minecraft, Fabric and the sounds and textures, then writes the start command.</summary>
    public static async Task<LaunchPlan> PrepareAsync(GameDownloads downloads, GameVersion version, IReadOnlyList<PackFile> loaderFiles, string gameDirectory, FastStartOptions options,
        GamePlatform platform, IProgress<GameProgress>? progress, CancellationToken token)
    {
        if (!PlayerNames.IsValid(options.PlayerName)) throw new InvalidDataException("Choose a player name first: 3 to 16 letters, digits or _.");
        if (!Directory.Exists(gameDirectory)) throw new IOException("Install Holy Lois before starting it.");
        var java = await JavaRuntime.EnsureAsync(downloads, version.JavaComponent, progress, token);
        var libraries = version.Libraries(platform, loaderFiles);
        var game = new List<GameFile>(libraries) { version.Client, version.AssetIndex };
        await downloads.EnsureAsync(game, "game", progress, token);
        var objects = AssetObjects(File.ReadAllBytes(downloads.PathOf(version.AssetIndex)));
        await downloads.EnsureAsync(objects, "assets", progress, token);

        var natives = SafePaths.Resolve(downloads.Root, "natives/" + version.Id);
        // The version file points Java, JNA, LWJGL and Netty at their own subfolders for unpacked native code.
        foreach (var part in new[] { "java", "jna", "lwjgl", "netty" }) Directory.CreateDirectory(Path.Combine(natives, part));
        var separator = platform.Os == "windows" ? ";" : ":";
        var classpath = string.Join(separator, libraries.Select(downloads.PathOf).Append(downloads.PathOf(version.Client)));
        var values = new Dictionary<string, string>
        {
            ["auth_player_name"] = options.PlayerName, ["version_name"] = version.Id, ["game_directory"] = Path.GetFullPath(gameDirectory),
            ["assets_root"] = SafePaths.Resolve(downloads.Root, "assets"), ["assets_index_name"] = version.AssetIndexId,
            ["auth_uuid"] = OfflineUuid(options.PlayerName), ["auth_access_token"] = "0", ["clientid"] = "", ["auth_xuid"] = "",
            ["user_type"] = "legacy", ["version_type"] = "release", ["natives_directory"] = natives, ["launcher_name"] = LauncherName,
            ["launcher_version"] = options.LauncherVersion, ["classpath"] = classpath, ["classpath_separator"] = separator,
            ["library_directory"] = SafePaths.Resolve(downloads.Root, "libraries"), ["quickPlayMultiplayer"] = options.JoinServer ?? ""
        };
        var features = new HashSet<string>();
        if (options.JoinServer is not null) features.Add("is_quick_play_multiplayer");
        var memory = Math.Clamp(options.MemoryMb, 1024, 32768);
        var jvm = new List<string> { "-Xmx" + memory + "M", "-Xms" + Math.Min(2048, memory) + "M" };
        jvm.AddRange(version.RecommendedJvmFlags(platform));
        var arguments = version.CommandLine(platform, features, values, jvm);
        // An argument file keeps the long class path clear of Windows' command line limit.
        var file = SafePaths.Resolve(downloads.Root, "launch/" + version.Id + ".args");
        AtomicFiles.Write(file, Encoding.UTF8.GetBytes(ArgumentFile(arguments)));
        return new LaunchPlan(java, file, arguments, Path.GetFullPath(gameDirectory));
    }

    /// <summary>Java's @file format: one quoted argument per line, backslashes and quotes escaped.</summary>
    public static string ArgumentFile(IEnumerable<string> arguments) =>
        string.Concat(arguments.Select(a => "\"" + a.Replace("\\", "\\\\").Replace("\"", "\\\"") + "\"\n"));
}
