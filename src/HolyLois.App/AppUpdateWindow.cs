using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace HolyLois.App;

public sealed class AppUpdateWindow : Window
{
    private readonly TextBlock status;
    public CancellationTokenSource Cancellation { get; } = new();
    public AppUpdateWindow()
    {
        Title = "Holy Lois: Reborn - Checking for updates"; Width = 520; Height = 240;
        ResizeMode = ResizeMode.NoResize; WindowStartupLocation = WindowStartupLocation.CenterScreen;
        Background = new SolidColorBrush(Color.FromRgb(32,33,31)); Foreground = Brushes.WhiteSmoke;
        FontFamily = new FontFamily("Segoe UI");
        var grid = new Grid { Margin = new Thickness(28,22,28,22) };
        grid.RowDefinitions.Add(new() { Height = GridLength.Auto }); grid.RowDefinitions.Add(new() { Height = new GridLength(1,GridUnitType.Star) }); grid.RowDefinitions.Add(new() { Height = GridLength.Auto });
        var heading = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center };
        heading.Children.Add(new Image { Source = new BitmapImage(new Uri("pack://application:,,,/Assets/logo.png")), Width = 42, Height = 42, Stretch = Stretch.Uniform, Margin = new Thickness(0,0,14,0) });
        heading.Children.Add(new TextBlock { Text = "Holy Lois: Reborn", FontSize = 23, FontWeight = FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center });
        grid.Children.Add(heading);
        status = new TextBlock { Text = "Checking for launcher updates...", TextAlignment = TextAlignment.Center, TextWrapping = TextWrapping.Wrap, VerticalAlignment = VerticalAlignment.Center, FontSize = 14, Foreground = new SolidColorBrush(Color.FromRgb(173,175,164)) };
        Grid.SetRow(status,1); grid.Children.Add(status);
        var bar = new ProgressBar { IsIndeterminate = true, Height = 3, Foreground = new SolidColorBrush(Color.FromRgb(245,239,66)), Background = Brushes.Black, BorderThickness = new Thickness(0) };
        Grid.SetRow(bar,2); grid.Children.Add(bar); Content = grid;
        SourceInitialized += (_,_) => WindowCaption.Apply(this);
        Closing += (_,_) => Cancellation.Cancel();
    }
    public void SetStatus(string text) => status.Text = text;
}
