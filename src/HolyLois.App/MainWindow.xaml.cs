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
        UpdateStatus.Text = T("AutoCheck"); StatusText.Text = AppUpdates.Notice ?? T("StartHint");
        // SKlauncher players move to fast start with the name they already play as; one note says how to go back.
        try
        {
            if (context.Settings.Launcher == "sk" && context.UsesFastStart && context.PlayerName is null && context.SuggestedPlayerName() is { } known)
            { context.UsePlayerName(known); StatusText.Text = string.Format(T("FastStartIntro"), known); }
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or UnauthorizedAccessException) { }
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
    private void Refresh()
    {
        // A settings action can shut down the app before its modal dialog returns.
        if (IsClosed || Dispatcher.HasShutdownStarted || Dispatcher.HasShutdownFinished) return;
        var sk = context.Settings.Launcher == "sk";
        AppVersion.Text = T("App") + " " + AppUpdates.RunningVersion.ToString(3) + "";
        WebsiteButton.Visibility = WebsiteUrl.Length > 0 ? Visibility.Visible : Visibility.Collapsed; LinkGrid.Columns = WebsiteUrl.Length > 0 ? 2 : 1; DiscordButton.Margin = new Thickness(0, 0, WebsiteUrl.Length > 0 ? 4 : 0, 0);
        ReleaseLabel.Text = "Minecraft 26.3 / Fabric 0.19.5 / " + T("Version") + " " + context.Manifest.Version;
        SizeLabel.Text = $"{context.Manifest.Files.Count(f => f.Path.StartsWith("mods/"))} {T("Mods")}  -  {context.Manifest.Files.Sum(f => f.Size) / 1048576:N0} MB";
        // Two ways to play: a bought account (its own launcher) or a player name (fast start; SKlauncher folders count as names).
        var named = context.Settings.Launcher != "official"; var fast = context.UsesFastStart;
        OfficialSelected.Visibility = named ? Visibility.Hidden : Visibility.Visible; SkSelected.Visibility = named ? Visibility.Visible : Visibility.Hidden;
        var neutral = (Brush)FindResource("Line"); var accent = (Brush)FindResource("Gold");
        OfficialCard.BorderBrush = named ? neutral : accent; SkCard.BorderBrush = named ? accent : neutral;
        // The chosen way gets a warm tint as well as the gold outline and "Selected" label.
        var tint = (Brush)FindResource("GoldSoft"); var plain = (Brush)FindResource("Control");
        OfficialCard.Background = named ? plain : tint; SkCard.Background = named ? tint : plain;
        LinkSkButton.Visibility = Visibility.Collapsed; RebuildSkButton.Visibility = Visibility.Collapsed;
        var player = context.PlayerName;
        NamePanel.Visibility = named ? Visibility.Visible : Visibility.Collapsed;
        NameShown.Visibility = player is null ? Visibility.Collapsed : Visibility.Visible; NameEntry.Visibility = player is null ? Visibility.Visible : Visibility.Collapsed;
        NameText.Text = player ?? "";
        if (player is null && NameBox.Text.Length == 0) NameBox.Text = context.SuggestedPlayerName() ?? "";
        if (nameNote is null) { NameNote.Text = sk ? T("SkFolderNote") : ""; NameNote.Visibility = sk ? Visibility.Visible : Visibility.Collapsed; NameNote.Foreground = (Brush)FindResource("Muted"); }
        LauncherHint.Text = fast ? T(context.Settings.JoinServer ? "FastHintJoin" : "FastHintTitle")
            : T(sk ? context.CanPlay ? "SkLinked" : context.RecoveredDeletedInstance ? "SkMissing" : "SkFirst" : "OfficialHint");
        var detected = fast ? null : context.DetectLauncher();
        DetectionPanel.Visibility = fast ? Visibility.Collapsed : Visibility.Visible;
        LauncherActions.Visibility = !fast && detected is null ? Visibility.Visible : Visibility.Collapsed;
        DetectionPanel.BorderBrush = detected is null ? (Brush)FindResource("Line") : (Brush)FindResource("Success");
        DetectionHint.Text = !LauncherDiscovery.Ready ? T("Detecting") : detected is null ? T("NotDetected") : !sk && detected.StartsWith("shell:") ? T("DetectedStore") : T("Detected") + ": " + (sk ? "SKlauncher" : "Minecraft Launcher");
        var receipt = Directory.Exists(context.Instance) ? context.Receipt : null;
        var available = receipt is not null && receipt.Version != context.Manifest.Version;
        PackStatus.Text = context.CanPlay ? T("Ready") : receipt is null ? T("NoPack") : T(available ? "NewPack" : "NeedsRepair");
        InstallLabel.Text = receipt is null ? T("Install") : available ? T("Update") : T("Verify");
        var packReady = context.CanPlay;
        var ready = PlayReady();
        PlayButton.IsEnabled = ready && cancellation is null && !starting;
        // Gold marks the next required step; green appears only when Play will work.
        InstallButton.Style = (Style)FindResource(packReady ? typeof(Button) : "PrimaryButton");
        InstallLabel.Foreground = (Brush)FindResource(packReady ? "Text" : "OnGold");
        PlayButton.Style = ready ? (Style)FindResource("PlayButtonStyle") : (Style)FindResource(typeof(Button));
        PlayLabel.Foreground = (Brush)FindResource(ready ? "OnGreen" : "Text");
        PlayLabel.Text = T("Play"); PlayLauncherLabel.Text = fast ? T("FastStart") : sk ? "SKlauncher" : "Minecraft Launcher";
        PlayHint.Text = T(ready ? fast ? "PlayFastHint" : "PlayReadyHint" : !context.CanPlay ? "PlayInstallHint" : fast ? "PlayNameHint" : "PlayMissingLauncher");
        PlayButton.Foreground = PlayLabel.Foreground; PlayLauncherLabel.Foreground = PlayLabel.Foreground; PlayButton.FontWeight = FontWeights.SemiBold;
        RefreshNews(available);
        // One card per release: gold version, summary, then one bullet per change so long notes stay readable.
        HistoryPanel.Children.Clear();
        foreach (var item in context.Manifest.History ?? [])
        {
            var card = new StackPanel();
            var head = new TextBlock { FontSize = 14, FontWeight = FontWeights.SemiBold };
            head.Inlines.Add(new System.Windows.Documents.Run(item.Version) { Foreground = (Brush)FindResource("Gold") });
            head.Inlines.Add(new System.Windows.Documents.Run("   " + item.Date) { Foreground = (Brush)FindResource("Muted"), FontWeight = FontWeights.Normal, FontSize = 12 });
            card.Children.Add(head);
            card.Children.Add(new TextBlock { Text = item.Summary, FontSize = 13, Margin = new Thickness(0, 4, 0, 0) });
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
    // The newest release at the top of the page; it turns gold with its own Update button while an update waits.
    private void RefreshNews(bool available)
    {
        var latest = context.Manifest.History?.FirstOrDefault();
        NewsCard.Visibility = latest is null ? Visibility.Collapsed : Visibility.Visible;
        if (latest is null) return;
        NewsEyebrow.Text = available ? latest.Date : T("LatestNews") + "  -  " + latest.Version + "  -  " + latest.Date;
        NewsTitle.Text = available ? string.Format(T("UpdateReady"), context.Manifest.Version) : latest.Summary;
        NewsCard.BorderBrush = (Brush)FindResource(available ? "Gold" : "Line");
        NewsCard.Background = (Brush)FindResource(available ? "GoldSoft" : "SurfaceRaised");
        NewsUpdateButton.Visibility = available ? Visibility.Visible : Visibility.Collapsed;
        NewsUpdateButton.IsEnabled = cancellation is null;
        NewsHighlights.Children.Clear();
        if (available) NewsHighlights.Children.Add(new TextBlock { Text = latest.Summary, FontSize = 13, Margin = new Thickness(0, 0, 0, 2) });
        foreach (var line in latest.Added.Concat(latest.Updated).Take(3)) NewsHighlights.Children.Add(Bullet(line));
        if (available) NewsHighlights.Children.Add(new TextBlock { Text = T("UpdateReadyHint"), FontSize = 12, Foreground = (Brush)FindResource("Muted"), Margin = new Thickness(0, 9, 0, 0) });
    }
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
        if (HistoryPanel is not null) { Refresh(); UpdateStatus.Text = T("AutoCheck"); StatusText.Text = context.CanPlay ? T("Installed") : T("NoPack"); }
    }
    private void Official_Click(object sender, RoutedEventArgs e) { context.SelectLauncher("official"); Refresh(); }
    // An existing SKlauncher folder stays in use; everyone else gets the app's own game folder.
    private void Sk_Click(object sender, RoutedEventArgs e) { if (context.Settings.Launcher != "sk") context.SelectLauncher("name"); Refresh(); }
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
    private async void Install_Click(object sender, RoutedEventArgs e) => await RunInstallAsync();
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
    private void SetBusy(bool busy) { Progress.IsIndeterminate = busy; if (busy) { operationTime.Restart(); progressClock.Start(); progressDetail = T("Checking"); Progress.Value = 0; Progress.Foreground = (Brush)FindResource("ActionGreen"); ProgressDetails.Visibility = Visibility.Visible; ProgressDetails.Text = T("Checking"); } if (!busy) { progressClock.Stop(); operationTime.Stop(); } SettingsButton.IsEnabled = !busy; LauncherActions.IsEnabled = !busy; LinkSkButton.IsEnabled = RebuildSkButton.IsEnabled = InstallButton.IsEnabled = NewsUpdateButton.IsEnabled = OfficialCard.IsEnabled = SkCard.IsEnabled = LanguageChoice.IsEnabled = !busy; CheckUpdatesButton.IsEnabled = !busy && !checking; PlayButton.IsEnabled = !busy && !starting && PlayReady(); NameSaveButton.IsEnabled = !busy; CancelButton.Visibility = busy ? Visibility.Visible : Visibility.Collapsed; }
    private void Cancel_Click(object sender, RoutedEventArgs e) => cancellation?.Cancel();
    private async void Play_Click(object sender, RoutedEventArgs e)
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
    private async Task SaveNameAsync()
    {
        var name = NameBox.Text.Trim();
        if (!PlayerNames.IsValid(name)) { ShowNameNote(T("NameInvalid"), true); return; }
        NameSaveButton.IsEnabled = false;
        try
        {
            // A name this folder already played with is the player's own; only new names are checked against bought accounts.
            var played = string.Equals(context.SuggestedPlayerName(), name, StringComparison.OrdinalIgnoreCase);
            if (!played && !PlayerNames.Known(context.Players, name))
            {
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(6));
                if (await context.IsPremiumNameAsync(name, timeout.Token) == true) { ShowNameNote(T("NamePremium"), true); return; }
            }
            context.UsePlayerName(name); nameNote = null;
            StatusText.Text = string.Format(T("NameSaved"), context.PlayerName);
        }
        catch (Exception ex) when (ex is IOException or InvalidDataException or UnauthorizedAccessException) { ShowNameNote(ex.Message, true); }
        finally { NameSaveButton.IsEnabled = cancellation is null; Refresh(); }
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
