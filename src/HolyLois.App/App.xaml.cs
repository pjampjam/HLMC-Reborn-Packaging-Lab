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
            if (args.Contains("--promote-install")) { await AppPromotion.RunAsync(args); Shutdown(0); return; }
            if (args.Contains("--remove-app")) { await AppMaintenance.RemoveAsync(args); Shutdown(0); return; }
            if (args.Contains("--apply-app-update") || args.Contains("--recover-app-update")) { Shutdown(await AppUpdates.ApplyAsync(args)); return; }
            if (!development && !args.Contains("--app-update-ready") && AppPromotion.Request(args)) { Shutdown(0); return; }
            if (!development && !AppUpdates.AcquireLock()) { Shutdown(0); return; }
            Exit += (_,_) => AppUpdates.ReleaseLock();
            if (args.Contains("--owner-root") || args.Contains("--publish-prepared"))
                throw new InvalidDataException("Owner publishing stays in the production admin app. The player app does not publish packs.");
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
            var migrationTestIndex = Array.IndexOf(args,"--promotion-base");
            if (migrationTestIndex >= 0 && migrationTestIndex + 1 < args.Length &&
                !Path.GetFullPath(args[migrationTestIndex+1]).Equals(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),StringComparison.OrdinalIgnoreCase))
            { data ??= Path.Combine(LauncherStartup.InstallRoot,"data"); launcherTestRoot ??= Path.Combine(Path.GetFullPath(args[migrationTestIndex+1]),"minecraft"); }
            if (!development) AppPromotion.RelocateSettings(args);
            var context = new ClientContext(data, launcherTestRoot);
            if (args.Contains("--verify-shortcut-names")) {
                if (data is null || !context.IsIsolated) throw new IOException("Shortcut verification requires an isolated folder.");
                AppPromotion.RenameShortcutAt(Path.Combine(data,"shortcuts"),Path.Combine(data,"source"),Path.Combine(data,"target"));
                File.WriteAllText(Path.Combine(data,"shortcut-result.txt"),"Shortcut naming pass completed."); Shutdown(0); return;
            }
            if (args.Contains("--verify-ui")) {
                if (data is null) throw new ArgumentException("UI verification requires an isolated folder.");
                var action = new System.Windows.Controls.Button { Content = "Continue", Background = (Brush)Resources["Gold"], Foreground = Brushes.Black, Width = 180, Height = 48 };
                var confirm = new System.Windows.Controls.Button { Content = "Remove launcher app", Style = (Style)Resources["DangerButton"], Width = 190, Height = 48 };
                var testPanel = new System.Windows.Controls.StackPanel(); testPanel.Children.Add(action); testPanel.Children.Add(confirm);
                testPanel.Measure(new Size(300,120)); testPanel.Arrange(new Rect(0,0,300,120)); testPanel.UpdateLayout();
                System.Windows.Controls.TextBlock? FindText(DependencyObject parent) { for (var n=0;n<VisualTreeHelper.GetChildrenCount(parent);n++) { var child=VisualTreeHelper.GetChild(parent,n); if (child is System.Windows.Controls.TextBlock text) return text; var nested=FindText(child); if(nested is not null)return nested; } return null; }
                if (FindText(action)?.Foreground is not SolidColorBrush { Color: var yellowText } || yellowText != Colors.Black
                    || FindText(confirm)?.Foreground is not SolidColorBrush { Color: var removalText } || removalText != Colors.White) throw new IOException("Button text no longer follows its action foreground.");
                var focusWindow = new ThemedWindow { Content = testPanel,Width=360,Height=220,Background=(Brush)Resources["Surface"] };
                focusWindow.Show(); action.Focus(); focusWindow.UpdateLayout();
                InputModality.SetKeyboardFocusVisible(focusWindow,true);
                var ring = (System.Windows.Controls.Border)action.Template.FindName("FocusRing",action);
                if (!action.IsKeyboardFocused || ring.Visibility != Visibility.Visible) throw new IOException("Keyboard navigation has no visible focus.");
                focusWindow.RaiseEvent(new System.Windows.Input.MouseButtonEventArgs(System.Windows.Input.Mouse.PrimaryDevice,Environment.TickCount,System.Windows.Input.MouseButton.Left) { RoutedEvent = System.Windows.Input.Mouse.PreviewMouseDownEvent });
                if (ring.Visibility != Visibility.Collapsed) throw new IOException("A mouse click left the keyboard focus ring active.");
                focusWindow.Close();
                File.WriteAllText(Path.Combine(data,"ui-verification.txt"),"Action text contrast passed. Keyboard focus stays visible; pointer interaction clears its ring without disabling focus."); Shutdown(0); return;
            }
            if (args.Contains("--verify-ui-polish")) {
                if (data is null || !context.IsIsolated) throw new ArgumentException("UI polish verification requires an isolated folder.");
                var setup = new SetupWindow(context, data); setup.EnsureChrome();
                var content = (FrameworkElement)setup.Content; content.Measure(new Size(780,780)); content.Arrange(new Rect(0,0,780,780)); content.UpdateLayout();
                IEnumerable<System.Windows.Controls.Border> Pictures(DependencyObject parent) {
                    for (var n=0;n<VisualTreeHelper.GetChildrenCount(parent);n++) {
                        var child=VisualTreeHelper.GetChild(parent,n);
                        if (child is System.Windows.Controls.Border border && border.Background is ImageBrush) yield return border;
                        foreach (var nested in Pictures(child)) yield return nested;
                    }
                }
                var pictures = Pictures(content).ToArray();
                if (pictures.Length != 2 || pictures.Any(p => p.Height != 155 || p.CornerRadius != new CornerRadius(6) || ((ImageBrush)p.Background).Stretch != Stretch.UniformToFill)
                    || Math.Abs(pictures[0].ActualWidth-pictures[1].ActualWidth) > 0.1) throw new IOException("Launcher picture frames no longer match or preserve crop proportions.");
                var marker = new System.Windows.Controls.Border { Width=40,Height=40,Background=Brushes.White };
                var motionWindow = new ThemedWindow { Content=marker,Width=120,Height=120 }; motionWindow.Show();
                UiMotion.FadeIn(marker,0,true);
                if (!marker.HasAnimatedProperties) throw new IOException("Reveal feedback did not animate.");
                UiMotion.FadeIn(marker,0,false);
                if (marker.HasAnimatedProperties || marker.Opacity != 1) throw new IOException("Reduced motion did not stop the fade immediately.");
                motionWindow.Close();
                File.WriteAllText(Path.Combine(data,"ui-polish-result.txt"),"Matching rounded 155px picture frames preserve proportions. Reveal feedback animates and reduced motion disables it immediately."); Shutdown(0); return;
            }
            if (args.Contains("--verify-modal-shutdown")) {
                if (data is null || !context.IsIsolated) throw new ArgumentException("Modal shutdown verification requires an isolated folder.");
                var main = new MainWindow(context); MainWindow = main; main.Show();
                var shutdown = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(10) };
                shutdown.Tick += (_,_) => {
                    if (!Windows.OfType<SettingsWindow>().Any()) return;
                    shutdown.Stop(); Shutdown(0);
                };
                shutdown.Start();
                ((System.Windows.Controls.Button)main.FindName("SettingsButton")).RaiseEvent(new RoutedEventArgs(System.Windows.Controls.Primitives.ButtonBase.ClickEvent));
                shutdown.Stop();
                File.WriteAllText(Path.Combine(data,"modal-shutdown-result.txt"),"Settings modal returned cleanly after application shutdown.");
                return;
            }
            if (args.Contains("--render-modal-preview")) {
                if (data is null) throw new ArgumentException("Rendering requires an isolated folder.");
                await LauncherDiscovery.WarmAsync();
                var main = new MainWindow(context); main.EnsureChrome();
                var settings = new SettingsWindow(context); settings.EnsureChrome();
                using var mainShade = main.DimForModal(); using var settingsShade = settings.DimForModal();
                var dialog = AppDialog.Create(Localize.Text("UninstallApp"),Localize.Text("UninstallInfo"),Localize.Text("UninstallApp"),true,_=>{}); dialog.EnsureChrome();
                var layers = new System.Windows.Controls.Canvas { Width = 1060, Height = 780, Background = (Brush)Resources["Surface"] };
                void Layer(Window window,int x,int y,int w,int h) { var body=(FrameworkElement)window.Content; window.Content=null; var border=new System.Windows.Controls.Border { Width=w, Height=h, Background=window.Background, Child=body }; System.Windows.Controls.Canvas.SetLeft(border,x); System.Windows.Controls.Canvas.SetTop(border,y); layers.Children.Add(border); }
                Layer(main,0,0,1060,780); Layer(settings,230,65,610,650); Layer(dialog,275,285,520,265);
                Render(new ThemedWindow { Content=layers,Background=(Brush)Resources["Surface"] },data,"modal-layering.png",1060,812); Shutdown(0); return;
            }
            if (args.Contains("--verify-first-run")) {
                if (data is null || !context.IsIsolated) throw new ArgumentException("First-run verification requires an isolated folder.");
                var root = LauncherStartup.InstallRoot;
                if (File.Exists(LauncherStartup.InstalledExe)) throw new IOException("First-run verification needs a fresh installation folder.");
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
            if (args.Contains("--render-settings-preview")) { Render(new SettingsWindow(context),data!,"settings-preview.png",610,650); Shutdown(0); return; }
            if (args.Contains("--render-update-preview")) {
                if (data is null) throw new ArgumentException("Rendering requires an isolated folder.");
                var update = new AppUpdateWindow(); update.SetTransfer("Downloading launcher " + AppUpdates.RunningVersion.ToString(3) + "...",37000000,67000000);
                Render(update,data,"update-preview.png",520,280); Shutdown(0); return;
            }
            if (args.Contains("--render-setup-preview")) {
                if (data is null) throw new ArgumentException("Rendering requires an isolated folder.");
                foreach (var language in new[] { "en", "ru", "lv" }) { context.SetLanguage(language); var setup = new SetupWindow(context, data); Render(setup,data,"setup-"+language+".png",780,780); }
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
                Render(window, data, "launcher-design-small.png", 920, 650);
                context.SelectLauncher("sk");
                window = new MainWindow(context);
                Render(window, data, "launcher-design-sk.png", 1020, 708);
                context.SetLanguage("ru"); window = new MainWindow(context); Render(window,data,"launcher-design-ru.png",1060,748);
                context.SetLanguage("lv"); window = new MainWindow(context); Render(window,data,"launcher-design-lv.png",1060,748);
                Shutdown(0); return;
            }
            if (args.Contains("--render-ready-preview")) {
                if (data is null || !context.CanPlay) throw new ArgumentException("Ready rendering requires an installed isolated fixture.");
                await LauncherDiscovery.WarmAsync();
                var window = new MainWindow(context); Render(window,data,"launcher-ready.png",1060,748);
                Shutdown(0); return;
            }
            if (args.Contains("--verify-responsive"))
            {
                if (data is null || !context.IsIsolated) throw new ArgumentException("Responsiveness verification requires an isolated folder.");
                var window = new MainWindow(context); MainWindow = window; window.Show();
                int ticks = 0; var pulse = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(10) };
                pulse.Tick += (_, _) => ticks++; pulse.Start();
                await window.VerifyInstallAsync(); pulse.Stop();
                if (!context.CanPlay || ticks < 5) throw new IOException("Repair failed or the window dispatcher did not remain responsive.");
                File.WriteAllText(Path.Combine(data,"responsiveness-result.txt"),"Real pack install/repair succeeded with " + ticks + " window dispatcher ticks.");
                Shutdown(0); return;
            }
            if (args.Contains("--verify-install"))
            {
                if (data is null) throw new ArgumentException("Verification requires an explicit isolated --data-dir.");
                if (args.Contains("--sk-launcher")) context.SelectLauncher("sk");
                var skIndex = Array.IndexOf(args, "--link-sk-instance");
                if (skIndex >= 0)
                {
                    if (launcherTestRoot is null || skIndex + 1 >= args.Length) throw new ArgumentException("Linked verification requires a launcher integration root.");
                    context.SelectLauncher("sk");
                    context.LinkSkInstance(args[skIndex + 1]);
                }
                if (args.Contains("--check-online")) await context.CheckUpdatesAsync(CancellationToken.None);
                await context.InstallAsync(null, CancellationToken.None);
                if (!context.CanPlay) throw new IOException("The pack is installed but its launcher registration is not ready.");
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
                if (args.Contains("--show-setup") || LauncherSetup.NeedsSetup(root, File.Exists(Path.Combine(context.Root,"launcher-settings.json"))))
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
                AppPromotion.CompleteNames(context,args);
                if (readyNonce is not null) main.ContentRendered += async (_,_) => { Ready(); try { await AppPromotion.AfterUpdateAsync(args,main); } catch(Exception ex) { AppDialog.Show(main,"Installation update",ex.Message); } };
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
            if (e.Args.Contains("--remove-app") || e.Args.Contains("--apply-app-update") || e.Args.Contains("--recover-app-update") || e.Args.Contains("--app-update-smoke") || e.Args.Contains("--app-update-smoke-fail")) {
                AtomicFiles.Write(SafePaths.Resolve(LauncherStartup.InstallRoot,"app-update-error.txt"),System.Text.Encoding.UTF8.GetBytes(ex.ToString())); Shutdown(1); return;
            }
            AppDialog.Show(null,"Holy Lois could not start",ex.Message);
            Shutdown(1);
        }
    }
    private static void Render(Window window, string root, string name, int width, int height)
    {
        if (window is ThemedWindow themed) themed.EnsureChrome();
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
