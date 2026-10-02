using System.Text.Json;

namespace HolyLois.Core;

public sealed class PackInstaller(string instanceRoot, string stateRoot, IFileDownloader downloader)
{
    private sealed record Change(string Path, bool Existed);
    private sealed record Journal(string Transaction, string Phase, string? OldReceipt, Change[] Changes);
    private string ReceiptPath => SafePaths.Resolve(stateRoot, "installed-pack.json");
    private string JournalPath => SafePaths.Resolve(stateRoot, "update-journal.json");
    public Action<int>? CommitObserver { get; init; } // Injected crash/failure boundary for recovery tests.

    public InstalledReceipt? ReadReceipt()
    {
        if (!File.Exists(ReceiptPath)) return null;
        if (new FileInfo(ReceiptPath).Length > 1024 * 1024) throw new InvalidDataException("Installed pack receipt is too large.");
        var receipt = JsonSerializer.Deserialize<InstalledReceipt>(File.ReadAllBytes(ReceiptPath), JsonSettings.Options)
            ?? throw new InvalidDataException("Installed pack receipt is unreadable.");
        foreach (var path in receipt.ManagedFiles.Keys) SafePaths.ValidateRelative(path);
        foreach (var path in receipt.ManagedFiles.Keys) ValidateManagedPath(path);
        return receipt;
    }

