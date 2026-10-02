using System.Globalization;
using System.Text.Json;
using System.Windows;
using System.Windows.Markup;
namespace HolyLois.App;
public static class Localize
{
    private static readonly Dictionary<string, Dictionary<string, string>> all = JsonSerializer.Deserialize<Dictionary<string, Dictionary<string, string>>>(ClientContext.Asset("strings.json"))!;
    public static string Language { get; private set; } = "en";
    public static string Text(string key) => all[Language].GetValueOrDefault(key) ?? all["en"].GetValueOrDefault(key) ?? key;
    public static void Apply(Window window, string language)
    {
        Language = all.ContainsKey(language) ? language : "en";
        var culture = CultureInfo.GetCultureInfo(Language);
        CultureInfo.CurrentUICulture = culture; CultureInfo.CurrentCulture = culture;
        window.Language = XmlLanguage.GetLanguage(culture.IetfLanguageTag);
        foreach (var (key, value) in all["en"]) Application.Current.Resources["L." + key] = all[Language].GetValueOrDefault(key) ?? value;
    }
    public static string Error(Exception exception) => exception.Message.StartsWith("Close Minecraft", StringComparison.Ordinal)
        ? Text("CloseGame") : Text("Error") + " " + exception.Message;
}
