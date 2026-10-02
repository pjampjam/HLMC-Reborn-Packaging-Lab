using HolyLois.Core;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace HolyLois.App;

public partial class SetupWindow : Window
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
        SourceInitialized += (_, _) => WindowCaption.Apply(this); Refresh();
    }
    private void Refresh()
    {
        var sk = launcher == "sk"; var accent = (Brush)FindResource("Gold"); var neutral = new SolidColorBrush(Color.FromRgb(132,134,122));
        OfficialSelected.Visibility = sk ? Visibility.Hidden : Visibility.Visible;
        SkSelected.Visibility = sk ? Visibility.Visible : Visibility.Hidden;
        OfficialCard.BorderBrush = sk ? neutral : accent; SkCard.BorderBrush = sk ? accent : neutral;
    }
    private void Official_Click(object sender, RoutedEventArgs e) { launcher = "official"; Refresh(); }
    private void Sk_Click(object sender, RoutedEventArgs e) { launcher = "sk"; Refresh(); }
    private void Language_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (LanguageChoice.SelectedItem is ComboBoxItem item) Localize.Apply(this, (string)item.Tag);
    }
    private void Continue_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            LauncherStartup.InstallCurrent();
            LauncherSetup.Complete(root, new(launcher, DesktopChoice.IsChecked == true, StartChoice.IsChecked == true), desktop => LauncherStartup.CreateShortcut(root, desktop));
            context.SelectLauncher(launcher); context.SetLanguage(Localize.Language);
            Completed = true; DialogResult = true;
        }
        catch (Exception ex) { ErrorText.Text = Localize.Error(ex); }
    }
}
