using System.IO.Compression;
using System.Text.Json;
using System.Text;
using System.Text.RegularExpressions;
namespace HolyLois.Core;
public static class FabricDependencies
{
    public static string[] CheckClient(string profile)
    {
        var metadata=new List<JsonElement>();var primary=new HashSet<string>();
        foreach(var path in Directory.GetFiles(Path.Combine(profile,"mods"),"*.jar"))
        {
            using var zip=ZipFile.OpenRead(path);var info=Read(zip,metadata,0);
            if(info is null)throw new InvalidDataException("Not a Fabric mod: "+Path.GetFileName(path));
            var id=info.Value.GetProperty("id").GetString()!;if(!primary.Add(id))throw new InvalidDataException("Duplicate mod: "+id);
            if(info.Value.TryGetProperty("environment",out var side)&&side.GetString()=="server")throw new InvalidDataException("Server-only mod in client pack: "+id);
        }
        var available=new Dictionary<string,string>{{"minecraft","26.3"},{"java","25"},{"fabricloader","0.19.5"},{"mixinextras","0.5.5"}};
        foreach(var info in metadata)
        {
            if(info.TryGetProperty("environment",out var environment)&&environment.GetString()=="server")continue;
            var ids=new List<string>{info.GetProperty("id").GetString()!};if(info.TryGetProperty("provides",out var aliases))ids.AddRange(aliases.EnumerateArray().Select(x=>x.GetString()!));
            var v=info.GetProperty("version").GetString()!;foreach(var id in ids)if(!available.TryGetValue(id,out var old)||Numbers(v)>Numbers(old))available[id]=v;
        }
        foreach(var info in metadata)
        {
            if(info.TryGetProperty("environment",out var environment)&&environment.GetString()=="server")continue;
            var id=info.GetProperty("id").GetString();
            if(info.TryGetProperty("depends",out var dependencies))foreach(var dependency in dependencies.EnumerateObject())
                if(!available.TryGetValue(dependency.Name,out var found)||!Satisfies(found,dependency.Value))throw new InvalidDataException(id+" needs "+dependency.Name+" "+dependency.Value+". Add/update that dependency before publishing.");
            if(info.TryGetProperty("breaks",out var conflicts))foreach(var conflict in conflicts.EnumerateObject())
                if(available.TryGetValue(conflict.Name,out var found)&&Satisfies(found,conflict.Value))throw new InvalidDataException(id+" conflicts with "+conflict.Name+" "+found);
        }
        return primary.Order().ToArray();
    }
    private static JsonElement? Read(ZipArchive zip,List<JsonElement> metadata,int depth)
    {
        if(depth>5)throw new InvalidDataException("Nested mod depth exceeded.");var entry=zip.GetEntry("fabric.mod.json");if(entry is null)return null;
        using var source=entry.Open();using var reader=new StreamReader(source);using var doc=JsonDocument.Parse(EscapeControls(reader.ReadToEnd()),new JsonDocumentOptions{AllowTrailingCommas=true,CommentHandling=JsonCommentHandling.Skip});var info=doc.RootElement.Clone();metadata.Add(info);
        if(info.TryGetProperty("jars",out var jars))foreach(var jar in jars.EnumerateArray())
        {
            var name=jar.GetProperty("file").GetString()!;var nested=zip.GetEntry(name);if(nested is null)continue;if(nested.Length>64*1024*1024)throw new InvalidDataException("Nested mod too large.");
            using var stream=nested.Open();using var memory=new MemoryStream();stream.CopyTo(memory);memory.Position=0;using var child=new ZipArchive(memory);Read(child,metadata,depth+1);
        }
        return info;
    }
    private static string EscapeControls(string json)
    {
        if(json.Length>1024*1024)throw new InvalidDataException("Mod metadata too large.");
        var output=new StringBuilder();var quoted=false;var escaped=false;var lineComment=false;var blockComment=false;
        for(var i=0;i<json.Length;i++)
        {
            var c=json[i];
            if(lineComment){output.Append(c);if(c=='\n')lineComment=false;continue;}
            if(blockComment){output.Append(c);if(c=='*'&&i+1<json.Length&&json[i+1]=='/'){output.Append(json[++i]);blockComment=false;}continue;}
            if(!quoted&&c=='/'&&i+1<json.Length&&(json[i+1]=='/'||json[i+1]=='*')){lineComment=json[i+1]=='/';blockComment=!lineComment;output.Append(c).Append(json[++i]);continue;}
            if(escaped){output.Append(c);escaped=false;continue;}
            if(quoted&&c=='\\'){output.Append(c);escaped=true;continue;}
            if(c=='"'){quoted=!quoted;output.Append(c);continue;}
            if(quoted&&c<32)output.Append("\\u").Append(((int)c).ToString("x4"));else output.Append(c);
        }
        return output.ToString();
    }
    private static Version Numbers(string value)
    {
        var m=Regex.Match(value,@"^(\d+)(?:\.(\d+))?(?:\.(\d+))?");if(!m.Success)throw new InvalidDataException("Review the dependency version: "+value);
        return new Version(int.Parse(m.Groups[1].Value),m.Groups[2].Success?int.Parse(m.Groups[2].Value):0,m.Groups[3].Success?int.Parse(m.Groups[3].Value):0);
    }
    private static bool Satisfies(string actual,JsonElement expression)=>expression.ValueKind==JsonValueKind.Array?expression.EnumerateArray().Any(e=>Satisfies(actual,e)):Satisfies(actual,expression.GetString()!);
    private static bool Satisfies(string actual,string expression)
    {
        if(expression=="*")return true;if(expression.Contains("||"))return expression.Split("||").Any(e=>Satisfies(actual,e.Trim()));var a=Numbers(actual);
        foreach(var part in expression.Split(' ',StringSplitOptions.RemoveEmptyEntries))
        {
            var m=Regex.Match(part,@"^(>=|<=|>|<|~|\^|=)?(.+)$");var op=m.Groups[1].Value;var raw=m.Groups[2].Value;
            if(raw.Contains('x')||raw.Contains('*')){var prefix=raw.Replace('*','x').Split('.');var actualParts=new[]{a.Major,a.Minor,a.Build};for(var i=0;i<prefix.Length&&prefix[i]!="x";i++)if(i>=3||int.Parse(prefix[i])!=actualParts[i])return false;continue;}
            var b=Numbers(raw);var ok=op switch{""=>a==b,"="=>a==b,">="=>a>=b,"<="=>a<=b,">"=>a>b,"<"=>a<b,"~"=>a>=b&&a<new Version(b.Major,b.Minor+1,0),"^"=>a>=b&&a<(b.Major>0?new Version(b.Major+1,0,0):new Version(0,b.Minor+1,0)),_=>false};if(!ok)return false;
        }
        return true;
    }
}

