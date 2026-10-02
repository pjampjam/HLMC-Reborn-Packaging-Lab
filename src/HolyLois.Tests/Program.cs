using HolyLois.Core;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

var root = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "test-output", Guid.NewGuid().ToString("N")));
Directory.CreateDirectory(root);
var tests = new List<(string Name, Func<Task> Run)>();
var passed = 0;
void Check(bool value, string message) { if (!value) throw new Exception(message); }
async Task Throws(Func<Task> action) { try { await action(); } catch (Exception ex) when (ex is IOException or InvalidDataException or OperationCanceledException or CryptographicException) { return; } throw new Exception("Expected a rejected operation."); }
string Dir(string test) { var d = Path.Combine(root, test); Directory.CreateDirectory(d); return d; }
PackFile FileSpec(string path, string value) { var b = Encoding.UTF8.GetBytes(value); return new(path, "https://cdn.modrinth.com/test.jar", b.Length, Convert.ToHexStringLower(SHA256.HashData(b))); }
PackManifest Manifest(params PackFile[] files) => new(1, "1.1.0", "26.3", "0.19.5", 25, "79.76.40.155:25565", files, []);
tests.Add(("First setup choices, cancelled setup, deletion and legacy migration", () => {
    var fresh = Dir("setup-fresh"); var calls = new List<bool>();
    Check(LauncherSetup.NeedsSetup(fresh,false), "Fresh installation skipped setup.");
    Check(LauncherSetup.NeedsSetup(fresh,false), "Closing setup unexpectedly recorded choices.");
    LauncherSetup.Complete(fresh,new("sk",false,true),calls.Add);
    Check(calls.SequenceEqual(new[] { false }), "Opted-out Desktop shortcut was created.");
    LauncherSetup.Complete(fresh,new("official",true,true),calls.Add);
    Check(calls.Count == 1 && !LauncherSetup.NeedsSetup(fresh,false), "Later launch recreated shortcuts.");
    var neither = Dir("setup-neither"); LauncherSetup.Complete(neither,new("official",false,false),calls.Add);
    Check(calls.Count == 1, "No-shortcut choice ignored.");
    var both = Dir("setup-both"); LauncherSetup.Complete(both,new("official",true,true),calls.Add);
    Check(calls.Skip(1).SequenceEqual(new[] { true,false }), "Both shortcut choices not respected.");
    var legacy = Dir("setup-legacy"); System.IO.File.WriteAllText(Path.Combine(legacy,"shortcuts-initialized.txt"),"old");
    Check(!LauncherSetup.NeedsSetup(legacy,false), "Legacy deleted shortcuts would be recreated.");
    LauncherSetup.Migrate(legacy,"sk"); Check(!LauncherSetup.NeedsSetup(legacy,false), "Migration did not persist.");
    Check(!LauncherSetup.NeedsSetup(Dir("setup-settings"),true), "Existing player was forced through first setup.");
    return Task.CompletedTask;
}));
tests.Add(("Reject traversal, reserved names, personal-file manifests and HTTP", async () => {
    foreach (var p in new[] { "../mods/a.jar", "mods/CON.jar", "mods/a.jar:stream", "mods\\a.jar", "mods/a. /b.jar" })
        await Throws(() => { SafePaths.ValidateRelative(p); return Task.CompletedTask; });
    await Throws(() => { ManifestSecurity.Validate(Manifest(FileSpec("saves/level.dat", "world"))); return Task.CompletedTask; });
    await Throws(() => { ManifestSecurity.ValidateDownloadUri("https://cdn.modrinth.com.evil.example/a.jar"); return Task.CompletedTask; });
    await Throws(() => { ManifestSecurity.ValidateDownloadUri("http://cdn.modrinth.com/a.jar"); return Task.CompletedTask; });
}));
tests.Add(("Verify release signature and reject tampering", async () => {
    using var rsa = RSA.Create(2048); var json = JsonSerializer.SerializeToUtf8Bytes(Manifest(FileSpec("mods/a.jar", "data")), JsonSettings.Options);
    var signature = rsa.SignData(json, HashAlgorithmName.SHA256, RSASignaturePadding.Pss);
    Check(ManifestSecurity.Parse(json, signature, rsa.ExportSubjectPublicKeyInfoPem()).Files.Length == 1, "Valid signature failed.");
    json[json.Length / 2] ^= 1;
    await Throws(() => { ManifestSecurity.Parse(json, signature, rsa.ExportSubjectPublicKeyInfoPem()); return Task.CompletedTask; });
}));
tests.Add(("Failed and corrupted downloads leave active files untouched", async () => {
    var d = Dir("download-failure"); var game = Path.Combine(d, "game"); var state = Path.Combine(d, "state");
    var down = new FakeDownloader(Path.Combine(d, "cache")); down.Data["mods/a.jar"] = "old";
    var installer = new PackInstaller(game, state, down); await installer.InstallAsync(Manifest(FileSpec("mods/a.jar", "old")), new Dictionary<string, byte[]>());
    down.Data["mods/a.jar"] = "new"; down.Data["mods/b.jar"] = "corrupt";
    await Throws(() => installer.InstallAsync(Manifest(FileSpec("mods/a.jar", "new"), FileSpec("mods/b.jar", "correct")), new Dictionary<string, byte[]>()));
    Check(System.IO.File.ReadAllText(Path.Combine(game, "mods/a.jar")) == "old" && !System.IO.File.Exists(Path.Combine(game, "mods/b.jar")), "Active pack changed before verification.");
    Check(installer.ReadReceipt()!.ManagedFiles.Count == 1, "Old receipt changed.");
}));
tests.Add(("Mid-commit failure restores old files, removed files and receipt", async () => {
    var d = Dir("rollback"); var game = Path.Combine(d, "game"); var state = Path.Combine(d, "state"); var down = new FakeDownloader(Path.Combine(d, "cache"));
    down.Data["mods/a.jar"] = "old-a"; down.Data["mods/obsolete.jar"] = "old-b";
    var install = new PackInstaller(game, state, down); await install.InstallAsync(Manifest(FileSpec("mods/a.jar", "old-a"), FileSpec("mods/obsolete.jar", "old-b")), new Dictionary<string, byte[]>());
    down.Data["mods/a.jar"] = "new-a"; down.Data["mods/new.jar"] = "new-b";
    var failing = new PackInstaller(game, state, down) { CommitObserver = _ => throw new IOException("Simulated power-loss boundary.") };
    await Throws(() => failing.InstallAsync(Manifest(FileSpec("mods/a.jar", "new-a"), FileSpec("mods/new.jar", "new-b")), new Dictionary<string, byte[]>()));
    Check(System.IO.File.ReadAllText(Path.Combine(game, "mods/a.jar")) == "old-a", "Original mod not restored.");
    Check(System.IO.File.ReadAllText(Path.Combine(game, "mods/obsolete.jar")) == "old-b", "Removed mod not restored.");
    Check(!System.IO.File.Exists(Path.Combine(game, "mods/new.jar")), "Failed new mod retained.");
    Check(install.ReadReceipt()!.ManagedFiles.ContainsKey("mods/obsolete.jar"), "Original receipt not restored.");
}));
tests.Add(("A forged recovery journal cannot delete personal worlds", async () => {
    var d = Dir("journal-guard"); var game = Path.Combine(d, "game"); var state = Path.Combine(d, "state"); Directory.CreateDirectory(Path.Combine(game, "saves")); Directory.CreateDirectory(state);
    var world = Path.Combine(game, "saves/world.txt"); System.IO.File.WriteAllText(world, "keep");
    AtomicFiles.WriteJson(Path.Combine(state, "update-journal.json"), new { transaction = Guid.NewGuid().ToString("N"), phase = "committing", oldReceipt = (string?)null, changes = new[] { new { path = "saves/world.txt", existed = false } } });
    await Throws(() => { new PackInstaller(game, state, new FakeDownloader(Path.Combine(d, "cache"))).Recover(); return Task.CompletedTask; });
    Check(System.IO.File.ReadAllText(world) == "keep", "Invalid journal deleted a world.");
}));
tests.Add(("Restart recovers a persisted interrupted transaction", () => {
    var d = Dir("restart"); var game = Path.Combine(d, "game"); var state = Path.Combine(d, "state"); Directory.CreateDirectory(game); Directory.CreateDirectory(state);
    var id = Guid.NewGuid().ToString("N"); var backup = Path.Combine(state, "transactions", id, "before/mods/a.jar"); Directory.CreateDirectory(Path.GetDirectoryName(backup)!);
    System.IO.File.WriteAllText(backup, "old"); Directory.CreateDirectory(Path.Combine(game, "mods")); System.IO.File.WriteAllText(Path.Combine(game, "mods/a.jar"), "new");
    AtomicFiles.WriteJson(Path.Combine(state, "update-journal.json"), new { transaction = id, phase = "committing", oldReceipt = (string?)null, changes = new[] { new { path = "mods/a.jar", existed = true } } });
    new PackInstaller(game, state, new FakeDownloader(Path.Combine(d, "cache"))).Recover();
    Check(System.IO.File.ReadAllText(Path.Combine(game, "mods/a.jar")) == "old", "Restart recovery failed."); return Task.CompletedTask;
}));
tests.Add(("Updates preserve worlds, personal mods and existing defaults", async () => {
    var d = Dir("preserve"); var game = Path.Combine(d, "game"); var down = new FakeDownloader(Path.Combine(d, "cache")); down.Data["mods/old.jar"] = "old"; down.Data["mods/new.jar"] = "new";
    var installer = new PackInstaller(game, Path.Combine(d, "state"), down);
    await installer.InstallAsync(Manifest(FileSpec("mods/old.jar", "old")), new Dictionary<string, byte[]> { ["config/yosbr/options.txt"] = Encoding.UTF8.GetBytes("initial") });
    System.IO.File.WriteAllText(Path.Combine(game, "config/yosbr/options.txt"), "personal"); System.IO.File.WriteAllText(Path.Combine(game, "mods/my-extra.jar"), "extra");
    Directory.CreateDirectory(Path.Combine(game, "saves")); System.IO.File.WriteAllText(Path.Combine(game, "saves/world.txt"), "world");
    await installer.InstallAsync(Manifest(FileSpec("mods/new.jar", "new")), new Dictionary<string, byte[]> { ["config/yosbr/options.txt"] = Encoding.UTF8.GetBytes("new-default") });
    Check(!System.IO.File.Exists(Path.Combine(game, "mods/old.jar")), "Obsolete managed mod not removed.");
    Check(System.IO.File.ReadAllText(Path.Combine(game, "mods/my-extra.jar")) == "extra" && System.IO.File.ReadAllText(Path.Combine(game, "saves/world.txt")) == "world", "Personal content changed.");
    Check(System.IO.File.ReadAllText(Path.Combine(game, "config/yosbr/options.txt")) == "personal", "Defaults reset.");
}));
tests.Add(("Unowned conflicts and cancelled installs are preserved", async () => {
    var d = Dir("conflict"); var game = Path.Combine(d, "game"); Directory.CreateDirectory(Path.Combine(game, "mods")); System.IO.File.WriteAllText(Path.Combine(game, "mods/a.jar"), "personal");
    var down = new FakeDownloader(Path.Combine(d, "cache")); down.Data["mods/a.jar"] = "approved";
    var installer = new PackInstaller(game, Path.Combine(d, "state"), down);
    await Throws(() => installer.InstallAsync(Manifest(FileSpec("mods/a.jar", "approved")), new Dictionary<string, byte[]>()));
    Check(System.IO.File.ReadAllText(Path.Combine(game, "mods/a.jar")) == "personal", "Unowned file overwritten.");
    await Throws(() => installer.InstallAsync(Manifest(FileSpec("mods/b.jar", "approved")), new Dictionary<string, byte[]>(), cancellationToken: new CancellationToken(true)));
    Check(!System.IO.File.Exists(Path.Combine(game, "mods/b.jar")), "Cancellation wrote a file.");
}));
tests.Add(("Server list keeps other entries and existing resource-pack consent", () => {
    var other = new Dictionary<string, NbtTag> { ["name"] = new(8, "Friend ðŸŽ® server"), ["ip"] = new(8, "example.net"), ["acceptTextures"] = new(1, (byte)0) };
    var holy = new Dictionary<string, NbtTag> { ["name"] = new(8, "Old name"), ["ip"] = new(8, "79.76.40.155:25565"), ["acceptTextures"] = new(1, (byte)0) };
    var initial = NbtCodec.Write(new("", new() { ["servers"] = new(9, new NbtList(10, [new(10, other), new(10, holy)])), ["custom"] = new(11, new int[] { 1, -2, 3 }) }));
    var changed = NbtCodec.Read(ServerList.Upsert(initial, "79.76.40.155:25565", [1, 2, 3]));
    var list = (NbtList)changed.Root["servers"].Value;
    Check(list.Items.Count == 2 && (string)((Dictionary<string, NbtTag>)list.Items[0].Value)["name"].Value == "Friend ðŸŽ® server", "Other entry changed.");
    Check((byte)((Dictionary<string, NbtTag>)list.Items[1].Value)["acceptTextures"].Value == 0, "User consent changed.");
    Check(((int[])changed.Root["custom"].Value)[1] == -2, "Unknown tag lost."); return Task.CompletedTask;
}));
tests.Add(("Official profile keeps other profiles and opaque account data", () => {
    var old = Encoding.UTF8.GetBytes("{\"profiles\":{\"vanilla\":{\"name\":\"Keep me\"}},\"authenticationDatabase\":{\"opaque\":\"fixture-only\"},\"settings\":{\"keep\":true}}");
    var next = JsonNode.Parse(LauncherProfiles.Upsert(old, Path.Combine(root, "game"), [1]))!;
    Check((string?)next["profiles"]?["vanilla"]?["name"] == "Keep me", "Other profile changed.");
    Check((string?)next["authenticationDatabase"]?["opaque"] == "fixture-only", "Opaque login data lost.");
    Check((string?)next["profiles"]?[LauncherProfiles.ProfileId]?["lastVersionId"] == LauncherProfiles.VersionId, "Wrong Fabric profile."); return Task.CompletedTask;
}));
tests.Add(("New animation packs activate once and preserve personal settings", async () => {
    var d = Dir("resource-pack-migration"); var game = Path.Combine(d, "game"); var down = new FakeDownloader(Path.Combine(d, "cache"));
    down.Data["mods/a.jar"] = "mod"; down.Data["resourcepacks/Objects.zip"] = "objects";
    var installer = new PackInstaller(game, Path.Combine(d, "state"), down);
    var initial = Manifest(FileSpec("mods/a.jar", "mod"));
    await installer.InstallAsync(initial, new Dictionary<string, byte[]>());
    var personal = "renderDistance:17\r\nresourcePacks:[\"vanilla\",\"file/My textures.zip\"]\r\nkey_jump:key.keyboard.space\r\n";
    System.IO.File.WriteAllText(Path.Combine(game, "options.txt"), personal, new UTF8Encoding(false));
    var next = Manifest(FileSpec("mods/a.jar", "mod"), FileSpec("resourcepacks/Objects.zip", "objects")) with { Version = "1.3.0" };
    await installer.InstallAsync(next, new Dictionary<string, byte[]>());
    var changed = System.IO.File.ReadAllText(Path.Combine(game, "options.txt"));
    Check(changed.Contains("file/Objects.zip") && changed.Contains("file/My textures.zip") && changed.Contains("renderDistance:17\r\n") && changed.Contains("key_jump:key.keyboard.space\r\n"), "Personal settings changed or new pack omitted.");
    System.IO.File.WriteAllText(Path.Combine(game, "options.txt"), personal, new UTF8Encoding(false));
    await installer.InstallAsync(next, new Dictionary<string, byte[]>());
    Check(System.IO.File.ReadAllText(Path.Combine(game, "options.txt")) == personal, "Verify re-enabled a pack the player disabled.");
}));
tests.Add(("Failed update rolls back resource-pack activation with managed files", async () => {
    var d = Dir("resource-pack-rollback"); var game = Path.Combine(d, "game"); var state = Path.Combine(d, "state"); var down = new FakeDownloader(Path.Combine(d, "cache"));
    down.Data["mods/a.jar"] = "old"; down.Data["resourcepacks/Objects.zip"] = "objects";
    var installer = new PackInstaller(game, state, down);
    await installer.InstallAsync(Manifest(FileSpec("mods/a.jar", "old")), new Dictionary<string, byte[]>());
    var personal = "resourcePacks:[\"vanilla\"]\nrenderDistance:17\n";
    System.IO.File.WriteAllText(Path.Combine(game, "options.txt"), personal);
    down.Data["mods/a.jar"] = "new";
    var broken = new PackInstaller(game, state, down) { CommitObserver = n => { if (n == 3) throw new IOException("Simulated failure after options write."); } };
    await Throws(() => broken.InstallAsync(Manifest(FileSpec("mods/a.jar", "new"), FileSpec("resourcepacks/Objects.zip", "objects")), new Dictionary<string, byte[]>()));
    Check(System.IO.File.ReadAllText(Path.Combine(game, "options.txt")) == personal && System.IO.File.ReadAllText(Path.Combine(game, "mods/a.jar")) == "old" && !System.IO.File.Exists(Path.Combine(game, "resourcepacks/Objects.zip")), "Resource-pack migration did not roll back.");
}));
tests.Add(("Download cleanup retains current release and ignores unrelated files", () => {
    var d = Dir("cache-prune"); var file = FileSpec("mods/a.jar", "a");
    var keep = Path.Combine(d, file.Sha256 + ".verified"); var obsolete = Path.Combine(d, new string('b',64) + ".verified"); var personal = Path.Combine(d, "notes.verified");
    System.IO.File.WriteAllText(keep,"a"); System.IO.File.WriteAllText(obsolete,"old"); System.IO.File.WriteAllText(personal,"keep");
    using var http = new HttpClient(); new DownloadCache(d,http).Prune([file]);
    Check(System.IO.File.Exists(keep) && System.IO.File.Exists(personal) && !System.IO.File.Exists(obsolete), "Cache pruning changed an unrelated/current file.");
    return Task.CompletedTask;
}));
tests.Add(("Fresh launcher setup seeds the correct base version and preserves existing metadata", async () => {
    var d = Dir("base-profile"); var game = Path.Combine(d,"game"); var minecraft = Path.Combine(d,"minecraft");
    var manifest = Manifest(FileSpec("mods/a.jar","a"));
    var fabric = Encoding.UTF8.GetBytes("{\"id\":\"holylois-reborn-fabric-0.19.5-26.3\",\"inheritsFrom\":\"26.3\"}");
    var vanilla = Encoding.UTF8.GetBytes("{\"id\":\"26.3\",\"type\":\"release\",\"javaVersion\":{\"majorVersion\":25}}");
    var down = new FakeDownloader(Path.Combine(d,"cache"));
    await LauncherProfiles.PrepareAsync(minecraft,game,manifest,fabric,[1],down,CancellationToken.None,vanilla);
    var basePath = Path.Combine(minecraft,"versions/26.3/26.3.json");
    Check(System.IO.File.ReadAllBytes(basePath).SequenceEqual(vanilla),"Vanilla metadata was not seeded.");
    System.IO.File.WriteAllText(basePath,"existing launcher metadata");
    await LauncherProfiles.PrepareAsync(minecraft,game,manifest,fabric,[1],down,CancellationToken.None,vanilla);
    Check(System.IO.File.ReadAllText(basePath)=="existing launcher metadata","Existing base metadata overwritten.");
    var wrong = Encoding.UTF8.GetBytes("{\"id\":\"1.21.4\",\"type\":\"release\",\"javaVersion\":{\"majorVersion\":21}}");
    await Throws(() => LauncherProfiles.PrepareAsync(minecraft,game,manifest,fabric,[1],down,CancellationToken.None,wrong));
}));
tests.Add(("Online pack feed rejects tampering, rollback and same-version replacement", async () => {
    using var rsa = RSA.Create(2048); using var http = new HttpClient();
    var current = Manifest(FileSpec("mods/a.jar", "a"));
    var defaults = FileSpec("defaults.zip", "defaults") with { Policy = "seed" };
    SignedPack Signed(PackManifest m) { var json = JsonSerializer.SerializeToUtf8Bytes(m, JsonSettings.Options); return new(json, rsa.SignData(json, HashAlgorithmName.SHA256, RSASignaturePadding.Pss)); }
    var feed = new PackFeed(http, rsa.ExportSubjectPublicKeyInfoPem());
    var next = current with { Version = "1.2.0", Defaults = defaults };
    Check(feed.Accept(Signed(next), current).Version == "1.2.0", "New release was rejected.");
    await Throws(() => { feed.Accept(Signed(current with { Version = "1.0.0", Defaults = defaults }), current); return Task.CompletedTask; });
    await Throws(() => { feed.Accept(Signed(current with { Files = [FileSpec("mods/a.jar", "changed")], Defaults = defaults }), current); return Task.CompletedTask; });
    await Throws(() => { feed.Accept(Signed(current), current); return Task.CompletedTask; });
    var bad = Signed(next); bad.Json[10] ^= 1;
    await Throws(() => { feed.Accept(bad, current); return Task.CompletedTask; });
    var accepted = current with { Defaults = defaults };
    await Throws(() => { feed.Accept(Signed(accepted with { Defaults = defaults with { Sha256 = new string('f',64) } }), accepted); return Task.CompletedTask; });
}));
tests.Add(("Defaults bundles exclude worlds, voice devices, duplicate paths and oversized content", async () => {
    byte[] Bundle(params (string Name, string Value)[] entries) {
        using var output = new MemoryStream();
        using (var zip = new System.IO.Compression.ZipArchive(output, System.IO.Compression.ZipArchiveMode.Create, true))
            foreach (var entry in entries) { using var writer = new StreamWriter(zip.CreateEntry(entry.Name).Open()); writer.Write(entry.Value); }
        return output.ToArray();
    }
    Check(PackFeed.ReadDefaults(Bundle(("config/yosbr/options.txt","safe"))).Count == 1,"Safe defaults rejected.");
    foreach (var path in new[] { "saves/world.dat", "../mods/a.jar", "config/yosbr/config/voicechat/voicechat-client.properties" })
        await Throws(() => { PackFeed.ReadDefaults(Bundle((path,"bad"))); return Task.CompletedTask; });
    await Throws(() => { PackFeed.ReadDefaults(Bundle(("config/yosbr/options.txt","a"),("config/yosbr/OPTIONS.txt","b"))); return Task.CompletedTask; });
    await Throws(() => { PackFeed.ReadDefaults(Bundle(("config/yosbr/options.txt",new string('a',2*1024*1024+1)))); return Task.CompletedTask; });
}));
tests.Add(("Shader recovery changes only enableShaders and retains a backup", async () => {
    var d=Dir("shader-recovery"); var game=Path.Combine(d,"game"); var backup=Path.Combine(d,"backup");
    Directory.CreateDirectory(Path.Combine(game,"config"));
    var path=Path.Combine(game,"config/iris.properties"); var before="shaderPack=MyShader.zip\r\nenableShaders=true\r\ncolorSpace=SRGB\r\n";
    System.IO.File.WriteAllText(path,before);
    GraphicsRecovery.DisableShaders(game,backup);
    Check(System.IO.File.ReadAllText(path)==before.Replace("enableShaders=true","enableShaders=false"),"Unrelated setting was changed.");
    Check(System.IO.File.ReadAllText(Path.Combine(backup,"iris-before-recovery.properties"))==before,"Original settings weren't backed up.");
    System.IO.File.WriteAllText(path,"enableShaders=true\nenableShaders=false\n");
    await Throws(()=>{GraphicsRecovery.DisableShaders(game,backup);return Task.CompletedTask;});
}));
tests.Add(("Changed shared settings apply once; unrelated preferences survive", async () => {
    var d=Dir("settings-migrate");var game=Path.Combine(d,"game");var down=new FakeDownloader(Path.Combine(d,"cache"));down.Data["mods/a.jar"]="mod";
    var installer=new PackInstaller(game,Path.Combine(d,"state"),down);
    var first=Manifest(FileSpec("mods/a.jar","mod")) with {ApplyDefaultsOnUpdate=true};
    var defaults=new Dictionary<string,byte[]>{["config/yosbr/options.txt"]=Encoding.UTF8.GetBytes("renderDistance:12\nresourcePacks:[\"vanilla\"]\nkey_jump:key.keyboard.space\n"),["config/yosbr/config/example.json"]=Encoding.UTF8.GetBytes("{\"effects\":{\"enabled\":true,\"size\":1}}")};
    await installer.InstallAsync(first,defaults);
    File.WriteAllText(Path.Combine(game,"options.txt"),"renderDistance:25\nresourcePacks:[\"vanilla\"]\nkey_jump:key.keyboard.j\nfov:95\n");
    File.WriteAllText(Path.Combine(game,"config/example.json"),"{\"effects\":{\"enabled\":true,\"size\":9},\"personal\":42}");
    defaults["config/yosbr/options.txt"]=Encoding.UTF8.GetBytes("renderDistance:14\nresourcePacks:[\"vanilla\"]\nkey_jump:key.keyboard.g\n");
    defaults["config/yosbr/config/example.json"]=Encoding.UTF8.GetBytes("{\"effects\":{\"enabled\":false,\"size\":1}}");
    var next=first with {Version="1.2.0"};await installer.InstallAsync(next,defaults);
    var options=File.ReadAllText(Path.Combine(game,"options.txt"));Check(options.Contains("renderDistance:14")&&options.Contains("key_jump:key.keyboard.j")&&options.Contains("fov:95"),"Changed setting or personal settings incorrect.");
    var json=JsonNode.Parse(File.ReadAllText(Path.Combine(game,"config/example.json")))!;Check((bool?)json["effects"]?["enabled"]==false&&(int?)json["effects"]?["size"]==9&&(int?)json["personal"]==42,"JSON merge failed.");
    File.WriteAllText(Path.Combine(game,"options.txt"),options.Replace("renderDistance:14","renderDistance:19"));await installer.InstallAsync(next,defaults);Check(File.ReadAllText(Path.Combine(game,"options.txt")).Contains("renderDistance:19"),"Verify reapplied settings within same release.");
}));
tests.Add(("Shared settings rollback with a failed commit", async () => {
    var d=Dir("settings-rollback");var game=Path.Combine(d,"game");var state=Path.Combine(d,"state");var down=new FakeDownloader(Path.Combine(d,"cache"));down.Data["mods/a.jar"]="mod";
    var first=Manifest(FileSpec("mods/a.jar","mod")) with {ApplyDefaultsOnUpdate=true};var seed=new Dictionary<string,byte[]>{["config/yosbr/config/example.json"]=Encoding.UTF8.GetBytes("{\"enabled\":true}")};
    var install=new PackInstaller(game,state,down);await install.InstallAsync(first,seed);seed["config/yosbr/config/example.json"]=Encoding.UTF8.GetBytes("{\"enabled\":false}");
    var failing=new PackInstaller(game,state,down){CommitObserver=i=>{if(i==2)throw new IOException("fixture");}};await Throws(()=>failing.InstallAsync(first with {Version="1.2.0"},seed));
    Check(File.ReadAllText(Path.Combine(game,"config/example.json")).Contains("true")&&install.ReadReceipt()!.Version==first.Version,"Settings were not restored.");
}));
tests.Add(("Fresh installs activate resource packs before options exist", async () => {
    var d=Dir("fresh-resource-defaults");var game=Path.Combine(d,"game");var down=new FakeDownloader(Path.Combine(d,"cache"));
    down.Data["mods/a.jar"]="mod";down.Data["resourcepacks/Objects.zip"]="objects";
    var installer=new PackInstaller(game,Path.Combine(d,"state"),down);
    var manifest=Manifest(FileSpec("mods/a.jar","mod"),FileSpec("resourcepacks/Objects.zip","objects")) with {ApplyDefaultsOnUpdate=true};
    await installer.InstallAsync(manifest,new Dictionary<string,byte[]>{["config/yosbr/options.txt"]=Encoding.UTF8.GetBytes("resourcePacks:[\"vanilla\"]\nrenderDistance:12\n")});
    Check(File.ReadAllText(Path.Combine(game,"options.txt")).Contains("file/Objects.zip")&&installer.ReadReceipt()!.Version==manifest.Version,"Fresh resource pack activation failed.");
}));
tests.Add(("Private default files remain excluded", async () => {
    foreach(var path in new[]{"config/yosbr/config/voicechat/voicechat-client.properties","config/yosbr/config/accounts.json","config/yosbr/saves/world.txt"})await Throws(()=>{_=SharedDefaults.Target(path);return Task.CompletedTask;});
}));

