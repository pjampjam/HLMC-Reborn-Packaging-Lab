using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shell;

namespace HolyLois.App;

public class ThemedWindow : Window
{
    private bool prepared;
    private Grid? chromeFrame;
    private Border? modalShade;
    public bool IsClosed { get; private set; }
    public ThemedWindow()
    {
        Foreground = new SolidColorBrush(Color.FromRgb(244,244,239));
        UseLayoutRounding = true; SnapsToDevicePixels = true;
        WindowStyle = WindowStyle.None;
        WindowChrome.SetWindowChrome(this, new WindowChrome { CaptionHeight = 32, ResizeBorderThickness = new Thickness(6), GlassFrameThickness = new Thickness(0), CornerRadius = new CornerRadius(0), UseAeroCaptionButtons = false });
        SourceInitialized += (_, _) => WindowCaption.Apply(this);
        Loaded += (_, _) => EnsureChrome();
        Closed += (_, _) => IsClosed = true;
        PreviewMouseDown += (_,_) => InputModality.SetKeyboardFocusVisible(this,false);
        PreviewKeyDown += (_,e) => { if (e.Key is Key.Tab or Key.Up or Key.Down or Key.Left or Key.Right or Key.Space or Key.Enter or Key.Escape) InputModality.SetKeyboardFocusVisible(this,true); };
        PreviewKeyDown += (_, e) => { if (e.Key == Key.Escape && Owner is not null) { Close(); e.Handled = true; } };
    }
    public void EnsureChrome()
    {
        if (prepared || Content is not UIElement body) return;
        prepared = true; Content = null;
        var frame = new Grid();
        chromeFrame = frame;
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
        caption.Children.Add(actions); frame.Children.Add(caption); Grid.SetRow(body,1); frame.Children.Add(body);
        var outline = new Border { BorderBrush = new SolidColorBrush(Color.FromRgb(93,102,88)), BorderThickness = new Thickness(1), IsHitTestVisible = false };
        Grid.SetRowSpan(outline,2); frame.Children.Add(outline); Content = frame;
        StateChanged += (_, _) => frame.Margin = WindowState == WindowState.Maximized ? new Thickness(7) : new Thickness(0);
    }
    public void ShowModal(Window owner)
    {
        Owner = owner;
        InputModality.SetKeyboardFocusVisible(this,InputModality.GetKeyboardFocusVisible(owner));
        using var shade = (owner as ThemedWindow)?.DimForModal();
        ShowDialog();
        InputModality.SetKeyboardFocusVisible(owner,InputModality.GetKeyboardFocusVisible(this));
    }
    public IDisposable DimForModal()
    {
        EnsureChrome();
        if (chromeFrame is null) throw new InvalidOperationException("Window content is not ready.");
        var shade = new Border { Background = new SolidColorBrush(Color.FromArgb(155,0,0,0)), IsHitTestVisible = false };
        Grid.SetRowSpan(shade,2); chromeFrame.Children.Add(shade); modalShade = shade;
        UiMotion.FadeIn(shade, 0);
        return new ShadeLease(() => { chromeFrame.Children.Remove(shade); if (modalShade == shade) modalShade = null; });
    }
    private sealed class ShadeLease(Action restore) : IDisposable { public void Dispose() => restore(); }
}
