using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shell;

namespace HolyLois.App;

public class ThemedWindow : Window
{
    private bool prepared;
    public ThemedWindow()
    {
        WindowStyle = WindowStyle.None;
        WindowChrome.SetWindowChrome(this, new WindowChrome { CaptionHeight = 32, ResizeBorderThickness = new Thickness(6), GlassFrameThickness = new Thickness(0), CornerRadius = new CornerRadius(0), UseAeroCaptionButtons = false });
        SourceInitialized += (_, _) => WindowCaption.Apply(this);
        Loaded += (_, _) => EnsureChrome();
        PreviewKeyDown += (_, e) => { if (e.Key == Key.Escape && Owner is not null) { Close(); e.Handled = true; } };
    }
    public void EnsureChrome()
    {
        if (prepared || Content is not UIElement body) return;
        prepared = true; Content = null;
        var frame = new Grid();
        frame.RowDefinitions.Add(new() { Height = new GridLength(32) }); frame.RowDefinitions.Add(new());
        var caption = new Grid { Background = new SolidColorBrush(Color.FromRgb(16,17,16)) };
        var actions = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        void Add(string glyph, string label, Action action)
        {
            var button = new Button { Content = glyph, Width = 46, Height = 32, MinHeight = 0, Padding = new Thickness(0), FontFamily = new FontFamily("Segoe MDL2 Assets"), FontSize = 11, BorderThickness = new Thickness(0), Background = Brushes.Transparent };
            System.Windows.Automation.AutomationProperties.SetName(button,label); button.ToolTip = label;
            WindowChrome.SetIsHitTestVisibleInChrome(button,true); button.Click += (_, _) => action(); actions.Children.Add(button);
        }
        if (ResizeMode is ResizeMode.CanResize or ResizeMode.CanResizeWithGrip or ResizeMode.CanMinimize)
            Add("\uE921","Minimize",() => SystemCommands.MinimizeWindow(this));
        if (ResizeMode is ResizeMode.CanResize or ResizeMode.CanResizeWithGrip)
            Add("\uE922","Maximize or restore",() => { if (WindowState == WindowState.Maximized) SystemCommands.RestoreWindow(this); else SystemCommands.MaximizeWindow(this); });
        Add("\uE8BB","Close",Close);
        caption.Children.Add(actions); frame.Children.Add(caption); Grid.SetRow(body,1); frame.Children.Add(body); Content = frame;
        StateChanged += (_, _) => frame.Margin = WindowState == WindowState.Maximized ? new Thickness(7) : new Thickness(0);
    }
}
