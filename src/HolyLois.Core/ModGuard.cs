namespace HolyLois.Core;

public sealed record GuardReport(string[] Moved, string[] Damaged, bool PacksRestored)
{
    public static readonly GuardReport Empty = new([], [], false);
    public bool Clean => Moved.Length == 0 && Damaged.Length == 0 && !PacksRestored;
}

/// <summary>
/// Keeps the instance on the pack: jars the pack does not know are moved aside (never deleted), pack files that were
/// changed or removed are reported so the caller can repair them, and every pack resource pack is switched back on.
/// Shaders, options, keybinds and the rest of the player's settings are not touched.
/// </summary>
public static class ModGuard
{
    public static string[] FindUnknownMods(string instanceRoot, PackManifest manifest)
    {
        var mods = SafePaths.Resolve(instanceRoot, "mods");
        if (!Directory.Exists(mods)) return [];
        var known = new HashSet<string>(manifest.Files.Select(f => f.Path), StringComparer.OrdinalIgnoreCase);
        return Directory.EnumerateFiles(mods, "*.jar", SearchOption.AllDirectories)
            .Select(p => Path.GetRelativePath(instanceRoot, p).Replace('\\', '/'))
            .Where(p => !known.Contains(p)).Order(StringComparer.Ordinal).ToArray();
    }

    public static string[] FindDamaged(string instanceRoot, PackManifest manifest) =>
        manifest.Files.Where(f => f.Policy == "managed" && f.Path.StartsWith("mods/", StringComparison.Ordinal)
            && !AtomicFiles.Matches(SafePaths.Resolve(instanceRoot, f.Path), f)).Select(f => f.Path).ToArray();

    public static string[] MoveAside(string instanceRoot, string stateRoot, IReadOnlyCollection<string> relativePaths)
    {
        if (relativePaths.Count == 0) return [];
        var target = SafePaths.Resolve(stateRoot, "quarantine/" + DateTime.UtcNow.ToString("yyyyMMdd'T'HHmmss'Z'"));
        var moved = new List<string>();
        foreach (var relative in relativePaths)
        {
            SafePaths.ValidateRelative(relative);
            var from = SafePaths.Resolve(instanceRoot, relative);
            if (!File.Exists(from)) continue;
            var to = SafePaths.Resolve(target, relative);
            Directory.CreateDirectory(Path.GetDirectoryName(to)!);
            File.Move(from, to, true);
            moved.Add(relative);
        }
        return moved.ToArray();
    }

    public static bool EnsureResourcePacks(string instanceRoot, PackManifest manifest)
    {
        var options = SafePaths.Resolve(instanceRoot, "options.txt");
        if (!File.Exists(options)) return false;
        var packs = manifest.Files.Where(f => f.Path.StartsWith("resourcepacks/", StringComparison.Ordinal))
            .Select(f => Path.GetFileName(f.Path)).ToArray();
        if (packs.Length == 0) return false;
        try
        {
            var original = File.ReadAllBytes(options);
            var updated = ResourcePackOptions.AddNewPacks(original, packs);
            if (ReferenceEquals(updated, original)) return false;
            AtomicFiles.Write(options, updated);
            return true;
        }
        catch (InvalidDataException) { return false; } // unreadable options are left alone
    }

    /// <summary>Move foreign jars aside and switch the pack's resource packs on; damaged pack files are returned for a repair.</summary>
    public static GuardReport Run(string instanceRoot, string stateRoot, PackManifest manifest)
    {
        if (!Directory.Exists(instanceRoot)) return GuardReport.Empty;
        var moved = MoveAside(instanceRoot, stateRoot, FindUnknownMods(instanceRoot, manifest));
        var damaged = FindDamaged(instanceRoot, manifest);
        return new GuardReport(moved, damaged, EnsureResourcePacks(instanceRoot, manifest));
    }
}
