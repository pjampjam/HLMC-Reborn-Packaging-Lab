using System.Text.Json;

namespace HolyLois.Core;

public sealed record AppUpdateJournal(string Nonce, SignedAppRelease Release, string? PreviousHash, long PreviousSize, SignedAppRelease? PreviousRelease);

// The application is replaced by a temporary invocation of the same verified app.
// The running process must exit before Commit; no shell script or embedded checker is used.
public sealed class AppDeployment(string root, string publicKey)
{
    public string Target => SafePaths.Resolve(root, "HolyLoisReborn.exe");
    private string Receipt => SafePaths.Resolve(root, "app-release-state.json");
    private string JournalPath => SafePaths.Resolve(root, "app-update-pending.json");
    private string Backup => SafePaths.Resolve(root, "rollback/HolyLoisReborn.exe");
    public Action? AfterReplacement { get; init; }

    public SignedAppRelease? InstalledSigned => Read<SignedAppRelease>(Receipt);
    public AppRelease? Installed => InstalledSigned is { } signed ? AppReleasePolicy.Parse(signed, publicKey) : null;
    public AppUpdateJournal? Pending => Read<AppUpdateJournal>(JournalPath);
    public string Stage(AppRelease release) => SafePaths.Resolve(root, "updates/" + release.Sha256 + "/HolyLoisReborn.exe");
    public void RecordCurrent(SignedAppRelease signed, Version running)
    {
        var release = AppReleasePolicy.Parse(signed,publicKey);
        AppReleasePolicy.Accept(release,running,Installed);
        if (release.NumericVersion != AppReleasePolicy.Normalize(running) || !AtomicFiles.Matches(Target,release.File))
            throw new InvalidDataException("The installed app differs from the signed release for this version.");
        AtomicFiles.WriteJson(Receipt,signed);
    }
    public string ReadyPath(string nonce)
    {
        if (!Guid.TryParseExact(nonce, "N", out _)) throw new InvalidDataException("Invalid update acknowledgement.");
        return SafePaths.Resolve(root, "updates/ready-" + nonce + ".txt");
    }

    public string Prepare(SignedAppRelease signed, string verifiedFile, Version running)
    {
        var release = AppReleasePolicy.Parse(signed, publicKey);
        AppReleasePolicy.Accept(release, running, Installed);
        if (!AtomicFiles.Matches(verifiedFile, release.File)) throw new InvalidDataException("App update checksum failed.");
        if (Pending is not null) throw new IOException("An app update is already pending.");
        var stage = Stage(release); Directory.CreateDirectory(Path.GetDirectoryName(stage)!);
        if (!AtomicFiles.Matches(stage, release.File))
        {
            var temp = stage + ".part";
            try { File.Copy(verifiedFile, temp, true); if (!AtomicFiles.Matches(temp, release.File)) throw new IOException("Staged app checksum failed."); File.Move(temp, stage, true); }
            finally { if (File.Exists(temp)) File.Delete(temp); }
        }
        var nonce = Guid.NewGuid().ToString("N");
        var journal = new AppUpdateJournal(nonce, signed, File.Exists(Target) ? AtomicFiles.Hash(Target) : null,
            File.Exists(Target) ? new FileInfo(Target).Length : 0, InstalledSigned);
        AtomicFiles.WriteJson(JournalPath, journal); return nonce;
    }

    public AppUpdateJournal ValidatePending(string nonce)
    {
        var pending = Pending ?? throw new InvalidDataException("No app update is pending.");
        if (pending.Nonce != nonce) throw new InvalidDataException("App update acknowledgement does not match.");
        _ = ReadyPath(nonce); _ = AppReleasePolicy.Parse(pending.Release, publicKey);
        if (pending.PreviousRelease is not null) _ = AppReleasePolicy.Parse(pending.PreviousRelease, publicKey);
        if (pending.PreviousHash is not null && (pending.PreviousHash.Length != 64 || pending.PreviousHash.Any(c => !char.IsAsciiHexDigit(c))
            || pending.PreviousSize is < 1024 or > 262144000)) throw new InvalidDataException("Invalid rollback identity.");
        return pending;
    }

