using HolyLois.Core;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace HolyLois.App;

/// <summary>First run in three short steps: welcome, how you play, shortcuts. Pictures are the owner's screenshots from the server.</summary>
public partial class SetupWindow : ThemedWindow
{
    private readonly ClientContext context;
    private readonly string root;
    private string launcher;
    private int step;
    public bool Completed { get; private set; }

    public SetupWindow(ClientContext context, string root)
    {
        this.context = context; this.root = root; launcher = context.Settings.Launcher;
        InitializeComponent(); Localize.Apply(this, context.Settings.Language);
        SetupNameBox.AddHandler(System.Windows.Input.Mouse.PreviewMouseWheelEvent, new System.Windows.Input.MouseWheelEventHandler(Name_MouseWheel), true);
        LanguageChoice.SelectedIndex = context.Settings.Language == "ru" ? 1 : context.Settings.Language == "lv" ? 2 : 0;
        var variants = !context.IsIsolated;
        Picture(WelcomePicture, LauncherArt.Pick("cow", variants), "0,0.04,1,0.92");
        Picture(OfficialPicture, LauncherArt.Pick("villager", variants), "0.08,0.3,0.84,0.7", AlignmentY.Bottom);
        Picture(NamePicture, LauncherArt.Pick("island", variants), "0.12,0.25,0.76,0.62");
        Picture(FinishPicture, LauncherArt.Pick("claims-map", false), "0,0.1,1,0.8");
        Refresh();
    }
    // The villagers stand low in their shots, so that picture keeps its bottom edge when the frame crops it.
    private static void Picture(Border frame, ImageSource image, string viewbox, AlignmentY vertical = AlignmentY.Center) =>
        frame.Background = new ImageBrush(image) { Stretch = Stretch.UniformToFill, Viewbox = Rect.Parse(viewbox), ViewboxUnits = BrushMappingMode.RelativeToBoundingBox, AlignmentX = AlignmentX.Center, AlignmentY = vertical };
    private void Name_MouseWheel(object sender, System.Windows.Input.MouseWheelEventArgs e)
    {
        SetupScroll.ScrollToVerticalOffset(SetupScroll.VerticalOffset - e.Delta);
        e.Handled = true;
    }
    /// <summary>Step 0 welcome, 1 how you play, 2 shortcuts and finish. Used by the setup checks too.</summary>
    public void GoTo(int target) { step = Math.Clamp(target, 0, 2); ErrorText.Text = ""; SetupScroll.ScrollToTop(); Refresh(); }
    private void Refresh()
    {
        WelcomeStep.Visibility = step == 0 ? Visibility.Visible : Visibility.Collapsed;
        ModeStep.Visibility = step == 1 ? Visibility.Visible : Visibility.Collapsed;
        FinishStep.Visibility = step == 2 ? Visibility.Visible : Visibility.Collapsed;
        BackButton.Visibility = step == 0 ? Visibility.Hidden : Visibility.Visible;
        ContinueLabel.Text = Localize.Text(step == 2 ? "SetupFinish" : "SetupNextStep");
        StepText.Text = string.Format(Localize.Text("SetupStep"), step + 1, 3);
        StepDots.Children.Clear();
        for (var n = 0; n < 3; n++)
            StepDots.Children.Add(new Ellipse { Width = 8, Height = 8, Margin = new Thickness(4, 0, 4, 0), Fill = (Brush)FindResource(n == step ? "Gold" : "LineStrong") });
        var sk = launcher != "official"; var accent = (Brush)FindResource("Gold"); var neutral = (Brush)FindResource("Line");
        OfficialSelected.Visibility = sk ? Visibility.Hidden : Visibility.Visible;
        SkSelected.Visibility = sk ? Visibility.Visible : Visibility.Hidden;
        OfficialCard.BorderBrush = sk ? neutral : accent; SkCard.BorderBrush = sk ? accent : neutral;
        // The chosen way gets a warm tint as well as the gold outline and "Selected" label.
        var tint = (Brush)FindResource("GoldSoft"); var plain = (Brush)FindResource("Control");
        OfficialCard.Background = sk ? plain : tint; SkCard.Background = sk ? tint : plain;
        SetupNamePanel.Visibility = sk ? Visibility.Visible : Visibility.Collapsed;
        if (sk && SetupNameBox.Text.Length == 0) SetupNameBox.Text = context.SuggestedPlayerName() ?? "";
        var megabytes = context.Manifest.Files.Sum(f => f.Size) / 1048576;
        Facts.Children.Clear();
        foreach (var (column, title, text) in new[] { (0, string.Format(Localize.Text("FactSizeTitle"), megabytes), "FactSizeText"), (2, Localize.Text("FactUpdatesTitle"), "FactUpdatesText"), (4, Localize.Text("FactKeepTitle"), "FactKeepText") })
        {
            var fact = new StackPanel();
            fact.Children.Add(new TextBlock { Text = title, FontSize = 14, FontWeight = FontWeights.SemiBold, Foreground = (Brush)FindResource("Text") });
            fact.Children.Add(new TextBlock { Text = Localize.Text(text), FontSize = 12, Foreground = (Brush)FindResource("Muted"), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 3, 0, 0) });
            var card = new Border { Child = fact, Background = (Brush)FindResource("SurfaceRaised"), BorderBrush = (Brush)FindResource("Line"), BorderThickness = new Thickness(1), CornerRadius = (CornerRadius)FindResource("Radius"), Padding = new Thickness(12, 10, 12, 10) };
            Grid.SetColumn(card, column); Facts.Children.Add(card);
        }
        // What happens next, numbered: the three things that really follow, for the chosen way to play.
        NextSteps.Children.Clear();
        var steps = new[] { string.Format(Localize.Text("Next1"), megabytes), Localize.Text(sk ? "Next2Name" : "Next2Account"), Localize.Text(sk ? "Next3Name" : "Next3Account") };
        for (var n = 0; n < steps.Length; n++)
        {
            var row = new Grid { Margin = new Thickness(0, 8, 0, 0) };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(32) }); row.ColumnDefinitions.Add(new ColumnDefinition());
            row.Children.Add(new Border { Width = 22, Height = 22, CornerRadius = new CornerRadius(11), Background = (Brush)FindResource("Control"), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top,
                Child = new TextBlock { Text = (n + 1).ToString(), FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = (Brush)FindResource("Text"), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center } });
            var text = new TextBlock { Text = steps[n], FontSize = 13, TextWrapping = TextWrapping.Wrap, VerticalAlignment = VerticalAlignment.Center };
            Grid.SetColumn(text, 1); row.Children.Add(text); NextSteps.Children.Add(row);
        }
    }
    private void Official_Click(object sender, RoutedEventArgs e) { launcher = "official"; Refresh(); }
    // A reset setup of a SKlauncher player keeps that folder; new players get a plain player name.
    private void Sk_Click(object sender, RoutedEventArgs e) { launcher = context.Settings.Launcher == "sk" ? "sk" : "name"; Refresh(); SetupNameBox.Focus(); }
    private void Language_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (LanguageChoice.SelectedItem is ComboBoxItem item) { Localize.Apply(this, (string)item.Tag); if (IsInitialized && StepDots is not null) Refresh(); }
    }
    private void Rules_Click(object sender, RoutedEventArgs e) => ClientContext.OpenUrl("https://holylois.com/rules");
    private void Privacy_Click(object sender, RoutedEventArgs e) => ClientContext.OpenUrl("https://holylois.com/privacy");
    private void Back_Click(object sender, RoutedEventArgs e) => GoTo(step - 1);
    private async void Continue_Click(object sender, RoutedEventArgs e)
    {
        var name = SetupNameBox.Text.Trim();
        if (step == 1 && launcher != "official" && !PlayerNames.IsValid(name)) { ErrorText.Text = Localize.Text("NameInvalid"); SetupNameBox.Focus(); return; }
        if (step < 2) { GoTo(step + 1); return; }
        try
        {
            ContinueButton.IsEnabled = BackButton.IsEnabled = OfficialCard.IsEnabled = SkCard.IsEnabled = LanguageChoice.IsEnabled = false; SetupProgress.Visibility = Visibility.Visible; ErrorText.Text = Localize.Text("Finishing");
            await Task.Run(LauncherStartup.InstallCurrent);
            LauncherSetup.Complete(root, new(launcher, DesktopChoice.IsChecked == true, StartChoice.IsChecked == true), desktop => LauncherStartup.CreateShortcut(root, desktop));
            context.SelectLauncher(launcher); context.SetLanguage(Localize.Language);
            if (launcher != "official") context.UsePlayerName(name);
            Completed = true; DialogResult = true;
        }
        catch (Exception ex) { ErrorText.Text = Localize.Error(ex); }
        finally { ContinueButton.IsEnabled = BackButton.IsEnabled = OfficialCard.IsEnabled = SkCard.IsEnabled = LanguageChoice.IsEnabled = true; SetupProgress.Visibility = Visibility.Collapsed; }
    }
}