    public async Task InstallAsync(PackManifest manifest, IReadOnlyDictionary<string, byte[]> defaults,
        IProgress<InstallProgress>? progress = null, CancellationToken cancellationToken = default)
    {
        ManifestSecurity.Validate(manifest);
        Directory.CreateDirectory(instanceRoot); Directory.CreateDirectory(stateRoot);
        SafePaths.RejectLinks(instanceRoot); SafePaths.RejectLinks(stateRoot);
        using var updateLock = new FileStream(SafePaths.Resolve(stateRoot, "update.lock"), FileMode.OpenOrCreate,
            FileAccess.ReadWrite, FileShare.None);
        Recover();
        var old = ReadReceipt();
        var ready = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        var all = manifest.Files; var totalBytes = all.Sum(x => x.Size); long doneBytes = 0; int doneFiles = 0;
        foreach (var file in all)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var installed = SafePaths.Resolve(instanceRoot, file.Path);
            if (AtomicFiles.Matches(installed, file))
            { doneBytes += file.Size; doneFiles++; progress?.Report(new("Verified " + Path.GetFileName(file.Path), doneBytes, totalBytes, doneFiles, all.Length)); continue; }
            if (File.Exists(installed) && file.Policy == "managed" && (old is null || !old.ManagedFiles.ContainsKey(file.Path)))
                throw new IOException("An unowned file conflicts with the pack: " + file.Path + ". Move it aside before installing.");
            progress?.Report(new("Downloading " + Path.GetFileName(file.Path), doneBytes, totalBytes, doneFiles, all.Length));
            // Keep the active installation unchanged until every required download verifies.
            var fileStart = doneBytes; var fileIndex = doneFiles;
            var cached = await downloader.GetAsync(file, new InlineProgress<long>(n => progress?.Report(
                new("Downloading " + Path.GetFileName(file.Path), fileStart + Math.Clamp(n,0,file.Size), totalBytes, fileIndex, all.Length))), cancellationToken);
            if (!AtomicFiles.Matches(cached, file)) throw new InvalidDataException("A staged download did not pass verification.");
            ready[file.Path] = cached;
            doneBytes += file.Size; doneFiles++;
        }
        cancellationToken.ThrowIfCancellationRequested();
        var transaction = Guid.NewGuid().ToString("N");
        var txRoot = SafePaths.Resolve(stateRoot, "transactions/" + transaction);
        Directory.CreateDirectory(txRoot);
        var nextPaths = manifest.Files.Select(f => f.Path).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var writes = new Dictionary<string, byte[]?>(StringComparer.OrdinalIgnoreCase);
        foreach (var file in manifest.Files)
            if (ready.ContainsKey(file.Path) && (file.Policy != "seed" || !File.Exists(SafePaths.Resolve(instanceRoot, file.Path))))
                writes[file.Path] = null;
        var migrateDefaults = manifest.ApplyDefaultsOnUpdate && old?.Version != manifest.Version;
        foreach (var (relative, bytes) in defaults)
        {
            var target = SharedDefaults.Target(relative);
            var seedPath = SafePaths.Resolve(instanceRoot, relative);
            var previous = File.Exists(seedPath) ? File.ReadAllBytes(seedPath) : null;
            if (previous is null || manifest.ApplyDefaultsOnUpdate && !previous.SequenceEqual(bytes)) writes[relative] = bytes;
            if (migrateDefaults)
            {
                var personalPath = SafePaths.Resolve(instanceRoot, target);
                var personal = File.Exists(personalPath) ? File.ReadAllBytes(personalPath) : null;
                if (personal?.Length > 2 * 1024 * 1024) throw new InvalidDataException("Setting file too large: " + target);
                var merged = SharedDefaults.Merge(target, personal, previous, bytes);
                if (personal is null || !personal.SequenceEqual(merged)) writes[target] = merged;
            }
        }
        // Activate only newly introduced packs. A later verify must respect packs the player disabled.
        var addedPacks = manifest.Files.Where(f => f.Path.StartsWith("resourcepacks/", StringComparison.Ordinal)
            && (old is null || !old.ManagedFiles.ContainsKey(f.Path))).Select(f => Path.GetFileName(f.Path)).ToArray();
        if (addedPacks.Length > 0)
            foreach (var relative in new[] { "options.txt", "config/yosbr/options.txt" })
            {
                var path = SafePaths.Resolve(instanceRoot, relative);
                if (!File.Exists(path) && !writes.ContainsKey(relative)) continue;
                var before = writes.TryGetValue(relative, out var stagedOptions) && stagedOptions is not null ? stagedOptions : File.ReadAllBytes(path);
                if (before.Length > 1024 * 1024) throw new InvalidDataException("Minecraft options are too large; settings were preserved.");
                var after = ResourcePackOptions.AddNewPacks(before, addedPacks);
                if (!before.SequenceEqual(after)) writes[relative] = after;
            }
        if (old is not null)
            foreach (var path in old.ManagedFiles.Keys.Where(path => !nextPaths.Contains(path)))
            {
                // Receipts never authorize touching saves or personal config.
                if (!path.StartsWith("mods/", StringComparison.Ordinal) && !path.StartsWith("resourcepacks/", StringComparison.Ordinal)
                    && !path.StartsWith("shaderpacks/", StringComparison.Ordinal)) throw new InvalidDataException("Invalid managed-file receipt.");
                if (File.Exists(SafePaths.Resolve(instanceRoot, path))) writes[path] = [];
            }
        var changes = writes.Keys.Select(path => new Change(path, File.Exists(SafePaths.Resolve(instanceRoot, path)))).ToArray();
        var journal = new Journal(transaction, "committing", File.Exists(ReceiptPath) ? File.ReadAllText(ReceiptPath) : null, changes);
        AtomicFiles.WriteJson(JournalPath, journal);
        try
        {
            var index = 0;
            foreach (var change in changes)
            {
                var target = SafePaths.Resolve(instanceRoot, change.Path);
                var backup = SafePaths.Resolve(txRoot, "before/" + change.Path);
                Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                if (change.Existed)
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(backup)!);
                    File.Move(target, backup);
                }
                if (writes[change.Path] is { Length: > 0 } bytes) AtomicFiles.Write(target, bytes);
                else if (ready.TryGetValue(change.Path, out var cached))
                {
                    var staged = SafePaths.Resolve(txRoot, "new/" + change.Path);
                    Directory.CreateDirectory(Path.GetDirectoryName(staged)!);
                    File.Copy(cached, staged, true);
                    File.Move(staged, target);
                }
                CommitObserver?.Invoke(++index);
            }
            var receipt = new InstalledReceipt(manifest.Version, manifest.Files.Where(f => f.Policy == "managed")
                .ToDictionary(f => f.Path, f => f.Sha256, StringComparer.OrdinalIgnoreCase));
            AtomicFiles.WriteJson(ReceiptPath, receipt);
            AtomicFiles.WriteJson(JournalPath, journal with { Phase = "committed" });
            File.Delete(JournalPath);
            if (changes.Length > 0) PruneTransactions(transaction);
            else Directory.Delete(txRoot);
            progress?.Report(new("Pack " + manifest.Version + " is ready", totalBytes, totalBytes, all.Length, all.Length));
        }
        catch
        {
            Recover();
            throw;
        }
    }

    public void Recover()
    {
        if (!File.Exists(JournalPath)) return;
        if (new FileInfo(JournalPath).Length > 4 * 1024 * 1024) throw new InvalidDataException("Interrupted update journal is too large.");
        var journal = JsonSerializer.Deserialize<Journal>(File.ReadAllBytes(JournalPath), JsonSettings.Options)
            ?? throw new InvalidDataException("Interrupted update journal is unreadable.");
        if (!Guid.TryParseExact(journal.Transaction, "N", out _) || journal.Phase is not ("committing" or "committed"))
            throw new InvalidDataException("Interrupted update journal is invalid.");
        var txRoot = SafePaths.Resolve(stateRoot, "transactions/" + journal.Transaction);
        if (journal.Phase != "committed")
        {
            foreach (var change in journal.Changes.Reverse())
            {
                SafePaths.ValidateRelative(change.Path);
                if (change.Path == "options.txt" || change.Path.StartsWith("config/", StringComparison.Ordinal)) _ = SharedDefaults.Target(change.Path.StartsWith("config/yosbr/", StringComparison.Ordinal) ? change.Path : "config/yosbr/" + change.Path);
                else ValidateManagedPath(change.Path);
                var target = SafePaths.Resolve(instanceRoot, change.Path);
                var backup = SafePaths.Resolve(txRoot, "before/" + change.Path);
                if (File.Exists(backup))
                {
                    if (File.Exists(target)) File.Delete(target);
                    Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                    File.Move(backup, target);
                }
                else if (!change.Existed && File.Exists(target)) File.Delete(target);
            }
            if (journal.OldReceipt is null) { if (File.Exists(ReceiptPath)) File.Delete(ReceiptPath); }
            else AtomicFiles.Write(ReceiptPath, System.Text.Encoding.UTF8.GetBytes(journal.OldReceipt));
        }
        File.Delete(JournalPath);
    }

    private void PruneTransactions(string current)
    {
        var root = Path.Combine(stateRoot, "transactions");
        foreach (var path in Directory.GetDirectories(root))
            if (Path.GetFileName(path) != current && Guid.TryParseExact(Path.GetFileName(path), "N", out _))
            { RejectTreeLinks(path); Directory.Delete(path, true); }
    }

    private static void ValidateManagedPath(string path)
    {
        if (!(path.StartsWith("mods/", StringComparison.Ordinal) && path.EndsWith(".jar", StringComparison.Ordinal)
            || path.StartsWith("resourcepacks/", StringComparison.Ordinal) && path.EndsWith(".zip", StringComparison.Ordinal)
            || path.StartsWith("shaderpacks/", StringComparison.Ordinal) && path.EndsWith(".zip", StringComparison.Ordinal)))
            throw new InvalidDataException("Invalid managed-file path.");
    }

    private static void RejectTreeLinks(string root)
    {
        SafePaths.RejectLinks(root);
        foreach (var file in Directory.GetFiles(root)) SafePaths.RejectLinks(file);
        foreach (var dir in Directory.GetDirectories(root)) RejectTreeLinks(dir);
    }
}