tests.Add(("One-app release safety and rollback", () => AppReleaseTests.RunAsync(Dir("app-release"))));

tests.Add(("Preview profile preserves an existing production installation and personal profiles", () => {
    var d=Dir("preview-profile"); var original=new JsonObject { ["profiles"] = new JsonObject { [LauncherProfiles.ProfileId] = new JsonObject { ["gameDir"]=Path.Combine(d,"production"), ["name"]="Holy Lois: Reborn", ["javaArgs"]="personal" }, ["other"] = new JsonObject { ["name"]="Personal world" } }, ["authenticationDatabase"] = new JsonObject { ["opaque"]="keep" } };
    var next=JsonNode.Parse(LauncherProfiles.Upsert(Encoding.UTF8.GetBytes(original.ToJsonString()),Path.Combine(d,"preview"),[1],LauncherProfiles.PreviewProfileId,"Holy Lois: Reborn (Preview)"))!;
    Check(next["profiles"]![LauncherProfiles.ProfileId]!.ToJsonString()==original["profiles"]![LauncherProfiles.ProfileId]!.ToJsonString(),"Production profile was replaced.");
    Check(next["profiles"]!["other"]!.ToJsonString()==original["profiles"]!["other"]!.ToJsonString() && next["authenticationDatabase"]!.ToJsonString()==original["authenticationDatabase"]!.ToJsonString(),"Personal profile or opaque account metadata changed.");
    Check((string?)next["profiles"]![LauncherProfiles.PreviewProfileId]!["gameDir"]==Path.Combine(d,"preview"),"Preview profile was not created."); return Task.CompletedTask;
}));
tests.Add(("App-only removal rejects unowned folders and preserves worlds, extras and settings", async () => {
    var d=Dir("remove-app"); File.WriteAllText(Path.Combine(d,"HolyLoisReborn.exe"),"app"); var hash=AtomicFiles.Hash(Path.Combine(d,"HolyLoisReborn.exe"));
    await Throws(()=> { ApplicationRemoval.RemoveOwnedApp(d,hash); return Task.CompletedTask; });
    File.WriteAllText(Path.Combine(d,"holylois-app.txt"),ApplicationRemoval.Marker); Directory.CreateDirectory(Path.Combine(d,"data/saves")); File.WriteAllText(Path.Combine(d,"data/saves/world.txt"),"world"); File.WriteAllText(Path.Combine(d,"personal.txt"),"keep"); File.WriteAllText(Path.Combine(d,"setup-completed.json"),"settings");
    await Throws(()=> { ApplicationRemoval.RemoveOwnedApp(d,new string('0',64)); return Task.CompletedTask; });
    Check(File.Exists(Path.Combine(d,"setup-completed.json")),"Failed removal changed app settings.");
    ApplicationRemoval.RemoveOwnedApp(d,hash);
    Check(!File.Exists(Path.Combine(d,"HolyLoisReborn.exe")) && File.ReadAllText(Path.Combine(d,"data/saves/world.txt"))=="world" && File.ReadAllText(Path.Combine(d,"personal.txt"))=="keep","Removal touched personal files or left the installed executable.");
}));

foreach (var test in tests)
{
    try { await test.Run(); Console.WriteLine("PASS " + test.Name); passed++; }
    catch (Exception ex) { Console.WriteLine("FAIL " + test.Name + ": " + ex.Message); }
}
Console.WriteLine($"{passed}/{tests.Count} test groups passed.");
Environment.ExitCode = passed == tests.Count ? 0 : 1;

sealed class FakeDownloader(string root) : IFileDownloader
{
    public Dictionary<string, string> Data { get; } = [];
    public Task<string> GetAsync(PackFile file, IProgress<long>? progress, CancellationToken token)
    {
        token.ThrowIfCancellationRequested(); Directory.CreateDirectory(root);
        if (!Data.TryGetValue(file.Path, out var text)) throw new IOException("Simulated network failure.");
        var path = Path.Combine(root, Guid.NewGuid().ToString("N")); System.IO.File.WriteAllText(path, text); return Task.FromResult(path);
    }
}
