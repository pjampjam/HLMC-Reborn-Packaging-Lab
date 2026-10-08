using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace HolyLois.Core;

public sealed record PackFile(string Path, string Url, long Size, string Sha256, string Policy = "managed",
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingDefault)] bool? AutoEnable = null);
public sealed record PackManifest(int Schema, string Version, string Minecraft, string Fabric,
    int Java, string Server, PackFile[] Files, PackFile[] LoaderFiles, PackFile? Defaults = null, ReleaseNote[]? History = null, bool ApplyDefaultsOnUpdate = false);
public sealed record ReleaseNote(string Version, string Date, string Summary, string[] Added, string[] Removed, string[] Updated);
public sealed record InstalledReceipt(string Version, Dictionary<string, string> ManagedFiles);
public sealed record InstallProgress(string Message, long CompletedBytes, long TotalBytes, int CompletedFiles, int TotalFiles);

public static class JsonSettings
{
    public static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = true,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow
    };
}

public static class ManifestSecurity
{
    private static readonly HashSet<string> DownloadHosts = new(StringComparer.OrdinalIgnoreCase)
    {
        "cdn.modrinth.com", "edge.forgecdn.net", "mediafilez.forgecdn.net",
        "maven.fabricmc.net", "libraries.minecraft.net", "maven.shedaniel.me"
    };

    public static PackManifest Parse(byte[] json, byte[] signature, string publicKeyPem)
    {
        if (json.Length > 2 * 1024 * 1024) throw new InvalidDataException("Release information is too large.");
        using var rsa = RSA.Create();
        rsa.ImportFromPem(publicKeyPem);
        if (!rsa.VerifyData(json, signature, HashAlgorithmName.SHA256, RSASignaturePadding.Pss))
            throw new InvalidDataException("Release signature is invalid. Nothing was installed.");
        var manifest = JsonSerializer.Deserialize<PackManifest>(json, JsonSettings.Options)
            ?? throw new InvalidDataException("Release information is empty.");
        Validate(manifest);
        return manifest;
    }

    public static void Validate(PackManifest manifest)
    {
        if (manifest.Schema != 1 || manifest.Minecraft != "26.3" || manifest.Fabric != "0.19.5" || manifest.Java != 25)
            throw new InvalidDataException("This launcher supports the approved Minecraft 26.3 / Fabric 0.19.5 release.");
        if (!System.Version.TryParse(manifest.Version, out _) || (manifest.Server != ServerAddress.Ip && manifest.Server != ServerAddress.Public))
            throw new InvalidDataException("Release identity is invalid.");
        if (manifest.Files.Length is < 1 or > 500 || manifest.LoaderFiles.Length > 50)
            throw new InvalidDataException("Release file list is invalid.");
        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        long bytes = 0;
        foreach (var file in manifest.Files)
        {
            ValidateFile(file);
            if(file.AutoEnable is not null && !file.Path.StartsWith("resourcepacks/",StringComparison.Ordinal))
                throw new InvalidDataException("Optional activation is only supported for resource packs.");
            if (!paths.Add(file.Path)) throw new InvalidDataException("Release contains duplicate file paths.");
            var permitted = file.Path.StartsWith("mods/", StringComparison.Ordinal) && file.Path.EndsWith(".jar", StringComparison.Ordinal)
                || file.Path.StartsWith("resourcepacks/", StringComparison.Ordinal) && file.Path.EndsWith(".zip", StringComparison.Ordinal)
                || file.Path.StartsWith("shaderpacks/", StringComparison.Ordinal) && file.Path.EndsWith(".zip", StringComparison.Ordinal)
                || file.Path.StartsWith("config/yosbr/", StringComparison.Ordinal) && file.Policy == "seed";
            if (!permitted || file.Policy is not ("managed" or "seed"))
                throw new InvalidDataException("Release requests an unmanaged or personal file.");
            bytes = checked(bytes + file.Size);
        }
        if (bytes > 2L * 1024 * 1024 * 1024) throw new InvalidDataException("Release exceeds the pack size limit.");
        if (manifest.Defaults is { } defaults)
        {
            ValidateFile(defaults);
            if (defaults.Path != "defaults.zip" || defaults.Size > 8 * 1024 * 1024 || defaults.Policy != "seed")
                throw new InvalidDataException("Invalid defaults bundle.");
        }
        paths.Clear();
        foreach (var file in manifest.LoaderFiles)
        {
            ValidateFile(file);
            if (!file.Path.StartsWith("libraries/", StringComparison.Ordinal) || !file.Path.EndsWith(".jar", StringComparison.Ordinal)
                || !paths.Add(file.Path)) throw new InvalidDataException("Loader file list is invalid.");
        }
    }

