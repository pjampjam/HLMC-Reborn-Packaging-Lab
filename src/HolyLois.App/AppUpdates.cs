using HolyLois.Core;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Reflection;
using System.Text;

namespace HolyLois.App;

public static class AppUpdates
{
    private static string Key => Encoding.ASCII.GetString(ClientContext.Asset("app-release-public.pem"));
    public static Version RunningVersion => Assembly.GetExecutingAssembly().GetName().Version!;
    public static AppDeployment Deployment => new(LauncherStartup.InstallRoot,Key);
    public static string? Notice { get; private set; }
    private static FileStream? updateLock;
    public static bool AcquireLock()
    {
        Directory.CreateDirectory(LauncherStartup.InstallRoot);
        try { updateLock = new FileStream(SafePaths.Resolve(LauncherStartup.InstallRoot,"app-session.lock"),FileMode.OpenOrCreate,FileAccess.ReadWrite,FileShare.None); return true; }
        catch (IOException) { return false; }
    }
    public static void ReleaseLock() { updateLock?.Dispose(); updateLock = null; }
    public static Process Start(string executable, IEnumerable<string> args)
    {
        var start = new ProcessStartInfo(executable) { UseShellExecute = false, WorkingDirectory = Path.GetDirectoryName(executable)! };
        start.ArgumentList.Add("--lab-root"); start.ArgumentList.Add(LauncherStartup.InstallRoot);
        foreach (var arg in args) start.ArgumentList.Add(arg);
        return Process.Start(start) ?? throw new IOException("The verified application could not start.");
    }
    public static async Task<bool> CheckAsync(AppUpdateWindow window)
    {
        var deployment = Deployment;
        if (deployment.Pending is { } interrupted)
        {
            if (deployment.IsAcknowledged(interrupted.Nonce)) deployment.Finalize(interrupted.Nonce);
            else {
                var interruptedRelease = AppReleasePolicy.Parse(interrupted.Release,Key);
                if (AtomicFiles.Matches(deployment.Target,interruptedRelease.File)) {
                    StartWorker(deployment.Stage(interruptedRelease),interrupted.Nonce,true); return true;
                }
                deployment.Rollback(interrupted.Nonce); Notice = "An interrupted launcher update was rolled back.";
            }
        }
        using var http = new HttpClient(new HttpClientHandler { AllowAutoRedirect = true }) { Timeout = TimeSpan.FromSeconds(20) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("HolyLoisReborn/" + RunningVersion.ToString(3));
        try
        {
            var signed = await new AppReleaseFeed(http,Key).FetchAsync(window.Cancellation.Token);
            var next = AppReleasePolicy.Parse(signed,Key);
            AppReleasePolicy.Accept(next,RunningVersion,deployment.Installed);
            if (next.NumericVersion == AppReleasePolicy.Normalize(RunningVersion)) { deployment.RecordCurrent(signed,RunningVersion); return false; }
            var failed = SafePaths.Resolve(LauncherStartup.InstallRoot,"failed-app-update.txt");
            if (File.Exists(failed) && new FileInfo(failed).Length <= 128 && File.ReadAllText(failed) == next.Sha256
                && DateTime.UtcNow - File.GetLastWriteTimeUtc(failed) < TimeSpan.FromHours(24))
            { Notice = "The last launcher update failed to start. Your working version was kept; another attempt will be made later."; return false; }
            window.SetStatus("Downloading launcher " + next.Version + "...");
            var progress = new Progress<long>(done => window.SetTransfer("Downloading launcher " + next.Version + "...",done,next.Size));
            using var downloadTimeout = new CancellationTokenSource(TimeSpan.FromMinutes(5));
            using var combined = CancellationTokenSource.CreateLinkedTokenSource(window.Cancellation.Token,downloadTimeout.Token);
            // Use a separate client because HttpClient's timeout cannot change after a request.
            using var downloadHttp = new HttpClient(new HttpClientHandler { AllowAutoRedirect = true }) { Timeout = TimeSpan.FromMinutes(5) };
            var downloaded = await new DownloadCache(SafePaths.Resolve(LauncherStartup.InstallRoot,"app-downloads"),downloadHttp).GetAsync(next.File,progress,combined.Token);
            window.SetStatus("Verifying and preparing the launcher update...");
            var nonce = await Task.Run(() => deployment.Prepare(signed,downloaded,RunningVersion));
            new DownloadCache(SafePaths.Resolve(LauncherStartup.InstallRoot,"app-downloads"),downloadHttp).Prune([next.File]);
            window.SetStatus("Installing the verified update...");
            try { StartWorker(deployment.Stage(next),nonce); }
            catch { deployment.Rollback(nonce); throw; }
            return true;
        }
        catch (Exception ex) when (ex is HttpRequestException or OperationCanceledException or IOException or InvalidDataException or System.Security.Cryptography.CryptographicException)
        {
            Notice = ex is HttpRequestException { StatusCode: System.Net.HttpStatusCode.NotFound }
                ? "Launcher updates are not published yet. Your installed app remains available."
                : "Launcher updates are unavailable right now. Your installed app remains available.";
            return false;
        }
    }
    public static void StartWorker(string executable, string nonce, bool recover = false)
    {
        using var self = Process.GetCurrentProcess();
        Start(executable,[recover ? "--recover-app-update" : "--apply-app-update",nonce,self.Id.ToString(System.Globalization.CultureInfo.InvariantCulture),self.StartTime.ToUniversalTime().Ticks.ToString(System.Globalization.CultureInfo.InvariantCulture)]).Dispose();
    }
    public static async Task<int> ApplyAsync(string[] args)
    {
        var recovery = args.Contains("--recover-app-update");
        var i = Array.IndexOf(args,recovery ? "--recover-app-update" : "--apply-app-update");
        if (i < 0 || i + 3 >= args.Length || !int.TryParse(args[i+2],out var pid) || !long.TryParse(args[i+3],out var ticks)) throw new InvalidDataException("Invalid app update handoff.");
        var workerWindow = args.Any(a => a.StartsWith("--app-update-smoke")) ? null : new AppUpdateWindow("Updating Holy Lois",false);
        workerWindow?.Show(); workerWindow?.SetStage("Waiting for the launcher to close...",10);
        var nonce = args[i+1]; var deployment = Deployment; var pending = deployment.ValidatePending(nonce);
        var release = AppReleasePolicy.Parse(pending.Release,Key);
        if (!Path.GetFullPath(Environment.ProcessPath!).Equals(deployment.Stage(release),StringComparison.OrdinalIgnoreCase)
            || !AtomicFiles.Matches(Environment.ProcessPath!,release.File)) throw new InvalidDataException("The update worker is not the verified staged app.");
        try
        {
            using var parent = Process.GetProcessById(pid);
            if (parent.StartTime.ToUniversalTime().Ticks != ticks) throw new InvalidDataException("The original app process changed.");
            using var wait = new CancellationTokenSource(TimeSpan.FromSeconds(30)); await parent.WaitForExitAsync(wait.Token);
        }
        catch (ArgumentException) { /* The parent already exited. */ }
        if (!AcquireLock()) throw new IOException("Another Holy Lois app is running. Close it before updating.");
        if (recovery) {
            try { deployment.Rollback(nonce); }
            finally { ReleaseLock(); }
            if (File.Exists(deployment.Target)) Start(deployment.Target,["--skip-app-update-once"]).Dispose();
            return 0;
        }
        Process? child = null;
        try
        {
            workerWindow?.SetStage("Installing the verified launcher...",40);
            await Task.Run(() => deployment.Commit(nonce));
            workerWindow?.SetStage("Starting the updated launcher...",80);
            ReleaseLock(); child = Start(deployment.Target,args.Contains("--app-update-smoke-fail") ? ["--app-update-ready",nonce,"--app-update-smoke-fail"] : args.Contains("--app-update-smoke") ? ["--app-update-ready",nonce,"--app-update-smoke"] : ["--app-update-ready",nonce]);
            var deadline = DateTime.UtcNow.AddSeconds(30);
            while (!child.HasExited && DateTime.UtcNow < deadline && !deployment.IsAcknowledged(nonce)) await Task.Delay(100);
            if (deployment.IsAcknowledged(nonce)) { deployment.Finalize(nonce); workerWindow?.SetStage("Launcher updated.",100,true); if (workerWindow is not null) await Task.Delay(500); return 0; }
            if (!child.HasExited) { child.Kill(); await child.WaitForExitAsync(); }
            if (!AcquireLock()) throw new IOException("Update recovery is waiting for Holy Lois to close.");
            deployment.Rollback(nonce); ReleaseLock();
            AtomicFiles.Write(SafePaths.Resolve(LauncherStartup.InstallRoot,"failed-app-update.txt"),Encoding.ASCII.GetBytes(release.Sha256));
            if (File.Exists(deployment.Target)) Start(deployment.Target,args.Contains("--app-update-smoke-fail") ? ["--skip-app-update-once","--app-update-smoke"] : ["--skip-app-update-once"]).Dispose();
            return 1;
        }
        catch
        {
            if (child is not null && !child.HasExited) { child.Kill(); await child.WaitForExitAsync(); }
            if (deployment.Pending is not null) deployment.Rollback(nonce);
            throw;
        }
        finally { workerWindow?.FinishAndClose(); child?.Dispose(); ReleaseLock(); }
    }
    public static void MarkReady(string nonce)
    {
        var pending = Deployment.ValidatePending(nonce);
        if (AppReleasePolicy.Parse(pending.Release,Key).NumericVersion != AppReleasePolicy.Normalize(RunningVersion))
            throw new InvalidDataException("The downloaded app version differs from its signed catalog.");
        Deployment.Acknowledge(nonce,Environment.ProcessPath!);
    }
}
