using HolyLois.Core;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace HolyLois.App;

/// <summary>
/// The "warming up" window of fast start: one row per step with a check mark when done, a progress bar with size and speed
/// for downloads, and a clear failure view with the next thing to try. It closes by itself once the game window is open.
/// </summary>
public sealed class LaunchWindow : ThemedWindow
{
    public static readonly string[] Steps = ["check", "java", "game", "assets", "start", "mods"];
    private readonly Dictionary<string, (TextBlock Icon, TextBlock Label, TextBlock Detail)> rows = [];
    private readonly TextBlock title, subtitle, details, message;
    private readonly ProgressBar bar;
    private readonly StackPanel buttons;
    private readonly TransferFeedback feedback = new();
    private readonly Stopwatch elapsed = Stopwatch.StartNew();
    private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(1) };
    private string transfer = "";
    private bool finished;
    private int current = -1;
    public CancellationTokenSource Cancellation { get; } = new();

    private static Brush Res(string key) => (Brush)Application.Current.Resources[key];

    public LaunchWindow(string player, bool join)
    {
        Title = "Holy Lois: Reborn"; Width = 520; SizeToContent = SizeToContent.Height; ResizeMode = ResizeMode.NoResize;
        WindowStartupLocation = WindowStartupLocation.CenterOwner; Background = Res("Surface"); FontFamily = new FontFamily("Segoe UI");
        var panel = new StackPanel { Margin = new Thickness(28, 22, 28, 24) };
        var heading = new Grid();
        heading.ColumnDefinitions.Add(new() { Width = GridLength.Auto }); heading.ColumnDefinitions.Add(new());
        heading.Children.Add(new Image { Source = new BitmapImage(new Uri("pack://application:,,,/Assets/logo.png")), Width = 44, Height = 44, Stretch = Stretch.Uniform, Margin = new Thickness(0, 0, 14, 0), VerticalAlignment = VerticalAlignment.Top });
        var names = new StackPanel(); Grid.SetColumn(names, 1); heading.Children.Add(names);
        title = new TextBlock { Text = Localize.Text("StartTitle"), FontSize = 22, FontWeight = FontWeights.SemiBold };
        subtitle = new TextBlock { Text = string.Format(Localize.Text(join ? "StartAsJoin" : "StartAsTitle"), player), FontSize = 13, Foreground = Res("Muted"), Margin = new Thickness(0, 4, 0, 0) };
        names.Children.Add(title); names.Children.Add(subtitle); panel.Children.Add(heading);
        var list = new StackPanel { Margin = new Thickness(0, 20, 0, 6) };
        foreach (var step in Steps)
        {
            var row = new Grid { Margin = new Thickness(0, 0, 0, 9) };
            row.ColumnDefinitions.Add(new() { Width = new GridLength(28) }); row.ColumnDefinitions.Add(new()); row.ColumnDefinitions.Add(new() { Width = GridLength.Auto });
            var icon = new TextBlock { Text = "○", FontFamily = new FontFamily("Segoe UI Symbol"), FontSize = 14, Foreground = Res("LineStrong"), VerticalAlignment = VerticalAlignment.Center };
            var label = new TextBlock { Text = Localize.Text("Start_" + step), FontSize = 14, Foreground = Res("Muted"), VerticalAlignment = VerticalAlignment.Center };
            var detail = new TextBlock { FontSize = 12, Foreground = Res("Muted"), VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.NoWrap };
            Grid.SetColumn(label, 1); Grid.SetColumn(detail, 2);
            row.Children.Add(icon); row.Children.Add(label); row.Children.Add(detail);
            rows[step] = (icon, label, detail); list.Children.Add(row);
        }
        panel.Children.Add(list);
        bar = new ProgressBar { IsIndeterminate = true, Height = 7, Margin = new Thickness(0, 6, 0, 0) };
        System.Windows.Automation.AutomationProperties.SetName(bar, Localize.Text("StartTitle"));
        panel.Children.Add(bar);
        details = new TextBlock { FontSize = 12, Foreground = Res("Muted"), Margin = new Thickness(0, 8, 0, 0) };
        System.Windows.Automation.AutomationProperties.SetLiveSetting(details, System.Windows.Automation.AutomationLiveSetting.Polite);
        panel.Children.Add(details);
        message = new TextBlock { FontSize = 13, Margin = new Thickness(0, 14, 0, 0), Visibility = Visibility.Collapsed };
        panel.Children.Add(message);
        buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 18, 0, 0) };
        var cancel = new Button { Content = Localize.Text("CancelAction"), Style = (Style)Application.Current.Resources["CancelButton"], IsCancel = true };
        cancel.Click += (_, _) => Cancellation.Cancel();
        buttons.Children.Add(cancel); panel.Children.Add(buttons);
        Content = panel;
        timer.Tick += (_, _) => UpdateDetails(); Loaded += (_, _) => timer.Start();
        Closed += (_, _) => { timer.Stop(); if (!finished) Cancellation.Cancel(); };
    }

    private void UpdateDetails() { if (!finished) details.Text = transfer + (transfer.Length > 0 ? "  -  " : "") + Localize.Text("WorkingTime") + " " + elapsed.Elapsed.ToString(@"m\:ss"); }

    /// <summary>Marks earlier steps done and STEP active. Steps that were not needed (files already there) are simply checked.</summary>
    public void SetStep(string step, string? detail = null)
    {
        var index = Array.IndexOf(Steps, step);
        // Progress reports can arrive late; never move back to an earlier step.
        if (index < current) return;
        if (index == current && detail is null) return;
        current = index;
        for (var i = 0; i < Steps.Length; i++)
        {
            var (icon, label, text) = rows[Steps[i]];
            if (i < index) { icon.Text = "✓"; icon.Foreground = Res("Success"); label.Foreground = Res("Text"); }
            else if (i == index) { icon.Text = "●"; icon.Foreground = Res("Gold"); label.Foreground = Res("Text"); label.FontWeight = FontWeights.SemiBold; text.Text = detail ?? ""; }
            else { icon.Text = "○"; icon.Foreground = Res("LineStrong"); label.Foreground = Res("Muted"); }
            if (i != index) label.FontWeight = FontWeights.Normal;
        }
    }

    public void SetBytes(long done, long total)
    {
        if (total <= 0) { bar.IsIndeterminate = true; transfer = ""; }
        else { bar.IsIndeterminate = false; bar.Value = Math.Clamp(100.0 * done / total, 0, 100); transfer = feedback.Describe(done, total); }
        UpdateDetails();
    }

    public void Waiting() { bar.IsIndeterminate = true; transfer = ""; UpdateDetails(); }

    /// <summary>Turns the window into a failure view with the actions that can help next.</summary>
    public void Fail(string heading, string text, IEnumerable<(string Label, Func<string?> Run, bool Primary)> actions)
    {
        finished = true; timer.Stop();
        foreach (var step in Steps) { var (icon, label, _) = rows[step]; if (icon.Text == "●") { icon.Text = "✕"; icon.Foreground = Res("Danger"); label.FontWeight = FontWeights.Normal; } }
        title.Text = heading; bar.IsIndeterminate = false; bar.Value = 100; bar.Foreground = Res("Danger");
        details.Text = ""; message.Text = text; message.Visibility = Visibility.Visible;
        buttons.Children.Clear();
        foreach (var (label, run, primary) in actions)
        {
            var button = new Button { Content = label, Margin = new Thickness(10, 0, 0, 0) };
            if (primary) button.Style = (Style)Application.Current.Resources["PrimaryButton"];
            button.Click += (_, _) => { try { if (run() is { } result) details.Text = result; } catch (Exception ex) { details.Text = Localize.Error(ex); } };
            buttons.Children.Add(button);
        }
        var close = new Button { Content = Localize.Text("Close"), IsCancel = true, Margin = new Thickness(10, 0, 0, 0) };
        close.Click += (_, _) => Close(); buttons.Children.Add(close);
    }

    public void Finish() { finished = true; Close(); }
}

