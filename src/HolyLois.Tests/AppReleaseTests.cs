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

        dir = Folder("app-storage-pending"); deployment = new(dir,pub); File.WriteAllBytes(deployment.Target,old);
        nonce = deployment.Prepare(signed,Candidate(dir),new Version(0,5,0));
        var cache = Path.Combine(dir,"app-downloads",release.Sha256 + ".verified");
        Directory.CreateDirectory(Path.GetDirectoryName(cache)!); File.WriteAllBytes(cache,next);
        Check(deployment.PruneCompleted().Files == 0 && File.Exists(cache) && File.Exists(deployment.Stage(release)),"Pending update files were pruned.");
        deployment.Commit(nonce); deployment.Acknowledge(nonce,deployment.Target); deployment.Finalize(nonce);
        File.WriteAllBytes(cache,next);
        Directory.CreateDirectory(Path.GetDirectoryName(deployment.Stage(release))!); File.WriteAllBytes(deployment.Stage(release),next);
        var unknown = Path.Combine(dir,"app-downloads","personal-note.txt"); File.WriteAllText(unknown,"keep");
        var unknownStage = Path.Combine(dir,"updates","custom-files"); Directory.CreateDirectory(unknownStage); File.WriteAllText(Path.Combine(unknownStage,"keep.txt"),"keep");
        var corruptCache = Path.Combine(dir,"app-downloads",new string('a',64) + ".verified"); File.WriteAllBytes(corruptCache,old);
        var removed = deployment.PruneCompleted([deployment.Stage(release)]);
        Check(removed.Files == 1 && removed.Bytes == next.Length && !File.Exists(cache),"Completed app download was not removed.");
        Check(File.Exists(deployment.Stage(release)) && File.Exists(unknown) && File.Exists(corruptCache)
            && Directory.Exists(unknownStage) && File.Exists(Path.Combine(dir,"rollback/HolyLoisReborn.exe")),"Active worker, rollback or unknown file was removed.");
        Check(deployment.PruneCompleted().Files == 1 && !File.Exists(deployment.Stage(release)),"Exited worker stage was not pruned.");
        var oldPartial = cache + "." + Guid.NewGuid().ToString("N") + ".part"; File.WriteAllBytes(oldPartial,old); File.SetLastWriteTimeUtc(oldPartial,DateTime.UtcNow.AddDays(-2));
        var newPartial = cache + "." + Guid.NewGuid().ToString("N") + ".part"; File.WriteAllBytes(newPartial,old);
        Check(deployment.PruneCompleted().Files == 1 && !File.Exists(oldPartial) && File.Exists(newPartial),"Partial download cleanup did not preserve recent work.");
        var migrationCopy = Path.Combine(dir,"rollback/before-name-migration.exe"); File.WriteAllBytes(migrationCopy,old);
        Check(deployment.PruneCompleted().Files == 1 && !File.Exists(migrationCopy),"Duplicate migration rollback was retained.");
        File.WriteAllBytes(migrationCopy,next);
        Check(deployment.PruneCompleted().Files == 0 && File.Exists(migrationCopy),"Distinct migration recovery copy was removed.");
        Console.WriteLine("PASS Completed update copies are reclaimed while pending, active, rollback and unknown files are preserved");

        dir = Folder("app-storage-no-receipt"); deployment = new(dir,pub); File.WriteAllBytes(deployment.Target,old);
        cache = Path.Combine(dir,"app-downloads",release.Sha256 + ".verified"); Directory.CreateDirectory(Path.GetDirectoryName(cache)!); File.WriteAllBytes(cache,next);
        Check(deployment.PruneCompleted().Files == 0 && File.Exists(cache),"Unverified installation authorized cleanup.");
        Console.WriteLine("PASS Storage cleanup requires a signed receipt and matching installed app");

        dir = Folder("worker-storage/HolyLoisReborn-Maintenance");
        var cutoff = DateTime.UtcNow.AddDays(-1);
        string Worker(string label, DateTime modified)
        {
            var folder = Path.Combine(dir,label); Directory.CreateDirectory(folder);
            var file = Path.Combine(folder,"HolyLoisReborn.exe"); File.WriteAllBytes(file,old); File.SetLastWriteTimeUtc(file,modified); return file;
        }
        var exited = Worker(Guid.NewGuid().ToString("N"),cutoff.AddDays(-1));
        var active = Worker(Guid.NewGuid().ToString("N"),cutoff.AddDays(-1));
        var recent = Worker(Guid.NewGuid().ToString("N"),DateTime.UtcNow);
        var userFolder = Worker(Guid.NewGuid().ToString("N"),cutoff.AddDays(-1)); File.WriteAllText(Path.Combine(Path.GetDirectoryName(userFolder)!,"personal.txt"),"keep");
        var unrecognized = Worker("my-folder",cutoff.AddDays(-1));
        removed = AppStorageCleanup.TemporaryWorkers(dir,[active],cutoff);
        Check(removed.Files == 1 && !File.Exists(exited) && File.Exists(active) && File.Exists(recent) && File.Exists(userFolder) && File.Exists(unrecognized),"Worker cleanup did not respect lifetime or ownership boundaries.");
        Reject(() => AppStorageCleanup.TemporaryWorkers(Folder("unowned-worker-root"),[],cutoff));
        Console.WriteLine("PASS Old temporary workers are reclaimed without touching running, recent or user folders");

        dir = Folder("runtime-storage/.net/HolyLoisReborn");
        var nativeNames = new[] { "D3DCompiler_47_cor3.dll", "PenImc_cor3.dll", "PresentationNative_cor3.dll", "vcruntime140_cor3.dll", "wpfgfx_cor3.dll" };
        string Runtime(string label, DateTime modified)
        {
            var folder = Path.Combine(dir,label); Directory.CreateDirectory(folder);
            foreach(var name in nativeNames) { var file = Path.Combine(folder,name); File.WriteAllBytes(file,old); File.SetLastWriteTimeUtc(file,modified); }
            return folder;
        }
        var oldRuntime = Runtime("old-runtime-cache",cutoff.AddDays(-1));
        var currentRuntime = Runtime("active-runtime-cache",cutoff.AddDays(-1));
        var newRuntime = Runtime("new-runtime-cache",DateTime.UtcNow);
        var unknownRuntime = Runtime("user-runtime-cache",cutoff.AddDays(-1)); File.WriteAllText(Path.Combine(unknownRuntime,"personal.txt"),"keep");
        removed = AppStorageCleanup.NativeRuntime(dir,[Path.Combine(currentRuntime,nativeNames[0])],cutoff);
        Check(removed.Files == 5 && !Directory.Exists(oldRuntime) && Directory.Exists(currentRuntime) && Directory.Exists(newRuntime) && Directory.Exists(unknownRuntime),"Native cleanup removed an active, recent or unknown runtime folder.");
        Reject(() => AppStorageCleanup.NativeRuntime(Folder("unowned-native-root"),[],cutoff));
        Console.WriteLine("PASS Stale native extractions are bounded to the launcher cache and preserve loaded modules");
        return Task.CompletedTask;
    }
}
