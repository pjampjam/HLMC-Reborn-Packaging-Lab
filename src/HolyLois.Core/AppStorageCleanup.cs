using System.Text.RegularExpressions;

namespace HolyLois.Core;

public sealed record StorageCleanupResult(int Files, long Bytes)
{
    public static StorageCleanupResult Empty { get; } = new(0, 0);
    public static StorageCleanupResult operator +(StorageCleanupResult first, StorageCleanupResult second)
        => new(first.Files + second.Files, first.Bytes + second.Bytes);
}

// Delete only recognizable launcher artifacts. Unknown files, directories and links stay untouched.
public static class AppStorageCleanup
{
    private static readonly Regex HashName = new("^[0-9a-fA-F]{64}$", RegexOptions.CultureInvariant);
    private static readonly HashSet<string> NativeFiles = new(StringComparer.OrdinalIgnoreCase)
    {
        "D3DCompiler_47_cor3.dll", "PenImc_cor3.dll", "PresentationNative_cor3.dll",
        "vcruntime140_cor3.dll", "wpfgfx_cor3.dll"
    };

    internal static StorageCleanupResult CompletedCopies(string root, IEnumerable<string> activeFiles)
    {
        var protectedPaths = activeFiles.Append(Environment.ProcessPath ?? "")
            .Where(p => !string.IsNullOrWhiteSpace(p)).Select(Path.GetFullPath)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var result = StorageCleanupResult.Empty;
        var downloads = SafePaths.Resolve(root, "app-downloads");
        if (Directory.Exists(downloads))
        {
            foreach (var file in Directory.EnumerateFiles(downloads, "*.verified"))
            {
                var hash = Path.GetFileNameWithoutExtension(file);
                if (!HashName.IsMatch(hash) || protectedPaths.Contains(Path.GetFullPath(file))) continue;
                result += DeleteVerifiedCopy(file, hash);
            }
            foreach (var file in Directory.EnumerateFiles(downloads, "*.part"))
            {
                var parts = Path.GetFileName(file).Split('.');
                if (parts.Length != 4 || !HashName.IsMatch(parts[0]) || parts[1] != "verified" || parts[3] != "part"
                    || !Guid.TryParseExact(parts[2], "N", out _) || protectedPaths.Contains(Path.GetFullPath(file))) continue;
                result += DeleteOldPartial(file);
            }
        }
        var updates = SafePaths.Resolve(root, "updates");
        if (Directory.Exists(updates))
            foreach (var folder in Directory.EnumerateDirectories(updates))
            {
                var hash = Path.GetFileName(folder);
                if (!HashName.IsMatch(hash)) continue;
                var file = SafePaths.Resolve(root, "updates/" + hash + "/HolyLoisReborn.exe");
                if (protectedPaths.Contains(file)) continue;
                result += DeleteVerifiedCopy(file, hash);
                var partial = SafePaths.Resolve(root, "updates/" + hash + "/HolyLoisReborn.exe.part");
                if (!protectedPaths.Contains(partial)) result += DeleteOldPartial(partial);
                DeleteEmptyFolder(folder);
            }
        // A completed name migration can leave an exact duplicate of the normal rollback copy.
        // Keep a distinct migration backup because it may still be useful for recovery.
        var rollback = SafePaths.Resolve(root, "rollback/HolyLoisReborn.exe");
        var migration = SafePaths.Resolve(root, "rollback/before-name-migration.exe");
        if (File.Exists(rollback) && File.Exists(migration) && !protectedPaths.Contains(migration))
            try
            {
                if (new FileInfo(rollback).Length == new FileInfo(migration).Length && AtomicFiles.Hash(rollback) == AtomicFiles.Hash(migration))
                    result += DeleteKnownFile(migration);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return result;
    }

    public static StorageCleanupResult TemporaryWorkers(string root, IEnumerable<string> activeFiles, DateTime cutoffUtc)
    {
        root = Path.GetFullPath(root);
        if (Path.GetFileName(root) is not ("HolyLoisReborn-Migration" or "HolyLoisReborn-Maintenance"))
            throw new IOException("Temporary cleanup requires a known launcher worker folder.");
        _ = SafePaths.Resolve(root, "cleanup-boundary.txt");
        if (!Directory.Exists(root)) return StorageCleanupResult.Empty;
        var protectedPaths = activeFiles.Select(Path.GetFullPath).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var result = StorageCleanupResult.Empty;
        foreach (var folder in Directory.EnumerateDirectories(root))
        {
            if (!Guid.TryParseExact(Path.GetFileName(folder), "N", out _)) continue;
            try
            {
                SafePaths.RejectLinks(folder);
                var file = SafePaths.Resolve(folder, "HolyLoisReborn.exe");
                if (protectedPaths.Contains(file) || !File.Exists(file) || File.GetLastWriteTimeUtc(file) >= cutoffUtc) continue;
                if (Directory.EnumerateFileSystemEntries(folder).Any(p => !p.Equals(file, StringComparison.OrdinalIgnoreCase))) continue;
                result += DeleteKnownFile(file);
                DeleteEmptyFolder(folder);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
        return result;
    }

    public static StorageCleanupResult NativeRuntime(string root, IEnumerable<string> activeFiles, DateTime cutoffUtc)
    {
        root = Path.GetFullPath(root);
        if (Path.GetFileName(root) != "HolyLoisReborn" || Path.GetFileName(Path.GetDirectoryName(root)) != ".net")
            throw new IOException("Runtime cleanup requires the Holy Lois extraction folder.");
        _ = SafePaths.Resolve(root, "cleanup-boundary.txt");
        if (!Directory.Exists(root)) return StorageCleanupResult.Empty;
        var protectedPaths = activeFiles.Select(Path.GetFullPath).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var result = StorageCleanupResult.Empty;
        foreach (var folder in Directory.EnumerateDirectories(root))
        {
            if (!Regex.IsMatch(Path.GetFileName(folder), "^[A-Za-z0-9_-]{10,24}$", RegexOptions.CultureInvariant)) continue;
            try
            {
                SafePaths.RejectLinks(folder);
                var entries = Directory.GetFileSystemEntries(folder);
                if (entries.Length != NativeFiles.Count || entries.Any(p => !File.Exists(p) || !NativeFiles.Contains(Path.GetFileName(p))
                    || protectedPaths.Contains(Path.GetFullPath(p)) || File.GetLastWriteTimeUtc(p) >= cutoffUtc)) continue;
                foreach (var file in entries) SafePaths.RejectLinks(file);
                // No recursive deletion: even a racing unknown file cannot be removed by this pass.
                foreach (var file in entries) result += DeleteKnownFile(file);
                DeleteEmptyFolder(folder);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        }
        return result;
    }

    private static StorageCleanupResult DeleteVerifiedCopy(string file, string hash)
    {
        try
        {
            SafePaths.RejectLinks(file);
            if (!File.Exists(file) || AtomicFiles.Hash(file) != hash.ToLowerInvariant()) return StorageCleanupResult.Empty;
            return DeleteKnownFile(file);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return StorageCleanupResult.Empty; }
    }
    private static StorageCleanupResult DeleteKnownFile(string file)
    {
        try
        {
            SafePaths.RejectLinks(file);
            var bytes = new FileInfo(file).Length;
            File.Delete(file);
            return new(1, bytes);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return StorageCleanupResult.Empty; }
    }
    private static StorageCleanupResult DeleteOldPartial(string file)
    {
        try
        {
            SafePaths.RejectLinks(file);
            return File.Exists(file) && File.GetLastWriteTimeUtc(file) < DateTime.UtcNow.AddDays(-1)
                ? DeleteKnownFile(file) : StorageCleanupResult.Empty;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { return StorageCleanupResult.Empty; }
    }
    private static void DeleteEmptyFolder(string folder)
    {
        try { SafePaths.RejectLinks(folder); if (!Directory.EnumerateFileSystemEntries(folder).Any()) Directory.Delete(folder); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
    }
}
