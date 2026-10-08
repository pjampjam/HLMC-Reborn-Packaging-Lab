using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
namespace HolyLois.App;

public sealed class AppUpdateWindow : ThemedWindow
{
    private readonly TextBlock status, details;
    private readonly ProgressBar bar;
    private readonly Stopwatch elapsed = Stopwatch.StartNew();
    private readonly TransferFeedback feedback = new();
    private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(1) };
    private bool allowClose;
    private string transfer = "";
    public CancellationTokenSource Cancellation { get; } = new();
    public AppUpdateWindow(string? title = null, bool cancellable = true)
    {
        Title = "Holy Lois: Reborn"; Width = 520; Height = 280;
        ResizeMode = ResizeMode.NoResize; WindowStartupLocation = WindowStartupLocation.CenterScreen;
        Background = (Brush)Application.Current.Resources["Surface"]; FontFamily = new FontFamily("Segoe UI");
        var grid = new Grid { Margin = new Thickness(28,22,28,22) };
        grid.RowDefinitions.Add(new() { Height = GridLength.Auto }); grid.RowDefinitions.Add(new()); grid.RowDefinitions.Add(new() { Height = GridLength.Auto }); grid.RowDefinitions.Add(new() { Height = GridLength.Auto });
        var heading = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center };
        heading.Children.Add(new Image { Source = new BitmapImage(new Uri("pack://application:,,,/Assets/logo.png")), Width = 36, Height = 36, Stretch = Stretch.Uniform, Margin = new Thickness(0,0,12,0) });
        heading.Children.Add(new TextBlock { Text = title ?? "Holy Lois: Reborn", FontSize = 22, FontWeight = FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center }); grid.Children.Add(heading);
        status = new TextBlock { Text = "Checking for launcher updates...", TextAlignment = TextAlignment.Center, VerticalAlignment = VerticalAlignment.Center, FontSize = 14 };
        Grid.SetRow(status,1); grid.Children.Add(status);
        details = new TextBlock { Text = "", FontSize = 12, TextAlignment = TextAlignment.Center, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0,8,0,14) };
        Grid.SetRow(details,2); grid.Children.Add(details);
        bar = new ProgressBar { IsIndeterminate = true, Height = 7, Foreground = (Brush)Application.Current.Resources["ActionGreen"], Background = (Brush)Application.Current.Resources["Line"], BorderThickness = new Thickness(0) };
        Grid.SetRow(bar,3); grid.Children.Add(bar); Content = grid;
        timer.Tick += (_,_) => UpdateDetails(); Loaded += (_,_) => timer.Start(); Closed += (_,_) => timer.Stop();
        Closing += (_,e) => { if (!cancellable && !allowClose) e.Cancel = true; else Cancellation.Cancel(); };
    }
    private void UpdateDetails() => details.Text = transfer + (transfer.Length > 0 ? "  -  " : "") + Localize.Text("WorkingTime") + " " + elapsed.Elapsed.ToString(@"m\:ss");
    public void SetStatus(string text) { status.Text = text; bar.IsIndeterminate = true; transfer = ""; UpdateDetails(); }
    public void SetTransfer(string text, long done, long total) { status.Text = text; bar.IsIndeterminate = false; bar.Value = total == 0 ? 0 : Math.Clamp(100.0*done/total,0,100); transfer = feedback.Describe(done,total); UpdateDetails(); }
    public void SetStage(string text, double percent, bool complete = false) { status.Text = text; bar.IsIndeterminate = false; bar.Value = percent; transfer = ""; if (complete) bar.Foreground = (Brush)Application.Current.Resources["Gold"]; UpdateDetails(); }
    public void FinishAndClose() { allowClose = true; Close(); }
}
