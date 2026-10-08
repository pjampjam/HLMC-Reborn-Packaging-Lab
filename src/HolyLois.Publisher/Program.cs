using HolyLois.Core;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.IO.Compression;

if (args.Length < 1) throw new ArgumentException("Use keys PRIVATE_DIR ASSETS_DIR or sign PRIVATE_KEY FILE SIGNATURE or catalog PRIVATE_KEY EXE VERSION HTTPS_URL OUTPUT.");
switch (args[0])
{
    case "keys" when args.Length == 3:
        Directory.CreateDirectory(args[1]); Directory.CreateDirectory(args[2]);
        var keyPath = Path.Combine(args[1], "release-private.pem");
        if (!File.Exists(keyPath)) { using var generated = RSA.Create(3072); AtomicFiles.Write(keyPath, Encoding.ASCII.GetBytes(generated.ExportPkcs8PrivateKeyPem())); }
        using (var rsa = Load(keyPath))
        {
            AtomicFiles.Write(Path.Combine(args[2], "release-public.pem"), Encoding.ASCII.GetBytes(rsa.ExportSubjectPublicKeyInfoPem()));
            AtomicFiles.Write(Path.Combine(args[2], "release-public.der"), rsa.ExportSubjectPublicKeyInfo());
        }
        Console.WriteLine("Release public key exported; private key remains in the private directory."); break;
    case "sign" when args.Length == 4:
        using (var rsa = Load(args[1])) AtomicFiles.Write(args[3], rsa.SignData(File.ReadAllBytes(args[2]), HashAlgorithmName.SHA256, RSASignaturePadding.Pss));
        Console.WriteLine("Signed " + Path.GetFileName(args[2])); break;
    case "catalog" when args.Length == 6:
        if (!Version.TryParse(args[3], out _)) throw new ArgumentException("Invalid app version.");
        var url = new Uri(args[4]);
        if (url.Scheme != "https" || url.Host != "github.com" || !url.AbsolutePath.Contains("/releases/download/") || !url.AbsolutePath.EndsWith(".exe"))
            throw new ArgumentException("App must use a versioned HTTPS GitHub release asset.");
        var catalog = Encoding.ASCII.GetBytes($"holylois-app-v1\n{args[3]}\n{args[4]}\n{AtomicFiles.Hash(args[2])}\n{new FileInfo(args[2]).Length}\n");
        AtomicFiles.Write(args[5], catalog);
        using (var rsa = Load(args[1])) AtomicFiles.Write(args[5] + ".sig", rsa.SignData(catalog, HashAlgorithmName.SHA256, RSASignaturePadding.Pss));
        Console.WriteLine("Created signed catalog; no files were published."); break;
    case "prepare-pack" when args.Length is 7 or 8:
        await PreparePack(args[1], args[2], args[3], args[4], args[5], args[6], args.Length == 8 ? args[7] : null); break;
    default: throw new ArgumentException("Invalid publisher command.");
}
static RSA Load(string path) { var rsa = RSA.Create(); rsa.ImportFromPem(File.ReadAllText(path)); return rsa; }

