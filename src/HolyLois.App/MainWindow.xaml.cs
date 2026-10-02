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
    public MainWindow(ClientContext context)
    {
        this.context = context; InitializeComponent(); Localize.Apply(this, context.Settings.Language);
        LanguageChoice.SelectedIndex = context.Settings.Language == "en" ? 1 : context.Settings.Language == "lv" ? 2 : 0;
        Closing += OnClosing; 
        UpdateStatus.Text = T("AutoCheck"); StatusText.Text = AppUpdates.Notice ?? T("StartHint"); Refresh();
        Loaded += async (_, _) => { await LauncherDiscovery.WarmAsync(); Refresh(); if (!context.IsIsolated) { await CheckUpdates(); updateTimer.Start(); } };
        updateTimer.Tick += async (_, _) => await CheckUpdates(); Closed += (_, _) => updateTimer.Stop();
    }
    private static string T(string key) => Localize.Text(key);
    private void Refresh()
    {
        var sk = context.Settings.Launcher == "sk";
        AppVersion.Text = T("App") + " " + AppUpdates.RunningVersion.ToString(3) + " preview";
        ReleaseLabel.Text = "Minecraft 26.3 / Fabric 0.19.5 / " + T("Version") + " " + context.Manifest.Version;
        SizeLabel.Text = $"{context.Manifest.Files.Count(f => f.Path.StartsWith("mods/"))} {T("Mods")}  -  {context.Manifest.Files.Sum(f => f.Size) / 1048576:N0} MB";
        OfficialSelected.Visibility = sk ? Visibility.Hidden : Visibility.Visible; SkSelected.Visibility = sk ? Visibility.Visible : Visibility.Hidden;
        var neutral = (Brush)FindResource("Line"); var accent = (Brush)FindResource("Gold");
        OfficialCard.BorderBrush = sk ? neutral : accent; SkCard.BorderBrush = sk ? accent : neutral;
        LinkSkButton.Visibility = sk && context.Settings.SkInstance is null ? Visibility.Visible : Visibility.Collapsed; RebuildSkButton.Visibility = Visibility.Collapsed;
        LauncherHint.Text = T(sk ? context.Settings.SkInstance is null ? context.RecoveredDeletedInstance ? "SkMissing" : "SkFirst" : "SkLinked" : "OfficialHint");
        var detected = context.DetectLauncher();
        LauncherActions.Visibility = detected is null ? Visibility.Visible : Visibility.Collapsed;
        DetectionPanel.BorderBrush = detected is null ? (Brush)FindResource("Line") : (Brush)FindResource("Success");
        DetectionHint.Text = !LauncherDiscovery.Ready ? T("Detecting") : detected is null ? T("NotDetected") : !sk && detected.StartsWith("shell:") ? T("DetectedStore") : T("Detected") + ": " + (sk ? "SKlauncher" : "Minecraft Launcher");
        var receipt = Directory.Exists(context.Instance) ? context.Receipt : null;
        var available = receipt is not null && receipt.Version != context.Manifest.Version;
        PackStatus.Text = context.CanPlay ? T("Ready") : receipt is null ? T("NoPack") : T(available ? "NewPack" : "NeedsRepair");
        InstallLabel.Text = receipt is null ? T("Install") : available ? T("Update") : T("Verify");
        PlayButton.IsEnabled = context.CanPlay && cancellation is null;
        var ready = context.CanPlay;
        InstallButton.Background = ready ? new SolidColorBrush(Color.FromRgb(41, 42, 38)) : accent;
        InstallLabel.Foreground = ready ? new SolidColorBrush(Color.FromRgb(243, 243, 238)) : new SolidColorBrush(Color.FromRgb(17, 18, 15));
        InstallButton.BorderThickness = ready ? new Thickness(1) : new Thickness(0);
        PlayButton.Background = ready ? accent : new SolidColorBrush(Color.FromRgb(41, 42, 38));
        PlayLabel.Foreground = ready ? new SolidColorBrush(Color.FromRgb(17, 18, 15)) : new SolidColorBrush(Color.FromRgb(243, 243, 238));
        PlayLabel.Text = T(sk ? "OpenSk" : "OpenOfficial");
        HistoryPanel.Children.Clear();
        foreach (var item in context.Manifest.History ?? [])
        {
            HistoryPanel.Children.Add(new TextBlock { Text = item.Version + "  -  " + item.Date, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 10, 0, 5) });
            HistoryPanel.Children.Add(new TextBlock { Text = item.Summary, FontSize = 12 });
            foreach (var group in new[] { ("Added", item.Added), ("Removed", item.Removed), ("Updated", item.Updated) })
                if (group.Item2.Length > 0) HistoryPanel.Children.Add(new TextBlock { Text = T(group.Item1) + ": " + string.Join(", ", group.Item2), FontSize = 12, Margin = new Thickness(0, 5, 0, 0) });
        }
        if (HistoryPanel.Children.Count == 0) HistoryPanel.Children.Add(new TextBlock { Text = T("NoHistory") });
    }
    private void Language_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (context is null || LanguageChoice.SelectedItem is not ComboBoxItem item) return;
        var language = (string)item.Tag; context.SetLanguage(language); Localize.Apply(this, language);
        if (HistoryPanel is not null) { Refresh(); UpdateStatus.Text = T("AutoCheck"); StatusText.Text = context.CanPlay ? T("Installed") : T("NoPack"); }
    }
    private void Official_Click(object sender, RoutedEventArgs e) { context.SelectLauncher("official"); Refresh(); }
    private void Sk_Click(object sender, RoutedEventArgs e) { context.SelectLauncher("sk"); Refresh(); }
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
            var installProgress = new Progress<InstallProgress>(p => { StatusText.Text = p.Message.StartsWith("Preparing") ? T("Preparing") : p.Message.StartsWith("Holy Lois") ? T("Installed") : T("Progress") + $" {p.CompletedFiles}/{p.TotalFiles}"; Progress.IsIndeterminate = p.Message.StartsWith("Preparing"); Progress.Value = p.TotalBytes == 0 ? 0 : 100.0 * p.CompletedBytes / p.TotalBytes; });
            await Task.Run(() => context.InstallAsync(installProgress, cancellation.Token), cancellation.Token);
            StatusText.Text = T("Installed"); Progress.Value = 100;
        }
        catch (OperationCanceledException) { StatusText.Text = T("Cancelled"); }
        catch (Exception ex) { StatusText.Text = Localize.Error(ex); }
        finally { cancellation.Dispose(); cancellation = null; SetBusy(false); Refresh(); }
    }
    private void SetBusy(bool busy) { Progress.IsIndeterminate = busy; SettingsButton.IsEnabled = !busy; LauncherActions.IsEnabled = !busy; LinkSkButton.IsEnabled = RebuildSkButton.IsEnabled = InstallButton.IsEnabled = OfficialCard.IsEnabled = SkCard.IsEnabled = LanguageChoice.IsEnabled = !busy; CheckUpdatesButton.IsEnabled = !busy && !checking; PlayButton.IsEnabled = !busy && context.CanPlay; CancelButton.Visibility = busy ? Visibility.Visible : Visibility.Collapsed; }
    private void Cancel_Click(object sender, RoutedEventArgs e) => cancellation?.Cancel();
    private void Play_Click(object sender, RoutedEventArgs e) { try { context.OpenLauncher(); StatusText.Text = T(context.Settings.Launcher == "sk" ? "SkLinked" : "OfficialHint"); } catch (Exception ex) { StatusText.Text = Localize.Error(ex); } }
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
    private void GetLauncher_Click(object sender, RoutedEventArgs e) => ClientContext.OpenUrl(context.Settings.Launcher == "sk" ? "https://next.skmedix.pl/downloads" : "https://www.minecraft.net/download");
    private void Settings_Click(object sender, RoutedEventArgs e) { if (cancellation is null) new SettingsWindow(context) { Owner = this }.ShowDialog(); Refresh(); }
    private void GameFolder_Click(object sender, RoutedEventArgs e) { Directory.CreateDirectory(context.Instance); Process.Start(new ProcessStartInfo("explorer.exe") { UseShellExecute = true, ArgumentList = { context.Instance } }); }
    private void CopyAddress_Click(object sender, RoutedEventArgs e) { Clipboard.SetText(context.Manifest.Server); StatusText.Text = T("Copied"); }
    private void DisableShaders_Click(object sender, RoutedEventArgs e) { if (cancellation is not null) return; try { context.DisableShaders(); StatusText.Text = T("Disabled"); } catch (Exception ex) { StatusText.Text = Localize.Error(ex); } }
    private void OnClosing(object? sender, CancelEventArgs e) { if (cancellation is not null) { e.Cancel = true; cancellation.Cancel(); StatusText.Text = T("Cancelled"); } }
}
