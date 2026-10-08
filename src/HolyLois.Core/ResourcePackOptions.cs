using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace HolyLois.Core;

public static class ResourcePackOptions
{
    public static byte[] SetPack(byte[] original,string filename,bool enabled)
    {
        if(enabled)return AddNewPacks(original,[filename]);
        SafePaths.ValidateRelative(filename);
        if(filename.Contains('/')||!filename.EndsWith(".zip",StringComparison.Ordinal))throw new InvalidDataException("Invalid resource-pack filename.");
        if(original.Length>1024*1024)throw new InvalidDataException("Minecraft options are too large.");
        var text=new UTF8Encoding(false,true).GetString(original);
        var matches=Regex.Matches(text,@"(?m)^resourcePacks:([^\r\n]*)");
        if(matches.Count!=1)throw new InvalidDataException("Resource-pack options could not be read.");
        var match=matches[0];var current=JsonSerializer.Deserialize<List<string>>(match.Groups[1].Value)??throw new InvalidDataException("Resource-pack options are empty.");
        if(current.RemoveAll(value=>value=="file/"+filename)==0)return original;
        return new UTF8Encoding(false).GetBytes(text[..match.Index]+"resourcePacks:"+JsonSerializer.Serialize(current)+text[(match.Index+match.Length)..]);
    }
    public static byte[] AddNewPacks(byte[] original, IEnumerable<string> filenames)
    {
        if (original.Length > 1024 * 1024) throw new InvalidDataException("Minecraft options are too large; settings were preserved.");
        var text = new UTF8Encoding(false, true).GetString(original);
        var matches = Regex.Matches(text, @"(?m)^resourcePacks:([^\r\n]*)");
        if (matches.Count != 1) throw new InvalidDataException("Resource-pack options could not be read; settings were preserved.");
        var match = matches[0];
        var current = JsonSerializer.Deserialize<List<string>>(match.Groups[1].Value)
            ?? throw new InvalidDataException("Resource-pack options are empty.");
        var changed = false;
        foreach (var filename in filenames)
        {
            SafePaths.ValidateRelative(filename);
            if (filename.Contains('/') || !filename.EndsWith(".zip", StringComparison.Ordinal))
                throw new InvalidDataException("Invalid resource-pack filename.");
            var value = "file/" + filename;
            if (!current.Contains(value, StringComparer.Ordinal)) { current.Add(value); changed = true; }
        }
        if (!changed) return original;
        var updated = text[..match.Index] + "resourcePacks:" + JsonSerializer.Serialize(current) + text[(match.Index + match.Length)..];
        return new UTF8Encoding(false).GetBytes(updated);
    }
}
