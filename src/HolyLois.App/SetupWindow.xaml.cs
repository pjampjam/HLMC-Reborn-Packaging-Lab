using HolyLois.Core;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace HolyLois.App;

public partial class SetupWindow : ThemedWindow
{
    private readonly ClientContext context;
    private readonly string root;
    private string launcher;
    public bool Completed { get; private set; }

    public SetupWindow(ClientContext context, string root)
    {
        this.context = context; this.root = root; launcher = context.Settings.Launcher;
        InitializeComponent(); Localize.Apply(this, context.Settings.Language);
        LanguageChoice.SelectedIndex = context.Settings.Language == "ru" ? 1 : context.Settings.Language == "lv" ? 2 : 0;
        Refresh();
    }
    private void Refresh()
    {
        var sk = launcher != "official"; var accent = (Brush)FindResource("Gold"); var neutral = (Brush)FindResource("Line");
        OfficialSelected.Visibility = sk ? Visibility.Hidden : Visibility.Visible;
        SkSelected.Visibility = sk ? Visibility.Visible : Visibility.Hidden;
        OfficialCard.BorderBrush = sk ? neutral : accent; SkCard.BorderBrush = sk ? accent : neutral;
        // The chosen launcher gets a warm tint as well as the gold outline and "Selected" label.
        var tint = (Brush)FindResource("GoldSoft"); var plain = (Brush)FindResource("Control");
        OfficialCard.Background = sk ? plain : tint; SkCard.Background = sk ? tint : plain;
        SetupNamePanel.Visibility = sk ? Visibility.Visible : Visibility.Collapsed;
        if (sk && SetupNameBox.Text.Length == 0) SetupNameBox.Text = context.SuggestedPlayerName() ?? "";
    }
    private void Official_Click(object sender, RoutedEventArgs e) { launcher = "official"; Refresh(); }
    // A reset setup of a SKlauncher player keeps that folder; new players get a plain player name.
    private void Sk_Click(object sender, RoutedEventArgs e) { launcher = context.Settings.Launcher == "sk" ? "sk" : "name"; Refresh(); SetupNameBox.Focus(); }
    private void Language_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (LanguageChoice.SelectedItem is ComboBoxItem item) Localize.Apply(this, (string)item.Tag);
    }
    private async void Continue_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var name = SetupNameBox.Text.Trim();
            if (launcher != "official")
            {
                if (!PlayerNames.IsValid(name)) { ErrorText.Text = Localize.Text("NameInvalid"); SetupNameBox.Focus(); return; }
                if (!string.Equals(context.SuggestedPlayerName(), name, StringComparison.OrdinalIgnoreCase) && !PlayerNames.Known(context.Players, name))
                {
                    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(6));
                    if (await context.IsPremiumNameAsync(name, timeout.Token) == true) { ErrorText.Text = Localize.Text("NamePremium"); SetupNameBox.Focus(); return; }
                }
            }
            ContinueButton.IsEnabled = OfficialCard.IsEnabled = SkCard.IsEnabled = LanguageChoice.IsEnabled = false; SetupProgress.Visibility = Visibility.Visible; ErrorText.Text = Localize.Text("Finishing");
            await Task.Run(LauncherStartup.InstallCurrent);
            LauncherSetup.Complete(root, new(launcher, DesktopChoice.IsChecked == true, StartChoice.IsChecked == true), desktop => LauncherStartup.CreateShortcut(root, desktop));
            context.SelectLauncher(launcher); context.SetLanguage(Localize.Language);
            if (launcher != "official") context.UsePlayerName(name);
            Completed = true; DialogResult = true;
        }
        catch (Exception ex) { ErrorText.Text = Localize.Error(ex); }
        finally { ContinueButton.IsEnabled = OfficialCard.IsEnabled = SkCard.IsEnabled = LanguageChoice.IsEnabled = true; SetupProgress.Visibility = Visibility.Collapsed; }
    }
}