/// <summary>Copy, save and send actions for a report, shared by the start window, the crash window and Settings.</summary>
public static class ReportActions
{
    public const string DiscordUrl = "https://discord.gg/FzBJSZwY2c";

    public static string Copy(ClientContext context)
    {
        Clipboard.SetText(context.BuildReport());
        return Localize.Text("ReportCopied");
    }

    public static string Save(ClientContext context)
    {
        var desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
        var path = Path.Combine(desktop, "holylois-report-" + DateTime.Now.ToString("yyyyMMdd-HHmm") + ".zip");
        GameReports.SaveZip(context.BuildReport(), path);
        return string.Format(Localize.Text("ReportSaved"), Path.GetFileName(path));
    }

    public static void OpenDiscord() => Process.Start(new ProcessStartInfo(DiscordUrl) { UseShellExecute = true });

    /// <summary>The window shown when the game closed by itself with an error code.</summary>
    public static void ShowCrash(Window? owner, ClientContext context, int exitCode)
    {
        var status = new TextBlock { FontSize = 12, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0, 0, 0, 14) };
        var window = AppDialog.Create(Localize.Text("CrashTitle"), string.Format(Localize.Text("CrashInfo"), exitCode), null, false, _ => { });
        var panel = (StackPanel)window.Content;
        var row = new WrapPanel { Margin = new Thickness(0, 0, 0, 12) };
        void Add(string label, Func<string?> run, bool primary = false)
        {
            var button = new Button { Content = label, Margin = new Thickness(0, 0, 10, 8) };
            if (primary) button.Style = (Style)Application.Current.Resources["PrimaryButton"];
            button.Click += (_, _) => { try { status.Text = run() ?? ""; } catch (Exception ex) { status.Text = Localize.Error(ex); } };
            row.Children.Add(button);
        }
        Add(Localize.Text("ReportCopy"), () => Copy(context), true);
        Add(Localize.Text("ReportSave"), () => Save(context));
        Add("Discord", () => { OpenDiscord(); return null; });
        panel.Children.Insert(panel.Children.Count - 1, row);
        panel.Children.Insert(panel.Children.Count - 1, status);
        if (owner is { IsVisible: true }) window.ShowModal(owner);
        else { window.WindowStartupLocation = WindowStartupLocation.CenterScreen; window.ShowDialog(); }
    }
}
