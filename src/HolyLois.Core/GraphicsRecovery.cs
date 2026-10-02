using System.Text;
using System.Text.RegularExpressions;

namespace HolyLois.Core;

public static class GraphicsRecovery
{
    public static void DisableShaders(string instance, string backupRoot)
    {
        var path = SafePaths.Resolve(instance, "config/iris.properties");
        if (!File.Exists(path)) throw new IOException("Launch this pack once before changing shader settings.");
        if (new FileInfo(path).Length > 65536) throw new InvalidDataException("Shader settings exceed their size limit.");
        var before = File.ReadAllBytes(path);
        var text = Encoding.UTF8.GetString(before);
        var pattern = @"(?m)^enableShaders=[^\r\n]*";
        if (Regex.Matches(text, pattern).Count != 1) throw new InvalidDataException("Shader setting is missing or duplicated; settings were kept.");
        var after = Regex.Replace(text, pattern, "enableShaders=false");
        if (after == text) return;
        AtomicFiles.Write(SafePaths.Resolve(backupRoot, "iris-before-recovery.properties"), before);
        AtomicFiles.Write(path, Encoding.UTF8.GetBytes(after));
    }
}
