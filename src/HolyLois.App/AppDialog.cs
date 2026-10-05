using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace HolyLois.App;

public static class AppDialog
{
    public static ThemedWindow Create(string title, string message, string? action, bool destructive, Action<bool> finish, UIElement? extra = null)
    {
        var window = new ThemedWindow { Title = "Holy Lois: Reborn", Width = 520, SizeToContent = SizeToContent.Height, ResizeMode = ResizeMode.NoResize, Background = (Brush)Application.Current.Resources["SurfaceRaised"], FontFamily = new FontFamily("Segoe UI"), WindowStartupLocation = WindowStartupLocation.CenterOwner };
        var panel = new StackPanel { Margin = new Thickness(28) };
        panel.Children.Add(new TextBlock { Text = title, FontSize = 23, FontWeight = FontWeights.SemiBold });
        panel.Children.Add(new TextBlock { Text = message, FontSize = 14, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0,14,0,24), TextWrapping = TextWrapping.Wrap });
        if (extra is not null) panel.Children.Add(extra);
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        if (action is not null) { var yes = new Button { Content = action, Style = (Style)Application.Current.Resources[destructive ? "DangerButton" : "SuccessButton"], Margin = new Thickness(0,0,10,0) }; yes.Click += (_, _) => { finish(true); window.Close(); }; buttons.Children.Add(yes); }
        var close = new Button { Content = Localize.Text(action is null ? "Close" : "CancelAction"), IsCancel = true, IsDefault = true };
        if (action is not null) close.Style = (Style)Application.Current.Resources["CancelButton"];
        close.Click += (_, _) => window.Close(); buttons.Children.Add(close); panel.Children.Add(buttons); window.Content = panel; return window;
    }
    public static bool Show(Window? owner, string title, string message, string? action = null, bool destructive = false, UIElement? extra = null)
    {
        var confirmed = false;
        var window = Create(title,message,action,destructive,value => confirmed = value,extra);
        if (owner is { IsVisible: true }) window.ShowModal(owner);
        else { window.WindowStartupLocation = WindowStartupLocation.CenterScreen; window.ShowDialog(); }
        return confirmed;
    }
}
