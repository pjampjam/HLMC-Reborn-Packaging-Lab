using HolyLois.Core;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Reflection;
using System.Text;
using System.Text.Json;

namespace HolyLois.App;

public sealed record UserSettings(string Launcher = "official", string? LauncherExe = null, string? SkInstance = null, string Language = "en");
public sealed class ClientContext
{
    private readonly HttpClient http;
    private readonly DownloadCache downloader;
    public PackManifest Manifest { get; private set; }
    private readonly PackFeed feed;
    public string Root { get; }
    public string MinecraftRoot { get; }
    public string SkLauncherRoot { get; }
    public string PreparedInstance { get; }
    public string Instance => Settings.Launcher == "sk" && Settings.SkInstance is not null ? Settings.SkInstance : PreparedInstance;
    public UserSettings Settings { get; private set; }
    private string SettingsPath => SafePaths.Resolve(Root, "launcher-settings.json");
    private string StatePath => SafePaths.Resolve(Root, Settings.SkInstance is not null && Settings.Launcher == "sk" ? "state/sk-instance" : "state/prepared-instance");
    public bool IsIsolated { get; }
    public bool RecoveredDeletedInstance { get; private set; }

    public ClientContext(string? isolatedRoot, string? launcherTestRoot = null)
    {
        IsIsolated = isolatedRoot is not null && launcherTestRoot is null;
        Root = isolatedRoot ?? Path.Combine(LauncherStartup.InstallRoot, "data");
        PreparedInstance = SafePaths.Resolve(Root, "instances/Holy Lois Reborn");
        MinecraftRoot = launcherTestRoot is not null ? Path.GetFullPath(launcherTestRoot) : isolatedRoot is not null ? SafePaths.Resolve(Root, "minecraft")
            : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".minecraft");
        SkLauncherRoot = isolatedRoot is not null ? SafePaths.Resolve(Root, "sklauncher")
            : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".sklauncher");
        Directory.CreateDirectory(Root);
        Settings = File.Exists(SettingsPath) ? JsonSerializer.Deserialize<UserSettings>(File.ReadAllBytes(SettingsPath), JsonSettings.Options) ?? new() : new();
        if (Settings.Launcher is not ("official" or "sk")) throw new InvalidDataException("Saved launcher selection is invalid.");
        if (Settings.SkInstance is not null)
        {
            try { ValidateSkInstance(Settings.SkInstance); }
            catch (IOException) { Settings = Settings with { SkInstance = null }; RecoveredDeletedInstance = true; AtomicFiles.WriteJson(SettingsPath, Settings); }
        }
        http = new HttpClient(new HttpClientHandler { AllowAutoRedirect = true }) { Timeout = TimeSpan.FromMinutes(4) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("HolyLoisReborn/0.3");
        downloader = new DownloadCache(SafePaths.Resolve(Root, "cache"), http);
        Manifest = ManifestSecurity.Parse(Asset("pack.json"), Asset("pack.json.sig"), Encoding.ASCII.GetString(Asset("release-public.pem")));
        feed = new PackFeed(http, Encoding.ASCII.GetString(Asset("release-public.pem")));
        var cached = SafePaths.Resolve(Root, "last-pack-release.json");
        if (File.Exists(cached))
        {
            if (new FileInfo(cached).Length > 3 * 1024 * 1024) throw new InvalidDataException("Cached release is too large.");
            var release = JsonSerializer.Deserialize<SignedPack>(File.ReadAllBytes(cached), JsonSettings.Options)!;
            var cachedManifest = ManifestSecurity.Parse(release.Json, release.Signature, Encoding.ASCII.GetString(Asset("release-public.pem")));
            if (new Version(cachedManifest.Version) >= new Version(Manifest.Version)) Manifest = feed.Accept(release, Manifest);
        }
        DiscoverSkInstance();
    }
    public async Task<bool> CheckUpdatesAsync(CancellationToken token)
    {
        var release = await feed.FetchAsync(token);
        var next = feed.Accept(release, Manifest);
        var changed = next.Version != Manifest.Version;
        AtomicFiles.WriteJson(SafePaths.Resolve(Root, "last-pack-release.json"), release);
        Manifest = next;
        return changed;
    }
    public static byte[] Asset(string name)
    {
        var assembly = Assembly.GetExecutingAssembly();
        var resource = assembly.GetManifestResourceNames().Single(n => n.EndsWith(".Assets." + name, StringComparison.Ordinal));
        using var source = assembly.GetManifestResourceStream(resource)!; using var output = new MemoryStream(); source.CopyTo(output); return output.ToArray();
    }
    public void SetLanguage(string language)
    { Settings = Settings with { Language = language is "ru" or "lv" ? language : "en" }; AtomicFiles.WriteJson(SettingsPath, Settings); }
    public void SelectLauncher(string launcher)
    { if (Settings.Launcher == launcher) return; Settings = Settings with { Launcher = launcher, LauncherExe = null }; AtomicFiles.WriteJson(SettingsPath, Settings); DiscoverSkInstance(); }
    private void DiscoverSkInstance()
    {
        if (Settings.Launcher != "sk" || Settings.SkInstance is not null) return;
        try {
            var path = SkLauncherProfiles.FindOwnedInstance(SkLauncherRoot);
            if (path is not null && !path.Equals(PreparedInstance, StringComparison.OrdinalIgnoreCase)) LinkSkInstance(path);
        }
        catch (IOException) { /* Repair reports unreadable registry details; startup remains available. */ }
    }
    public void SetLauncher(string path)
    {
        if (!File.Exists(path) || !(Path.GetExtension(path).Equals(".exe", StringComparison.OrdinalIgnoreCase) || Path.GetExtension(path).Equals(".lnk", StringComparison.OrdinalIgnoreCase))) throw new IOException("Choose an installed launcher executable.");
        Settings = Settings with { LauncherExe = Path.GetFullPath(path) }; AtomicFiles.WriteJson(SettingsPath, Settings);
    }
    public void RebuildSkImport()
    { Settings = Settings with { SkInstance = null }; RecoveredDeletedInstance = true; AtomicFiles.WriteJson(SettingsPath, Settings); }
    public void LinkSkInstance(string path)
    {
        ValidateSkInstance(path); Settings = Settings with { SkInstance = Path.GetFullPath(path) }; AtomicFiles.WriteJson(SettingsPath, Settings);
        var imported = SafePaths.Resolve(path, "holylois-pack-receipt.json");
        if (File.Exists(imported) && !File.Exists(SafePaths.Resolve(StatePath, "installed-pack.json")))
        {
            AtomicFiles.Write(SafePaths.Resolve(StatePath, "installed-pack.json"), File.ReadAllBytes(imported));
            _ = Installer.ReadReceipt();
        }
    }
    private static void ValidateSkInstance(string path)
    {
        SafePaths.RejectLinks(path);
        var marker = SafePaths.Resolve(path, "holylois-instance.json");
        if (!File.Exists(marker) || File.ReadAllText(marker) != "{\"instance\":\"holylois-reborn-26.3\"}")
            throw new IOException("Choose the game folder of the imported Holy Lois instance. Other modpacks cannot be linked.");
    }
    private PackInstaller Installer => new(Instance, StatePath, downloader);
    public InstalledReceipt? Receipt => Installer.ReadReceipt();
    public bool CanPlay => (Settings.Launcher != "sk" || SkLauncherProfiles.IsRegistered(SkLauncherRoot, Instance))
        && Directory.Exists(Instance) && File.Exists(SafePaths.Resolve(Instance, "holylois-instance.json")) && Receipt?.Version == Manifest.Version && File.Exists(SafePaths.Resolve(StatePath, "ready.txt"))
        && File.ReadAllText(SafePaths.Resolve(StatePath, "ready.txt")) == Manifest.Version;
    public async Task InstallAsync(IProgress<InstallProgress>? progress, CancellationToken token)
    {
        if (!IsIsolated && IsGameOrLauncherRunning()) throw new IOException("Close Minecraft and your Minecraft launcher before updating, then try again.");
        var defaultsBytes = Asset("defaults.zip");
        if (Manifest.Defaults is { } bundle && (defaultsBytes.LongLength != bundle.Size
            || !Convert.ToHexStringLower(System.Security.Cryptography.SHA256.HashData(defaultsBytes)).Equals(bundle.Sha256, StringComparison.OrdinalIgnoreCase)))
            defaultsBytes = await File.ReadAllBytesAsync(await downloader.GetAsync(bundle, null, token), token);
        var defaults = PackFeed.ReadDefaults(defaultsBytes);
        await Installer.InstallAsync(Manifest, defaults, progress, token);
        ServerList.Ensure(Instance, Manifest.Server, Asset("server-icon.png"));
        AtomicFiles.Write(SafePaths.Resolve(Instance, "holylois-instance.json"), Encoding.ASCII.GetBytes("{\"instance\":\"holylois-reborn-26.3\"}"));
        if (Settings.Launcher == "sk")
        {
            progress?.Report(new("Preparing SKlauncher library...", 1, 1, Manifest.Files.Length, Manifest.Files.Length));
            await LauncherProfiles.PrepareVersionAsync(SkLauncherProfiles.DataRoot(SkLauncherRoot), Manifest,
                Asset("fabric-profile.json"), downloader, token, Asset("vanilla-profile.json"));
            if (!IsIsolated && IsGameOrLauncherRunning()) throw new IOException("Close SKlauncher, then click Repair / check files to finish adding Holy Lois.");
            SkLauncherProfiles.Register(SkLauncherRoot, Instance, Manifest, Asset("profile-icon.png"));
        }
        else
        {
            progress?.Report(new("Preparing Fabric launcher profile...", 1, 1, Manifest.Files.Length, Manifest.Files.Length));
            await LauncherProfiles.PrepareAsync(MinecraftRoot, Instance, Manifest, Asset("fabric-profile.json"), Asset("profile-icon.png"), downloader, token, Asset("vanilla-profile.json"), LauncherProfiles.ReleaseProfileId, "Holy Lois: Reborn");
        }
        AtomicFiles.WriteJson(SafePaths.Resolve(Instance, "holylois-pack-receipt.json"), Installer.ReadReceipt());
        AtomicFiles.Write(SafePaths.Resolve(StatePath, "ready.txt"), Encoding.ASCII.GetBytes(Manifest.Version));
        // Active files and the last transaction contain recovery copies; obsolete downloads need not accumulate.
        downloader.Prune(Manifest.Files.Concat(Manifest.LoaderFiles).Concat(Manifest.Defaults is { } retained ? [retained] : []));
        progress?.Report(new("Holy Lois is ready. Select its profile in your launcher.", 1, 1, Manifest.Files.Length, Manifest.Files.Length));
    }
    public static bool IsGameOrLauncherRunning(bool includeLauncher = true)
    {
        foreach (var name in includeLauncher ? new[] { "java", "javaw", "MinecraftLauncher", "Minecraft", "SKlauncher" } : new[] { "java", "javaw" })
        {
            var processes = Process.GetProcessesByName(name); var running = processes.Length > 0;
            foreach (var process in processes) process.Dispose(); if (running) return true;
        }
        return false;
    }
    public string? DetectLauncher()
    {
        if (Settings.LauncherExe is not null && File.Exists(Settings.LauncherExe)) return Settings.LauncherExe;
        var paths = Settings.Launcher == "official" ? new[] {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Minecraft Launcher", "MinecraftLauncher.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe") }
            : new[] { Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "SKlauncher", "SKlauncher.exe"), Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "sklauncher", "SKlauncher.exe") };
        return paths.FirstOrDefault(File.Exists) ?? LauncherDiscovery.Registered(Settings.Launcher);
    }
    public void OpenLauncher()
    {
        if (!CanPlay) throw new IOException("Finish Verify & update before opening your launcher.");
        if (IsIsolated) throw new IOException("Test setup is ready. Opening Minecraft is disabled in this development build.");
        var exe = DetectLauncher() ?? throw new IOException("Choose your installed launcher using 'Locate launcher'. Microsoft Store launcher users can open it from Start after setup.");
        var launch = new ProcessStartInfo(exe.StartsWith("shell:", StringComparison.Ordinal) ? "explorer.exe" : exe) { UseShellExecute = true };
        if (exe.StartsWith("shell:", StringComparison.Ordinal)) launch.ArgumentList.Add(exe);
        if (Settings.Launcher == "official" && exe.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)) { launch.ArgumentList.Add("--workDir"); launch.ArgumentList.Add(MinecraftRoot); }
        Process.Start(launch);
    }
    public void DisableShaders()
    {
        if (IsGameOrLauncherRunning(false)) throw new IOException("Close Minecraft before changing shader settings.");
        GraphicsRecovery.DisableShaders(Instance, SafePaths.Resolve(Root, "graphics-backup"));
    }
    public static void OpenUrl(string url) => Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
}
