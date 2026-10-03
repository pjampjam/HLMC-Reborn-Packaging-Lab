using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace HolyLois.App;

internal static class WindowCaption
{
    public static void Apply(Window window)
    {
        var handle = new WindowInteropHelper(window).Handle;
        if (handle == IntPtr.Zero) return;
        // Preserve native resizing, Snap Layouts and accessible caption buttons.
        Set(handle, 20, 1);
        Set(handle, 35, 0x00151211); // Canvas #111215, COLORREF (BGR)
        Set(handle, 36, 0x00E8F0F3); // Text #F3F0E8
        Set(handle, 34, 0x00151211);
    }

    private static void Set(IntPtr handle, int attribute, int value)
    {
        // Unsupported attributes on older Windows versions leave native defaults intact.
        _ = DwmSetWindowAttribute(handle, attribute, ref value, sizeof(int));
    }

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);
}
