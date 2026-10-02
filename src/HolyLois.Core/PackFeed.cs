using System.IO.Compression;
using System.Text.Json;

namespace HolyLois.Core;

public sealed record SignedPack(byte[] Json, byte[] Signature);

public sealed class PackFeed(HttpClient client, string publicKey)
{
    public const string StableUrl = "https://github.com/pjampjam/HLMC-Reborn/releases/download/pack-stable/pack.json";

    public PackManifest Accept(SignedPack release, PackManifest current)
    {
        var next = ManifestSecurity.Parse(release.Json, release.Signature, publicKey);
        if (new Version(next.Version) < new Version(current.Version))
            throw new InvalidDataException("An older pack release was offered; your current pack was kept.");
        if (next.Defaults is null) throw new InvalidDataException("The online release has no verified defaults bundle.");
        if (next.Version == current.Version && !JsonSerializer.SerializeToUtf8Bytes(next with { Defaults = null }, JsonSettings.Options)
            .SequenceEqual(JsonSerializer.SerializeToUtf8Bytes(current with { Defaults = null }, JsonSettings.Options)))
            throw new InvalidDataException("An existing release was changed without a new version number.");
        if (next.Version == current.Version && current.Defaults is not null && next.Defaults != current.Defaults)
            throw new InvalidDataException("An existing defaults bundle was changed without a new version number.");
        return next;
    }

    public async Task<SignedPack> FetchAsync(CancellationToken token)
    {
        var json = await ReadAsync(StableUrl, 2 * 1024 * 1024, token);
        var signature = await ReadAsync(StableUrl + ".sig", 512, token);
        return new(json, signature);
    }

    private async Task<byte[]> ReadAsync(string url, int limit, CancellationToken token)
    {
        using var response = await client.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, token);
        response.EnsureSuccessStatusCode();
        ManifestSecurity.ValidateDownloadUri(response.RequestMessage?.RequestUri?.AbsoluteUri ?? url);
        if (response.Content.Headers.ContentLength > limit) throw new InvalidDataException("Release metadata is too large.");
        using var output = new MemoryStream();
        await using var input = await response.Content.ReadAsStreamAsync(token);
        var buffer = new byte[8192]; int count;
        while ((count = await input.ReadAsync(buffer, token)) > 0)
        {
            if (output.Length + count > limit) throw new InvalidDataException("Release metadata is too large.");
            output.Write(buffer, 0, count);
        }
        return output.ToArray();
    }

    public static Dictionary<string, byte[]> ReadDefaults(byte[] bytes)
    {
        if (bytes.Length > 8 * 1024 * 1024) throw new InvalidDataException("Default bundle too large.");
        var result = new Dictionary<string, byte[]>(StringComparer.OrdinalIgnoreCase);
        using var zip = new ZipArchive(new MemoryStream(bytes));
        long expanded = 0;
        if (zip.Entries.Count > 150) throw new InvalidDataException("Too many default files.");
        foreach (var entry in zip.Entries)
        {
            SafePaths.ValidateRelative(entry.FullName);
            _ = SharedDefaults.Target(entry.FullName);
            if (!entry.FullName.StartsWith("config/yosbr/", StringComparison.Ordinal) || entry.FullName.Contains("/voicechat/", StringComparison.OrdinalIgnoreCase)
                || entry.Length > 2 * 1024 * 1024 || (expanded += entry.Length) > 8 * 1024 * 1024)
                throw new InvalidDataException("Default bundle contains a personal file or exceeds its limit.");
            using var stream = entry.Open(); using var output = new MemoryStream(); stream.CopyTo(output);
            if (output.Length != entry.Length || !result.TryAdd(entry.FullName, output.ToArray())) throw new InvalidDataException("Invalid default bundle entry.");
        }
        return result;
    }
}