static async Task PreparePack(string key, string baseManifest, string profile, string version, string defaultsZip, string output, string? selection)
{
    var previous = JsonSerializer.Deserialize<PackManifest>(File.ReadAllBytes(baseManifest), JsonSettings.Options)!;
    ManifestSecurity.Validate(previous);
    if (!Version.TryParse(version, out var nextVersion) || nextVersion <= new Version(previous.Version))
        throw new ArgumentException("Choose a new pack version higher than " + previous.Version + ".");
    profile = Path.GetFullPath(profile); SafePaths.RejectLinks(profile);
    var checkedMods=FabricDependencies.CheckClient(profile);
    Console.WriteLine("Dependency check passed for "+checkedMods.Length+" client mods, including bundled libraries.");
    var files = new List<PackFile>();
    using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(45) };
    http.DefaultRequestHeaders.UserAgent.ParseAdd("HolyLoisReborn-Publisher/0.2");
    foreach (var folder in new[] { "mods", "resourcepacks", "shaderpacks" })
    {
        var directory = SafePaths.Resolve(profile, folder);
        if (!Directory.Exists(directory)) continue;
        foreach (var path in Directory.GetFiles(directory, folder == "mods" ? "*.jar" : "*.zip").Order())
        {
            SafePaths.RejectLinks(path);
            var relative = folder + "/" + Path.GetFileName(path);
            var hash = AtomicFiles.Hash(path); var size = new FileInfo(path).Length;
            var known = previous.Files.FirstOrDefault(f => f.Sha256 == hash && f.Size == size);
            string url;
            if (known is not null) url = known.Url;
            else
            {
                await using var source = File.OpenRead(path);
                var sha512 = Convert.ToHexStringLower(await SHA512.HashDataAsync(source));
                using var response = await http.GetAsync("https://api.modrinth.com/v2/version_file/" + sha512 + "?algorithm=sha512");
                if (!response.IsSuccessStatusCode) throw new IOException("No verified Modrinth file was found for " + relative + ". Curate its official download in the base manifest first.");
                var bytes = await response.Content.ReadAsByteArrayAsync();
                if (bytes.Length > 2 * 1024 * 1024) throw new InvalidDataException("Publisher metadata exceeds its limit.");
                using var metadata = JsonDocument.Parse(bytes); var info = metadata.RootElement;
                if (info.GetProperty("version_type").GetString() != "release" || !info.GetProperty("game_versions").EnumerateArray().Any(x => x.GetString() == "26.3")
                    || folder == "mods" && !info.GetProperty("loaders").EnumerateArray().Any(x => x.GetString() == "fabric"))
                    throw new InvalidDataException(relative + " is not a final 26.3 release for the required loader.");
                var remoteFile = info.GetProperty("files").EnumerateArray().Single(f => f.GetProperty("hashes").GetProperty("sha512").GetString() == sha512);
                if (remoteFile.GetProperty("size").GetInt64() != size) throw new InvalidDataException("Publisher file size mismatch.");
                url = remoteFile.GetProperty("url").GetString()!;
            }
            files.Add(new(relative, url, size, hash, "managed", known?.AutoEnable));
            Console.WriteLine("Verified publisher source: " + relative);
        }
    }
    var defaultBytes = selection is null ? File.ReadAllBytes(defaultsZip) : CaptureDefaults(profile, defaultsZip, selection);

    _ = PackFeed.ReadDefaults(defaultBytes);
    var bundle = new PackFile("defaults.zip", $"https://github.com/pjampjam/HLMC-Reborn/releases/download/pack-v{version}/defaults.zip", defaultBytes.Length,
        Convert.ToHexStringLower(SHA256.HashData(defaultBytes)), "seed");
    var before = previous.Files.ToDictionary(f => f.Path, StringComparer.OrdinalIgnoreCase);
    var after = files.ToDictionary(f => f.Path, StringComparer.OrdinalIgnoreCase);
    var note = new ReleaseNote(version, DateTime.UtcNow.ToString("yyyy-MM-dd"), "Updated mods, packs and shared settings.",
        after.Keys.Where(p => !before.ContainsKey(p)).Select(Path.GetFileName).Select(n => n!).ToArray(),
        before.Keys.Where(p => !after.ContainsKey(p)).Select(Path.GetFileName).Select(n => n!).ToArray(),
        after.Keys.Where(p => before.TryGetValue(p, out var old) && old.Sha256 != after[p].Sha256).Select(Path.GetFileName).Select(n => n!).Concat(defaultBytes.SequenceEqual(File.ReadAllBytes(defaultsZip)) ? [] : new[] { "Shared settings" }).ToArray());
    var next = previous with { Version = version, Files = files.ToArray(), Defaults = bundle, ApplyDefaultsOnUpdate = true,
        History = new[] { note }.Concat(previous.History ?? []).Take(20).ToArray() };
    ManifestSecurity.Validate(next); Directory.CreateDirectory(output);
    AtomicFiles.WriteJson(Path.Combine(output,"pack.json"), next);
    using (var rsa = Load(key)) AtomicFiles.Write(Path.Combine(output,"pack.json.sig"), rsa.SignData(File.ReadAllBytes(Path.Combine(output,"pack.json")), HashAlgorithmName.SHA256, RSASignaturePadding.Pss));
    AtomicFiles.Write(Path.Combine(output,"defaults.zip"), defaultBytes);
    Console.WriteLine("Prepared signed release. Test a fresh install and required dependencies before publishing. No personal files or mod JARs were copied.");
}

static byte[] CaptureDefaults(string profile, string defaultsZip, string selection)
{
    var selected = JsonSerializer.Deserialize<string[]>(File.ReadAllBytes(selection)) ?? throw new IOException("Choose the shared settings first.");
    var defaults = PackFeed.ReadDefaults(File.ReadAllBytes(defaultsZip));
    foreach (var relative in selected)
    {
        var target = SharedDefaults.Target("config/yosbr/" + relative);
        var source = SafePaths.Resolve(profile, target);
        if (!File.Exists(source)) continue;
        if (new FileInfo(source).Length > 2 * 1024 * 1024) throw new IOException("Config too large: " + target);
        var data = File.ReadAllBytes(source);
        if (target == "options.txt")
        {
            var lines = Encoding.UTF8.GetString(data).Replace("\r\n", "\n").Split('\n').Where(l => !l.StartsWith("lastServer:")).Select(l => l.StartsWith("renderDistance:") ? "renderDistance:12" : l);
            data = Encoding.UTF8.GetBytes(string.Join("\n", lines));
        }
        if (target == "config/iris.properties") data = Encoding.UTF8.GetBytes(System.Text.RegularExpressions.Regex.Replace(Encoding.UTF8.GetString(data), @"(?m)^enableShaders=.*$", "enableShaders=false"));
        if (target == "config/DistantHorizons.toml") data = Encoding.UTF8.GetBytes(System.Text.RegularExpressions.Regex.Replace(Encoding.UTF8.GetString(data), @"(?m)^(\s*lodChunkRenderDistance\s*=\s*)\d+", "${1}64"));
        defaults["config/yosbr/" + target] = data;
    }
    using var memory = new MemoryStream();
    using (var zip = new ZipArchive(memory, ZipArchiveMode.Create, true)) foreach (var (path, bytes) in defaults)
    {
        var entry = zip.CreateEntry(path, CompressionLevel.Optimal); using var stream = entry.Open(); stream.Write(bytes);
    }
    var result = memory.ToArray(); _ = PackFeed.ReadDefaults(result); return result;
}
