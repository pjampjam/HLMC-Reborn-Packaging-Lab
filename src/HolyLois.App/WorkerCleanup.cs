using HolyLois.Core;
using System.Diagnostics;
using System.IO;

namespace HolyLois.App;

public static class WorkerCleanup
{
    public static StorageCleanupResult CompletedLauncherFiles()
    {
        // An unreadable process inventory means we cannot prove a worker is inactive.
        if (!TryActiveFiles(out var active)) return StorageCleanupResult.Empty;
        var result = AppUpdates.Deployment.PruneCompleted(active);
        var cutoff = DateTime.UtcNow.AddDays(-1);
        foreach (var name in new[] { "HolyLoisReborn-Migration", "HolyLoisReborn-Maintenance" })
            try { result += AppStorageCleanup.TemporaryWorkers(Path.Combine(Path.GetTempPath(), name), active, cutoff); }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        try { result += AppStorageCleanup.NativeRuntime(Path.Combine(Path.GetTempPath(), ".net", "HolyLoisReborn"), active, cutoff); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { }
        return result;
    }

    private static bool TryActiveFiles(out HashSet<string> active)
    {
        active = new(StringComparer.OrdinalIgnoreCase);
        try
        {
            var processes = Process.GetProcessesByName("HolyLoisReborn");
            try
            {
                foreach (var process in processes)
                {
                    if (process.HasExited) continue;
                    try
                    {
                        var executable = process.MainModule?.FileName;
                        if (executable is null) return false;
                        active.Add(Path.GetFullPath(executable));
                        foreach (ProcessModule module in process.Modules)
                            if (module.FileName is { Length: > 0 } path) active.Add(Path.GetFullPath(path));
                    }
                    catch (InvalidOperationException) { if (!process.HasExited) return false; }
                }
            }
            finally { foreach (var process in processes) process.Dispose(); }
            if (Environment.ProcessPath is { } self) active.Add(Path.GetFullPath(self));
            return true;
        }
        catch (Exception e) when (e is System.ComponentModel.Win32Exception or InvalidOperationException or IOException or UnauthorizedAccessException)
        { return false; }
    }
}
