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
        Title = "Holy Lois: Reborn - Settings"; Width = 610; Height = 650; ResizeMode = ResizeMode.NoResize;
        WindowStartupLocation = WindowStartupLocation.CenterOwner; Background = (Brush)Application.Current.Resources["Surface"]; FontFamily = new FontFamily("Segoe UI");
        var panel = new StackPanel { Margin = new Thickness(28,22,28,24) };
        Text(Localize.Text("Settings"),27,true);
        Text(Localize.Text("SettingsIntro"));
        Text(Localize.Text("LauncherLocation"),14,true);
        var path = new TextBox { Text = context.DetectLauncher() ?? Localize.Text("NotDetected"), IsReadOnly = true, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0,8,0,10) }; panel.Children.Add(path);
        Action(Localize.Text("ChangeLauncher"), () => {
            var picker = new OpenFileDialog { Title = Localize.Text("ChooseExe"), Filter = "Launcher (*.exe;*.lnk)|*.exe;*.lnk", CheckFileExists = true };
            if (picker.ShowDialog(this) == true) { context.SetLauncher(picker.FileName); path.Text = context.DetectLauncher(); }
        });
        Text(Localize.Text("AppFiles"),14,true);
        Action(Localize.Text("OpenAppFolder"), () => Process.Start(new ProcessStartInfo("explorer.exe") { UseShellExecute = true, ArgumentList = { LauncherStartup.InstallRoot } }));
        Action(Localize.Text("CreateDesktop"), () => LauncherStartup.CreateShortcut(LauncherStartup.InstallRoot,true), !context.IsIsolated);
        Action(Localize.Text("CreateStart"), () => LauncherStartup.CreateShortcut(LauncherStartup.InstallRoot,false), !context.IsIsolated);
        Text(Localize.Text("SetupShortcutHint"),12);
        Text(Localize.Text("Maintenance"),14,true);
        Action(Localize.Text("ResetSetup"), () => {
            if (!AppDialog.Show(this,Localize.Text("ResetSetup"),Localize.Text("ResetSetupInfo"),Localize.Text("ResetSetup"))) return;
            var receipt = SafePaths.Resolve(LauncherStartup.InstallRoot,"setup-completed.json");
            if (File.Exists(receipt)) File.Move(receipt,SafePaths.Resolve(LauncherStartup.InstallRoot,"setup-completed.previous-" + DateTime.UtcNow.Ticks + ".json"));
            AppUpdates.ReleaseLock(); AppUpdates.Start(LauncherStartup.InstalledExe,["--show-setup","--skip-app-update-once"]).Dispose(); Application.Current.Shutdown();
        }, !context.IsIsolated);
        Action(Localize.Text("UninstallApp"), () => {
            if (!AppDialog.Show(this,Localize.Text("UninstallApp"),Localize.Text("UninstallInfo"),Localize.Text("UninstallApp"))) return;
            AppMaintenance.RequestRemoval(); Application.Current.Shutdown();
        }, !context.IsIsolated && LauncherStartup.IsInstalled);
        Text(Localize.Text("KeepPersonalFiles"),12);
        Content = new ScrollViewer { Content = panel, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };
        void Text(string text, int size = 13, bool strong = false) => panel.Children.Add(new TextBlock { Text = text, FontSize = size, FontWeight = strong ? FontWeights.SemiBold : FontWeights.Normal, Foreground = (Brush)Application.Current.Resources[strong ? "Gold" : "Muted"], Margin = new Thickness(0,strong ? 16 : 8,0,0) });
        void Action(string text, System.Action action, bool enabled = true)
        {
            var button = new Button { Content = text, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0,8,0,0), IsEnabled = enabled, FontSize = 13, Padding = new Thickness(14,8,14,8) };
            button.Click += (_, _) => { try { action(); } catch (Exception ex) { AppDialog.Show(this,Localize.Text("Error"),ex.Message); } }; panel.Children.Add(button);
        }
    }
}
