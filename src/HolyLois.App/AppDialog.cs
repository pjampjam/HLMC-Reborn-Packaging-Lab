using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace HolyLois.App;

public static class AppDialog
{
    public static bool Show(Window? owner, string title, string message, string? action = null)
    {
        var window = new ThemedWindow { Title = "Holy Lois: Reborn", Width = 520, SizeToContent = SizeToContent.Height, ResizeMode = ResizeMode.NoResize, Background = (Brush)Application.Current.Resources["Surface"], FontFamily = new FontFamily("Segoe UI"), WindowStartupLocation = owner is null ? WindowStartupLocation.CenterScreen : WindowStartupLocation.CenterOwner };
        if (owner is { IsVisible: true }) window.Owner = owner;
        var panel = new StackPanel { Margin = new Thickness(28) };
        panel.Children.Add(new TextBlock { Text = title, FontSize = 23, FontWeight = FontWeights.SemiBold });
        panel.Children.Add(new TextBlock { Text = message, FontSize = 14, Foreground = (Brush)Application.Current.Resources["Muted"], Margin = new Thickness(0,14,0,24), TextWrapping = TextWrapping.Wrap });
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        var confirmed = false;
        if (action is not null) { var yes = new Button { Content = action, Background = (Brush)Application.Current.Resources["Gold"], Foreground = Brushes.Black, Margin = new Thickness(0,0,10,0) }; yes.Click += (_, _) => { confirmed = true; window.Close(); }; buttons.Children.Add(yes); }
        var close = new Button { Content = Localize.Text(action is null ? "Close" : "CancelAction"), IsCancel = true, IsDefault = true };
        close.Click += (_, _) => window.Close(); buttons.Children.Add(close); panel.Children.Add(buttons); window.Content = panel; window.ShowDialog(); return confirmed;
    }
}
