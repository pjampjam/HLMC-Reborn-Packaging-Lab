using HolyLois.Core;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Nodes;

internal static class FastStartTests
{
    private static void Check(bool value, string message) { if (!value) throw new Exception(message); }

    // The real version files shipped in the launcher, found from the test folder upwards.
    private static byte[] Asset(string name)
    {
        for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir is not null; dir = dir.Parent)
        {
            var path = Path.Combine(dir.FullName, "assets", name);
            if (File.Exists(path)) return File.ReadAllBytes(path);
        }
        throw new FileNotFoundException("Launcher asset not found: " + name);
    }

    private static string Sha1(byte[] bytes) => Convert.ToHexStringLower(SHA1.HashData(bytes));

    public static IEnumerable<(string Name, Func<Task> Run)> Create(string testRoot)
    {
        yield return ("Fast start reads the real Minecraft and Fabric files into Windows libraries and a full command line", () =>
        {
            var version = new GameVersion(Asset("vanilla-profile.json"), Asset("fabric-profile.json"));
            var platform = GamePlatform.Windows(new Version(10, 0, 22631));
            var pack = System.Text.Json.JsonSerializer.Deserialize<PackManifest>(Asset("pack.json"), JsonSettings.Options)!;
            var libraries = version.Libraries(platform, pack.LoaderFiles);
            Check(libraries.Single(l => l.Path.Contains("fabric-loader-0.19.5.jar")).Hash == pack.LoaderFiles.Single(f => f.Path.Contains("fabric-loader")).Sha256, "Fabric Loader is not checked against the signed pack.");
            try { version.Libraries(platform, []); throw new Exception("Unsigned Fabric libraries were accepted."); } catch (InvalidDataException) { }
            Check(version.Id == LauncherProfiles.VersionId && version.Minecraft == "26.3" && version.JavaComponent == "java-runtime-epsilon", "Version identity was read wrong.");
            Check(version.MainClass == "net.fabricmc.loader.impl.launch.knot.KnotClient", "Fabric's main class was not used.");
            Check(libraries[0].Path.StartsWith("libraries/org/ow2/asm/asm/", StringComparison.Ordinal) && libraries.Any(l => l.Path.Contains("fabric-loader-0.19.5.jar")), "Fabric libraries do not come first.");
            Check(libraries.Any(l => l.Path.EndsWith("natives-windows.jar", StringComparison.Ordinal)) && !libraries.Any(l => l.Path.Contains("natives-linux") || l.Path.Contains("natives-macos") || l.Path.Contains("java-objc-bridge")),
                "Native libraries for other systems were included or Windows ones are missing.");
            Check(libraries.Count(l => l.Path.Contains("/asm/")) == 1 || libraries.Where(l => l.Path.Contains("org/ow2/asm/asm/")).Count() == 1, "A library appears twice.");
            Check(libraries.All(l => l.Hash.Length is 40 or 64 && l.Size > 0), "A library has no checksum.");
            var values = new Dictionary<string, string> { ["auth_player_name"] = "pjamtest", ["auth_uuid"] = FastStart.OfflineUuid("pjamtest"), ["auth_access_token"] = "0",
                ["auth_xuid"] = "", ["clientid"] = "", ["classpath"] = "CP", ["natives_directory"] = "N", ["quickPlayMultiplayer"] = "play.holylois.com", ["path"] = "LOG", ["version_name"] = version.Id };
            var join = version.CommandLine(platform, new HashSet<string> { "is_quick_play_multiplayer" }, values, ["-Xmx4096M"]).ToList();
            var main = join.ToList().IndexOf(version.MainClass);
            Check(main > 0 && join[0] == "-Xmx4096M" && !join.Any(a => a.StartsWith("-Dlog4j.configurationFile")) && join.Contains("-DFabricMcEmu= net.minecraft.client.main.Main "), "JVM arguments are incomplete or use the XML console log.");
            Check(join.SkipWhile(a => a != "-cp").Skip(1).First() == "CP" && join.IndexOf("-cp") < main, "The class path is not passed to Java.");
            var game = join.Skip(main + 1).ToList();
            Check(game[game.IndexOf("--username") + 1] == "pjamtest" && game[game.IndexOf("--quickPlayMultiplayer") + 1] == "play.holylois.com", "Name or server join is missing.");
            Check(!game.Contains("--xuid") && !game.Contains("--clientId") && !game.Contains("--demo") && !game.Contains("--quickPlayPath") && !game.Any(a => a.Contains("${")), "Empty or unfilled options were passed.");
            var title = version.CommandLine(platform, new HashSet<string>(), values, []);
            Check(!title.Contains("--quickPlayMultiplayer"), "The title-screen start still joins the server.");
            Check(version.RecommendedJvmFlags(platform).Contains("-XX:+UseZGC") && !version.RecommendedJvmFlags(platform).Any(f => f.StartsWith("-Xmx")), "Windows 11 did not get Mojang's ZGC flags.");
            Check(version.RecommendedJvmFlags(GamePlatform.Windows(new Version(10, 0, 17000))).Contains("-XX:+UseG1GC"), "Old Windows did not get G1.");
            Check(version.AssetIndex.Path == "assets/indexes/" + version.AssetIndexId + ".json" && version.Client.Path == "versions/26.3/26.3.jar" && version.LogConfig is not null, "Game file paths are wrong.");
            return Task.CompletedTask;
        });
        yield return ("Player-name UUIDs match what the server gives and memory follows the computer", () =>
        {
            Check(FastStart.OfflineUuid("Notch") == "b50ad385829d3141a2167e7d7539ba7f", "Offline UUID differs from Minecraft's.");
            Check(FastStart.MemoryMb(8L << 30) == 4096 && FastStart.MemoryMb(16L << 30) == 6144 && FastStart.MemoryMb(32L << 30) == 8192 && FastStart.MemoryMb(4L << 30) == 3072, "Memory steps changed.");
            Check(PlayMode.UsesFastStart("name", false) && PlayMode.UsesFastStart("sk", null) && !PlayMode.UsesFastStart("sk", false) && !PlayMode.UsesFastStart("official", true), "Fast start rule changed.");
            var file = FastStart.ArgumentFile(["-cp", @"C:\Games\A B\x.jar;y.jar", "say \"hi\""]);
            Check(file == "\"-cp\"\n\"C:\\\\Games\\\\A B\\\\x.jar;y.jar\"\n\"say \\\"hi\\\"\"\n", "Java argument file escaping changed.");
            return Task.CompletedTask;
        });
        yield return ("Game files are checked, reused from another launcher, downloaded, and a bad download is refused", async () =>
        {
            var root = Path.Combine(testRoot, "fast-files");
            var other = Path.Combine(root, "other-launcher");
            var good = Encoding.UTF8.GetBytes("library bytes"); var fresh = Encoding.UTF8.GetBytes("asset bytes");
            var reused = new GameFile("libraries/a/a.jar", "https://libraries.minecraft.net/a/a.jar", good.Length, Sha1(good));
            var fetched = new GameFile("assets/objects/ab/abc", "https://resources.download.minecraft.net/ab/abc", fresh.Length, Sha1(fresh));
            Directory.CreateDirectory(Path.Combine(other, "libraries", "a")); File.WriteAllBytes(Path.Combine(other, "libraries", "a", "a.jar"), good);
            var web = new FakeWeb { [fetched.Url] = fresh };
            var downloads = new GameDownloads(new HttpClient(web), Path.Combine(root, "game"), [other]);
            await downloads.EnsureAsync([reused, fetched], "game", null, CancellationToken.None);
            Check(downloads.Reused == 1 && downloads.Downloaded == 1 && web.Calls == 1, "Files were not reused or downloaded as expected.");
            Check(File.ReadAllBytes(downloads.PathOf(reused)).SequenceEqual(good) && File.ReadAllBytes(downloads.PathOf(fetched)).SequenceEqual(fresh), "Placed files differ.");
            web.Clear();
            var again = new GameDownloads(new HttpClient(web), Path.Combine(root, "game"), []);
            await again.EnsureAsync([reused, fetched], "game", null, CancellationToken.None);
            Check(again.Downloaded == 0 && web.Calls == 0, "A ready file was fetched again.");
            File.WriteAllBytes(again.PathOf(fetched), Encoding.UTF8.GetBytes("asset bytez"));
            var tampered = new GameDownloads(new HttpClient(web), Path.Combine(root, "game"), []);
            Check(!tampered.IsReady(fetched), "A changed file still counted as ready.");
            var bad = new GameFile("assets/objects/cd/cde", "https://resources.download.minecraft.net/cd/cde", 5, Sha1(Encoding.UTF8.GetBytes("right")));
            web[bad.Url] = Encoding.UTF8.GetBytes("wrong");
            try { await tampered.EnsureAsync([bad], "assets", null, CancellationToken.None); throw new Exception("A corrupt download was accepted."); }
            catch (IOException) { }
            Check(!File.Exists(tampered.PathOf(bad)) && !Directory.GetFiles(Path.Combine(root, "game"), "*.part", SearchOption.AllDirectories).Any(), "A corrupt download left files behind.");
            try { GameDownloads.ValidateUri("https://evil.example/a.jar"); throw new Exception("An unknown host was allowed."); } catch (InvalidDataException) { }
            try { GameDownloads.ValidateUri("http://libraries.minecraft.net/a.jar"); throw new Exception("Plain HTTP was allowed."); } catch (InvalidDataException) { }
        });
        yield return ("Mojang's Java is installed from its file list and later starts without the network", async () =>
        {
            var root = Path.Combine(testRoot, "fast-java");
            var java = Encoding.UTF8.GetBytes("java.exe bytes"); var lib = Encoding.UTF8.GetBytes("modules");
            var files = new JsonObject
            {
                ["files"] = new JsonObject
                {
                    ["bin"] = new JsonObject { ["type"] = "directory" },
                    ["bin/java.exe"] = new JsonObject { ["type"] = "file", ["executable"] = true, ["downloads"] = new JsonObject { ["raw"] = new JsonObject { ["sha1"] = Sha1(java), ["size"] = java.Length, ["url"] = "https://piston-data.mojang.com/java" } } },
                    ["lib/modules"] = new JsonObject { ["type"] = "file", ["downloads"] = new JsonObject { ["raw"] = new JsonObject { ["sha1"] = Sha1(lib), ["size"] = lib.Length, ["url"] = "https://piston-data.mojang.com/modules" } } }
                }
            };
            var manifest = Encoding.UTF8.GetBytes(files.ToJsonString());
            var list = new JsonObject { ["windows-x64"] = new JsonObject { ["java-runtime-epsilon"] = new JsonArray(new JsonObject { ["manifest"] = new JsonObject { ["sha1"] = Sha1(manifest), ["size"] = manifest.Length, ["url"] = "https://piston-meta.mojang.com/java.json" } }) } };
            var web = new FakeWeb { [JavaRuntime.ListUrl] = Encoding.UTF8.GetBytes(list.ToJsonString()), ["https://piston-meta.mojang.com/java.json"] = manifest, ["https://piston-data.mojang.com/java"] = java, ["https://piston-data.mojang.com/modules"] = lib };
            var path = await JavaRuntime.EnsureAsync(new GameDownloads(new HttpClient(web), root, []), "java-runtime-epsilon", null, CancellationToken.None);
            Check(path.Replace('\\', '/').EndsWith("runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon/bin/java.exe") && File.ReadAllBytes(path).SequenceEqual(java), "Java was not placed in the launcher layout.");
            web.Clear();
            var offline = await JavaRuntime.EnsureAsync(new GameDownloads(new HttpClient(web), root, []), "java-runtime-epsilon", null, CancellationToken.None);
            Check(offline == path && web.Calls == 0, "A ready Java needed the network.");
        });
        yield return ("Player names: rules, history, a daily limit for new names, and the name from the game log", () =>
        {
            var now = DateTimeOffset.UtcNow;
            Check(PlayerNames.IsValid("pjam_test") && !PlayerNames.IsValid("pj") && !PlayerNames.IsValid("has space") && !PlayerNames.IsValid("seventeen_chars_x") && !PlayerNames.IsValid("Ünicode"), "Name rules changed.");
            var book = PlayerNames.Use(new PlayerBook(), "Mariks", now);
            book = PlayerNames.Use(book, "second", now); book = PlayerNames.Use(book, "third", now); book = PlayerNames.Use(book, "fourth", now);
            Check(book.Current == "fourth" && book.History.Length == 4 && PlayerNames.NewNamesLeft(book, now) == 0, "History or limit was not kept.");
            try { PlayerNames.Use(book, "fifth", now); throw new Exception("A fourth new name in a day was allowed."); } catch (InvalidDataException) { }
            book = PlayerNames.Use(book, "mariks", now);
            Check(book.Current == "Mariks" && book.History[0].Name == "Mariks" && book.History.Length == 4, "Switching back did not reuse the old spelling.");
            Check(PlayerNames.Use(book, "fifth", now.AddDays(1.1)).Current == "fifth", "The limit did not reset after a day.");
            var path = Path.Combine(testRoot, "players", "players.json");
            PlayerNames.Save(path, book);
            Check(PlayerNames.Load(path).Current == "Mariks" && PlayerNames.Load(Path.Combine(testRoot, "players", "missing.json")).Current is null, "Names were not saved.");
            File.WriteAllText(path, "{broken"); Check(PlayerNames.Load(path).History.Length == 0, "A broken file was not ignored.");
            var game = Path.Combine(testRoot, "players", "game"); Directory.CreateDirectory(Path.Combine(game, "logs"));
            File.WriteAllText(Path.Combine(game, "logs", "latest.log"), "[12:00:01] [main/INFO]: Loading 214 mods\n[12:00:09] [Render thread/INFO]: Setting user: Elza_LV\n");
            Check(PlayerNames.FromGameLog(game) == "Elza_LV" && PlayerNames.FromGameLog(Path.Combine(testRoot, "players")) is null, "The game log name was not read.");
            return Task.CompletedTask;
        });
        yield return ("Reports hide Windows user names, tokens and IPs, keep the server address and include the crash", () =>
        {
            var text = GameReports.Redact("C:/Users/example/AppData/x --accessToken eyJabc.def --uuid 1234 token:secret) joined 79.76.40.155 from 192.168.1.5 and 127.0.0.1", "example");
            Check(!text.Contains("example") && !text.Contains("eyJabc") && !text.Contains("secret") && !text.Contains("192.168.1.5") && !text.Contains("1234"), "Personal details stayed in the report: " + text);
            Check(text.Contains("79.76.40.155") && text.Contains("127.0.0.1") && text.Contains("C:/Users/<user>/AppData"), "Useful details were removed: " + text);
            var game = Path.Combine(testRoot, "report-game"); Directory.CreateDirectory(Path.Combine(game, "crash-reports")); Directory.CreateDirectory(Path.Combine(game, "logs"));
            File.WriteAllText(Path.Combine(game, "crash-reports", "crash-2026-10-05_12.00.00-client.txt"), "---- Minecraft Crash Report ----\nDescription: Rendering overlay");
            File.WriteAllText(Path.Combine(game, "logs", "latest.log"), "Setting user: pjamtest\nfrom C:/Users/example/x");
            var report = GameReports.Build(new StartRecord("fast start", "1.7.11", "1.3.0", DateTimeOffset.UtcNow.AddMinutes(-1), -1, DateTimeOffset.UtcNow), game, null, "Windows 11", "example");
            Check(report.Contains("Rendering overlay") && report.Contains("exit code -1") && report.Contains("Setting user: pjamtest") && !report.Contains("example"), "The report is missing parts or leaks the user name.");
            var zip = Path.Combine(testRoot, "report.zip"); GameReports.SaveZip(report, zip);
            using (var archive = System.IO.Compression.ZipFile.OpenRead(zip)) Check(archive.Entries.Single().Name == "holylois-report.txt", "The report zip is wrong.");
            return Task.CompletedTask;
        });
        yield return ("Fast start prepares Java, libraries, sounds and a start command end to end, then again without the network", async () =>
        {
            var root = Path.Combine(testRoot, "fast-e2e");
            var web = new FakeWeb();
            GameFile Serve(string path, string url, string content, bool sha256 = false)
            {
                var bytes = Encoding.UTF8.GetBytes(content); web[url] = bytes;
                return new GameFile(path, url, bytes.Length, sha256 ? Convert.ToHexStringLower(SHA256.HashData(bytes)) : Sha1(bytes));
            }
            JsonObject Download(GameFile f) => new() { ["sha1"] = f.Hash, ["size"] = f.Size, ["url"] = f.Url };
            var lwjgl = Serve("libraries/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar", "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar", "natives");
            var gson = Serve("libraries/com/google/gson/2.14.0/gson-2.14.0.jar", "https://libraries.minecraft.net/com/google/gson/2.14.0/gson-2.14.0.jar", "gson");
            var client = Serve("versions/26.3/26.3.jar", "https://piston-data.mojang.com/client.jar", "client");
            var sound = Encoding.UTF8.GetBytes("ogg"); var soundHash = Sha1(sound);
            web[FastStart.AssetHost + soundHash[..2] + "/" + soundHash] = sound;
            var index = Serve("assets/indexes/34.json", "https://piston-meta.mojang.com/34.json", new JsonObject { ["objects"] = new JsonObject { ["minecraft/sounds/a.ogg"] = new JsonObject { ["hash"] = soundHash, ["size"] = sound.Length } } }.ToJsonString());
            var log = Serve("assets/log_configs/client-1.21.2.xml", "https://piston-data.mojang.com/log.xml", "<xml/>");
            var loader = Serve("libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", "loader", true);
            var vanilla = new JsonObject
            {
                ["id"] = "26.3", ["mainClass"] = "net.minecraft.client.main.Main", ["javaVersion"] = new JsonObject { ["component"] = "java-runtime-epsilon", ["majorVersion"] = 25 },
                ["assetIndex"] = new JsonObject { ["id"] = "34", ["sha1"] = index.Hash, ["size"] = index.Size, ["url"] = index.Url },
                ["downloads"] = new JsonObject { ["client"] = Download(client) },
                ["logging"] = new JsonObject { ["client"] = new JsonObject { ["argument"] = "-Dlog4j.configurationFile=${path}", ["file"] = new JsonObject { ["id"] = "client-1.21.2.xml", ["sha1"] = log.Hash, ["size"] = log.Size, ["url"] = log.Url } } },
                ["libraries"] = new JsonArray(
                    new JsonObject { ["name"] = "com.google:gson:2.14.0", ["downloads"] = new JsonObject { ["artifact"] = Download(gson).Also("path", "com/google/gson/2.14.0/gson-2.14.0.jar") } },
                    new JsonObject { ["name"] = "org.lwjgl:lwjgl:3.4.3:natives-windows", ["rules"] = new JsonArray(new JsonObject { ["action"] = "allow", ["os"] = new JsonObject { ["name"] = "windows" } }), ["downloads"] = new JsonObject { ["artifact"] = Download(lwjgl).Also("path", "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar") } },
                    new JsonObject { ["name"] = "org.lwjgl:lwjgl:3.4.3:natives-linux", ["rules"] = new JsonArray(new JsonObject { ["action"] = "allow", ["os"] = new JsonObject { ["name"] = "linux" } }), ["downloads"] = new JsonObject { ["artifact"] = new JsonObject { ["path"] = "x/linux.jar", ["sha1"] = new string('0', 40), ["size"] = 1, ["url"] = "https://libraries.minecraft.net/x/linux.jar" } } }),
                ["arguments"] = new JsonObject
                {
                    ["game"] = new JsonArray("--username", "${auth_player_name}", "--gameDir", "${game_directory}", "--assetsDir", "${assets_root}", "--uuid", "${auth_uuid}", "--xuid", "${auth_xuid}",
                        new JsonObject { ["rules"] = new JsonArray(new JsonObject { ["action"] = "allow", ["features"] = new JsonObject { ["is_quick_play_multiplayer"] = true } }), ["value"] = new JsonArray("--quickPlayMultiplayer", "${quickPlayMultiplayer}") }),
                    ["jvm"] = new JsonArray("-Djava.library.path=${natives_directory}", "-cp", "${classpath}"),
                    ["default-user-jvm"] = new JsonArray(new JsonObject { ["value"] = new JsonArray("-Xmx4G", "-XX:+UseCompactObjectHeaders") })
                }
            };
            var fabric = new JsonObject { ["id"] = LauncherProfiles.VersionId, ["inheritsFrom"] = "26.3", ["mainClass"] = "net.fabricmc.loader.impl.launch.knot.KnotClient",
                ["libraries"] = new JsonArray(new JsonObject { ["name"] = "net.fabricmc:fabric-loader:0.19.5", ["url"] = "https://maven.fabricmc.net/" }), ["arguments"] = new JsonObject { ["game"] = new JsonArray(), ["jvm"] = new JsonArray() } };
            var javaExe = Encoding.UTF8.GetBytes("java");
            var runtime = Encoding.UTF8.GetBytes(new JsonObject { ["files"] = new JsonObject { ["bin/java.exe"] = new JsonObject { ["type"] = "file", ["downloads"] = new JsonObject { ["raw"] = new JsonObject { ["sha1"] = Sha1(javaExe), ["size"] = javaExe.Length, ["url"] = "https://piston-data.mojang.com/java.exe" } } } } }.ToJsonString());
            web["https://piston-data.mojang.com/java.exe"] = javaExe; web["https://piston-meta.mojang.com/runtime.json"] = runtime;
            web[JavaRuntime.ListUrl] = Encoding.UTF8.GetBytes(new JsonObject { ["windows-x64"] = new JsonObject { ["java-runtime-epsilon"] = new JsonArray(new JsonObject { ["manifest"] = new JsonObject { ["sha1"] = Sha1(runtime), ["size"] = runtime.Length, ["url"] = "https://piston-meta.mojang.com/runtime.json" } }) } }.ToJsonString());
            var version = new GameVersion(Encoding.UTF8.GetBytes(vanilla.ToJsonString()), Encoding.UTF8.GetBytes(fabric.ToJsonString()));
            var pack = new[] { new PackFile(loader.Path, loader.Url, loader.Size, loader.Hash) };
            var game = Path.Combine(root, "instance"); Directory.CreateDirectory(game);
            var steps = new List<string>();
            var progress = new SyncProgress(p => { if (steps.LastOrDefault() != p.Step) steps.Add(p.Step); });
            var options = new FastStartOptions("pjamtest", "play.holylois.com", "1.3.0", 6144);
            var plan = await FastStart.PrepareAsync(new GameDownloads(new HttpClient(web), Path.Combine(root, "game"), []), version, pack, game, options, GamePlatform.Windows(), progress, CancellationToken.None);
            Check(steps.SequenceEqual(new[] { "java", "game", "assets" }), "Steps were reported out of order: " + string.Join(",", steps));
            Check(File.Exists(plan.Java) && File.Exists(plan.ArgumentFile) && plan.GameDirectory == Path.GetFullPath(game), "The plan points to missing files.");
            var args = plan.Arguments.ToList();
            var classpath = args[args.IndexOf("-cp") + 1].Split(';');
            Check(classpath.Length == 4 && classpath[0].EndsWith("fabric-loader-0.19.5.jar") && classpath[^1].EndsWith("26.3.jar") && classpath.All(File.Exists), "The class path is wrong: " + string.Join(" | ", classpath));
            Check(args[0] == "-Xmx6144M" && args[1] == "-Xms2048M" && args.Contains("-XX:+UseCompactObjectHeaders") && !args.Contains("-Xmx4G"), "Heap flags were not replaced by the computed size.");
            Check(args[args.IndexOf("--uuid") + 1] == FastStart.OfflineUuid("pjamtest") && args[args.IndexOf("--quickPlayMultiplayer") + 1] == "play.holylois.com" && !args.Contains("--xuid"), "Name, UUID or join are wrong.");
            Check(File.Exists(Path.Combine(root, "game", "assets", "objects", soundHash[..2], soundHash)), "Sounds are missing.");
            Check(File.ReadAllText(plan.ArgumentFile) == FastStart.ArgumentFile(args), "The argument file differs from the arguments.");
            var calls = web.Calls; web.Clear();
            var offline = await FastStart.PrepareAsync(new GameDownloads(new HttpClient(web), Path.Combine(root, "game"), []), version, pack, game, options with { JoinServer = null }, GamePlatform.Windows(), null, CancellationToken.None);
            Check(web.Calls == 0 && calls > 5 && !offline.Arguments.Contains("--quickPlayMultiplayer"), "A ready game needed the network, or the title-screen start still joins.");
            try { await FastStart.PrepareAsync(new GameDownloads(new HttpClient(web), Path.Combine(root, "game"), []), version, pack, game, options with { PlayerName = "bad name" }, GamePlatform.Windows(), null, CancellationToken.None); throw new Exception("An invalid name started."); }
            catch (InvalidDataException) { }
        });
        yield return ("The launcher profile gains and loses the join option without touching the rest", () =>
        {
            var original = Asset("fabric-profile.json");
            var joined = GameVersion.WithJoin(original, "play.holylois.com");
            var game = (JsonNode.Parse(joined)!["arguments"]!["game"] as JsonArray)!.Select(n => (string?)n).ToArray();
            Check(game.SequenceEqual(new[] { "--quickPlayMultiplayer", "play.holylois.com" }), "The join option was not added once.");
            Check(GameVersion.WithJoin(joined, "play.holylois.com").SequenceEqual(joined), "Adding the option twice changed it again.");
            var plain = GameVersion.WithJoin(joined, null);
            Check(((JsonNode.Parse(plain)!["arguments"]!["game"] as JsonArray)!).Count == 0, "The join option was not removed.");
            var a = JsonNode.Parse(original)!; var b = JsonNode.Parse(plain)!;
            Check(JsonNode.DeepEquals(a["libraries"], b["libraries"]) && (string?)a["id"] == (string?)b["id"] && JsonNode.DeepEquals(a["arguments"]!["jvm"], b["arguments"]!["jvm"]), "Other parts of the profile changed.");
            return Task.CompletedTask;
        });
    }
}

/// <summary>Progress that reports at once on the calling thread, so tests see every step in order.</summary>
internal sealed class SyncProgress(Action<GameProgress> report) : IProgress<GameProgress> { public void Report(GameProgress value) => report(value); }

internal static class JsonExtensions
{
    public static JsonObject Also(this JsonObject node, string key, string value) { node[key] = value; return node; }
}

/// <summary>A tiny in-memory web: known URLs answer with bytes, anything else is a network failure.</summary>
internal sealed class FakeWeb : HttpMessageHandler, System.Collections.IEnumerable
{
    private readonly Dictionary<string, byte[]> pages = [];
    public int Calls;
    public byte[] this[string url] { get => pages[url]; set => pages[url] = value; }
    public void Clear() { pages.Clear(); Calls = 0; }
    public System.Collections.IEnumerator GetEnumerator() => pages.GetEnumerator();
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
    {
        Interlocked.Increment(ref Calls);
        var url = request.RequestUri!.AbsoluteUri;
        if (!pages.TryGetValue(url, out var bytes)) throw new HttpRequestException("Simulated network failure for " + url);
        return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(bytes), RequestMessage = request });
    }
}
