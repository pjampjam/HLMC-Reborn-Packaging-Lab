using System.Text;

namespace HolyLois.Core;

public sealed record PromotionReceipt(string PreviousRoot, string? PreviousInstallationBackup);

public static class InstallationPromotion
{
    public static string ArchiveRedundantSource(string source, string target)
    {
        source = Path.GetFullPath(source); target = Path.GetFullPath(target);
        var parent = Path.GetDirectoryName(source)!;
        if (Path.GetFileName(source) != "HolyLoisRebornLab" || Path.GetFileName(target) != "HolyLoisReborn"
            || !parent.Equals(Path.GetDirectoryName(target),StringComparison.OrdinalIgnoreCase)) throw new IOException("Invalid application folder migration.");
        if (!ApplicationRemoval.IsOwnedMarker(File.ReadAllText(SafePaths.Resolve(source,"holylois-app.txt")))
            || File.ReadAllText(SafePaths.Resolve(target,"holylois-app.txt")) != ApplicationRemoval.Marker
            || File.Exists(SafePaths.Resolve(source,"app-update-pending.json"))) throw new IOException("The redundant installation is not ready to archive.");
        var backup = Path.Combine(parent,"HolyLoisReborn-backup-" + Guid.NewGuid().ToString("N"));
        Directory.Move(source,backup); return backup;
    }
    public static PromotionReceipt Promote(string source, string target, string replacement, Action? afterMove = null)
    {
        source = Path.GetFullPath(source); target = Path.GetFullPath(target);
        var parent = Path.GetDirectoryName(source)!;
        if (Path.GetFileName(source) != "HolyLoisRebornLab" || Path.GetFileName(target) != "HolyLoisReborn"
            || !parent.Equals(Path.GetDirectoryName(target), StringComparison.OrdinalIgnoreCase))
            throw new IOException("The installation migration requires the two known sibling application folders.");
        SafePaths.RejectLinks(source); SafePaths.RejectLinks(target);
        var marker = SafePaths.Resolve(source,"holylois-app.txt");
        if (!File.Exists(marker) || !ApplicationRemoval.IsOwnedMarker(File.ReadAllText(marker)))
            throw new IOException("The previous installation is not owned by Holy Lois.");
        if (File.Exists(SafePaths.Resolve(source,"app-update-pending.json")))
            throw new IOException("Finish the launcher update before moving its installation.");
        var expected = AtomicFiles.Hash(replacement);
        var id = Guid.NewGuid().ToString("N");
        var stage = Path.Combine(parent,"HolyLoisReborn-promotion-" + id + ".exe");
        string? backup = null; var moved = false;
        try
        {
            File.Copy(replacement,stage);
            if (AtomicFiles.Hash(stage) != expected) throw new IOException("The migration app copy could not be verified.");
            if (Directory.Exists(target))
            {
                if (!File.Exists(SafePaths.Resolve(target,"setup-completed.json")) &&
                    !File.Exists(SafePaths.Resolve(target,"holylois-app.txt")))
                    throw new IOException("An unrelated folder occupies the Holy Lois installation path. It was preserved.");
                backup = Path.Combine(parent,"HolyLoisReborn-backup-" + id);
                Directory.Move(target,backup);
            }
            Directory.Move(source,target); moved = true;
            afterMove?.Invoke();
            var executable = SafePaths.Resolve(target,"HolyLoisReborn.exe");
            Directory.CreateDirectory(SafePaths.Resolve(target,"rollback"));
            if (File.Exists(executable)) File.Replace(stage,executable,SafePaths.Resolve(target,"rollback/before-name-migration.exe"),true);
            else File.Move(stage,executable);
            AtomicFiles.Write(SafePaths.Resolve(target,"holylois-app.txt"),Encoding.ASCII.GetBytes(ApplicationRemoval.Marker));
            var receipt = new PromotionReceipt(source,backup);
            AtomicFiles.WriteJson(SafePaths.Resolve(target,"name-migration.json"),receipt);
            return receipt;
        }
        catch
        {
            if (moved && Directory.Exists(target) && !Directory.Exists(source)) Directory.Move(target,source);
            if (backup is not null && Directory.Exists(backup) && !Directory.Exists(target)) Directory.Move(backup,target);
            throw;
        }
        finally { if (File.Exists(stage)) File.Delete(stage); }
    }
}
