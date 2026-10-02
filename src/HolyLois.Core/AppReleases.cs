using System.Globalization;
using System.Security.Cryptography;
using System.Text;

namespace HolyLois.Core;

public sealed record SignedAppRelease(byte[] Catalog, byte[] Signature);
public sealed record AppRelease(string Version, string Url, string Sha256, long Size)
{
    public Version NumericVersion => AppReleasePolicy.Normalize(System.Version.Parse(Version));
    public PackFile File => new("HolyLoisReborn.exe", Url, Size, Sha256);
}

public static class AppReleasePolicy
{
    public const string Repository = "pjampjam/HLMC-Reborn-Packaging-Lab";
    public const string FeedUrl = "https://github.com/" + Repository + "/releases/download/app-stable/app-release.txt";
    public static Version Normalize(Version version) => new(version.Major,version.Minor,Math.Max(version.Build,0),Math.Max(version.Revision,0));

    public static AppRelease Parse(SignedAppRelease signed, string publicKey)
    {
        if (signed.Catalog.Length > 65536 || signed.Signature.Length > 512)
            throw new InvalidDataException("App release metadata exceeds its size limit.");
        using var rsa = RSA.Create(); rsa.ImportFromPem(publicKey);
        if (!rsa.VerifyData(signed.Catalog, signed.Signature, HashAlgorithmName.SHA256, RSASignaturePadding.Pss))
            throw new InvalidDataException("App release signature failed. Your installed launcher was kept.");
        if (signed.Catalog.Any(b => b > 127 || b == 0 || b == 13)) throw new InvalidDataException("Invalid app release encoding.");
        var lines = Encoding.ASCII.GetString(signed.Catalog).Split('\n');
        if (lines.Length != 6 || lines[5] != "" || lines[0] != "holylois-app-v1") throw new InvalidDataException("Invalid app release catalog.");
        var parts = lines[1].Split('.');
        if (parts.Length is < 3 or > 4 || parts.Any(p => p.Length is < 1 or > 6 || p.Any(c => c is < '0' or > '9'))
            || !System.Version.TryParse(lines[1], out _)) throw new InvalidDataException("Invalid app version.");
        if (lines[3].Length != 64 || lines[3].Any(c => !(c is >= '0' and <= '9' or >= 'a' and <= 'f'))
            || !long.TryParse(lines[4], NumberStyles.None, CultureInfo.InvariantCulture, out var size)
            || size is < 1024 or > 262144000) throw new InvalidDataException("Invalid app release size or checksum.");
        ValidateReleaseUrl(lines[2]);
        return new(lines[1], lines[2], lines[3], size);
    }

    public static void ValidateReleaseUrl(string url)
    {
        var prefix = "https://github.com/" + Repository + "/releases/download/";
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || !url.StartsWith(prefix, StringComparison.Ordinal)
            || uri.Query.Length != 0 || uri.Fragment.Length != 0 || uri.UserInfo.Length != 0
            || url.Contains('%') || url.Any(c => char.IsControl(c) || c is ' ' or '\\' or '"')
            || !url.EndsWith("/HolyLoisReborn.exe", StringComparison.Ordinal)) throw new InvalidDataException("App release URL is outside the approved repository.");
        var tail = url[prefix.Length..].Split('/');
        if (tail.Length != 2 || tail[0].Length is < 1 or > 80 || tail[0] is "." or ".."
            || tail[0].Any(c => !(char.IsAsciiLetterOrDigit(c) || c is '.' or '-' or '_')))
            throw new InvalidDataException("Invalid app release tag.");
    }

    public static void Accept(AppRelease next, Version running, AppRelease? installed)
    {
        if (next.NumericVersion < Normalize(running) || installed is not null && next.NumericVersion < installed.NumericVersion)
            throw new InvalidDataException("An older app version was offered. Your installed launcher was kept.");
        if (installed is not null && next.NumericVersion == installed.NumericVersion && next != installed)
            throw new InvalidDataException("An existing app release changed without a new version number.");
    }
}

public sealed class AppReleaseFeed(HttpClient http, string publicKey)
{
    public async Task<SignedAppRelease> FetchAsync(CancellationToken token)
    {
        var catalog = await ReadAsync(AppReleasePolicy.FeedUrl, 65536, token);
        var signature = await ReadAsync(AppReleasePolicy.FeedUrl + ".sig", 512, token);
        var result = new SignedAppRelease(catalog, signature);
        _ = AppReleasePolicy.Parse(result, publicKey); return result;
    }

    private async Task<byte[]> ReadAsync(string url, int limit, CancellationToken token)
    {
        using var response = await http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, token);
        response.EnsureSuccessStatusCode();
        ManifestSecurity.ValidateDownloadUri(response.RequestMessage?.RequestUri?.AbsoluteUri ?? url);
        if (response.Content.Headers.ContentLength > limit) throw new InvalidDataException("App metadata exceeds its size limit.");
        using var output = new MemoryStream(); await using var input = await response.Content.ReadAsStreamAsync(token);
        var buffer = new byte[8192]; int count;
        while ((count = await input.ReadAsync(buffer, token)) > 0)
        { if (output.Length + count > limit) throw new InvalidDataException("App metadata exceeds its size limit."); output.Write(buffer, 0, count); }
        return output.ToArray();
    }
}
