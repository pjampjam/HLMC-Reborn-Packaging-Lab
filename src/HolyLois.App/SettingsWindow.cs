using HolyLois.Core;
using Microsoft.Win32;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace HolyLois.App;

/// <summary>
/// One dialog in four groups, most used first: player and launch, help and diagnostics, shortcuts, rules and privacy. Rare
/// maintenance sits folded under Advanced, with Remove launcher last.
/// </summary>
public sealed class SettingsWindow : ThemedWindow
{
    public SettingsWindow(ClientContext context)
    {
        Title = "Holy Lois: Reborn - Settings"; Width = 610; Height = 720; ResizeMode = ResizeMode.NoResize;
        WindowStartupLocation = WindowStartupLocation.CenterOwner; Background = (Brush)Application.Current.Resources["SurfaceRaised"]; FontFamily = new FontFamily("Segoe UI");
        var panel = new StackPanel { Margin = new Thickness(28,22,28,24) };
        var root = panel;
        StackPanel location = new();
        Text(Localize.Text("Settings"),27,true);
        Text(Localize.Text("SettingsIntro"));

        Group("GroupPlay");
        var named = context.Settings.Launcher != "official";
        if (named)
        {
            Text(Localize.Text("PlayerName"),14,true);
            var current = new TextBlock { Text = context.PlayerName ?? Localize.Text("NameNotSet"), FontSize = 17, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0,6,0,0) };
            panel.Children.Add(current);
            Text(Localize.Text("NameWarningShort"),12);
            Action(Localize.Text("NameChange"), () => { new NameWindow(context).ShowModalResult(this); current.Text = context.PlayerName ?? Localize.Text("NameNotSet"); });
        }
        Text(Localize.Text("PlayMode"),14,true);
        if (context.Settings.Launcher == "sk")
        {
            Text(Localize.Text("PlayModeIntro"),12);
            Choice("PlayFast","PlayFastInfo",true,context.UsesFastStart);
            Choice("PlayOpenSk","PlayOpenSkInfo",false,!context.UsesFastStart);
        }
        else Text(Localize.Text(named ? "PlayFastOnly" : "PlayAccountOnly"),12);
        var join = new CheckBox { Content = Localize.Text("JoinOnStart"), IsChecked = context.Settings.JoinServer, Margin = new Thickness(0,12,0,0) };
        join.Checked += (_, _) => Save(() => context.SetJoinServer(true)); join.Unchecked += (_, _) => Save(() => context.SetJoinServer(false));
        panel.Children.Add(join);
        Text(Localize.Text("JoinOnStartInfo"),12);
        location.Visibility = context.UsesFastStart ? Visibility.Collapsed : Visibility.Visible;
        panel.Children.Add(location);
        panel = location;
        Text(Localize.Text("LauncherLocation"),14,true);
        var path = new TextBox { Text = context.DetectLauncher() ?? Localize.Text("NotDetected"), IsReadOnly = true, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0,8,0,10) }; panel.Children.Add(path);
        Action(Localize.Text("ChangeLauncher"), () => {
            var picker = new OpenFileDialog { Title = Localize.Text("ChooseExe"), Filter = "Launcher (*.exe;*.lnk)|*.exe;*.lnk", CheckFileExists = true };
            if (picker.ShowDialog(this) == true) { context.SetLauncher(picker.FileName); path.Text = context.DetectLauncher(); }
        });
        panel = root;

        Group("GroupHelp");
        Text(Localize.Text("ReportsInfo"),12);
        var reportStatus = Status();
        if (context.LastStart is { } last)
            reportStatus.Text = string.Format(Localize.Text("LastStart"), last.Started.ToLocalTime().ToString("g"), last.Mode == "fast start" ? Localize.Text("FastStart") : last.Mode)
                + (last.Error is not null ? "  -  " + Localize.Text("LastStartFailed") : last.ExitCode is int code && code != 0 ? "  -  " + string.Format(Localize.Text("LastStartCrashed"), code) : "");
        var reportRow = new WrapPanel();
        void ReportButton(string label, Func<string?> run)
        {
            var button = new Button { Content = label, Margin = new Thickness(0,8,8,0), FontSize = 13, Padding = new Thickness(14,8,14,8) };
            button.Click += (_, _) => { try { reportStatus.Text = run() ?? reportStatus.Text; } catch (Exception ex) { reportStatus.Text = Localize.Error(ex); } };
            reportRow.Children.Add(button);
        }
        ReportButton(Localize.Text("ReportCopy"), () => ReportActions.Copy(context));
        ReportButton(Localize.Text("ReportSave"), () => ReportActions.Save(context));
        ReportButton("Discord", () => { ReportActions.OpenDiscord(); return null; });
        panel.Children.Add(reportRow); panel.Children.Add(reportStatus);
        Text(Localize.Text("ShaderHint"),12);
        var graphicsRow = new WrapPanel();
        var graphicsStatus = Status();
        void GraphicsButton(string label, Func<string?> run)
        {
            var button = new Button { Content = label, Margin = new Thickness(0,8,8,0), FontSize = 13, Padding = new Thickness(14,8,14,8) };
            button.Click += (_, _) => { try { graphicsStatus.Text = run() ?? graphicsStatus.Text; } catch (Exception ex) { graphicsStatus.Text = Localize.Error(ex); } };
            graphicsRow.Children.Add(button);
        }
        GraphicsButton(Localize.Text("ShaderOff"), () => { context.DisableShaders(); return Localize.Text("Disabled"); });
        GraphicsButton(Localize.Text("Folder"), () => { Directory.CreateDirectory(context.Instance); Process.Start(new ProcessStartInfo("explorer.exe") { UseShellExecute = true, ArgumentList = { context.Instance } }); return null; });
        panel.Children.Add(graphicsRow); panel.Children.Add(graphicsStatus);

        Group("GroupShortcuts");
        var shortcutRow = new WrapPanel();
        foreach (var (label, desktop) in new[] { (Localize.Text("CreateDesktop"), true), (Localize.Text("CreateStart"), false) })
        {
            var button = new Button { Content = label, Margin = new Thickness(0,8,8,0), FontSize = 13, Padding = new Thickness(14,8,14,8), IsEnabled = !context.IsIsolated };
            button.Click += (_, _) => { try { LauncherStartup.CreateShortcut(LauncherStartup.InstallRoot, desktop); } catch (Exception ex) { AppDialog.Show(this,Localize.Text("Error"),ex.Message); } };
            shortcutRow.Children.Add(button);
        }
        panel.Children.Add(shortcutRow);
        Text(Localize.Text("SetupShortcutHint"),12);

        Group("GroupRules");
        var linkRow = new WrapPanel();
        foreach (var (label, url) in new[] { (Localize.Text("RulesLink"), "https://holylois.com/rules"), (Localize.Text("PrivacyLink"), "https://holylois.com/privacy"), (Localize.Text("Website"), "https://holylois.com") })
        {
            var button = new Button { Content = label + "  ↗", Margin = new Thickness(0,8,8,0), FontSize = 13, Padding = new Thickness(14,8,14,8) };
            button.Click += (_, _) => ClientContext.OpenUrl(url);
            linkRow.Children.Add(button);
        }
        panel.Children.Add(linkRow);

        // Rare maintenance, folded so it never competes with the everyday choices.
        var advanced = new StackPanel();
        panel.Children.Add(new Expander { Header = Localize.Text("GroupAdvanced"), Content = advanced, Margin = new Thickness(0,18,0,0), FontSize = 14 });
        panel = advanced;
        Action(Localize.Text("OpenAppFolder"), () => Process.Start(new ProcessStartInfo("explorer.exe") { UseShellExecute = true, ArgumentList = { LauncherStartup.InstallRoot } }));
        var cleanupStatus = Status();
        Action(Localize.Text("CleanDownloads"), () => { _ = CleanDownloads(); }, !context.IsIsolated);
        panel.Children.Add(cleanupStatus);
        async Task CleanDownloads()
        {
            var busy = new AppUpdateWindow(Localize.Text("CleanDownloads"),false) { Owner = this };
            using var shade = DimForModal(); IsEnabled = false; busy.Show(); busy.SetStatus(Localize.Text("CleaningDownloads"));
            try
            {
                var result = await Task.Run(() => WorkerCleanup.CompletedLauncherFiles() + context.CleanInstalledDownloads());
                cleanupStatus.Text = string.Format(Localize.Text("CleanedDownloads"), result.Bytes / 1048576.0);
            }
            catch (Exception ex) { cleanupStatus.Text = Localize.Error(ex); }
            finally { busy.FinishAndClose(); if (!IsClosed) IsEnabled = true; }
        }
        Action(Localize.Text("ResetSetup"), () => {
            if (!AppDialog.Show(this,Localize.Text("ResetSetup"),Localize.Text("ResetSetupInfo"),Localize.Text("ResetSetup"))) return;
            var receipt = SafePaths.Resolve(LauncherStartup.InstallRoot,"setup-completed.json");
            if (File.Exists(receipt)) File.Move(receipt,SafePaths.Resolve(LauncherStartup.InstallRoot,"setup-completed.previous-" + DateTime.UtcNow.Ticks + ".json"));
            AppUpdates.ReleaseLock(); AppUpdates.Start(LauncherStartup.InstalledExe,["--show-setup","--skip-app-update-once"]).Dispose(); Application.Current.Shutdown();
        }, !context.IsIsolated);
        Text(Localize.Text("KeepPersonalFiles"),12);
        Action(Localize.Text("UninstallApp"), () => {
            // Player names stay for a later reinstall unless the player asks to forget them.
            var forget = new CheckBox { Content = Localize.Text("ForgetNames"), IsChecked = false, Margin = new Thickness(0,0,0,18), Visibility = File.Exists(context.PlayersPath) ? Visibility.Visible : Visibility.Collapsed };
            if (!AppDialog.Show(this,Localize.Text("UninstallApp"),Localize.Text("UninstallInfo"),Localize.Text("UninstallApp"),true,forget)) return;
            if (forget.IsChecked == true) context.ForgetPlayers();
            var busy = new AppUpdateWindow(Localize.Text("UninstallApp"),false) { Owner = this }; busy.Show();
            busy.SetStatus(Localize.Text("PreparingRemoval"));
            _ = Remove();
            async Task Remove() {
                using var shade = DimForModal(); IsEnabled = false;
                try { await Task.Run(AppMaintenance.RequestRemoval); Application.Current.Shutdown(); }
                catch (Exception ex) { busy.FinishAndClose(); IsEnabled = true; AppDialog.Show(this,Localize.Text("Error"),ex.Message); }
            }
        }, !context.IsIsolated && LauncherStartup.IsInstalled, danger: true);
        panel = root;
        Content = new ScrollViewer { Content = root, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };

        void Group(string key)
        {
            panel.Children.Add(new Border { BorderBrush = (Brush)Application.Current.Resources["Line"], BorderThickness = new Thickness(0,1,0,0), Margin = new Thickness(0,18,0,0) });
            panel.Children.Add(new TextBlock { Text = Localize.Text(key).ToUpperInvariant(), FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0,12,0,0) });
        }
        // Result lines stay collapsed until an action writes to them, so empty ones leave no gaps.
        static TextBlock Status()
        {
            var status = new TextBlock { TextWrapping = TextWrapping.Wrap, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0,8,0,0), Visibility = Visibility.Collapsed };
            System.ComponentModel.DependencyPropertyDescriptor.FromProperty(TextBlock.TextProperty, typeof(TextBlock)).AddValueChanged(status, (_, _) => status.Visibility = string.IsNullOrEmpty(status.Text) ? Visibility.Collapsed : Visibility.Visible);
            return status;
        }
        void Text(string text, int size = 13, bool strong = false) => panel.Children.Add(new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap, FontSize = size, FontWeight = strong ? FontWeights.SemiBold : FontWeights.Normal, Foreground = (Brush)Application.Current.Resources[strong ? "Text" : "Muted"], Margin = new Thickness(0,strong ? 12 : 6,0,0) });
        void Choice(string title, string info, bool fast, bool selected)
        {
            var text = new StackPanel();
            text.Children.Add(new TextBlock { Text = Localize.Text(title), FontWeight = FontWeights.SemiBold, Foreground = (Brush)Application.Current.Resources["Text"] });
            text.Children.Add(new TextBlock { Text = Localize.Text(info), FontSize = 12, TextWrapping = TextWrapping.Wrap, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0,2,0,0) });
            var radio = new RadioButton { GroupName = "playmode", Content = text, IsChecked = selected, Margin = new Thickness(0,10,0,0) };
            radio.Checked += (_, _) => { Save(() => context.SetFastStart(fast)); location.Visibility = fast ? Visibility.Collapsed : Visibility.Visible; };
            panel.Children.Add(radio);
        }
        void Save(System.Action change) { try { change(); } catch (Exception ex) { AppDialog.Show(this,Localize.Text("Error"),ex.Message); } }
        void Action(string text, System.Action action, bool enabled = true, bool danger = false)
        {
            var button = new Button { Content = text, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0,8,0,0), IsEnabled = enabled, FontSize = 13, Padding = new Thickness(14,8,14,8) };
            if (danger) button.Style = (Style)Application.Current.Resources["CancelButton"];
            button.Click += (_, _) => { try { action(); } catch (Exception ex) { AppDialog.Show(this,Localize.Text("Error"),ex.Message); } }; panel.Children.Add(button);
        }
    }
}
