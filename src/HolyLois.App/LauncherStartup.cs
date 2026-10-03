using HolyLois.Core;
using System.Diagnostics;
using System.IO;
namespace HolyLois.App;
public static class LauncherStartup
{
    public static string InstallRoot
    {
        get
        {
            var args = Environment.GetCommandLineArgs(); var i = Array.IndexOf(args,"--lab-root");
            var root = i >= 0 && i + 1 < args.Length ? Path.GetFullPath(args[i+1])
                : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "HolyLoisReborn");
            _ = SafePaths.Resolve(root,"HolyLoisReborn.exe"); return root;
        }
    }
    public static string InstalledExe => SafePaths.Resolve(InstallRoot,"HolyLoisReborn.exe");
    public static bool IsInstalled => Path.GetFullPath(Environment.ProcessPath!).Equals(InstalledExe,StringComparison.OrdinalIgnoreCase);
    public static void InstallCurrent()
    {
        if (IsInstalled) { AtomicFiles.Write(SafePaths.Resolve(InstallRoot,"holylois-app.txt"),System.Text.Encoding.ASCII.GetBytes(ApplicationRemoval.Marker)); return; }
        var self = Environment.ProcessPath ?? throw new IOException("Application path unavailable.");
        Directory.CreateDirectory(InstallRoot);
        if (File.Exists(InstalledExe))
        {
            var deployment = AppUpdates.Deployment;
            // A genuine signed installation (older or newer) starts instead; an older one updates itself from app-stable.
            if (deployment.Installed is { } release && AtomicFiles.Matches(InstalledExe,release.File)) return;
            if (AtomicFiles.Hash(InstalledExe) == AtomicFiles.Hash(self)) return;
            // An unknown or damaged installation is kept as a backup and replaced by this copy, like a first install.
            ReplaceUnknownInstallation(self);
            return;
        }
        var temp = SafePaths.Resolve(InstallRoot,"HolyLoisReborn.exe.first-install");
        try
        {
            File.Copy(self,temp,true);
            if (new FileInfo(temp).Length != new FileInfo(self).Length || AtomicFiles.Hash(temp) != AtomicFiles.Hash(self)) throw new IOException("Application copy verification failed.");
            File.Move(temp,InstalledExe);
            AtomicFiles.Write(SafePaths.Resolve(InstallRoot,"holylois-app.txt"),System.Text.Encoding.ASCII.GetBytes(ApplicationRemoval.Marker));
        }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    private static void ReplaceUnknownInstallation(string self)
    {
        var backup = SafePaths.Resolve(InstallRoot,"rollback/HolyLoisReborn.replaced-" + DateTime.UtcNow.ToString("yyyyMMddHHmmss") + ".exe");
        Directory.CreateDirectory(Path.GetDirectoryName(backup)!);
        var temp = SafePaths.Resolve(InstallRoot,"HolyLoisReborn.exe.replace");
        try
        {
            File.Copy(self,temp,true);
            if (AtomicFiles.Hash(temp) != AtomicFiles.Hash(self)) throw new IOException("Application copy verification failed.");
            File.Replace(temp,InstalledExe,backup);
            AtomicFiles.Write(SafePaths.Resolve(InstallRoot,"holylois-app.txt"),System.Text.Encoding.ASCII.GetBytes(ApplicationRemoval.Marker));
        }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    public static void RunInstalled() => AppUpdates.Start(InstalledExe, []).Dispose();
    public static void CreateShortcut(string root, bool desktop)
    {
        var folder = Environment.GetFolderPath(desktop ? Environment.SpecialFolder.DesktopDirectory : Environment.SpecialFolder.Programs);
        var shortcut = SafePaths.Resolve(folder,"Holy Lois Reborn.lnk");
        if (File.Exists(shortcut)) return;
        var target = SafePaths.Resolve(root,"HolyLoisReborn.exe");
        var shellType = Type.GetTypeFromProgID("WScript.Shell") ?? throw new IOException("Windows shortcuts are unavailable.");
        dynamic shell = Activator.CreateInstance(shellType)!;
        try
        {
            dynamic link = shell.CreateShortcut(shortcut);
            try { link.TargetPath = target; link.IconLocation = target + ",0"; link.WorkingDirectory = root; link.Description = "Holy Lois: Reborn"; link.Save(); }
            finally { System.Runtime.InteropServices.Marshal.FinalReleaseComObject(link); }
        }
        finally { System.Runtime.InteropServices.Marshal.FinalReleaseComObject(shell); }
    }
}
