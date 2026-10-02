using System.Windows;
namespace HolyLois.App;

public static class InputModality
{
    public static readonly DependencyProperty KeyboardFocusVisibleProperty = DependencyProperty.RegisterAttached(
        "KeyboardFocusVisible",typeof(bool),typeof(InputModality),new FrameworkPropertyMetadata(false,FrameworkPropertyMetadataOptions.Inherits));
    public static bool GetKeyboardFocusVisible(DependencyObject element) => (bool)element.GetValue(KeyboardFocusVisibleProperty);
    public static void SetKeyboardFocusVisible(DependencyObject element,bool value) => element.SetValue(KeyboardFocusVisibleProperty,value);
}