    public void Commit(string nonce)
    {
        var pending = ValidatePending(nonce); var release = AppReleasePolicy.Parse(pending.Release, publicKey);
        var stage = Stage(release); if (!AtomicFiles.Matches(stage, release.File)) throw new InvalidDataException("Staged app changed before installation.");
        if (pending.PreviousHash is not null && (!File.Exists(Target) || AtomicFiles.Hash(Target) != pending.PreviousHash))
            throw new IOException("Installed app changed while the update was prepared.");
        var temp = SafePaths.Resolve(root, "HolyLoisReborn.exe.next");
        Directory.CreateDirectory(Path.GetDirectoryName(Backup)!);
        try
        {
            File.Copy(stage, temp, true);
            if (!AtomicFiles.Matches(temp, release.File)) throw new InvalidDataException("Replacement app checksum failed.");
            if (File.Exists(Target)) File.Replace(temp, Target, Backup, true); else File.Move(temp, Target);
            AfterReplacement?.Invoke();
        }
        catch { Rollback(nonce); throw; }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }

    public void Acknowledge(string nonce, string executable)
    {
        var pending = ValidatePending(nonce); var release = AppReleasePolicy.Parse(pending.Release, publicKey);
        if (!Path.GetFullPath(executable).Equals(Target, StringComparison.OrdinalIgnoreCase) || !AtomicFiles.Matches(Target, release.File))
            throw new InvalidDataException("The updated app could not verify its installed file.");
        AtomicFiles.Write(ReadyPath(nonce), System.Text.Encoding.ASCII.GetBytes(release.Sha256));
    }

    public bool IsAcknowledged(string nonce)
    {
        var pending = ValidatePending(nonce); var release = AppReleasePolicy.Parse(pending.Release, publicKey);
        var path = ReadyPath(nonce);
        return File.Exists(path) && new FileInfo(path).Length == 64 && File.ReadAllText(path) == release.Sha256 && AtomicFiles.Matches(Target, release.File);
    }

    public void Finalize(string nonce)
    {
        var pending = ValidatePending(nonce);
        if (!IsAcknowledged(nonce)) throw new IOException("The updated app has not completed startup.");
        AtomicFiles.WriteJson(Receipt, pending.Release);
        File.Delete(JournalPath); File.Delete(ReadyPath(nonce));
        // Keep one known previous app for rollback; only our older staging files are pruned.
        var current = AppReleasePolicy.Parse(pending.Release, publicKey).Sha256;
        var updates = SafePaths.Resolve(root, "updates");
        foreach (var dir in Directory.GetDirectories(updates))
        {
            var name = Path.GetFileName(dir);
            if (name.Length != 64 || name.Any(c => !char.IsAsciiHexDigit(c)) || name == current) continue;
            var file = SafePaths.Resolve(root, "updates/" + name + "/HolyLoisReborn.exe");
            try { if (File.Exists(file)) File.Delete(file); if (!Directory.EnumerateFileSystemEntries(dir).Any()) Directory.Delete(dir); }
            catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { /* An old worker may still be exiting; a later update retries cleanup. */ }
        }
    }

    public void Rollback(string nonce)
    {
        var pending = ValidatePending(nonce); var next = AppReleasePolicy.Parse(pending.Release, publicKey);
        if (pending.PreviousHash is not null)
        {
            if (File.Exists(Target) && new FileInfo(Target).Length == pending.PreviousSize && AtomicFiles.Hash(Target) == pending.PreviousHash) { }
            else
            {
                if (!File.Exists(Backup) || new FileInfo(Backup).Length != pending.PreviousSize || AtomicFiles.Hash(Backup) != pending.PreviousHash)
                    throw new IOException("The rollback copy could not be verified. No unknown executable was started.");
                File.Copy(Backup, Target, true);
            }
        }
        else if (AtomicFiles.Matches(Target, next.File)) File.Delete(Target);
        if (pending.PreviousRelease is not null) AtomicFiles.WriteJson(Receipt, pending.PreviousRelease);
        else if (File.Exists(Receipt)) File.Delete(Receipt);
        File.Delete(JournalPath); if (File.Exists(ReadyPath(nonce))) File.Delete(ReadyPath(nonce));
    }

    private static T? Read<T>(string path) where T : class
    {
        if (!File.Exists(path)) return null;
        if (new FileInfo(path).Length > 131072) throw new InvalidDataException("App update state exceeds its size limit.");
        return JsonSerializer.Deserialize<T>(File.ReadAllBytes(path), JsonSettings.Options) ?? throw new InvalidDataException("Invalid app update state.");
    }
}
