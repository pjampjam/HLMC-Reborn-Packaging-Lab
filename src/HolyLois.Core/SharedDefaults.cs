using System.Text;
using System.Text.Json.Nodes;

namespace HolyLois.Core;

public static class SharedDefaults
{
    public static string Target(string seed)
    {
        SafePaths.ValidateRelative(seed);
        if (!seed.StartsWith("config/yosbr/", StringComparison.Ordinal)) throw new InvalidDataException("Invalid shared setting.");
        var target = seed["config/yosbr/".Length..];
        if (target != "options.txt" && !target.StartsWith("config/", StringComparison.Ordinal)) throw new InvalidDataException("Invalid shared setting target.");
        if (target.Contains("voicechat", StringComparison.OrdinalIgnoreCase) || target.Contains("account", StringComparison.OrdinalIgnoreCase)
            || target.Contains("credential", StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("Private settings cannot be shared.");
        return target;
    }

    public static byte[] Merge(string target, byte[]? personal, byte[]? previous, byte[] next)
    {
        if (personal is null) return next;
        if (target.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
        {
            var current = JsonNode.Parse(personal) as JsonObject ?? throw new InvalidDataException("Cannot merge " + target);
            var newer = JsonNode.Parse(next) as JsonObject ?? throw new InvalidDataException("Invalid defaults: " + target);
            var older = previous is null ? null : JsonNode.Parse(previous) as JsonObject;
            MergeObject(current, older, newer);
            return Encoding.UTF8.GetBytes(current.ToJsonString(JsonSettings.Options));
        }
        if (target == "options.txt" || target.EndsWith(".properties", StringComparison.OrdinalIgnoreCase))
        {
            var delimiter = target == "options.txt" ? ':' : '=';
            var oldKeys = Lines(previous, delimiter); var newKeys = Lines(next, delimiter);
            var changed = newKeys.Where(x => !oldKeys.TryGetValue(x.Key, out var old) || old != x.Value)
                .Where(x => target != "options.txt" || !x.Key.StartsWith("key_", StringComparison.Ordinal) && x.Key != "lastServer")
                .ToDictionary(x => x.Key, x => x.Value);
            var lines = Encoding.UTF8.GetString(personal).Replace("\r\n", "\n").Split('\n').ToList();
            foreach (var (key, value) in changed)
            {
                var i = lines.FindIndex(line => line.StartsWith(key + delimiter, StringComparison.Ordinal));
                if (i >= 0) lines[i] = key + delimiter + value; else lines.Add(key + delimiter + value);
            }
            if (target == "options.txt") { MigrateBodyToggle(lines, oldKeys, newKeys); SeedNewKeybinds(lines, oldKeys, newKeys); }
            return Encoding.UTF8.GetBytes(string.Join("\n", lines).TrimEnd('\n') + "\n");
        }
        // TOML and other formats use the reviewed complete shared file when its bytes change.
        return previous is not null && previous.SequenceEqual(next) ? personal : next;
    }

    private static void MigrateBodyToggle(List<string> lines, Dictionary<string, string> oldKeys, Dictionary<string, string> newKeys)
    {
        const string key = "key_key.firstperson.toggle", binding = "key.keyboard.page.down";
        if (!newKeys.TryGetValue(key, out var next) || next != binding
            || oldKeys.TryGetValue(key, out var previous) && previous == next) return;
        var index = lines.FindIndex(line => line.StartsWith(key + ":", StringComparison.Ordinal));
        // Only replace the former bundled F6 default or an unbound toggle, and never steal another control.
        if (index < 0 || lines[index][(key.Length + 1)..] is not ("key.keyboard.295" or "key.keyboard.f6" or "key.keyboard.unknown")) return;
        if (lines.Any(line => line.StartsWith("key_", StringComparison.Ordinal)
            && !line.StartsWith(key + ":", StringComparison.Ordinal) && line.EndsWith(":" + binding, StringComparison.Ordinal))) return;
        lines[index] = key + ":" + binding;
    }

    // A control first published in this release (normally from a newly added mod) is written once, before the
    // game would register the mod's own default. Existing lines stay personal, and a key another control already
    // uses is never taken; the mod default then applies and the player can rebind it in Controls.
    private static void SeedNewKeybinds(List<string> lines, Dictionary<string, string> oldKeys, Dictionary<string, string> newKeys)
    {
        foreach (var (key, binding) in newKeys)
        {
            if (!key.StartsWith("key_", StringComparison.Ordinal) || oldKeys.ContainsKey(key)
                || lines.Any(line => line.StartsWith(key + ":", StringComparison.Ordinal))) continue;
            if (binding != "key.keyboard.unknown" && lines.Any(line => line.StartsWith("key_", StringComparison.Ordinal)
                && line.EndsWith(":" + binding, StringComparison.Ordinal))) continue;
            lines.Add(key + ":" + binding);
        }
    }

    private static Dictionary<string, string> Lines(byte[]? bytes, char delimiter) => bytes is null ? [] :
        Encoding.UTF8.GetString(bytes).Replace("\r\n", "\n").Split('\n').Where(x => x.IndexOf(delimiter) > 0 && !x.StartsWith('#'))
            .GroupBy(x => x[..x.IndexOf(delimiter)]).ToDictionary(x => x.Key, x => x.Last()[(x.Last().IndexOf(delimiter) + 1)..]);
    private static void MergeObject(JsonObject target, JsonObject? old, JsonObject next)
    {
        foreach (var (key, value) in next)
        {
            if (value is JsonObject obj && target[key] is JsonObject current) MergeObject(current, old?[key] as JsonObject, obj);
            else if (old is null || !old.ContainsKey(key) || !JsonNode.DeepEquals(old[key], value)) target[key] = value?.DeepClone();
        }
    }
}
