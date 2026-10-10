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
            // A second start while the app runs (also while it waits hidden behind the game) brings the open window forward.
            if (!development && !AppUpdates.AcquireLock()) { ShowRequest.Send(); Shutdown(0); return; }
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
            if (args.Contains("--verify-setup-scroll")) {
                if(data is null || !context.IsIsolated)throw new ArgumentException("Setup verification requires an isolated folder.");
                context.SelectLauncher("name");
                foreach(var language in new[]{"en","ru","lv"})foreach(int height in new[]{780,600}){
                    context.SetLanguage(language);var setup=new SetupWindow(context,data){Height=height};setup.EnsureChrome();setup.Show();setup.GoTo(1);setup.UpdateLayout();
                    var box=(System.Windows.Controls.TextBox)setup.FindName("SetupNameBox");box.Text="Notch";box.Focus();
                    var scroll=(System.Windows.Controls.ScrollViewer)setup.FindName("SetupScroll");
                    var wheel=new System.Windows.Input.MouseWheelEventArgs(System.Windows.Input.Mouse.PrimaryDevice,Environment.TickCount,-1200){RoutedEvent=System.Windows.Input.Mouse.PreviewMouseWheelEvent};box.RaiseEvent(wheel);setup.UpdateLayout();
                    if(!wheel.Handled || scroll.ScrollableHeight>0 && scroll.VerticalOffset<=0)throw new IOException("Name entry blocked setup scrolling.");
                    Render(setup,data,$"setup-mode-{language}-{height}.png",780,height);
                    setup.GoTo(2);setup.UpdateLayout();scroll.ScrollToEnd();setup.UpdateLayout();
                    foreach(string name in new[]{"DesktopChoice","StartChoice"}){
                        var control=(FrameworkElement)setup.FindName(name);var point=control.TranslatePoint(new Point(0,0),scroll);
                        if(point.Y<0 || point.Y+control.ActualHeight>scroll.ActualHeight)throw new IOException("Shortcut choice is outside the scroll viewport.");
                    }
                    var next=(FrameworkElement)setup.FindName("ContinueButton");var nextPoint=next.TranslatePoint(new Point(0,0),setup);
                    if(nextPoint.Y+next.ActualHeight>height)throw new IOException("Finish button is outside the window.");
                    Render(setup,data,$"setup-finish-{language}-{height}.png",780,height);setup.Close();
                }
                File.WriteAllText(Path.Combine(data,"setup-scroll-result.txt"),"EN/RU/LV setup steps at 780px and 600px: name wheel scroll works on the play step, both shortcut choices and the Finish button stay reachable on the last step.");Shutdown(0);return;
            }
            if (args.Contains("--verify-ui-polish")) {
                if (data is null || !context.IsIsolated) throw new ArgumentException("UI polish verification requires an isolated folder.");
                var setup = new SetupWindow(context, data); setup.EnsureChrome();setup.Show();setup.UpdateLayout();
                var content = (FrameworkElement)setup.Content; content.Measure(new Size(780,780)); content.Arrange(new Rect(0,0,780,780)); content.UpdateLayout();
                IEnumerable<System.Windows.Controls.Border> Pictures(DependencyObject parent) {
                    for (var n=0;n<VisualTreeHelper.GetChildrenCount(parent);n++) {
                        var child=VisualTreeHelper.GetChild(parent,n);
                        if (child is System.Windows.Controls.Border border && border.Background is ImageBrush) yield return border;
                        foreach (var nested in Pictures(child)) yield return nested;
                    }
                }
                setup.GoTo(1); content.Measure(new Size(780,780)); content.Arrange(new Rect(0,0,780,780)); content.UpdateLayout();
                var pictures = Pictures(content).Where(p => p.IsVisible).ToArray();
                var nameFrame = (System.Windows.Controls.Border)setup.FindName("NamePicture");
                var accountFrame = (System.Windows.Controls.Border)setup.FindName("OfficialPicture");
                if (pictures.Length != 2 || pictures.Any(p => p.Height != 155 || p.CornerRadius != new CornerRadius(6) || ((ImageBrush)p.Background).Stretch != Stretch.UniformToFill)
                    || !pictures.Contains(nameFrame) || !pictures.Contains(accountFrame)
                    || Math.Abs(accountFrame.ActualWidth-nameFrame.ActualWidth) > 1) throw new IOException("Setup picture frames no longer match or preserve crop proportions. Picture count="+pictures.Length+", name width="+nameFrame.ActualWidth);
                setup.Close();
                var marker = new System.Windows.Controls.Border { Width=40,Height=40,Background=Brushes.White };
                var motionWindow = new ThemedWindow { Content=marker,Width=120,Height=120 }; motionWindow.Show();
                UiMotion.FadeIn(marker,0,true);
                if (!marker.HasAnimatedProperties) throw new IOException("Reveal feedback did not animate.");
                UiMotion.FadeIn(marker,0,false);
                if (marker.HasAnimatedProperties || marker.Opacity != 1) throw new IOException("Reduced motion did not stop the fade immediately.");
                motionWindow.Close();
                File.WriteAllText(Path.Combine(data,"ui-polish-result.txt"),"Both play-mode pictures use matching rounded 155px frames that preserve proportions. Reveal feedback animates and reduced motion disables it immediately."); Shutdown(0); return;
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
            if (args.Contains("--verify-fast-start")) {
                if (data is null || !context.IsIsolated) throw new ArgumentException("Fast start verification requires an isolated folder.");
                var playerIndex = Array.IndexOf(args,"--player");
                var player = playerIndex >= 0 && playerIndex + 1 < args.Length ? args[playerIndex+1] : "pjamtest";
                context.SelectLauncher("name"); context.UsePlayerName(player);
                if (!args.Contains("--no-join")) context.SetJoinServer(true);
                if (args.Contains("--reuse-real")) {
                    var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                    var reuse = new List<string> { Path.Combine(appData,".minecraft") };
                    try { reuse.Add(SkLauncherProfiles.DataRoot(Path.Combine(appData,".sklauncher"))); } catch (IOException) { }
                    context.ExtraReuseRoots = reuse;
                }
                var clock = System.Diagnostics.Stopwatch.StartNew(); var log = new System.Text.StringBuilder();
                if (!context.CanPlay) { await context.InstallAsync(null, CancellationToken.None); log.AppendLine($"Pack {context.Manifest.Version} installed in {clock.Elapsed:m\\:ss}"); }
                clock.Restart();
                var plan = await context.PrepareFastStartAsync(null, CancellationToken.None);
                log.AppendLine($"Java, Minecraft and Fabric ready in {clock.Elapsed:m\\:ss} (downloaded {context.LastDownloads?.Downloaded}, reused from other launchers {context.LastDownloads?.Reused})");
                clock.Restart();
                var again = await context.PrepareFastStartAsync(null, CancellationToken.None);
                log.AppendLine($"Second check (every later Play) took {clock.Elapsed.TotalSeconds:0.0} s, downloaded {context.LastDownloads?.Downloaded}");
                log.AppendLine("Java: " + plan.Java);
                if (!args.Contains("--no-game")) {
                    context.AllowTestStart = true; clock.Restart();
                    var session = context.StartGame(again); int? mods = null;
                    while (!session.HasWindow() && !session.Exited.IsCompleted && clock.Elapsed < TimeSpan.FromMinutes(10)) { mods ??= session.ModCount; await Task.Delay(250); }
                    if (session.Exited.IsCompleted) throw new IOException("Minecraft closed while starting with exit code " + await session.Exited + ". See " + context.OutputLog);
                    if (!session.HasWindow()) throw new IOException("No Minecraft window after 10 minutes. See " + context.OutputLog);
                    log.AppendLine($"Minecraft window open {clock.Elapsed:m\\:ss} after start ({session.ModCount ?? mods} mods). The game stays open: join Holy Lois as {player} and close it when done.");
                }
                File.WriteAllText(Path.Combine(data,"fast-start-result.txt"), log.ToString()); Shutdown(0); return;
            }
            if (args.Contains("--render-start-preview")) {
                if (data is null) throw new ArgumentException("Rendering requires an isolated folder.");
                context.SelectLauncher("name"); context.UsePlayerName("pjamtest");
                var start = new LaunchWindow("pjamtest", true); start.SetStep("assets"); start.SetBytes(212_000_000, 483_000_000); Render(start,data,"start-progress.png",520,470);
                start = new LaunchWindow("pjamtest", true); start.SetStep("mods", string.Format(Localize.Text("ModsCount"), 214)); Render(start,data,"start-mods.png",520,470);
                start = new LaunchWindow("pjamtest", false); start.SetStep("java");
                start.Fail(Localize.Text("StartFailed"), string.Format(Localize.Text("StartClosed"), 1) + "\n\n" + Localize.Text("StartFailedHelp"), [(Localize.Text("ReportCopy"), () => null, true), ("Discord", () => null, false)]);
                Render(start,data,"start-failed.png",520,560);
                Render(new NameWindow(context),data,"name-window.png",540,560);
                await LauncherDiscovery.WarmAsync();
                Render(new MainWindow(context),data,"main-fast-start.png",1060,748);
                context.SelectLauncher("official"); Render(new MainWindow(context),data,"main-account.png",1060,748);
                context.SelectLauncher("name"); Render(new SettingsWindow(context),data,"settings-fast-start.png",610,1100);
                Shutdown(0); return;
            }
            if (args.Contains("--render-settings-preview")) { Render(new SettingsWindow(context),data!,"settings-preview.png",610,900); Shutdown(0); return; }
            if (args.Contains("--render-update-preview")) {
                if (data is null) throw new ArgumentException("Rendering requires an isolated folder.");
                var update = new AppUpdateWindow(); update.SetTransfer("Downloading launcher " + AppUpdates.RunningVersion.ToString(3) + "...",37000000,67000000);
                Render(update,data,"update-preview.png",520,280); Shutdown(0); return;
            }
            if (args.Contains("--render-setup-preview")) {
                if (data is null) throw new ArgumentException("Rendering requires an isolated folder.");
                foreach (var language in new[] { "en", "ru", "lv" }) for (var step = 0; step < 3; step++) { context.SetLanguage(language); if (step == 1) context.SelectLauncher("name"); var setup = new SetupWindow(context, data); setup.GoTo(step); Render(setup,data,$"setup-{language}-{step + 1}.png",780,780); }
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
            if (args.Contains("--render-history-preview")) {
                if (data is null) throw new ArgumentException("History rendering requires an isolated --data-dir.");
                var window = new MainWindow(context); window.HistoryExpander.IsExpanded = true; Render(window,data,"launcher-history.png",1060,2600);
                Shutdown(0); return;
            }
            if (args.Contains("--render-ready-preview")) {
                if (data is null || !context.CanPlay) throw new ArgumentException("Ready rendering requires an installed isolated fixture.");
                if (context.Settings.Launcher != "official" && context.PlayerName is null) context.UsePlayerName("pjamtest");
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
            if (!development) ShowRequest.Listen(() => Dispatcher.BeginInvoke(() => { if (MainWindow is { } window) { window.Show(); if (window.WindowState == WindowState.Minimized) window.WindowState = WindowState.Normal; window.Activate(); } }));
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
