using HolyLois.Core;
using Microsoft.Win32;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace HolyLois.App;

public sealed class SettingsWindow : ThemedWindow
{
    public SettingsWindow(ClientContext context)
    {
        Title = "Holy Lois: Reborn - Settings"; Width = 610; Height = 780; ResizeMode = ResizeMode.NoResize;
        WindowStartupLocation = WindowStartupLocation.CenterOwner; Background = (Brush)Application.Current.Resources["SurfaceRaised"]; FontFamily = new FontFamily("Segoe UI");
        var panel = new StackPanel { Margin = new Thickness(28,22,28,24) };
        StackPanel location = new();
        Text(Localize.Text("Settings"),27,true);
        Text(Localize.Text("SettingsIntro"));
        var named = context.Settings.Launcher != "official";
        if (named)
        {
            Text(Localize.Text("PlayerName"),14,true);
            var current = new TextBlock { Text = context.PlayerName ?? Localize.Text("NameNotSet"), FontSize = 17, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0,8,0,0) };
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
        var outer = panel; panel = location;
        Text(Localize.Text("LauncherLocation"),14,true);
        var path = new TextBox { Text = context.DetectLauncher() ?? Localize.Text("NotDetected"), IsReadOnly = true, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0,8,0,10) }; panel.Children.Add(path);
        Action(Localize.Text("ChangeLauncher"), () => {
            var picker = new OpenFileDialog { Title = Localize.Text("ChooseExe"), Filter = "Launcher (*.exe;*.lnk)|*.exe;*.lnk", CheckFileExists = true };
            if (picker.ShowDialog(this) == true) { context.SetLauncher(picker.FileName); path.Text = context.DetectLauncher(); }
        });
        panel = outer;
        Text(Localize.Text("Reports"),14,true);
        Text(Localize.Text("ReportsInfo"),12);
        var reportStatus = new TextBlock { TextWrapping = TextWrapping.Wrap, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0,8,0,0) };
        if (context.LastStart is { } last)
            reportStatus.Text = string.Format(Localize.Text("LastStart"), last.Started.ToLocalTime().ToString("g"), last.Mode == "fast start" ? Localize.Text("FastStart") : last.Mode)
                + (last.Error is not null ? "  -  " + Localize.Text("LastStartFailed") : last.ExitCode is int code && code != 0 ? "  -  " + string.Format(Localize.Text("LastStartCrashed"), code) : "");
        var reportRow = new WrapPanel();
        void ReportButton(string label, Func<string?> run, bool primary = false)
        {
            var button = new Button { Content = label, Margin = new Thickness(0,8,8,0), FontSize = 13, Padding = new Thickness(14,8,14,8) };
            if (primary) button.Style = (Style)Application.Current.Resources["PrimaryButton"];
            button.Click += (_, _) => { try { reportStatus.Text = run() ?? reportStatus.Text; } catch (Exception ex) { reportStatus.Text = Localize.Error(ex); } };
            reportRow.Children.Add(button);
        }
        ReportButton(Localize.Text("ReportCopy"), () => ReportActions.Copy(context), true);
        ReportButton(Localize.Text("ReportSave"), () => ReportActions.Save(context));
        ReportButton("Discord", () => { ReportActions.OpenDiscord(); return null; });
        panel.Children.Add(reportRow); panel.Children.Add(reportStatus);
        Text(Localize.Text("AppFiles"),14,true);
        Action(Localize.Text("OpenAppFolder"), () => Process.Start(new ProcessStartInfo("explorer.exe") { UseShellExecute = true, ArgumentList = { LauncherStartup.InstallRoot } }));
        Action(Localize.Text("CreateDesktop"), () => LauncherStartup.CreateShortcut(LauncherStartup.InstallRoot,true), !context.IsIsolated);
        Action(Localize.Text("CreateStart"), () => LauncherStartup.CreateShortcut(LauncherStartup.InstallRoot,false), !context.IsIsolated);
        Text(Localize.Text("SetupShortcutHint"),12);
        Text(Localize.Text("Maintenance"),14,true);
        var cleanupStatus = new TextBlock { TextWrapping = TextWrapping.Wrap, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0,8,0,0) };
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
        }, !context.IsIsolated && LauncherStartup.IsInstalled);
        Text(Localize.Text("KeepPersonalFiles"),12);
        Content = new ScrollViewer { Content = panel, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };
        void Text(string text, int size = 13, bool strong = false) => panel.Children.Add(new TextBlock { Text = text, FontSize = size, FontWeight = strong ? FontWeights.SemiBold : FontWeights.Normal, Foreground = (Brush)Application.Current.Resources[strong ? "Gold" : "Muted"], Margin = new Thickness(0,strong ? 16 : 8,0,0) });
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
        void Action(string text, System.Action action, bool enabled = true)
        {
            var button = new Button { Content = text, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0,8,0,0), IsEnabled = enabled, FontSize = 13, Padding = new Thickness(14,8,14,8) };
            if (text == Localize.Text("UninstallApp")) button.Style = (Style)Application.Current.Resources["CancelButton"];
            button.Click += (_, _) => { try { action(); } catch (Exception ex) { AppDialog.Show(this,Localize.Text("Error"),ex.Message); } }; panel.Children.Add(button);
        }
    }
}
