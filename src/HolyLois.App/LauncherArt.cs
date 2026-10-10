using HolyLois.Core;
using System.IO;
using System.Net.Http;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace HolyLois.App;

/// <summary>Screenshots from the server (Brand/pics) and the player's skin head.</summary>
public static class LauncherArt
{
    private static readonly Random random = new();
    private static readonly string[] Islands = ["island-sunny", "island-sunny-2", "island-rainy", "island-thunder"];

    /// <summary>
    /// The main picture of a kind, now and then a rare variant (owner's choice: cow 3 and villager 3 are the main ones; the mushroom
    /// island shows a random weather). Development renders and tests always get the main picture.
    /// </summary>
    public static ImageSource Pick(string kind, bool variants)
    {
        var name = kind switch
        {
            "island" => variants ? Islands[random.Next(Islands.Length)] : Islands[0],
            "cow" => variants && random.Next(8) == 0 ? "cow-rare-" + (1 + random.Next(2)) : "cow-main",
            "villager" => variants && random.Next(8) == 0 ? "villager-rare-" + (1 + random.Next(3)) : "villager-main",
            _ => kind,
        };
        var image = new BitmapImage();
        image.BeginInit(); image.UriSource = new Uri("pack://application:,,,/Assets/pics/" + name + ".jpg"); image.CacheOption = BitmapCacheOption.OnLoad; image.EndInit();
        image.Freeze();
        return image;
    }

    private static readonly HttpClient http = new() { Timeout = TimeSpan.FromSeconds(10) };

    /// <summary>The 8x8 face plus the hat layer of the player's selected skin, scaled up without smoothing; null when unknown or offline.</summary>
    public static async Task<ImageSource?> HeadAsync(string name, CancellationToken cancel)
    {
        try
        {
            var stats = await http.GetStringAsync(SkinHeads.Feed, cancel);
            if (SkinHeads.Find(stats, name) is not { } texture) return null;
            using var response = await http.GetAsync(texture, HttpCompletionOption.ResponseHeadersRead, cancel);
            if (!response.IsSuccessStatusCode || response.Content.Headers.ContentLength > SkinHeads.MaxTextureBytes) return null;
            var bytes = await response.Content.ReadAsByteArrayAsync(cancel);
            if (bytes.Length > SkinHeads.MaxTextureBytes) return null;
            var skin = BitmapFrame.Create(new MemoryStream(bytes), BitmapCreateOptions.None, BitmapCacheOption.OnLoad);
            if (skin.PixelWidth != 64 || skin.PixelHeight is not (64 or 32)) return null;
            var group = new DrawingGroup();
            RenderOptions.SetBitmapScalingMode(group, BitmapScalingMode.NearestNeighbor);
            group.Children.Add(new ImageDrawing(new CroppedBitmap(skin, new Int32Rect(8, 8, 8, 8)), new Rect(0, 0, 8, 8)));
            group.Children.Add(new ImageDrawing(new CroppedBitmap(skin, new Int32Rect(40, 8, 8, 8)), new Rect(0, 0, 8, 8)));
            var head = new DrawingImage(group); head.Freeze();
            return head;
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or IOException or NotSupportedException or ArgumentException or FileFormatException) { return null; }
    }
}
