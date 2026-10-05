using System.Collections.Concurrent;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text.Json;

namespace HolyLois.Core;

public sealed record GameProgress(string Step, long DoneBytes, long TotalBytes, int DoneFiles, int TotalFiles);

/// <summary>
/// Puts Mojang's game files (Java, libraries, sounds and textures) in one folder. Every file is checked by its hash before use.
/// A file that already sits in another launcher's folder with the same checksum is linked (or copied) instead of downloaded,
/// so most players start without a big download. Checked files are remembered by size and time, so later starts skip hashing.
/// </summary>
public sealed class GameDownloads
{
    private static readonly HashSet<string> Hosts = new(StringComparer.OrdinalIgnoreCase)
    {
        "piston-meta.mojang.com", "piston-data.mojang.com", "launchermeta.mojang.com", "launcher.mojang.com",
        "libraries.minecraft.net", "resources.download.minecraft.net", "maven.fabricmc.net"
    };
    private readonly HttpClient http;
    private readonly string root;
    private readonly IReadOnlyList<string> reuseRoots;
    private readonly string stampPath;
    private readonly ConcurrentDictionary<string, Stamp> stamps;
    public int Parallel { get; init; } = 12;
    /// <summary>For tests: count of files that came from the network.</summary>
    public int Downloaded => downloaded;
    private int downloaded;
    public int Reused => reused;
    private int reused;

    private sealed record Stamp(long Size, long Ticks, string Hash);

    public GameDownloads(HttpClient http, string root, IEnumerable<string> reuseRoots)
    {
        this.http = http; this.root = Path.GetFullPath(root);
        this.reuseRoots = reuseRoots.Where(r => !string.IsNullOrWhiteSpace(r) && Directory.Exists(r)).Select(Path.GetFullPath)
            .Where(r => !r.Equals(this.root, StringComparison.OrdinalIgnoreCase)).Distinct(StringComparer.OrdinalIgnoreCase).ToArray();
        Directory.CreateDirectory(this.root);
        stampPath = SafePaths.Resolve(this.root, "checked-files.json");
        stamps = new(StringComparer.OrdinalIgnoreCase);
        try
        {
            if (File.Exists(stampPath) && new FileInfo(stampPath).Length < 32 * 1024 * 1024)
                foreach (var (key, value) in JsonSerializer.Deserialize<Dictionary<string, Stamp>>(File.ReadAllBytes(stampPath), JsonSettings.Options) ?? [])
                    stamps[key] = value;
        }
        catch (JsonException) { /* a damaged list only means the files get checked again */ }
    }

    public string Root => root;
    public string PathOf(GameFile file) => SafePaths.Resolve(root, file.Path);

