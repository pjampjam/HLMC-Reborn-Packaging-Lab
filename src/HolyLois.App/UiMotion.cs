using System.Windows;
using System.Windows.Media.Animation;

namespace HolyLois.App;

public static class UiMotion
{
    public static readonly DependencyProperty FadeOnRevealProperty = DependencyProperty.RegisterAttached(
        "FadeOnReveal", typeof(bool), typeof(UiMotion), new PropertyMetadata(false, RevealChanged));

    public static bool GetFadeOnReveal(DependencyObject element) => (bool)element.GetValue(FadeOnRevealProperty);
    public static void SetFadeOnReveal(DependencyObject element, bool value) => element.SetValue(FadeOnRevealProperty, value);

    private static void RevealChanged(DependencyObject source, DependencyPropertyChangedEventArgs args)
    {
        if (source is not UIElement element) return;
        if ((bool)args.NewValue) element.IsVisibleChanged += VisibleChanged;
        else element.IsVisibleChanged -= VisibleChanged;
    }

    private static void VisibleChanged(object sender, DependencyPropertyChangedEventArgs args)
    {
        if ((bool)args.NewValue) FadeIn((UIElement)sender);
        else ((UIElement)sender).BeginAnimation(UIElement.OpacityProperty, null);
    }

    public static void FadeIn(UIElement element, double from = 0.82)
        => FadeIn(element, from, SystemParameters.ClientAreaAnimation && !SystemParameters.HighContrast);

    internal static void FadeIn(UIElement element, double from, bool enabled)
    {
        element.BeginAnimation(UIElement.OpacityProperty, null);
        if (!enabled || !element.IsVisible) return;
        var animation = new DoubleAnimation(from, 1, TimeSpan.FromMilliseconds(120)) { FillBehavior = FillBehavior.Stop };
        element.BeginAnimation(UIElement.OpacityProperty, animation, HandoffBehavior.SnapshotAndReplace);
    }
}
