using HolyLois.Core;
using System.Security.Cryptography;
using System.Text;

internal static class AppReleaseTests
{
    public static Task RunAsync(string root)
    {
        using var key = RSA.Create(3072); var pub = key.ExportSubjectPublicKeyInfoPem();
        SignedAppRelease Sign(string version, byte[] bytes, string? url = null)
        {
            var catalog = Encoding.ASCII.GetBytes($"holylois-app-v1\n{version}\n{url ?? "https://github.com/" + AppReleasePolicy.Repository + "/releases/download/v" + version + "/HolyLoisReborn.exe"}\n{Convert.ToHexStringLower(SHA256.HashData(bytes))}\n{bytes.Length}\n");
            return new(catalog,key.SignData(catalog,HashAlgorithmName.SHA256,RSASignaturePadding.Pss));
        }
        void Check(bool value,string message) { if (!value) throw new Exception(message); }
        void Reject(Action action) { try { action(); } catch (Exception e) when (e is IOException or InvalidDataException or CryptographicException) { return; } throw new Exception("Unsafe operation accepted."); }
        var old = Enumerable.Repeat((byte)1,2048).ToArray(); var next = Enumerable.Repeat((byte)2,4096).ToArray();
        var signed = Sign("0.5.1",next); var release = AppReleasePolicy.Parse(signed,pub);
        Check(release.NumericVersion == new Version(0,5,1,0),"Version normalization failed.");
        AppReleasePolicy.Accept(AppReleasePolicy.Parse(Sign("0.5.0",old),pub),new Version(0,5,0,0),null);
        var tampered = signed.Catalog.ToArray(); tampered[20] ^= 1;
        Reject(() => AppReleasePolicy.Parse(new(tampered,signed.Signature),pub));
        using (var other = RSA.Create(2048)) Reject(() => AppReleasePolicy.Parse(signed,other.ExportSubjectPublicKeyInfoPem()));
        foreach (var url in new[] { "http://github.com/" + AppReleasePolicy.Repository + "/releases/download/v1/HolyLoisReborn.exe", "https://github.com/pjampjam/HLMC-Reborn/releases/download/v1/HolyLoisReborn.exe", "https://github.com/" + AppReleasePolicy.Repository + "/releases/download/../HolyLoisReborn.exe", "https://github.com/" + AppReleasePolicy.Repository + "/releases/download/v1/HolyLoisReborn.exe?x=1" })
            Reject(() => AppReleasePolicy.Parse(Sign("0.5.1",next,url),pub));
        Reject(() => AppReleasePolicy.Parse(new(new byte[65537],signed.Signature),pub));
        Reject(() => AppReleasePolicy.Accept(release,new Version(0,6,0),null));
        Reject(() => AppReleasePolicy.Accept(AppReleasePolicy.Parse(Sign("0.5.1",old),pub),new Version(0,5,0),release));
        Console.WriteLine("PASS App signatures, bounded metadata, repository restrictions, normalized versions and downgrade rejection");

        string Folder(string name) { var dir = Path.Combine(root,name); Directory.CreateDirectory(dir); return dir; }
        string Candidate(string dir) { var path = Path.Combine(dir,"candidate.exe"); File.WriteAllBytes(path,next); return path; }
        var dir = Folder("app-commit"); var deployment = new AppDeployment(dir,pub);
        File.WriteAllBytes(deployment.Target,old); var nonce = deployment.Prepare(signed,Candidate(dir),new Version(0,5,0));
        Reject(() => deployment.Finalize(nonce)); Reject(() => deployment.Commit(Guid.NewGuid().ToString("N")));
        deployment.Commit(nonce); Check(File.ReadAllBytes(deployment.Target).SequenceEqual(next),"Verified app not committed.");
        Reject(() => deployment.Acknowledge(nonce,Path.Combine(dir,"candidate.exe")));
        deployment.Acknowledge(nonce,deployment.Target); deployment.Finalize(nonce);
        Check(deployment.Installed == release && deployment.Pending is null,"App receipt not finalized.");
        Check(File.ReadAllBytes(Path.Combine(dir,"rollback/HolyLoisReborn.exe")).SequenceEqual(old),"Rollback copy missing.");
        Console.WriteLine("PASS App replacement requires verified startup acknowledgement and retains rollback copy");

        dir = Folder("app-crash"); deployment = new AppDeployment(dir,pub) { AfterReplacement = () => throw new IOException("Simulated replacement crash.") };
        File.WriteAllBytes(deployment.Target,old); nonce = deployment.Prepare(signed,Candidate(dir),new Version(0,5,0));
        Reject(() => deployment.Commit(nonce)); Check(File.ReadAllBytes(deployment.Target).SequenceEqual(old) && deployment.Pending is null,"Replacement crash did not roll back.");
        Console.WriteLine("PASS Failure after atomic replacement restores the previous app");

        dir = Folder("app-corrupt"); deployment = new(dir,pub); File.WriteAllBytes(deployment.Target,old);
        nonce = deployment.Prepare(signed,Candidate(dir),new Version(0,5,0)); File.WriteAllBytes(deployment.Stage(release),old);
        Reject(() => deployment.Commit(nonce)); deployment.Rollback(nonce);
        Check(File.ReadAllBytes(deployment.Target).SequenceEqual(old),"Corrupted stage altered installed app.");
        Console.WriteLine("PASS Corrupted staging is rejected without altering installed app");

        dir = Folder("app-interrupted"); deployment = new(dir,pub); File.WriteAllBytes(deployment.Target,old);
        nonce = deployment.Prepare(signed,Candidate(dir),new Version(0,5,0)); deployment.Commit(nonce);
        new AppDeployment(dir,pub).Rollback(nonce);
        Check(File.ReadAllBytes(deployment.Target).SequenceEqual(old),"Interrupted unacknowledged update was not recoverable.");
        Console.WriteLine("PASS Interrupted update recovers using persistent transaction state");

        dir = Folder("app-fresh"); deployment = new(dir,pub); nonce = deployment.Prepare(signed,Candidate(dir),new Version(0,5,0));
        deployment.Commit(nonce); deployment.Rollback(nonce); Check(!File.Exists(deployment.Target),"Failed fresh update left an active app.");
        Console.WriteLine("PASS Failed first installation removes only its own uncommitted executable");
        return Task.CompletedTask;
    }
}