    public static void ValidateUri(string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != "https" || !string.IsNullOrEmpty(uri.UserInfo)
            || !uri.IsDefaultPort || !Hosts.Contains(uri.Host))
            throw new InvalidDataException("A game file points to an unapproved download location.");
    }

    /// <summary>The file's hash in the same kind as EXPECTED: SHA-256 for 64 hex digits, otherwise SHA-1.</summary>
    public static string HashOf(string path, string expected)
    {
        using var stream = File.OpenRead(path);
        return Convert.ToHexStringLower(expected.Length == 64 ? SHA256.HashData(stream) : SHA1.HashData(stream));
    }
    private static bool Same(string path, GameFile file) => HashOf(path, file.Hash).Equals(file.Hash, StringComparison.OrdinalIgnoreCase);

    /// <summary>True when the file is in place and either matches its remembered stamp or hashes correctly now.</summary>
    public bool IsReady(GameFile file)
    {
        var path = PathOf(file);
        var info = new FileInfo(path);
        if (!info.Exists || info.Length != file.Size) return false;
        if (stamps.TryGetValue(file.Path, out var stamp) && stamp.Size == info.Length && stamp.Ticks == info.LastWriteTimeUtc.Ticks
            && stamp.Hash.Equals(file.Hash, StringComparison.OrdinalIgnoreCase)) return true;
        if (!Same(path, file)) return false;
        Remember(file, info);
        return true;
    }

    private void Remember(GameFile file, FileInfo info) { info.Refresh(); stamps[file.Path] = new Stamp(info.Length, info.LastWriteTimeUtc.Ticks, file.Hash); }

    public void SaveStamps() => AtomicFiles.Write(stampPath, JsonSerializer.SerializeToUtf8Bytes(stamps.ToDictionary(p => p.Key, p => p.Value), JsonSettings.Options));

    /// <summary>Makes every file ready: already checked, reused from another launcher, or downloaded. Reports bytes as they arrive.</summary>
    public async Task EnsureAsync(IReadOnlyList<GameFile> files, string step, IProgress<GameProgress>? progress, CancellationToken token)
    {
        var unique = files.GroupBy(f => f.Path, StringComparer.OrdinalIgnoreCase).Select(g => g.First()).ToArray();
        long total = unique.Sum(f => f.Size), done = 0; var count = 0;
        void Report() => progress?.Report(new GameProgress(step, Interlocked.Read(ref done), total, Volatile.Read(ref count), unique.Length));
        Report();
        var lastReport = DateTime.UtcNow;
        await System.Threading.Tasks.Parallel.ForEachAsync(unique, new ParallelOptions { MaxDegreeOfParallelism = Parallel, CancellationToken = token }, async (file, ct) =>
        {
            if (!IsReady(file) && !TryReuse(file)) await DownloadAsync(file, bytes => { Interlocked.Add(ref done, bytes); }, ct);
            else Interlocked.Add(ref done, file.Size);
            Interlocked.Increment(ref count);
            if (DateTime.UtcNow - lastReport > TimeSpan.FromMilliseconds(120)) { lastReport = DateTime.UtcNow; Report(); }
        });
        // Bytes of a retried download can be counted twice; finish on the exact total.
        Interlocked.Exchange(ref done, total); Report();
        SaveStamps();
    }

    private bool TryReuse(GameFile file)
    {
        foreach (var other in reuseRoots)
        {
            try
            {
                var source = SafePaths.Resolve(other, file.Path);
                var info = new FileInfo(source);
                if (!info.Exists || info.Length != file.Size || !Same(source, file)) continue;
                var target = PathOf(file);
                Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                var temp = target + ".holylois-tmp-" + Guid.NewGuid().ToString("N");
                try
                {
                    if (!HardLink(source, temp)) File.Copy(source, temp);
                    if (!Same(temp, file)) continue;
                    File.Move(temp, target, true);
                }
                finally { if (File.Exists(temp)) File.Delete(temp); }
                Remember(file, new FileInfo(target));
                Interlocked.Increment(ref reused);
                return true;
            }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or InvalidDataException) { }
        }
        return false;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, EntryPoint = "CreateHardLinkW")]
    private static extern bool CreateHardLink(string newFile, string existingFile, IntPtr reserved);

    // Game files never change in place, so a hard link shares the bytes with the other launcher without copying them.
    private static bool HardLink(string source, string target)
    {
        if (!OperatingSystem.IsWindows()) return false;
        try { return CreateHardLink(target, source, IntPtr.Zero); }
        catch (Exception ex) when (ex is EntryPointNotFoundException or DllNotFoundException) { return false; }
    }

    private async Task DownloadAsync(GameFile file, Action<long> counted, CancellationToken token)
    {
        ValidateUri(file.Url);
        var target = PathOf(file);
        Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        Exception? last = null;
        for (var attempt = 0; attempt < 3; attempt++)
        {
            var partial = target + "." + Guid.NewGuid().ToString("N") + ".part";
            long received = 0;
            try
            {
                using var response = await http.GetAsync(file.Url, HttpCompletionOption.ResponseHeadersRead, token);
                response.EnsureSuccessStatusCode();
                ValidateUri(response.RequestMessage?.RequestUri?.AbsoluteUri ?? file.Url);
                await using (var input = await response.Content.ReadAsStreamAsync(token))
                await using (var output = new FileStream(partial, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, true))
                {
                    var buffer = new byte[81920]; int read;
                    while ((read = await input.ReadAsync(buffer, token)) > 0)
                    {
                        received += read;
                        if (received > file.Size) throw new InvalidDataException("A game file is larger than expected.");
                        await output.WriteAsync(buffer.AsMemory(0, read), token);
                        counted(read);
                    }
                }
                if (received != file.Size || !Same(partial, file))
                    throw new InvalidDataException("A game file failed its checksum.");
                File.Move(partial, target, true);
                Remember(file, new FileInfo(target));
                Interlocked.Increment(ref downloaded);
                return;
            }
            catch (Exception ex) when (ex is HttpRequestException or IOException or InvalidDataException && !token.IsCancellationRequested)
            {
                last = ex; counted(-received);
                if (attempt < 2) await Task.Delay(TimeSpan.FromSeconds(attempt + 1), token);
            }
            finally { if (File.Exists(partial)) File.Delete(partial); }
        }
        throw new IOException("Minecraft files could not be downloaded. Check your internet connection and try again.", last);
    }

    /// <summary>Downloads a small metadata file (asset index, Java list) that has no published checksum, with a size cap.</summary>
    public async Task<byte[]> FetchAsync(string url, int maxBytes, CancellationToken token)
    {
        ValidateUri(url);
        using var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, token);
        response.EnsureSuccessStatusCode();
        ValidateUri(response.RequestMessage?.RequestUri?.AbsoluteUri ?? url);
        await using var input = await response.Content.ReadAsStreamAsync(token);
        using var buffer = new MemoryStream();
        var chunk = new byte[81920]; int read;
        while ((read = await input.ReadAsync(chunk, token)) > 0)
        {
            if (buffer.Length + read > maxBytes) throw new InvalidDataException("Game metadata is too large.");
            buffer.Write(chunk, 0, read);
        }
        return buffer.ToArray();
    }
}
