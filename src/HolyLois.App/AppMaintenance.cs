using HolyLois.Core;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;

namespace HolyLois.App;

public static class AppMaintenance
{
    public static void RequestRemoval()
    {
        if (!LauncherStartup.IsInstalled) throw new IOException("Open the installed Holy Lois app before removing it.");
        var root = Path.Combine(Path.GetTempPath(),"HolyLoisReborn-Maintenance",Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(root); var worker = SafePaths.Resolve(root,"HolyLoisReborn.exe");
        File.Copy(Environment.ProcessPath!,worker); var hash = AtomicFiles.Hash(Environment.ProcessPath!);
        if (AtomicFiles.Hash(worker) != hash) throw new IOException("Maintenance copy verification failed.");
        using var parent = Process.GetCurrentProcess();
        AppUpdates.Start(worker,["--remove-app",parent.Id.ToString(),parent.StartTime.ToUniversalTime().Ticks.ToString(),hash]).Dispose();
    }
    public static async Task RemoveAsync(string[] args)
    {
        var i = Array.IndexOf(args,"--remove-app");
        if (i < 0 || i + 3 >= args.Length || !int.TryParse(args[i+1],out var pid) || !long.TryParse(args[i+2],out var ticks)) throw new IOException("Invalid maintenance handoff.");
        var expected = args[i+3];
        var source = Path.GetFullPath(Environment.ProcessPath!); var tempRoot = Path.Combine(Path.GetTempPath(),"HolyLoisReborn-Maintenance") + Path.DirectorySeparatorChar;
        if (!source.StartsWith(tempRoot,StringComparison.OrdinalIgnoreCase) || AtomicFiles.Hash(source) != expected) throw new IOException("The maintenance worker could not be verified.");
        var window = new AppUpdateWindow(Localize.Text("UninstallApp"),false); window.Show(); window.SetStage("Waiting for the launcher to close...",10);
        try { using var parent = Process.GetProcessById(pid); if (parent.StartTime.ToUniversalTime().Ticks != ticks) throw new IOException("The original app process changed."); using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(30)); await parent.WaitForExitAsync(timeout.Token); }
        catch (ArgumentException) { }
        if (!AppUpdates.AcquireLock()) throw new IOException("Another Holy Lois window is still open.");
        try
        {
            window.SetStage("Removing launcher shortcuts...",35);
            RemoveShortcut(true); RemoveShortcut(false);
            window.SetStage("Removing the launcher app. Keeping game files...",65);
            await Task.Run(() => ApplicationRemoval.RemoveOwnedApp(LauncherStartup.InstallRoot,expected));
        }
        finally { AppUpdates.ReleaseLock(); }
        AtomicFiles.Write(SafePaths.Resolve(LauncherStartup.InstallRoot,"removal-result.txt"),"Holy Lois launcher removed. Game files and personal settings were kept."u8.ToArray());
        window.SetStage("Launcher removed. Game files were kept.",100,true); await Task.Delay(750); window.FinishAndClose();
    }
    private static void RemoveShortcut(bool desktop)
    {
        var folder = Environment.GetFolderPath(desktop ? Environment.SpecialFolder.DesktopDirectory : Environment.SpecialFolder.Programs);
        var path = SafePaths.Resolve(folder,"Holy Lois Reborn.lnk"); if (!File.Exists(path)) return;
        var type = Type.GetTypeFromProgID("WScript.Shell")!; dynamic shell = Activator.CreateInstance(type)!;
        try { dynamic shortcut = shell.CreateShortcut(path); try { string target = shortcut.TargetPath; if (Path.GetFullPath(target).Equals(LauncherStartup.InstalledExe,StringComparison.OrdinalIgnoreCase)) File.Delete(path); } finally { Marshal.FinalReleaseComObject(shortcut); } }
        finally { Marshal.FinalReleaseComObject(shell); }
    }
}
