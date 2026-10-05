using HolyLois.Core;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace HolyLois.App;

/// <summary>
/// Changing the player name. The server knows players by name, so the window says plainly what a new name means and keeps
/// every earlier name one click away. New names are limited per day; switching back to an old one is always free.
/// </summary>
public sealed class NameWindow : ThemedWindow
{
    public NameWindow(ClientContext context)
    {
        Title = "Holy Lois: Reborn"; Width = 540; SizeToContent = SizeToContent.Height; ResizeMode = ResizeMode.NoResize;
        WindowStartupLocation = WindowStartupLocation.CenterOwner; Background = Res("SurfaceRaised"); FontFamily = new FontFamily("Segoe UI");
        var panel = new StackPanel { Margin = new Thickness(28, 22, 28, 24) };
        panel.Children.Add(new TextBlock { Text = Localize.Text("NameChangeTitle"), FontSize = 23, FontWeight = FontWeights.SemiBold });
        var book = context.Players;
        panel.Children.Add(new TextBlock { Text = string.Format(Localize.Text("NameCurrent"), book.Current ?? "-"), FontSize = 13, Foreground = Res("Muted"), Margin = new Thickness(0, 6, 0, 0) });
        panel.Children.Add(new Border
        {
            Background = Res("GoldSoft"), BorderBrush = Res("Gold"), BorderThickness = new Thickness(1), CornerRadius = (CornerRadius)Application.Current.Resources["Radius"],
            Padding = new Thickness(14, 10, 14, 10), Margin = new Thickness(0, 14, 0, 0),
            Child = new TextBlock { Text = Localize.Text("NameWarning"), FontSize = 13 }
        });
        var error = new TextBlock { FontSize = 12, Foreground = Res("Danger"), Margin = new Thickness(0, 8, 0, 0), Visibility = Visibility.Collapsed };
        void Fail(string text) { error.Text = text; error.Visibility = Visibility.Visible; }
        var earlier = book.History.Where(n => !n.Name.Equals(book.Current, StringComparison.OrdinalIgnoreCase)).ToArray();
        if (earlier.Length > 0)
        {
            panel.Children.Add(new TextBlock { Text = Localize.Text("NameHistory"), FontSize = 14, FontWeight = FontWeights.SemiBold, Foreground = Res("Gold"), Margin = new Thickness(0, 18, 0, 0) });
            panel.Children.Add(new TextBlock { Text = Localize.Text("NameHistoryInfo"), FontSize = 12, Foreground = Res("Muted"), Margin = new Thickness(0, 3, 0, 4) });
            var list = new WrapPanel();
            foreach (var entry in earlier)
            {
                var button = new Button { Content = entry.Name, Margin = new Thickness(0, 6, 8, 0), Padding = new Thickness(14, 7, 14, 7), FontSize = 13,
                    ToolTip = string.Format(Localize.Text("NameLastUsed"), entry.LastUsed.ToLocalTime().ToString("d")) };
                button.Click += (_, _) => { try { context.UsePlayerName(entry.Name); DialogResult = true; } catch (Exception ex) { Fail(ex.Message); } };
                list.Children.Add(button);
            }
            panel.Children.Add(list);
        }
        panel.Children.Add(new TextBlock { Text = Localize.Text("NameNew"), FontSize = 14, FontWeight = FontWeights.SemiBold, Foreground = Res("Gold"), Margin = new Thickness(0, 18, 0, 0) });
        panel.Children.Add(new TextBlock { Text = Localize.Text("NameRules") + " " + string.Format(Localize.Text("NamesLeft"), PlayerNames.NewNamesLeft(book, DateTimeOffset.UtcNow)),
            FontSize = 12, Foreground = Res("Muted"), Margin = new Thickness(0, 3, 0, 8) });
        var row = new Grid();
        row.ColumnDefinitions.Add(new()); row.ColumnDefinitions.Add(new() { Width = GridLength.Auto });
        var box = new TextBox { MaxLength = 16, FontSize = 14, Padding = new Thickness(10, 8, 10, 8) };
        System.Windows.Automation.AutomationProperties.SetName(box, Localize.Text("NameNew"));
        var use = new Button { Content = Localize.Text("NameUse"), Style = (Style)Application.Current.Resources["PrimaryButton"], Margin = new Thickness(8, 0, 0, 0), Padding = new Thickness(14, 8, 14, 8) };
        Grid.SetColumn(use, 1); row.Children.Add(box); row.Children.Add(use);
        panel.Children.Add(row); panel.Children.Add(error);
        async Task Save()
        {
            var name = box.Text.Trim();
            if (!PlayerNames.IsValid(name)) { Fail(Localize.Text("NameInvalid")); return; }
            use.IsEnabled = false;
            try
            {
                if (!PlayerNames.Known(context.Players, name))
                {
                    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(6));
                    if (await context.IsPremiumNameAsync(name, timeout.Token) == true) { Fail(Localize.Text("NamePremium")); return; }
                }
                context.UsePlayerName(name); DialogResult = true;
            }
            catch (Exception ex) { Fail(ex.Message); }
            finally { if (!IsClosed) use.IsEnabled = true; }
        }
        use.Click += async (_, _) => await Save();
        box.KeyDown += async (_, e) => { if (e.Key == Key.Enter) { e.Handled = true; await Save(); } };
        var close = new Button { Content = Localize.Text("CancelAction"), Style = (Style)Application.Current.Resources["CancelButton"], IsCancel = true, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 22, 0, 0) };
        close.Click += (_, _) => Close();
        panel.Children.Add(close);
        Content = panel;
        Loaded += (_, _) => box.Focus();
    }

    private static Brush Res(string key) => (Brush)Application.Current.Resources[key];
}
