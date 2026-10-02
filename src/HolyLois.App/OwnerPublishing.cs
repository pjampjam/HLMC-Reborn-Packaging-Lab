using HolyLois.Core;
using System.Diagnostics;
using System.IO;
using System.Text;

namespace HolyLois.App;

public static class OwnerPublishing
{
    public static async Task<string> PublishAsync(string root, string prepared, Action<string>? progress = null)
    {
        root = Path.GetFullPath(root); prepared = Path.GetFullPath(prepared);
        SafePaths.RejectLinks(prepared);
        var manifest = ManifestSecurity.Parse(File.ReadAllBytes(Path.Combine(prepared,"pack.json")),
            File.ReadAllBytes(Path.Combine(prepared,"pack.json.sig")), File.ReadAllText(Path.Combine(root,"assets","release-public.pem")));
        var tag = "pack-v" + manifest.Version;
        if (manifest.Defaults is not { } defaults || defaults.Url != "https://github.com/pjampjam/HLMC-Reborn/releases/download/"+tag+"/defaults.zip"
            || !AtomicFiles.Matches(Path.Combine(prepared,"defaults.zip"), defaults)) throw new IOException("Prepared settings do not match the signed release.");
        var gh = Path.GetFullPath(Path.Combine(root,"..","toolchain","github-cli","bin","gh.exe"));
        await Run(gh,root,"auth","status");
        var stable=Path.Combine(prepared,"stable-check");Directory.CreateDirectory(stable);
        await Run(gh,root,"release","download","pack-stable","--repo","pjampjam/HLMC-Reborn","--pattern","pack.json*","--dir",stable,"--clobber");
        var current=ManifestSecurity.Parse(File.ReadAllBytes(Path.Combine(stable,"pack.json")),File.ReadAllBytes(Path.Combine(stable,"pack.json.sig")),File.ReadAllText(Path.Combine(root,"assets","release-public.pem")));
        if(new Version(manifest.Version)<new Version(current.Version) || manifest.Version==current.Version && !File.ReadAllBytes(Path.Combine(prepared,"pack.json")).SequenceEqual(File.ReadAllBytes(Path.Combine(stable,"pack.json"))))
            throw new IOException("The published pack is already this version or newer. Choose a higher version; released versions cannot be replaced.");
        var body = Path.Combine(prepared,"release-notes.txt");
        File.WriteAllText(body,(manifest.History?.FirstOrDefault()?.Summary??"Pack update.").Replace('\u2014','-'),Encoding.UTF8);
        progress?.Invoke("Publishing checked files to your GitHub releases...");
        // An interrupted publish can be retried, but immutable settings must match exactly.
        var existing = await TryRun(gh,root,"release","view",tag,"--repo","pjampjam/HLMC-Reborn","--json","tagName");
        if (existing.Code == 0)
        {
            var downloaded = Path.Combine(prepared,"published-check"); Directory.CreateDirectory(downloaded);
            await Run(gh,root,"release","download",tag,"--repo","pjampjam/HLMC-Reborn","--pattern","defaults.zip","--dir",downloaded,"--clobber");
            if (!AtomicFiles.Matches(Path.Combine(downloaded,"defaults.zip"),defaults)) throw new IOException("This release number already has different settings. Choose a higher version.");
        }
        else await Run(gh,root,"release","create",tag,Path.Combine(prepared,"defaults.zip"),"--repo","pjampjam/HLMC-Reborn",
            "--title","Holy Lois: Reborn - Pack "+manifest.Version,"--notes-file",body,"--latest=false");
        await Run(gh,root,"release","upload","pack-stable",Path.Combine(prepared,"pack.json"),Path.Combine(prepared,"pack.json.sig"),"--repo","pjampjam/HLMC-Reborn","--clobber");
        foreach (var file in new[]{"pack.json","pack.json.sig","defaults.zip"}) AtomicFiles.Write(Path.Combine(root,"assets",file),File.ReadAllBytes(Path.Combine(prepared,file)));
        return manifest.Version;
    }
    private static async Task<string> Run(string executable,string directory,params string[] arguments)
    {
        var result=await TryRun(executable,directory,arguments);
        if(result.Code!=0)throw new IOException(string.IsNullOrWhiteSpace(result.Error)?result.Output:result.Error);
        return result.Output;
    }
    private static async Task<(int Code,string Output,string Error)> TryRun(string executable,string directory,params string[] arguments)
    {
        var start=new ProcessStartInfo(executable){WorkingDirectory=directory,UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true};
        foreach(var argument in arguments)start.ArgumentList.Add(argument);
        using var process=Process.Start(start)!;var output=process.StandardOutput.ReadToEndAsync();var error=process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();return(process.ExitCode,await output,await error);
    }
}
