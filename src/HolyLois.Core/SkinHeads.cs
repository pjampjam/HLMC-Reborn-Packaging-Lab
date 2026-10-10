using System.Text.Json;
using System.Text.RegularExpressions;

namespace HolyLois.Core;

/// <summary>
/// The player's skin for the launcher's account card. The public stats feed (stats.holylois.com) lists each visible player's
/// selected skin as a Mojang texture link; nothing else is read from it. Hidden players and new names simply have no head.
/// </summary>
public static partial class SkinHeads
{
    public const string Feed = "https://stats.holylois.com/stats.json";
    public const int MaxTextureBytes = 64 * 1024;

    [GeneratedRegex("^https://textures\\.minecraft\\.net/texture/[0-9a-f]{32,80}$")]
    private static partial Regex TextureUrl();

    /// <summary>The Mojang texture link for a name (case-insensitive), or null when the feed has none or it is not a plain Mojang texture.</summary>
    public static Uri? Find(string statsJson, string name)
    {
        if (string.IsNullOrWhiteSpace(name) || statsJson.Length > 4 * 1024 * 1024) return null;
        try
        {
            using var document = JsonDocument.Parse(statsJson);
            if (!document.RootElement.TryGetProperty("heads", out var heads) || heads.ValueKind != JsonValueKind.Object) return null;
            foreach (var head in heads.EnumerateObject())
                if (head.Name.Equals(name, StringComparison.OrdinalIgnoreCase) && head.Value.ValueKind == JsonValueKind.String
                    && head.Value.GetString() is { } url && TextureUrl().IsMatch(url))
                    return new Uri(url);
        }
        catch (JsonException) { }
        return null;
    }
}
