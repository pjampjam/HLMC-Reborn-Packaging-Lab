using HolyLois.Core;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Windows;

namespace HolyLois.App;

public static class AppPromotion
{
    private static string Parent(string[] args)
    {
        var i = Array.IndexOf(args,"--promotion-base");
        return i >= 0 && i + 1 < args.Length ? Path.GetFullPath(args[i+1]) : Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
    }
    public static bool Needed(string[] args)
    {
        var source = Path.Combine(Parent(args),"HolyLoisRebornLab");
        var i = Array.IndexOf(args,"--lab-root");
        if (i >= 0 && !Path.GetFullPath(args[i+1]).Equals(source,StringComparison.OrdinalIgnoreCase)) return false;
        return Directory.Exists(source) && File.Exists(SafePaths.Resolve(source,"holylois-app.txt"));
    }
    public static bool Request(string[] args)
    {
        if (!Needed(args)) return false;
        if (ClientContext.IsGameOrLauncherRunning()) throw new IOException("Close Minecraft and your Minecraft launcher before updating the Holy Lois installation name.");
        var source = Path.Combine(Parent(args),"HolyLoisRebornLab"); var target = Path.Combine(Parent(args),"HolyLoisReborn");
        if (File.Exists(SafePaths.Resolve(source,"app-update-pending.json"))) return false;
        var workerFolder = Path.Combine(Path.GetTempPath(),"HolyLoisReborn-Migration",Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(workerFolder); var worker = Path.Combine(workerFolder,"HolyLoisReborn.exe");
        File.Copy(Environment.ProcessPath!,worker);
        var hash = AtomicFiles.Hash(Environment.ProcessPath!);
        if (AtomicFiles.Hash(worker) != hash) throw new IOException("The name migration worker could not be verified.");
        using var self = Process.GetCurrentProcess();
        Start(worker,["--promote-install",source,target,self.Id.ToString(),self.StartTime.ToUniversalTime().Ticks.ToString(),hash,"--promotion-base",Parent(args)]).Dispose();
        return true;
    }
    private static Process Start(string exe, IEnumerable<string> args)
    {
        var start = new ProcessStartInfo(exe) { UseShellExecute = false,WorkingDirectory = Path.GetDirectoryName(exe)! };
        foreach (var arg in args) start.ArgumentList.Add(arg);
        return Process.Start(start) ?? throw new IOException("The migrated application could not start.");
    }
    public static async Task RunAsync(string[] args)
    {
        var i = Array.IndexOf(args,"--promote-install");
        if (i < 0 || i + 5 >= args.Length || !int.TryParse(args[i+3],out var pid) || !long.TryParse(args[i+4],out var ticks))
            throw new IOException("Invalid installation migration handoff.");
        var source = Path.GetFullPath(args[i+1]); var target = Path.GetFullPath(args[i+2]); var hash = args[i+5];
        if (source != Path.Combine(Parent(args),"HolyLoisRebornLab") || target != Path.Combine(Parent(args),"HolyLoisReborn")
            || !Path.GetFullPath(Environment.ProcessPath!).StartsWith(Path.Combine(Path.GetTempPath(),"HolyLoisReborn-Migration") + Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase)
            || AtomicFiles.Hash(Environment.ProcessPath!) != hash) throw new IOException("The installation migration worker is invalid.");
        var window = new AppUpdateWindow("Updating Holy Lois installation",false); window.Show(); window.SetStage("Waiting for Holy Lois to close...",10);
        try { using var parent = Process.GetProcessById(pid); if (parent.StartTime.ToUniversalTime().Ticks != ticks) throw new IOException("The original launcher process changed."); using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(30)); await parent.WaitForExitAsync(timeout.Token); } catch (ArgumentException) { }
        if (ClientContext.IsGameOrLauncherRunning()) throw new IOException("Close Minecraft and your Minecraft launcher before moving Holy Lois.");
        // Refuse to move a folder still in use by any launcher process, including an old update worker.
        foreach (var process in Process.GetProcessesByName("HolyLoisReborn")) using (process)
        {
            if (process.Id == Environment.ProcessId) continue;
            var path = process.MainModule?.FileName;
            if (path is not null && (path.StartsWith(source + Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase)
                || path.StartsWith(target + Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase)))
                throw new IOException("Another Holy Lois window is still open. Close it and try again.");
        }
        window.SetStage("Preserving the older installation and moving game files...",40);
        if (PreferExisting(target)) await Task.Run(() => InstallationPromotion.ArchiveRedundantSource(source,target));
        else await Task.Run(() => InstallationPromotion.Promote(source,target,Environment.ProcessPath!));
        window.SetStage("Starting Holy Lois: Reborn...",90);
        Start(Path.Combine(target,"HolyLoisReborn.exe"),["--lab-root",target,"--promotion-base",Parent(args)]).Dispose();
        window.SetStage("Installation updated. Your game files were kept.",100,true); await Task.Delay(500); window.FinishAndClose();
    }
    private static bool PreferExisting(string target)
    {
        var marker = SafePaths.Resolve(target,"holylois-app.txt"); var exe = SafePaths.Resolve(target,"HolyLoisReborn.exe");
        if (!File.Exists(marker) || File.ReadAllText(marker) != ApplicationRemoval.Marker || !File.Exists(exe)) return false;
        var key = System.Text.Encoding.ASCII.GetString(ClientContext.Asset("app-release-public.pem"));
        var installed = new AppDeployment(target,key).Installed;
        return installed is not null && installed.NumericVersion >= AppReleasePolicy.Normalize(AppUpdates.RunningVersion) && AtomicFiles.Matches(exe,installed.File)
            || AtomicFiles.Hash(exe) == AtomicFiles.Hash(Environment.ProcessPath!);
    }
    public static async Task AfterUpdateAsync(string[] args, Window main)
    {
        if (!Needed(args)) return;
        var deadline = DateTime.UtcNow.AddSeconds(40);
        while (File.Exists(SafePaths.Resolve(LauncherStartup.InstallRoot,"app-update-pending.json")) && DateTime.UtcNow < deadline) await Task.Delay(100);
        await Task.Delay(1000); // Allow the finalized update worker to close its progress window.
        if (Request(args)) { main.Hide(); AppUpdates.ReleaseLock(); Application.Current.Shutdown(0); }
    }
    public static void CompleteNames(ClientContext context, string[] args)
    {
        var target = Path.Combine(Parent(args),"HolyLoisReborn");
        if (context.IsIsolated || !LauncherStartup.InstallRoot.Equals(target,StringComparison.OrdinalIgnoreCase)) return;
        var source = Path.Combine(Parent(args),"HolyLoisRebornLab");
        var previousGame = Path.Combine(source,"data","instances","Holy Lois Reborn");
        var newGame = context.PreparedInstance;
        var profiles = SafePaths.Resolve(context.MinecraftRoot,"launcher_profiles.json");
        if (File.Exists(profiles) && !ClientContext.IsGameOrLauncherRunning())
        {
            var old = File.ReadAllBytes(profiles);
            var receiptPath = SafePaths.Resolve(target,"name-migration.json");
            var receipt = File.Exists(receiptPath) && new FileInfo(receiptPath).Length < 8192
                ? JsonSerializer.Deserialize<PromotionReceipt>(File.ReadAllBytes(receiptPath),JsonSettings.Options) : null;
            var next = LauncherProfiles.PromoteNames(old,previousGame,newGame,ClientContext.Asset("profile-icon.png"),receipt?.PreviousInstallationBackup is not null);
            if (context.CanPlay && next.SequenceEqual(old)) next = LauncherProfiles.Upsert(next,newGame,ClientContext.Asset("profile-icon.png"),LauncherProfiles.ReleaseProfileId,"Holy Lois: Reborn");
            if (!old.SequenceEqual(next))
            {
                var backup = SafePaths.Resolve(context.MinecraftRoot,"launcher_profiles.holylois-name-backup.json");
                if (!File.Exists(backup)) AtomicFiles.Write(backup,old);
                AtomicFiles.Write(profiles,next);
            }
        }
        if (!ClientContext.IsGameOrLauncherRunning())
        {
            var skRoot = Parent(args).Equals(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),StringComparison.OrdinalIgnoreCase)
                ? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),".sklauncher") : Path.Combine(Parent(args),"sklauncher");
            var skFile = SafePaths.Resolve(skRoot,"instances.json");
            if (File.Exists(skFile))
            {
                var old = File.ReadAllBytes(skFile); var next = LauncherProfiles.PromoteSkNames(old,source,target);
                if (!old.SequenceEqual(next))
                {
                    var backup = SafePaths.Resolve(skRoot,"instances.holylois-name-backup.json");
                    if (!File.Exists(backup)) AtomicFiles.Write(backup,old);
                    AtomicFiles.Write(skFile,next);
                }
            }
        }
        RenameShortcut(true,source,target); RenameShortcut(false,source,target);
    }
    public static void RelocateSettings(string[] args)
    {
        var target = Path.Combine(Parent(args),"HolyLoisReborn");
        if (!LauncherStartup.InstallRoot.Equals(target,StringComparison.OrdinalIgnoreCase)) return;
        var source = Path.Combine(Parent(args),"HolyLoisRebornLab");
        var path = SafePaths.Resolve(target,"data/launcher-settings.json");
        if (!File.Exists(path)) return;
        var settings = JsonSerializer.Deserialize<UserSettings>(File.ReadAllBytes(path),JsonSettings.Options)!;
        if (settings.SkInstance is { } sk && Path.GetFullPath(sk).StartsWith(source + Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))
            AtomicFiles.WriteJson(path,settings with { SkInstance = Path.Combine(target,Path.GetRelativePath(source,sk)) });
    }
    public static void RenameShortcut(bool desktop, string source, string target)
    {
        var folder = Environment.GetFolderPath(desktop ? Environment.SpecialFolder.DesktopDirectory : Environment.SpecialFolder.Programs);
        RenameShortcutAt(folder,source,target);
    }
    public static void RenameShortcutAt(string folder, string source, string target)
    {
        var old = SafePaths.Resolve(folder,"Holy Lois Reborn Preview.lnk");
        var next = SafePaths.Resolve(folder,"Holy Lois Reborn.lnk");
        if (!File.Exists(old) && !File.Exists(next)) return; // A deleted shortcut must stay deleted.
        var type = Type.GetTypeFromProgID("WScript.Shell")!; dynamic shell = Activator.CreateInstance(type)!;
        try
        {
            dynamic link = shell.CreateShortcut(File.Exists(old) ? old : next);
            try
            {
                string previousTarget = link.TargetPath;
                var owned = Path.GetFullPath(previousTarget).Equals(Path.Combine(source,"HolyLoisReborn.exe"),StringComparison.OrdinalIgnoreCase)
                    || Path.GetFullPath(previousTarget).Equals(Path.Combine(target,"HolyLoisReborn.exe"),StringComparison.OrdinalIgnoreCase)
                    || Path.GetFullPath(previousTarget).Equals(Path.Combine(target,"HolyLoisSetup.exe"),StringComparison.OrdinalIgnoreCase);
                if (!owned) return;
                if (File.Exists(next))
                {
                    dynamic existing = shell.CreateShortcut(next);
                    try {
                        var existingPath = Path.GetFullPath((string)existing.TargetPath);
                        if (!existingPath.Equals(Path.Combine(target,"HolyLoisReborn.exe"),StringComparison.OrdinalIgnoreCase)
                            && !existingPath.Equals(Path.Combine(target,"HolyLoisSetup.exe"),StringComparison.OrdinalIgnoreCase)) return;
                    }
                    finally { Marshal.FinalReleaseComObject(existing); }
                }
                else File.Move(old,next);
                dynamic renamed = shell.CreateShortcut(next);
                try { renamed.TargetPath = Path.Combine(target,"HolyLoisReborn.exe"); renamed.IconLocation = renamed.TargetPath + ",0"; renamed.WorkingDirectory = target; renamed.Description = "Holy Lois: Reborn"; renamed.Save(); }
                finally { Marshal.FinalReleaseComObject(renamed); }
                if (File.Exists(old)) File.Delete(old);
            }
            finally { Marshal.FinalReleaseComObject(link); }
        }
        finally { Marshal.FinalReleaseComObject(shell); }
    }
}
