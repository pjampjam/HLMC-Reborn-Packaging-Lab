using System.IO;
using HolyLois.Core;
using System.Windows;
using System.Reflection;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace HolyLois.App;

public partial class App : Application
{
    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        try
        {
            var args = e.Args;
            var development = args.Contains("--data-dir") || args.Any(a => a.StartsWith("--render-", StringComparison.Ordinal) || a.StartsWith("--verify-", StringComparison.Ordinal)) || args.Contains("--publish-prepared");
            ShutdownMode = ShutdownMode.OnExplicitShutdown;
            if (args.Contains("--apply-app-update") || args.Contains("--recover-app-update")) { Shutdown(await AppUpdates.ApplyAsync(args)); return; }
            if (!development && !AppUpdates.AcquireLock()) { Shutdown(0); return; }
            Exit += (_,_) => AppUpdates.ReleaseLock();
            if (args.Contains("--owner-root") || args.Contains("--publish-prepared"))
                throw new InvalidDataException("Owner publishing stays in the production admin app. This preview does not publish packs.");
            string? data = null;
            var index = Array.IndexOf(args, "--data-dir");
            if (index >= 0 && args.Length > index + 1) data = Path.GetFullPath(args[index + 1]);
            if (data is null && Assembly.GetExecutingAssembly().GetCustomAttributes<AssemblyMetadataAttribute>().Any(a => a.Key == "HolyLoisPreview" && a.Value == "true"))
                data = Path.Combine(AppContext.BaseDirectory, "preview-data");
            string? launcherTestRoot = null;
            var launcherIndex = Array.IndexOf(args, "--launcher-test-root");
            if (launcherIndex >= 0)
            {
                if (index < 0 || launcherIndex + 1 >= args.Length || args.Contains("--render-preview"))
                    throw new ArgumentException("Launcher integration tests require an explicit --data-dir and launcher library path.");
                launcherTestRoot = Path.GetFullPath(args[launcherIndex + 1]);
            }
            var context = new ClientContext(data, launcherTestRoot);
            if (args.Contains("--verify-first-run")) {
                if (data is null || !context.IsIsolated) throw new ArgumentException("First-run verification requires an isolated folder.");
                var root = LauncherStartup.InstallRoot;
                if (File.Exists(LauncherStartup.InstalledExe)) throw new IOException("First-run verification needs a fresh preview root.");
                if (!LauncherSetup.NeedsSetup(root,false)) throw new InvalidDataException("Fresh setup did not offer choices.");
                LauncherStartup.InstallCurrent(); var shortcuts = new List<bool>();
                LauncherSetup.Complete(root,new("official",false,false),shortcuts.Add);
                LauncherSetup.Complete(root,new("sk",true,true),shortcuts.Add);
                LauncherStartup.InstallCurrent();
                if (shortcuts.Count != 0 || LauncherSetup.NeedsSetup(root,false) || AtomicFiles.Hash(LauncherStartup.InstalledExe) != AtomicFiles.Hash(Environment.ProcessPath!))
                    throw new InvalidDataException("First-run install or shortcut preferences failed.");
                if (System.Reflection.Assembly.GetExecutingAssembly().GetManifestResourceNames().Any(n => n.EndsWith("HolyLoisSetup.exe",StringComparison.Ordinal)))
                    throw new InvalidDataException("The old checker remains embedded.");
                AtomicFiles.Write(SafePaths.Resolve(root,"first-run-result.txt"),"Self-install verified; no embedded checker; optional shortcuts remain off after repeated setup."u8.ToArray()); Shutdown(0); return;
            }
            if (args.Contains("--render-update-preview")) {
                if (data is null) throw new ArgumentException("Rendering requires an isolated folder.");
                Render(new AppUpdateWindow(),data,"update-preview.png",504,201); Shutdown(0); return;
            }
            if (args.Contains("--render-setup-preview")) {
                if (data is null) throw new ArgumentException("Rendering requires an isolated folder.");
                foreach (var language in new[] { "en", "ru", "lv" }) { context.SetLanguage(language); var setup = new SetupWindow(context, data); Render(setup,data,"setup-"+language+".png",764,721); }
                Shutdown(0); return;
            }
            if (args.Contains("--verify-recovery")) {
                if (data is null || !context.IsIsolated) throw new ArgumentException("Recovery test requires an isolated folder.");
                File.WriteAllText(Path.Combine(data,"launcher-settings.json"), "{\"launcher\":\"sk\",\"skInstance\":" + System.Text.Json.JsonSerializer.Serialize(Path.Combine(data,"deleted-sk-instance")) + "}");
                context = new ClientContext(data);
                if (context.Settings.SkInstance is not null || !context.RecoveredDeletedInstance || context.CanPlay) throw new InvalidDataException("Deleted instance recovery failed.");
                await LauncherDiscovery.WarmAsync();
                File.WriteAllText(Path.Combine(data,"recovery-result.txt"), "Deleted SK link reset without a startup failure. Official launcher: " + LauncherDiscovery.Registered("official"));
                Shutdown(0); return;
            }
            if (args.Contains("--render-preview"))
            {
                if (data is null) throw new ArgumentException("Rendering requires an isolated --data-dir.");
                await LauncherDiscovery.WarmAsync();
                context.SelectLauncher("official");
                var window = new MainWindow(context);
                Render(window, data, "launcher-design.png", 1060, 748);
                Render(window, data, "launcher-design-small.png", 900, 588);
                context.SelectLauncher("sk");
                window = new MainWindow(context);
                Render(window, data, "launcher-design-sk.png", 1020, 708);
                context.SetLanguage("ru"); window = new MainWindow(context); Render(window,data,"launcher-design-ru.png",1060,748);
                context.SetLanguage("lv"); window = new MainWindow(context); Render(window,data,"launcher-design-lv.png",1060,748);
                Shutdown(0); return;
            }
            if (args.Contains("--verify-install"))
            {
                if (data is null) throw new ArgumentException("Verification requires an explicit isolated --data-dir.");
                var skIndex = Array.IndexOf(args, "--link-sk-instance");
                if (skIndex >= 0)
                {
                    if (launcherTestRoot is null || skIndex + 1 >= args.Length) throw new ArgumentException("Linked verification requires a launcher integration root.");
                    context.SelectLauncher("sk");
                    context.LinkSkInstance(args[skIndex + 1]);
                }
                if (args.Contains("--check-online")) await context.CheckUpdatesAsync(CancellationToken.None);
                await context.InstallAsync(null, CancellationToken.None);
                File.WriteAllText(Path.Combine(data, "verification-result.txt"), "Verified downloads, installed pack, server list and Fabric profile. Pack " + context.Manifest.Version);
                Shutdown(0); return;
            }
            if (!development)
            {
                var readyIndex = Array.IndexOf(args,"--app-update-ready");
                string? readyNonce = readyIndex >= 0 && readyIndex + 1 < args.Length ? args[readyIndex+1] : null;
                if (readyNonce is not null) _ = AppUpdates.Deployment.ValidatePending(readyNonce);
                bool readyMarked = false;
                void Ready() { if (readyNonce is not null && !readyMarked) { AppUpdates.MarkReady(readyNonce); readyMarked = true; } }
                if (args.Contains("--app-update-smoke-fail")) throw new IOException("Simulated updated app startup failure.");
                if (args.Contains("--app-update-smoke") && args.Contains("--skip-app-update-once")) {
                    AtomicFiles.Write(SafePaths.Resolve(LauncherStartup.InstallRoot,"smoke-rollback.txt"),System.Text.Encoding.ASCII.GetBytes(AppUpdates.RunningVersion.ToString())); Shutdown(0); return;
                }
                if (args.Contains("--app-update-smoke") && readyNonce is not null) {
                    AppUpdates.MarkReady(readyNonce);
                    AtomicFiles.Write(SafePaths.Resolve(LauncherStartup.InstallRoot,"smoke-ready.txt"),System.Text.Encoding.ASCII.GetBytes(AppUpdates.RunningVersion.ToString()));
                    Shutdown(0); return;
                }
                var root = LauncherStartup.InstallRoot;
                if (LauncherSetup.NeedsSetup(root, File.Exists(Path.Combine(context.Root,"launcher-settings.json"))))
                {
                    var setup = new SetupWindow(context,root); MainWindow = setup;
                    if (readyNonce is not null) setup.ContentRendered += (_,_) => Ready();
                    setup.ShowDialog();
                    if (!setup.Completed) { Shutdown(0); return; }
                }
                else { LauncherStartup.InstallCurrent(); LauncherSetup.Migrate(root,context.Settings.Launcher); }
                if (!LauncherStartup.IsInstalled)
                {
                    AppUpdates.ReleaseLock(); LauncherStartup.RunInstalled(); Shutdown(0); return;
                }
                if (readyNonce is null && !args.Contains("--skip-app-update-once"))
                {
                    var update = new AppUpdateWindow(); MainWindow = update; update.Show();
                    var handedOff = await AppUpdates.CheckAsync(update); update.Close();
                    if (handedOff) { AppUpdates.ReleaseLock(); Shutdown(0); return; }
                }
                var main = new MainWindow(context);
                if (readyNonce is not null) main.ContentRendered += (_,_) => Ready();
                MainWindow = main;
            }
            else MainWindow = new MainWindow(context);
            MainWindow.Show(); ShutdownMode = ShutdownMode.OnLastWindowClose;
        }
        catch (Exception ex)
        {
            if (e.Args.Any(a => a.StartsWith("--verify-", StringComparison.Ordinal)) || e.Args.Contains("--publish-prepared"))
            {
                var dataIndex = Array.IndexOf(e.Args, "--data-dir");
                if (dataIndex >= 0 && dataIndex + 1 < e.Args.Length)
                    File.WriteAllText(Path.Combine(e.Args[dataIndex + 1], "verification-error.txt"), ex.ToString());
                var preparedIndex=Array.IndexOf(e.Args,"--publish-prepared");
                if(preparedIndex>=0 && preparedIndex+1<e.Args.Length) File.WriteAllText(Path.Combine(e.Args[preparedIndex+1],"publish-error.txt"),ex.Message);
                Shutdown(1); return;
            }
            if (e.Args.Contains("--apply-app-update") || e.Args.Contains("--recover-app-update") || e.Args.Contains("--app-update-smoke") || e.Args.Contains("--app-update-smoke-fail")) {
                AtomicFiles.Write(SafePaths.Resolve(LauncherStartup.InstallRoot,"app-update-error.txt"),System.Text.Encoding.UTF8.GetBytes(ex.ToString())); Shutdown(1); return;
            }
            MessageBox.Show(ex.Message, "Holy Lois: Reborn - setup could not start", MessageBoxButton.OK, MessageBoxImage.Warning);
            Shutdown(1);
        }
    }
    private static void Render(Window window, string root, string name, int width, int height)
    {
        var content = (FrameworkElement)window.Content;
        content.Measure(new Size(width, height)); content.Arrange(new Rect(0, 0, width, height)); content.UpdateLayout();
        var bitmap = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
        var background = new DrawingVisual();
        using (var drawing = background.RenderOpen()) drawing.DrawRectangle(window.Background, null, new Rect(0, 0, width, height));
        bitmap.Render(background); bitmap.Render(content);
        var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using var file = File.Create(Path.Combine(root, name)); encoder.Save(file);
    }
}