    private static void ValidateFile(PackFile file)
    {
        SafePaths.ValidateRelative(file.Path);
        if (file.Size is < 1 or > 512L * 1024 * 1024 || file.Sha256.Length != 64
            || !file.Sha256.All(Uri.IsHexDigit)) throw new InvalidDataException("File size or checksum is invalid.");
        ValidateDownloadUri(file.Url);
    }

    public static void ValidateDownloadUri(string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != "https"
            || !string.IsNullOrEmpty(uri.UserInfo) || !uri.IsDefaultPort || !(DownloadHosts.Contains(uri.Host)
                || uri.Host == "github.com" && uri.AbsolutePath.StartsWith("/pjampjam/HLMC-Reborn/releases/download/", StringComparison.Ordinal)
                || uri.Host == "github.com" && uri.AbsolutePath.StartsWith("/" + AppReleasePolicy.Repository + "/releases/download/", StringComparison.Ordinal)
                || uri.Host == "release-assets.githubusercontent.com"))
            throw new InvalidDataException("The release contains an unapproved download location.");
    }
}

public static class SafePaths
{
    public static void ValidateRelative(string relative)
    {
        if (string.IsNullOrWhiteSpace(relative) || relative.Length > 220 || relative.Contains('\\')
            || relative.StartsWith('/') || relative.Contains(':') || relative.Any(char.IsControl))
            throw new InvalidDataException("Unsafe file path.");
        foreach (var segment in relative.Split('/'))
        {
            if (segment is "" or "." or ".." || segment.EndsWith('.') || segment.EndsWith(' ')
                || segment.IndexOfAny(['<', '>', '"', '|', '?', '*']) >= 0)
                throw new InvalidDataException("Unsafe file path.");
            var stem = segment.Split('.')[0].ToUpperInvariant();
            if (stem is "CON" or "PRN" or "AUX" or "NUL" || stem.Length == 4
                && (stem.StartsWith("COM") || stem.StartsWith("LPT")) && stem[3] is >= '1' and <= '9')
                throw new InvalidDataException("Reserved Windows file name.");
        }
    }

    public static string Resolve(string root, string relative)
    {
        ValidateRelative(relative);
        var basePath = System.IO.Path.GetFullPath(root);
        var full = System.IO.Path.GetFullPath(System.IO.Path.Combine(basePath, relative.Replace('/', System.IO.Path.DirectorySeparatorChar)));
        if (!full.StartsWith(basePath.TrimEnd(System.IO.Path.DirectorySeparatorChar) + System.IO.Path.DirectorySeparatorChar,
            StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("File escaped the installation folder.");
        RejectLinks(basePath);
        for (var parent = System.IO.Path.GetDirectoryName(basePath); parent is not null; parent = System.IO.Path.GetDirectoryName(parent))
            RejectLinks(parent);
        for (var current = full; current is not null && current.Length > basePath.Length; current = System.IO.Path.GetDirectoryName(current))
            RejectLinks(current);
        return full;
    }

    public static void RejectLinks(string path)
    {
        if ((File.Exists(path) || Directory.Exists(path)) && (File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
            throw new IOException("Linked folders or files cannot be used for pack installation.");
    }
}

public static class AtomicFiles
{
    public static void Write(string path, byte[] bytes)
    {
        var dir = System.IO.Path.GetDirectoryName(path) ?? throw new IOException("Missing destination folder.");
        Directory.CreateDirectory(dir);
        var temp = path + ".holylois-tmp-" + Guid.NewGuid().ToString("N");
        try
        {
            using (var file = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            { file.Write(bytes); file.Flush(true); }
            File.Move(temp, path, true);
        }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }

    public static void WriteJson<T>(string path, T value) => Write(path, JsonSerializer.SerializeToUtf8Bytes(value, JsonSettings.Options));
    public static string Hash(string path)
    {
        using var file = File.OpenRead(path);
        return Convert.ToHexStringLower(SHA256.HashData(file));
    }
    public static bool Matches(string path, PackFile file) => File.Exists(path) && new FileInfo(path).Length == file.Size
        && Hash(path).Equals(file.Sha256, StringComparison.OrdinalIgnoreCase);
}
