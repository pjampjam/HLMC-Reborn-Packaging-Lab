using HolyLois.Core;
using Microsoft.Win32;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
namespace HolyLois.App;
public partial class MainWindow : ThemedWindow
{
    private readonly ClientContext context;
    private CancellationTokenSource? cancellation;
    private readonly DispatcherTimer updateTimer = new() { Interval = TimeSpan.FromMinutes(2) };
    private bool checking;
    private readonly Stopwatch operationTime = new();
    private readonly DispatcherTimer progressClock = new() { Interval = TimeSpan.FromSeconds(1) };
    private string progressDetail = "";
    private readonly DispatcherTimer statusTimer = new() { Interval = TimeSpan.FromSeconds(30) };
    private DateTime lastOnline = DateTime.MinValue;
    private bool pinging;
    private bool starting;
    private string? nameNote;
    public MainWindow(ClientContext context)
    {
        this.context = context; InitializeComponent();
        progressClock.Tick += (_,_) => ProgressDetails.Text = progressDetail + "  -  " + T("WorkingTime") + " " + operationTime.Elapsed.ToString(@"m\:ss"); Localize.Apply(this, context.Settings.Language);
        LanguageChoice.SelectedIndex = context.Settings.Language == "en" ? 1 : context.Settings.Language == "lv" ? 2 : 0;
        Closing += OnClosing; 
        UpdateStatus.Text = T("AutoCheck"); StatusText.Text = AppUpdates.Notice ?? "";
        // SKlauncher players move to fast start with the name they already play as; one note says how to go back.
        try
        {
            if (context.Settings.Launcher == "sk" && context.UsesFastStart && context.PlayerName is null && context.SuggestedPlayerName() is { } known)
            { context.UsePlayerName(known); StatusText.Text = string.Format(T("FastStartIntro"), known); }
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or UnauthorizedAccessException) { }
        NewsPicture.Background = new ImageBrush(LauncherArt.Pick("cow", !context.IsIsolated)) { Stretch = Stretch.UniformToFill, AlignmentX = AlignmentX.Center };
        // Short windows get a smaller logo so the server block never crowds the action feedback.
        SizeChanged += (_, _) => Logo.Width = Logo.Height = ActualHeight < 740 ? 112 : 140;
        Refresh();
        Loaded += async (_, _) => { await LauncherDiscovery.WarmAsync(); if (IsClosed || Dispatcher.HasShutdownStarted) return; Refresh(); if (!context.IsIsolated) { await Task.Run(context.CleanInstalledDownloads); if (IsClosed || Dispatcher.HasShutdownStarted) return; await CheckUpdates(); if (!IsClosed) updateTimer.Start(); await PingServer(); if (!IsClosed) statusTimer.Start(); } };
        updateTimer.Tick += async (_, _) => await CheckUpdates(); statusTimer.Tick += async (_, _) => await PingServer();
        Closed += (_, _) => { updateTimer.Stop(); progressClock.Stop(); statusTimer.Stop(); };
    }
    private static string T(string key) => Localize.Text(key);
    private async Task PingServer()
    {
        if (pinging || IsClosed) return;
        pinging = true;
        try
        {
            var ping = await ServerStatus.PingAsync(context.Manifest.Server, CancellationToken.None);
            if (IsClosed || Dispatcher.HasShutdownStarted) return;
            if (ping is not null) lastOnline = DateTime.UtcNow;
            // A short outage right after the server was seen online is nearly always a restart.
            var restarting = ping is null && DateTime.UtcNow - lastOnline < TimeSpan.FromMinutes(4);
            var brush = (Brush)FindResource(ping is not null ? "Success" : restarting ? "Gold" : "Danger");
            ServerDot.Fill = brush; AddressText.Foreground = brush;
            ServerStateText.Text = ping is not null ? string.Format(T("ServerOnline"), ping.Online, ping.Max) : T(restarting ? "ServerRestarting" : "ServerOffline");
            ServerBadge.ToolTip = ping is null ? T(restarting ? "ServerRestartingHint" : "ServerOfflineHint")
                : ping.Players.Length > 0 ? T("ServerWho") + "\n" + string.Join("\n", ping.Players) : T("ServerNobody");
        }
        finally { pinging = false; }
    }
    /// <summary>What the left-rail button does right now: one action, chosen from the real state.</summary>
    private enum Step { Install, Update, Repair, Play, Blocked }
    private Step CurrentStep()
    {
        var receipt = Directory.Exists(context.Instance) ? context.Receipt : null;
        if (receipt is null) return Step.Install;
        if (receipt.Version != context.Manifest.Version) return Step.Update;
        if (!context.CanPlay) return Step.Repair;
        return PlayReady() ? Step.Play : Step.Blocked;
    }
    private void Refresh()
    {
        // A settings action can shut down the app before its modal dialog returns.
        if (IsClosed || Dispatcher.HasShutdownStarted || Dispatcher.HasShutdownFinished) return;
        var sk = context.Settings.Launcher == "sk";
        var named = context.Settings.Launcher != "official"; var fast = context.UsesFastStart;
        var player = context.PlayerName;
        AppVersion.Text = T("App") + " " + AppUpdates.RunningVersion.ToString(3);
        WebsiteButton.Visibility = WebsiteUrl.Length > 0 ? Visibility.Visible : Visibility.Collapsed; LinkGrid.Columns = WebsiteUrl.Length > 0 ? 2 : 1; DiscordButton.Margin = new Thickness(0, 0, WebsiteUrl.Length > 0 ? 3 : 0, 0);
        var megabytes = context.Manifest.Files.Sum(f => f.Size) / 1048576;
        HeaderTitle.Text = named && player is not null ? string.Format(T("WelcomeBack"), player) : T("PlayTitle");
        ReleaseLabel.Text = $"Minecraft 26.3  -  {T("Version")} {context.Manifest.Version}  -  {context.Manifest.Files.Count(f => f.Path.StartsWith("mods/"))} {T("Mods")}  -  {megabytes:N0} MB";

        // Left rail: gold for a step that still has to happen, green only when Play will work.
        var step = CurrentStep(); var busy = cancellation is not null;
        var actionable = step != Step.Blocked && !busy && !starting;
        PrimaryAction.IsEnabled = actionable;
        PrimaryAction.Style = (Style)FindResource(step == Step.Play ? "PlayButtonStyle" : step == Step.Blocked ? (object)typeof(Button) : "PrimaryButton");
        var onAction = (Brush)FindResource(step == Step.Play ? "OnGreen" : step == Step.Blocked ? "Muted" : "OnGold");
        PrimaryLabel.Foreground = PrimarySub.Foreground = onAction;
        PrimaryLabel.Text = T(step switch { Step.Install => "BigInstall", Step.Update => "BigUpdate", Step.Repair => "BigRepair", _ => "Play" });
        PrimarySub.Text = step is Step.Install or Step.Update ? $"{T("Version")} {context.Manifest.Version}  -  {megabytes:N0} MB"
            : step == Step.Repair ? T("RepairSub") : fast ? T("FastStart") : sk ? "SKlauncher" : "Minecraft Launcher";
        PrimaryHint.Text = busy ? T("WorkingHint") : step == Step.Install ? string.Format(T("StateInstall"), megabytes) : T(step switch
        {
            Step.Update => "UpdateHint", Step.Repair => "RepairHint",
            Step.Play => fast ? "PlayFastHint" : "PlayReadyHint",
            _ => fast ? "PlayNameHint" : "PlayMissingLauncher",
        });

        VerifyButton.Visibility = step is Step.Play or Step.Blocked ? Visibility.Visible : Visibility.Collapsed;
        StatusText.Visibility = string.IsNullOrEmpty(StatusText.Text) ? Visibility.Collapsed : Visibility.Visible;

        // Account card: who plays and how; the two ways to play open only from "Change how you play" (or when nothing fits yet).
        AccountEyebrow.Text = T(named ? "PlayingAs" : "YourAccount");
        AccountTitle.Text = named ? player ?? T("NameNotSet") : T("AccountCard");
        AccountMode.Text = named ? T("NameCard") + "  -  " + (fast ? T("FastStart") : "SKlauncher") : T("AccountModeHint");
        ChangeNameButton.Visibility = named && player is not null ? Visibility.Visible : Visibility.Collapsed;
        NameEntry.Visibility = named && player is null ? Visibility.Visible : Visibility.Collapsed;
        if (player is null && NameBox.Text.Length == 0) NameBox.Text = context.SuggestedPlayerName() ?? "";
        if (nameNote is null) { NameNote.Text = sk ? T("SkFolderNote") : ""; NameNote.Visibility = sk ? Visibility.Visible : Visibility.Collapsed; NameNote.Foreground = (Brush)FindResource("Muted"); }
        ModeChooser.Visibility = choosing ? Visibility.Visible : Visibility.Collapsed;
        ModeButton.Content = T(choosing ? "ModeDone" : "ModeChange");
        OfficialSelected.Visibility = named ? Visibility.Hidden : Visibility.Visible; SkSelected.Visibility = named ? Visibility.Visible : Visibility.Hidden;
        var neutral = (Brush)FindResource("Line"); var accent = (Brush)FindResource("Gold");
        OfficialCard.BorderBrush = named ? neutral : accent; SkCard.BorderBrush = named ? accent : neutral;
        var tint = (Brush)FindResource("GoldSoft"); var plain = (Brush)FindResource("Control");
        OfficialCard.Background = named ? plain : tint; SkCard.Background = named ? tint : plain;
        LinkSkButton.Visibility = Visibility.Collapsed; RebuildSkButton.Visibility = Visibility.Collapsed;
        LauncherHint.Text = fast ? T(context.Settings.JoinServer ? "FastHintJoin" : "FastHintTitle")
            : T(sk ? context.CanPlay ? "SkLinked" : context.RecoveredDeletedInstance ? "SkMissing" : "SkFirst" : context.Settings.JoinServer ? "OfficialHintJoin" : "OfficialHint");
        var detected = fast ? null : context.DetectLauncher();
        LauncherPanel.Visibility = fast ? Visibility.Collapsed : Visibility.Visible;
        // The hint lines up with the account text only when nothing full-width sits between them.
        LauncherHint.Margin = new Thickness(LauncherPanel.Visibility == Visibility.Visible || NameEntry.Visibility == Visibility.Visible ? 0 : 62, 8, 0, 0);
        LauncherActions.Visibility = !fast && detected is null ? Visibility.Visible : Visibility.Collapsed;
        DetectionPanel.BorderBrush = detected is null ? (Brush)FindResource("Line") : (Brush)FindResource("Success");
        DetectionHint.Text = !LauncherDiscovery.Ready ? T("Detecting") : detected is null ? T("NotDetected") : !sk && detected.StartsWith("shell:") ? T("DetectedStore") : T("Detected") + ": " + (sk ? "SKlauncher" : "Minecraft Launcher");
        RefreshHead(named ? player : null);
        QuickKeys.Child = Quick("QuickKeysTitle", "QuickKeysBody"); QuickCommands.Child = Quick("QuickCommandsTitle", "QuickCommandsBody"); QuickLand.Child = Quick("QuickLandTitle", "QuickLandBody");
        RefreshNews(step == Step.Update);
        // One card per release: gold version, summary, then one bullet per change so long notes stay readable.
        HistoryPanel.Children.Clear();
        foreach (var item in context.Manifest.History ?? [])
        {
            var card = new StackPanel();
            var head = new TextBlock { FontSize = 14, FontWeight = FontWeights.SemiBold };
            head.Inlines.Add(new System.Windows.Documents.Run(item.Version) { Foreground = (Brush)FindResource("Gold") });
            head.Inlines.Add(new System.Windows.Documents.Run("   " + item.Date) { Foreground = (Brush)FindResource("Muted"), FontWeight = FontWeights.Normal, FontSize = 12 });
            card.Children.Add(head);
            card.Children.Add(new TextBlock { Text = item.Summary, FontSize = 13, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 4, 0, 0) });
            foreach (var (name, lines) in new[] { ("Added", item.Added), ("Updated", item.Updated), ("Removed", item.Removed) })
            {
                if (lines.Length == 0) continue;
                card.Children.Add(new TextBlock { Text = T(name), FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = (Brush)FindResource("Muted"), Margin = new Thickness(0, 9, 0, 1) });
                foreach (var line in lines) card.Children.Add(Bullet(line));
            }
            HistoryPanel.Children.Add(new Border { Child = card, Background = (Brush)FindResource("Canvas"), BorderBrush = (Brush)FindResource("Line"), BorderThickness = new Thickness(1),
                CornerRadius = (CornerRadius)FindResource("Radius"), Padding = new Thickness(14, 12, 14, 12), Margin = new Thickness(0, 0, 0, 10) });
        }
        if (HistoryPanel.Children.Count == 0) HistoryPanel.Children.Add(new TextBlock { Text = T("NoHistory") });
    }
    private bool PlayReady() => context.CanPlay && (context.UsesFastStart ? context.PlayerName is not null : context.DetectLauncher() is not null);
    private bool choosing;
    private string? headFor;
    /// <summary>The player's selected skin head from the public stats feed; the Holy Lois mark until it loads, offline or for unknown names.</summary>
    private async void RefreshHead(string? player)
    {
        if (player == headFor) return;
        headFor = player; HeadImage.Source = null; HeadFallback.Visibility = Visibility.Visible;
        if (player is null || context.IsIsolated) return;
        var head = await LauncherArt.HeadAsync(player, CancellationToken.None);
        if (IsClosed || headFor != player || head is null) return;
        HeadImage.Source = head; HeadFallback.Visibility = Visibility.Collapsed;
    }
    // The newest release near the top: summary and three bullets; gold while that update still waits to be installed.
    private void RefreshNews(bool available)
    {
        var latest = context.Manifest.History?.FirstOrDefault();
        NewsCard.Visibility = latest is null ? Visibility.Collapsed : Visibility.Visible;
        if (latest is null) return;
        NewsEyebrow.Text = (available ? string.Format(T("UpdateReady"), context.Manifest.Version) : T("LatestNews") + "  -  " + latest.Version) + "  -  " + latest.Date;
        NewsTitle.Text = latest.Summary;
        NewsCard.BorderBrush = (Brush)FindResource(available ? "Gold" : "Line");
        NewsCard.Background = (Brush)FindResource(available ? "GoldSoft" : "SurfaceRaised");
        NewsHighlights.Children.Clear();
        foreach (var line in latest.Added.Concat(latest.Updated).Take(3)) NewsHighlights.Children.Add(Bullet(line));
    }
    /// <summary>A quick-reference card: a title, then "KEY|what it does" lines with the key in a gold badge.</summary>
    private StackPanel Quick(string title, string body)
    {
        var card = new StackPanel();
        card.Children.Add(new TextBlock { Text = T(title), FontSize = 13, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 0, 0, 6) });
        foreach (var line in T(body).Split((char)10))
        {
            var parts = line.Split('|', 2);
            var row = new Grid { Margin = new Thickness(0, 4, 0, 0) };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto }); row.ColumnDefinitions.Add(new ColumnDefinition());
            var badge = new Border { Background = (Brush)FindResource("GoldSoft"), CornerRadius = new CornerRadius(4), Padding = new Thickness(6, 1, 6, 1), Margin = new Thickness(0, 0, 8, 0), VerticalAlignment = VerticalAlignment.Top,
                Child = new TextBlock { Text = parts[0], FontFamily = new FontFamily("Consolas"), FontSize = 12, Foreground = (Brush)FindResource("Gold") } };
            var text = new TextBlock { Text = parts.Length > 1 ? parts[1] : "", FontSize = 12, Foreground = (Brush)FindResource("Muted"), TextWrapping = TextWrapping.Wrap, VerticalAlignment = VerticalAlignment.Center };
            Grid.SetColumn(text, 1); row.Children.Add(badge); row.Children.Add(text); card.Children.Add(row);
        }
        return card;
    }
    private void Guide_Click(object sender, RoutedEventArgs e) => ClientContext.OpenUrl("https://holylois.com/guide");
    private void AllChanges_Click(object sender, RoutedEventArgs e) { HistoryExpander.IsExpanded = true; HistoryExpander.BringIntoView(); }
    private void Mode_Click(object sender, RoutedEventArgs e) { choosing = !choosing; Refresh(); }
    private async void Primary_Click(object sender, RoutedEventArgs e)
    {
        if (CurrentStep() == Step.Play) await PlayAsync();
        else await RunInstallAsync();
    }
    private async void Verify_Click(object sender, RoutedEventArgs e) => await RunInstallAsync();
    private Grid Bullet(string text)
    {
        var row = new Grid { Margin = new Thickness(0, 3, 0, 0) };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(16) }); row.ColumnDefinitions.Add(new ColumnDefinition());
        row.Children.Add(new TextBlock { Text = "•", FontSize = 12, Foreground = (Brush)FindResource("Gold") });
        var body = new TextBlock { Text = text, FontSize = 12 }; Grid.SetColumn(body, 1); row.Children.Add(body);
        return row;
    }
    private void Language_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (context is null || LanguageChoice.SelectedItem is not ComboBoxItem item) return;
        var language = (string)item.Tag; context.SetLanguage(language); Localize.Apply(this, language);
        if (HistoryPanel is not null) { Refresh(); UpdateStatus.Text = T("AutoCheck"); StatusText.Text = ""; }
    }
    private void Official_Click(object sender, RoutedEventArgs e) { context.SelectLauncher("official"); choosing = false; Refresh(); }
    // An existing SKlauncher folder stays in use; everyone else gets the app's own game folder.
    private void Sk_Click(object sender, RoutedEventArgs e) { if (context.Settings.Launcher != "sk") context.SelectLauncher("name"); choosing = false; Refresh(); if (context.PlayerName is null) NameBox.Focus(); }
    private async void CheckUpdates_Click(object sender, RoutedEventArgs e) { await LauncherDiscovery.WarmAsync(); await CheckUpdates(); }
    private async Task CheckUpdates()
    {
        if (checking || cancellation is not null) return;
        checking = true; CheckUpdatesButton.IsEnabled = false; UpdateStatus.Text = T("Checking");
        try
        {
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(25)); var changed = await context.CheckUpdatesAsync(timeout.Token);
            Refresh(); UpdateStatus.Text = T(changed || context.Receipt is { } receipt && receipt.Version != context.Manifest.Version ? "NewPack" : "Latest");
        }
        catch (Exception ex) when (ex is System.Net.Http.HttpRequestException or OperationCanceledException) { UpdateStatus.Text = T("OfflineCheck"); }
        catch { UpdateStatus.Text = T("BadCheck"); }
        finally { checking = false; CheckUpdatesButton.IsEnabled = cancellation is null; }
    }
    public Task VerifyInstallAsync() => RunInstallAsync(false);
    private async Task RunInstallAsync(bool checkOnline = true)
    {
        cancellation = new(); SetBusy(true);
        try
        {
            if (checking) throw new IOException("Wait for the release check to finish.");
            if (checkOnline && !context.IsIsolated)
            {
                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellation.Token); timeout.CancelAfter(TimeSpan.FromSeconds(25));
                try { await context.CheckUpdatesAsync(timeout.Token); }
                catch (System.Net.Http.HttpRequestException) { UpdateStatus.Text = T("OfflineCheck"); }
                catch (OperationCanceledException) when (!cancellation.IsCancellationRequested) { UpdateStatus.Text = T("OfflineCheck"); }
            }
            if (!context.IsIsolated && ClientContext.IsGameOrLauncherRunning())
            {
                if (!AppDialog.Show(this, T("CloseForUpdate"), T("CloseForUpdateInfo"), T("Agree"))) throw new OperationCanceledException();
                StatusText.Text = T("Closing");
                await Task.Run(ClientContext.CloseGameAndLauncher, cancellation.Token);
            }
            var feedback = new TransferFeedback(); var finished = false;
            var installProgress = new Progress<InstallProgress>(p => {
                if (finished || cancellation is null) return;
                var preparing = p.Message.StartsWith("Preparing") || p.Message.StartsWith("Pack ") || p.Message.StartsWith("Holy Lois");
                StatusText.Text = preparing ? T("Preparing") : T("Progress") + $" {p.CompletedFiles}/{p.TotalFiles}";
                Progress.IsIndeterminate = preparing;
                Progress.Value = preparing ? 94 : p.TotalBytes == 0 ? 0 : Math.Max(Progress.Value,90.0 * Math.Clamp(p.CompletedBytes,0,p.TotalBytes) / p.TotalBytes);
                progressDetail = preparing ? T("Finishing") : feedback.Describe(p.CompletedBytes,p.TotalBytes); ProgressDetails.Text = progressDetail;
            });
            await Task.Run(() => context.InstallAsync(installProgress, cancellation.Token), cancellation.Token);
            finished = true; StatusText.Text = T("Installed"); Progress.IsIndeterminate = false; Progress.Value = 100; Progress.Foreground = (Brush)FindResource("Gold"); ProgressDetails.Text = T("Ready");
        }
        catch (OperationCanceledException) { StatusText.Text = T("Cancelled"); Progress.Foreground = (Brush)FindResource("Muted"); ProgressDetails.Text = T("Cancelled"); }
        catch (Exception ex) { StatusText.Text = Localize.Error(ex); Progress.Foreground = (Brush)FindResource("Danger"); ProgressDetails.Text = T("Error"); }
        finally { cancellation.Dispose(); cancellation = null; SetBusy(false); Refresh(); }
    }
    private void SetBusy(bool busy)
    {
        Progress.IsIndeterminate = busy;
        if (busy) { operationTime.Restart(); progressClock.Start(); progressDetail = T("Checking"); Progress.Value = 0; Progress.Foreground = (Brush)FindResource("ActionGreen"); WorkPanel.Visibility = Visibility.Visible; ProgressDetails.Text = T("Checking"); }
        else { progressClock.Stop(); operationTime.Stop(); }
        SettingsButton.IsEnabled = !busy; LauncherActions.IsEnabled = !busy;
        LinkSkButton.IsEnabled = RebuildSkButton.IsEnabled = VerifyButton.IsEnabled = OfficialCard.IsEnabled = SkCard.IsEnabled = ModeButton.IsEnabled = ChangeNameButton.IsEnabled = LanguageChoice.IsEnabled = !busy;
        CheckUpdatesButton.IsEnabled = !busy && !checking; PrimaryAction.IsEnabled = !busy && !starting && CurrentStep() != Step.Blocked; NameSaveButton.IsEnabled = !busy;
        CancelButton.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
        StatusText.Visibility = Visibility.Visible;
        PrimaryHint.Visibility = busy ? Visibility.Collapsed : Visibility.Visible;
    }
    private void Cancel_Click(object sender, RoutedEventArgs e) => cancellation?.Cancel();
    private async Task PlayAsync()
    {
        if (context.UsesFastStart) { await FastStartAsync(); return; }
        try
        {
            var report = context.Guard(); var note = "";
            if (report.Moved.Length > 0) note += string.Format(T("GuardMoved"), report.Moved.Length) + " ";
            if (report.PacksRestored) note += T("GuardPacks") + " ";
            if (report.Damaged.Length > 0)
            {
                await RunInstallAsync(false);
                if (context.Guard().Damaged.Length > 0) { StatusText.Text = T("GuardFailed"); return; }
                note += T("GuardRepaired") + " ";
            }
            context.OpenLauncher();
            StatusText.Text = note + T(context.Settings.Launcher == "sk" ? "SkLinked" : context.Settings.JoinServer ? "OfficialHintJoin" : "OfficialHint");
        }
        catch (Exception ex) { StatusText.Text = Localize.Error(ex); }
    }
    private async void Locate_Click(object sender, RoutedEventArgs e)
    {
        if (cancellation is not null) return;
        await LauncherDiscovery.WarmAsync(); Refresh();
        if (context.DetectLauncher() is not null) { StatusText.Text = DetectionHint.Text; return; }
        var dialog = new OpenFileDialog { Title = T("ChooseExe"), Filter = "Launcher (*.exe;*.lnk)|*.exe;*.lnk", CheckFileExists = true };
        if (dialog.ShowDialog(this) == true) { context.SetLauncher(dialog.FileName); Refresh(); StatusText.Text = T("LauncherSaved"); }
    }
    private void LinkSk_Click(object sender, RoutedEventArgs e)
    {
        if (cancellation is not null) return;
        var dialog = new OpenFolderDialog { Title = T("ChooseFolder"), InitialDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".sklauncher", "instances") };
        if (dialog.ShowDialog(this) == true) try { context.LinkSkInstance(dialog.FolderName); Refresh(); StatusText.Text = T("Linked"); } catch (Exception ex) { StatusText.Text = Localize.Error(ex); }
    }
    private void RebuildSk_Click(object sender, RoutedEventArgs e)
    { if (cancellation is not null) return; context.RebuildSkImport(); Refresh(); StatusText.Text = T("SkMissing"); }
    private void GetLauncher_Click(object sender, RoutedEventArgs e) { ClientContext.OpenUrl(context.Settings.Launcher == "sk" ? "https://next.skmedix.pl/downloads" : "https://www.minecraft.net/download"); StatusText.Text = T("BrowserDownload"); }
    private void Settings_Click(object sender, RoutedEventArgs e) { if (cancellation is null) new SettingsWindow(context).ShowModal(this); Refresh(); }
    private void GameFolder_Click(object sender, RoutedEventArgs e) { Directory.CreateDirectory(context.Instance); Process.Start(new ProcessStartInfo("explorer.exe") { UseShellExecute = true, ArgumentList = { context.Instance } }); }
    // The website button appears once the site has its own address.
    private const string DiscordUrl = "https://discord.gg/FzBJSZwY2c", WebsiteUrl = "https://holylois.com";
    private void Discord_Click(object sender, RoutedEventArgs e) => Process.Start(new ProcessStartInfo(DiscordUrl) { UseShellExecute = true });
    private void Website_Click(object sender, RoutedEventArgs e) { if (WebsiteUrl.Length > 0) Process.Start(new ProcessStartInfo(WebsiteUrl) { UseShellExecute = true }); }
    private void CopyAddress_Click(object sender, RoutedEventArgs e) { Clipboard.SetText(ServerAddress.Public); StatusText.Text = T("Copied"); }
    private void DisableShaders_Click(object sender, RoutedEventArgs e) { if (cancellation is not null) return; try { context.DisableShaders(); StatusText.Text = T("Disabled"); } catch (Exception ex) { StatusText.Text = Localize.Error(ex); } }
    private GameSession? game;
    /// <summary>Fast start: check the pack, get Java and Minecraft ready, start the game, then step aside once its window is open.</summary>
    private async Task FastStartAsync()
    {
        if (starting || cancellation is not null) return;
        if (game is not null) { StatusText.Text = T("GameRunning"); return; }
        starting = true; Refresh();
        var window = new LaunchWindow(context.PlayerName ?? "", context.Settings.JoinServer) { Owner = this };
        window.Show();
        GameSession? session = null;
        var token = window.Cancellation.Token;
        try
        {
            window.SetStep("check");
            var report = context.Guard();
            if (report.Damaged.Length > 0)
            {
                await RunInstallAsync(false);
                if (context.Guard().Damaged.Length > 0) throw new IOException(T("GuardFailed"));
            }
            token.ThrowIfCancellationRequested();
            var progress = new Progress<GameProgress>(p => { if (!window.IsClosed) { window.SetStep(p.Step); window.SetBytes(p.DoneBytes, p.TotalBytes); } });
            var plan = await Task.Run(() => context.PrepareFastStartAsync(progress, token), token);
            window.SetStep("start"); window.Waiting();
            session = context.StartGame(plan);
            while (!session.HasWindow())
            {
                if (session.Exited.IsCompleted)
                {
                    var code = await session.Exited;
                    context.SaveStart((context.LastStart ?? new StartRecord("fast start", context.Manifest.Version, AppUpdates.RunningVersion.ToString(3), DateTimeOffset.UtcNow)) with { ExitCode = code, Ended = DateTimeOffset.UtcNow });
                    throw new GameStartException(string.Format(T("StartClosed"), code));
                }
                if (token.IsCancellationRequested) { try { session.Process.Kill(true); } catch (Exception ex) when (ex is InvalidOperationException or System.ComponentModel.Win32Exception) { } throw new OperationCanceledException(token); }
                if (session.ModCount is int mods) window.SetStep("mods", string.Format(T("ModsCount"), mods));
                await Task.Delay(250);
            }
            game = session; session = null;
            window.Finish();
            StatusText.Text = T("GameRunning");
            Hide();
            _ = WatchGameAsync(game);
        }
        catch (OperationCanceledException) { if (!window.IsClosed) window.Finish(); StatusText.Text = T("Cancelled"); }
        catch (Exception ex)
        {
            var reason = ex is GameStartException ? ex.Message : Localize.Error(ex);
            var record = context.LastStart;
            if (ex is not GameStartException) context.SaveStart(new StartRecord("fast start", context.Manifest.Version, AppUpdates.RunningVersion.ToString(3), record?.Started ?? DateTimeOffset.UtcNow, Error: ex.Message));
            StatusText.Text = reason;
            var actions = new List<(string, Func<string?>, bool)>();
            if (context.Settings.Launcher == "sk" && context.DetectLauncher() is not null)
                actions.Add((T("OpenLauncherInstead"), () => { context.OpenLauncher(); window.Close(); return null; }, true));
            actions.Add((T("ReportCopy"), () => ReportActions.Copy(context), actions.Count == 0));
            actions.Add(("Discord", () => { ReportActions.OpenDiscord(); return null; }, false));
            if (!window.IsClosed) window.Fail(T("StartFailed"), reason + "\n\n" + T("StartFailedHelp"), actions);
        }
        finally { session?.Dispose(); starting = false; Refresh(); }
    }
    private sealed class GameStartException(string message) : Exception(message);
    /// <summary>After the game closes: quietly exit, or come back with the crash window when it ended with an error.</summary>
    private async Task WatchGameAsync(GameSession session)
    {
        var code = await session.Exited;
        var record = context.LastStart;
        if (record is not null) context.SaveStart(record with { ExitCode = code, Ended = DateTimeOffset.UtcNow });
        var crashed = code != 0 || record is not null && GameReports.NewestCrash(context.Instance, record.Started) is not null;
        session.Dispose(); game = null;
        if (IsClosed || Dispatcher.HasShutdownStarted) return;
        if (!crashed && !IsVisible) { Application.Current.Shutdown(); return; }
        Show(); WindowState = WindowState.Normal; Activate(); Refresh();
        StatusText.Text = crashed ? string.Format(T("CrashInfoShort"), code) : T("GameClosed");
        if (crashed) ReportActions.ShowCrash(this, context, code);
    }
    private async void SaveName_Click(object sender, RoutedEventArgs e) => await SaveNameAsync();
    private async void NameBox_KeyDown(object sender, System.Windows.Input.KeyEventArgs e) { if (e.Key == System.Windows.Input.Key.Enter) { e.Handled = true; await SaveNameAsync(); } }
    private Task SaveNameAsync()
    {
        var name = NameBox.Text.Trim();
        if (!PlayerNames.IsValid(name)) { ShowNameNote(T("NameInvalid"), true); return Task.CompletedTask; }
        NameSaveButton.IsEnabled = false;
        try
        {
            context.UsePlayerName(name); nameNote = null;
            StatusText.Text = string.Format(T("NameSaved"), context.PlayerName);
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or UnauthorizedAccessException) { ShowNameNote(ex.Message, true); }
        finally { NameSaveButton.IsEnabled = cancellation is null; Refresh(); }
        return Task.CompletedTask;
    }
    private void ShowNameNote(string text, bool error)
    {
        nameNote = text; NameNote.Text = text; NameNote.Visibility = Visibility.Visible;
        NameNote.Foreground = (Brush)FindResource(error ? "Danger" : "Muted");
    }
    private void ChangeName_Click(object sender, RoutedEventArgs e)
    {
        if (cancellation is not null || starting) return;
        if (new NameWindow(context).ShowModalResult(this)) StatusText.Text = string.Format(T("NameSaved"), context.PlayerName);
        nameNote = null; Refresh();
    }
    private void OnClosing(object? sender, CancelEventArgs e) { if (cancellation is not null) { e.Cancel = true; cancellation.Cancel(); StatusText.Text = T("Cancelled"); } }
}
