using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.Json;

namespace HolyLois.App;

public static class LauncherDiscovery
{
    private static readonly Lazy<Task<Dictionary<string, string>>> registered = new(() => Task.Run(ReadStartApps));
    public static bool Ready => registered.Value.IsCompleted;
    public static async Task WarmAsync() => await registered.Value;
    public static string? Registered(string launcher) => registered.Value.IsCompletedSuccessfully
        ? registered.Value.Result.GetValueOrDefault(launcher) : null;

    private static Dictionary<string, string> ReadStartApps()
    {
        try
        {
            var start = new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "WindowsPowerShell/v1.0/powershell.exe"))
            { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true, StandardOutputEncoding = Encoding.UTF8 };
            foreach (var argument in new[] { "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", "[Console]::OutputEncoding=[Text.UTF8Encoding]::new(); @(Get-StartApps | Where-Object { $_.Name -eq 'Minecraft Launcher' -or $_.Name -match '^SKlauncher( |$)' } | Select-Object Name,AppID) | ConvertTo-Json -Compress" }) start.ArgumentList.Add(argument);
            using var process = Process.Start(start)!;
            var outputTask = process.StandardOutput.ReadToEndAsync();
            var errorTask = process.StandardError.ReadToEndAsync();
            if (!process.WaitForExit(7000)) { process.Kill(); return []; }
            var output = outputTask.GetAwaiter().GetResult();
            if (process.ExitCode != 0 || output.Length > 65536 || string.IsNullOrWhiteSpace(output)) return [];
            using var json = JsonDocument.Parse(output);
            var result = new Dictionary<string, string>();
            foreach (var entry in json.RootElement.EnumerateArray())
            {
                var name = entry.GetProperty("Name").GetString()!; var id = entry.GetProperty("AppID").GetString()!;
                if (id.Any(char.IsControl) || id.Length > 512) continue;
                var key = name == "Minecraft Launcher" ? "official" : "sk";
                result[key] = File.Exists(id) ? id : "shell:AppsFolder\\" + id;
            }
            return result;
        }
        catch { return []; }
    }
}
