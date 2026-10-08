using HolyLois.Core;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Reflection;
using System.Text;
using System.Text.Json;

namespace HolyLois.App;

/// <summary>Launcher is "official" (bought account, opens Minecraft Launcher), "sk" (SKlauncher game folder) or "name" (player name, fast start only).</summary>
public sealed record UserSettings(string Launcher = "official", string? LauncherExe = null, string? SkInstance = null, string Language = "en", string PlayMode = HolyLois.Core.PlayMode.Standard,
    bool JoinServer = true, bool? FastStart = null);
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
    private string? newSkInstance;
    public string Instance => Settings.Launcher == "sk" ? Settings.SkInstance ?? (newSkInstance ??= SkInstallDirectory()) : PreparedInstance;
    private string SkInstallDirectory()
    {
        try { return SkLauncherProfiles.InstallDirectory(SkLauncherRoot); }
        catch (IOException) { return SafePaths.Resolve(SkLauncherRoot, "instances/holy-lois-reborn"); }
    }
    public UserSettings Settings { get; private set; }
    private string SettingsPath => SafePaths.Resolve(Root, "launcher-settings.json");
    private string StatePath => SafePaths.Resolve(Root, Settings.Launcher == "sk" ? "state/sk-instance" : "state/prepared-instance");
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
        if (Settings.Launcher is not ("official" or "sk" or "name")) throw new InvalidDataException("Saved launcher selection is invalid.");
        Settings = Settings with { PlayMode = HolyLois.Core.PlayMode.Normalize(Settings.PlayMode) };
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
            if (path is not null) LinkSkInstance(path);
        }
        catch (IOException) { /* Repair reports unreadable registry details; startup remains available. */ }
    }
    public void SetLauncher(string path)
    {
        if (!File.Exists(path) || !(Path.GetExtension(path).Equals(".exe", StringComparison.OrdinalIgnoreCase) || Path.GetExtension(path).Equals(".lnk", StringComparison.OrdinalIgnoreCase))) throw new IOException("Choose an installed launcher executable.");
        Settings = Settings with { LauncherExe = Path.GetFullPath(path) }; AtomicFiles.WriteJson(SettingsPath, Settings);
    }
    public void RebuildSkImport()
    { newSkInstance = null; Settings = Settings with { SkInstance = null }; RecoveredDeletedInstance = true; AtomicFiles.WriteJson(SettingsPath, Settings); }
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
    private void ValidateSkInstance(string path)
    {
        SkLauncherProfiles.ValidateNativeDirectory(SkLauncherRoot, path);
        SafePaths.RejectLinks(path);
        var marker = SafePaths.Resolve(path, "holylois-instance.json");
        if (!File.Exists(marker) || File.ReadAllText(marker) != "{\"instance\":\"holylois-reborn-26.3\"}")
            throw new IOException("Choose the game folder of the imported Holy Lois instance. Other modpacks cannot be linked.");
    }
    private PackInstaller Installer => new(Instance, StatePath, downloader);
    public InstalledReceipt? Receipt
    {
        get { try { return Installer.ReadReceipt(); } catch (Exception ex) when (ex is IOException or JsonException) { return null; } }
    }
    public bool CanPlay => (Settings.Launcher != "sk" || SkLauncherProfiles.IsRegistered(SkLauncherRoot, Instance))
        && (Settings.Launcher != "official" || LauncherProfileReady())
        && Directory.Exists(Instance) && File.Exists(SafePaths.Resolve(Instance, "holylois-instance.json")) && Receipt?.Version == Manifest.Version && File.Exists(SafePaths.Resolve(StatePath, "ready.txt"))
        && File.ReadAllText(SafePaths.Resolve(StatePath, "ready.txt")) == Manifest.Version;
    // Switching from a player name to a bought account needs the Minecraft Launcher profile, which a name install never wrote.
    private bool LauncherProfileReady() => File.Exists(Path.Combine(MinecraftRoot, "versions", LauncherProfiles.VersionId, LauncherProfiles.VersionId + ".json"));
    public async Task InstallAsync(IProgress<InstallProgress>? progress, CancellationToken token)
    {
        if (!IsIsolated && IsGameOrLauncherRunning()) throw new IOException("Close Minecraft and your Minecraft launcher before updating, then try again.");
        if (Settings.Launcher == "sk")
        {
            // A library move or a pre-hotfix saved link must be recovered before downloading into it.
            if (Settings.SkInstance is not null)
            {
                try { ValidateSkInstance(Settings.SkInstance); }
                catch (IOException) { RebuildSkImport(); DiscoverSkInstance(); }
            }
            if (Settings.SkInstance is null) newSkInstance = SkLauncherProfiles.InstallDirectory(SkLauncherRoot);
        }
        var defaultsBytes = Asset("defaults.zip");
        if (Manifest.Defaults is { } bundle && (defaultsBytes.LongLength != bundle.Size
            || !Convert.ToHexStringLower(System.Security.Cryptography.SHA256.HashData(defaultsBytes)).Equals(bundle.Sha256, StringComparison.OrdinalIgnoreCase)))
            defaultsBytes = await File.ReadAllBytesAsync(await downloader.GetAsync(bundle, null, token), token);
        var defaults = PackFeed.ReadDefaults(defaultsBytes);
        await Installer.InstallAsync(Manifest, defaults, progress, token);
        ServerList.Ensure(Instance, ServerAddress.Public, Asset("server-icon.png"), Manifest.Server);
        AtomicFiles.Write(SafePaths.Resolve(Instance, "holylois-instance.json"), Encoding.ASCII.GetBytes("{\"instance\":\"holylois-reborn-26.3\"}"));
        if (Settings.Launcher == "sk")
        {
            progress?.Report(new("Preparing SKlauncher library...", 1, 1, Manifest.Files.Length, Manifest.Files.Length));
            await LauncherProfiles.PrepareVersionAsync(SkLauncherProfiles.DataRoot(SkLauncherRoot), Manifest,
                LauncherFabricProfile(), downloader, token, Asset("vanilla-profile.json"));
            if (!IsIsolated && IsGameOrLauncherRunning()) throw new IOException("Close SKlauncher, then click Repair / check files to finish adding Holy Lois.");
            SkLauncherProfiles.Register(SkLauncherRoot, Instance, Manifest, Asset("profile-icon.png"));
        }
        else if (Settings.Launcher == "official")
        {
            progress?.Report(new("Preparing Fabric launcher profile...", 1, 1, Manifest.Files.Length, Manifest.Files.Length));
            await LauncherProfiles.PrepareAsync(MinecraftRoot, Instance, Manifest, LauncherFabricProfile(), Asset("profile-icon.png"), downloader, token, Asset("vanilla-profile.json"), LauncherProfiles.ReleaseProfileId, "Holy Lois: Reborn");
        }
        AtomicFiles.WriteJson(SafePaths.Resolve(Instance, "holylois-pack-receipt.json"), Installer.ReadReceipt());
        AtomicFiles.Write(SafePaths.Resolve(StatePath, "ready.txt"), Encoding.ASCII.GetBytes(Manifest.Version));
        // Active files and the last transaction contain recovery copies; obsolete downloads need not accumulate.
        downloader.Prune(Manifest.Files.Concat(Manifest.LoaderFiles).Concat(Manifest.Defaults is { } retained ? [retained] : []));
        _ = CleanInstalledDownloads();
        progress?.Report(new("Holy Lois is ready. Select its profile in your launcher.", 1, 1, Manifest.Files.Length, Manifest.Files.Length));
    }
    // The profile a player's own launcher starts: the pack's Fabric profile, plus "join Holy Lois" when that setting is on.
    private byte[] LauncherFabricProfile() => GameVersion.WithJoin(Asset("fabric-profile.json"), Settings.JoinServer ? ServerAddress.Public : null);
    /// <summary>Rewrites the launcher's copy of the Holy Lois profile after the join setting changed. Missing profiles wait for the next install.</summary>
    public void ApplyJoinToLauncherProfile()
    {
        try
        {
            string? root = Settings.Launcher switch { "official" => MinecraftRoot, "sk" => SkLauncherProfiles.DataRoot(SkLauncherRoot), _ => null };
            if (root is null) return;
            var path = SafePaths.Resolve(root, "versions/" + LauncherProfiles.VersionId + "/" + LauncherProfiles.VersionId + ".json");
            if (!File.Exists(path)) return;
            var next = LauncherFabricProfile();
            if (!File.ReadAllBytes(path).AsSpan().SequenceEqual(next)) AtomicFiles.Write(path, next);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException or InvalidDataException) { /* the next install writes it */ }
    }
    public void SetJoinServer(bool join) { Settings = Settings with { JoinServer = join }; AtomicFiles.WriteJson(SettingsPath, Settings); ApplyJoinToLauncherProfile(); }
    public void SetFastStart(bool fast) { Settings = Settings with { FastStart = fast }; AtomicFiles.WriteJson(SettingsPath, Settings); }
    public bool UsesFastStart => HolyLois.Core.PlayMode.UsesFastStart(Settings.Launcher, Settings.FastStart);

    // Player names live in the data folder, which uninstalling keeps unless the player asks to forget them.
    public string PlayersPath => SafePaths.Resolve(Root, "players.json");
    public PlayerBook Players => PlayerNames.Load(PlayersPath);
    public string? PlayerName => Players.Current;
    public void UsePlayerName(string name) => PlayerNames.Save(PlayersPath, PlayerNames.Use(Players, name, DateTimeOffset.UtcNow));
    public void ForgetPlayers() { if (File.Exists(PlayersPath)) File.Delete(PlayersPath); }
    /// <summary>First fast start for a SKlauncher player: take the name they already play with from the game log.</summary>
    public string? SuggestedPlayerName()
    {
        if (PlayerName is not null) return PlayerName;
        foreach (var folder in new[] { Instance, PreparedInstance }.Distinct())
            if (PlayerNames.FromGameLog(folder) is { } name) return name;
        return null;
    }

    // Fast start keeps Java and Minecraft in the app's own folder and reuses identical files from other launchers.
    public string GameRoot => SafePaths.Resolve(Root, "game");
    public string LogsRoot => SafePaths.Resolve(Root, "logs");
    public string OutputLog => SafePaths.Resolve(LogsRoot, "game-output.log");
    private string StartRecordPath => SafePaths.Resolve(LogsRoot, "last-start.json");
    /// <summary>Verification only: extra folders whose identical files may be reused, and permission to start the game from a test folder.</summary>
    public IReadOnlyList<string> ExtraReuseRoots { get; set; } = [];
    public bool AllowTestStart { get; set; }
    public GameDownloads? LastDownloads { get; private set; }
    private IEnumerable<string> ReuseRoots()
    {
        foreach (var extra in ExtraReuseRoots) yield return extra;
        yield return MinecraftRoot;
        string? sk = null;
        try { sk = SkLauncherProfiles.DataRoot(SkLauncherRoot); } catch (IOException) { }
        if (sk is not null) yield return sk;
        if (!IsIsolated) yield return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe", "LocalCache", "Local");
    }
    public async Task<LaunchPlan> PrepareFastStartAsync(IProgress<GameProgress>? progress, CancellationToken token)
    {
        if (!UsesFastStart) throw new IOException("Fast start is off. Play opens your launcher.");
        var name = PlayerName ?? throw new IOException("Choose your player name first.");
        if (!CanPlay) throw new IOException("Finish Verify & update before playing.");
        var downloads = new GameDownloads(http, GameRoot, ReuseRoots());
        LastDownloads = downloads;
        var version = new GameVersion(Asset("vanilla-profile.json"), Asset("fabric-profile.json"));
        var memory = FastStart.MemoryMb(GC.GetGCMemoryInfo().TotalAvailableMemoryBytes);
        var options = new FastStartOptions(name, Settings.JoinServer ? ServerAddress.Public : null, AppUpdates.RunningVersion.ToString(3), memory);
        HolyLois.Core.PlayMode.RemoveOldQuickPlayNote(Instance);
        return await FastStart.PrepareAsync(downloads, version, Manifest.LoaderFiles, Instance, options, GamePlatform.Current, progress, token);
    }
    public GameSession StartGame(LaunchPlan plan)
    {
        if (IsIsolated && !AllowTestStart) throw new IOException("Test setup is ready. Starting Minecraft is disabled in this development build.");
        var session = GameSession.Start(plan, OutputLog);
        SaveStart(new StartRecord("fast start", Manifest.Version, AppUpdates.RunningVersion.ToString(3), DateTimeOffset.UtcNow));
        return session;
    }
    public StartRecord? LastStart
    {
        get { try { return File.Exists(StartRecordPath) ? JsonSerializer.Deserialize<StartRecord>(File.ReadAllBytes(StartRecordPath), JsonSettings.Options) : null; } catch (Exception ex) when (ex is IOException or JsonException) { return null; } }
    }
    public void SaveStart(StartRecord record) { try { AtomicFiles.WriteJson(StartRecordPath, record); } catch (IOException) { } }
    public string BuildReport() => GameReports.Build(LastStart, Instance, File.Exists(OutputLog) ? OutputLog : null, Environment.OSVersion.VersionString, Environment.UserName);
    public StorageCleanupResult CleanInstalledDownloads()
    {
        try
        {
            if (!CanPlay) return StorageCleanupResult.Empty;
            using var cleanupLock = new FileStream(SafePaths.Resolve(StatePath, "update.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
            var result = downloader.RemoveInstalledCopies(Manifest.Files, Instance);
            result += downloader.RemoveInstalledCopies(Manifest.LoaderFiles, Settings.Launcher == "sk" ? SkLauncherProfiles.DataRoot(SkLauncherRoot) : MinecraftRoot);
            return result;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { return StorageCleanupResult.Empty; }
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
    /// <summary>Asks the game and the player's launcher to close (windows first), then ends the launchers that stay. Java is only closed through its Minecraft window.</summary>
    public static void CloseGameAndLauncher()
    {
        foreach (var name in new[] { "MinecraftLauncher", "Minecraft", "SKlauncher" })
            foreach (var process in Process.GetProcessesByName(name)) { try { process.CloseMainWindow(); } catch (InvalidOperationException) { } finally { process.Dispose(); } }
        foreach (var name in new[] { "java", "javaw" })
            foreach (var process in Process.GetProcessesByName(name))
            {
                try { var title = process.MainWindowTitle; if (title.StartsWith("Minecraft", StringComparison.Ordinal) || title.StartsWith("Holy Lois", StringComparison.Ordinal)) process.CloseMainWindow(); }
                catch (InvalidOperationException) { } finally { process.Dispose(); }
            }
        if (WaitUntilClosed(15)) return;
        foreach (var name in new[] { "MinecraftLauncher", "Minecraft", "SKlauncher" })
            foreach (var process in Process.GetProcessesByName(name)) { try { process.Kill(); } catch (Exception ex) when (ex is InvalidOperationException or System.ComponentModel.Win32Exception) { } finally { process.Dispose(); } }
        if (!WaitUntilClosed(8)) throw new IOException("Close Minecraft and your Minecraft launcher before updating, then try again.");
    }
    private static bool WaitUntilClosed(int seconds)
    {
        for (var i = 0; i < seconds * 4; i++) { if (!IsGameOrLauncherRunning()) return true; Thread.Sleep(250); }
        return !IsGameOrLauncherRunning();
    }
    public string? DetectLauncher()
    {
        if (Settings.Launcher == "name") return null;
        if (Settings.LauncherExe is not null && File.Exists(Settings.LauncherExe)) return Settings.LauncherExe;
        var paths = Settings.Launcher == "official" ? new[] {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Minecraft Launcher", "MinecraftLauncher.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Minecraft Launcher", "MinecraftLauncher.exe") }
            : new[] { Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "SKlauncher", "SKlauncher.exe"), Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "sklauncher", "SKlauncher.exe") };
        return paths.FirstOrDefault(File.Exists) ?? LauncherDiscovery.Registered(Settings.Launcher);
    }
    public GuardReport Guard()
    {
        if (IsIsolated || !CanPlay || IsGameOrLauncherRunning(false)) return GuardReport.Empty;
        try { return ModGuard.Run(Instance, StatePath, Manifest); }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException) { return GuardReport.Empty; }
    }
    public void OpenLauncher()
    {
        if (!CanPlay) throw new IOException("Finish Verify & update before opening your launcher.");
        if (IsIsolated) throw new IOException("Test setup is ready. Opening Minecraft is disabled in this development build.");
        var exe = DetectLauncher() ?? throw new IOException("Choose your installed launcher using 'Locate launcher'. Microsoft Store launcher users can open it from Start after setup.");
        HolyLois.Core.PlayMode.RemoveOldQuickPlayNote(Instance);
        ApplyJoinToLauncherProfile();
        SaveStart(new StartRecord(Settings.Launcher == "sk" ? "SKlauncher" : "Minecraft Launcher", Manifest.Version, AppUpdates.RunningVersion.ToString(3), DateTimeOffset.UtcNow));
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
