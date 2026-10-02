using HolyLois.Core;
using Microsoft.Win32;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
namespace HolyLois.App;
public sealed class OwnerWindow : Window
{
    private readonly string root;
    private readonly TextBox profile = new() { Text = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "curseforge", "minecraft", "Instances", "Holy Lois Reborn") };
    private readonly TextBox version = new();
    private readonly TextBox notes = new() { Text = "Updated mods and shared settings.", AcceptsReturn = true, Height = 65, TextWrapping = TextWrapping.Wrap };
    private readonly TextBox log = new() { IsReadOnly = true, AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, Height = 155, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
    private readonly StackPanel settings = new();
    private readonly CheckBox tested = new() { Content = "I tested this CurseForge profile with Minecraft closed now.", Foreground = Brushes.White, Margin = new Thickness(0,14,0,8) };
    private readonly Button prepare = new() { Content = "2. Prepare update", Margin = new Thickness(0,8,8,0) };
    private readonly Button publish = new() { Content = "3. Publish to friends", IsEnabled = false, Margin = new Thickness(0,8,0,0) };
    private readonly Button connect = new() { Content = "Connect GitHub once", Margin = new Thickness(0,8,0,0) };
    private string? prepared;
    private string Gh => Path.Combine(root,"..","toolchain","github-cli","bin","gh.exe");
    private string Sdk => Path.Combine(root,"..","toolchain","dotnet","dotnet.exe");
    public OwnerWindow(string ownerRoot)
    {
        root = Path.GetFullPath(ownerRoot);
        if (!File.Exists(Path.Combine(root,"assets","pack.json")) || !File.Exists(Path.Combine(root,"private","release-private.pem"))) throw new IOException("Open the owner app from your private Holy Lois development folder. Friends do not receive the signing key.");
        Title = "Holy Lois: Reborn - Owner"; Width=850; Height=880; MinWidth=760; MinHeight=700; WindowStartupLocation=WindowStartupLocation.CenterScreen; Background=new SolidColorBrush(Color.FromRgb(32,33,31));
        SourceInitialized += (_,_)=>WindowCaption.Apply(this);
        var content = new StackPanel { Margin=new Thickness(30) }; Content=new ScrollViewer { Content=content, VerticalScrollBarVisibility=ScrollBarVisibility.Auto };
        content.Children.Add(new TextBlock { Text="Publish a Holy Lois update", FontSize=27, FontWeight=FontWeights.SemiBold });
        content.Children.Add(new TextBlock { Text="Choose your tested CurseForge profile. Review the shared settings. Prepare, then publish. Friends see the new version automatically.", Margin=new Thickness(0,8,0,20) });
        content.Children.Add(new TextBlock { Text="1. CurseForge profile folder" }); content.Children.Add(profile);
        var browse = new Button { Content="Choose folder", Margin=new Thickness(0,8,0,15), HorizontalAlignment=HorizontalAlignment.Left }; content.Children.Add(browse);
        browse.Click += (_,_)=>{var dialog=new OpenFolderDialog {Title="Choose the profile folder containing mods, config and options.txt"};if(dialog.ShowDialog(this)==true){profile.Text=dialog.FolderName;LoadSettings();}};
        content.Children.Add(new TextBlock { Text="New version" }); content.Children.Add(version);
        var previous = JsonSerializer.Deserialize<PackManifest>(File.ReadAllBytes(Path.Combine(root,"assets","pack.json")),JsonSettings.Options)!;
        var v=new Version(previous.Version);version.Text=$"{v.Major}.{v.Minor}.{v.Build+1}";
        content.Children.Add(new TextBlock {Text="What changed? Friends see these notes in the launcher.",Margin=new Thickness(0,14,0,5)});content.Children.Add(notes);
        content.Children.Add(new TextBlock {Text="Shared settings - checked files are copied from your profile. Microphone/account files are excluded. Unchecked new files stay personal.",Margin=new Thickness(0,16,0,8)});
        content.Children.Add(new ScrollViewer {Content=settings,Height=150,VerticalScrollBarVisibility=ScrollBarVisibility.Auto});
        content.Children.Add(new TextBlock {Text="Starting graphics stay at 12 chunks, DH 64, shaders off. A new pack version applies changed shared defaults to existing players too.",FontSize=12,Margin=new Thickness(0,8,0,0)});
        content.Children.Add(tested);var buttons=new WrapPanel();buttons.Children.Add(prepare);buttons.Children.Add(publish);content.Children.Add(buttons);content.Children.Add(connect);content.Children.Add(log);
        var server=new Button {Content="Server start / stop / update",HorizontalAlignment=HorizontalAlignment.Left,Margin=new Thickness(0,14,0,0)};content.Children.Add(server);
        server.Click+=(_,_)=>Process.Start(new ProcessStartInfo("powershell.exe") {UseShellExecute=true,ArgumentList={"-NoProfile","-File",Path.Combine(root,"maintainer","Server-Admin.ps1")}});
        var guide=new Button {Content="Open the short owner guide",HorizontalAlignment=HorizontalAlignment.Left,Margin=new Thickness(0,8,0,0)};content.Children.Add(guide);
        guide.Click+=(_,_)=>Process.Start(new ProcessStartInfo(Path.Combine(root,"ADMIN-GUIDE.md")){UseShellExecute=true});
        prepare.Click+=async(_,_)=>await Prepare();publish.Click+=async(_,_)=>await Publish();connect.Click+=(_,_)=>Connect();LoadSettings();
    }
    private void LoadSettings()
    {
        settings.Children.Clear(); var known=PackFeed.ReadDefaults(File.ReadAllBytes(Path.Combine(root,"assets","defaults.zip"))).Keys.Select(SharedDefaults.Target).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var files=new HashSet<string>(known,StringComparer.OrdinalIgnoreCase){"options.txt"};
        var config=Path.Combine(profile.Text,"config");if(Directory.Exists(config)) foreach(var file in Directory.GetFiles(config,"*",SearchOption.AllDirectories))
        {
            var relative=Path.GetRelativePath(profile.Text,file).Replace('\\','/');
            if(relative.StartsWith("config/yosbr/") || relative.Contains("voicechat",StringComparison.OrdinalIgnoreCase) || relative.Contains("xaero",StringComparison.OrdinalIgnoreCase))continue;
            if(Path.GetExtension(file) is not (".json" or ".json5" or ".toml" or ".properties" or ".conf"))continue;
            try{_=SharedDefaults.Target("config/yosbr/"+relative);files.Add(relative);}catch(InvalidDataException){}
        }
        foreach(var file in files.Order())settings.Children.Add(new CheckBox {Content=file,Tag=file,IsChecked=known.Contains(file),Foreground=Brushes.White,Margin=new Thickness(0,3,0,3)});
    }
    private async Task<string> Run(string executable,params string[] arguments)
    {
        var info=new ProcessStartInfo(executable){UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,WorkingDirectory=root};
        info.Environment["DOTNET_CLI_HOME"]=Path.GetFullPath(Path.Combine(root,"..","dotnet-home"));info.Environment["NUGET_PACKAGES"]=Path.GetFullPath(Path.Combine(root,"..","nuget"));
        foreach(var argument in arguments)info.ArgumentList.Add(argument);using var process=Process.Start(info)!;var stdout=process.StandardOutput.ReadToEndAsync();var stderr=process.StandardError.ReadToEndAsync();
        await process.WaitForExitAsync();var output=await stdout;var error=await stderr;if(process.ExitCode!=0)throw new IOException(string.IsNullOrWhiteSpace(error)?output:error);return output;
    }
    private async Task Prepare()
    {
        if(tested.IsChecked!=true){log.Text="Test the profile first, close Minecraft, then tick the test box.";return;}
        if(Process.GetProcessesByName("javaw").Length>0 || Process.GetProcessesByName("java").Length>0){log.Text="Close Minecraft before preparing an update.";return;}
        prepare.IsEnabled=publish.IsEnabled=false;
        try
        {
            if(!Version.TryParse(version.Text,out var v)||v.Build<0||v.Revision>=0)throw new IOException("Use a new version such as 1.5.1.");
            prepared=Path.Combine(root,"publish","pack-"+version.Text);if(Directory.Exists(prepared))throw new IOException("This version was already prepared. Choose a new version.");
            Directory.CreateDirectory(Path.Combine(root,"publish"));var selection=Path.Combine(root,"publish","selected-settings.json");
            AtomicFiles.WriteJson(selection,settings.Children.OfType<CheckBox>().Where(c=>c.IsChecked==true).Select(c=>(string)c.Tag).ToArray());
            log.Text="Checking official mod downloads and preparing shared settings...";
            var publisher=Path.Combine(root,"src","HolyLois.Publisher","bin","Release","net10.0","HolyLois.Publisher.dll");
            log.Text=await Run(Sdk,publisher,"prepare-pack",Path.Combine(root,"private","release-private.pem"),Path.Combine(root,"assets","pack.json"),profile.Text,version.Text,Path.Combine(root,"assets","defaults.zip"),prepared,selection);
            var manifest=JsonSerializer.Deserialize<PackManifest>(File.ReadAllBytes(Path.Combine(prepared,"pack.json")),JsonSettings.Options)!;
            manifest=manifest with {History=(manifest.History??[]).Select((n,i)=>i==0?n with {Summary=notes.Text}:n).ToArray()};
            AtomicFiles.WriteJson(Path.Combine(prepared,"pack.json"),manifest);
            await Run(Sdk,publisher,"sign",Path.Combine(root,"private","release-private.pem"),Path.Combine(prepared,"pack.json"),Path.Combine(prepared,"pack.json.sig"));
            log.AppendText("\nPrepared. Review your changes, then click Publish to friends. Server-required mods must be updated on the server first.");publish.IsEnabled=true;
        }
        catch(Exception ex){log.Text=ex.Message;prepared=null;}finally{prepare.IsEnabled=true;}
    }
    private void Connect()
    {
        if(!File.Exists(Gh)){log.Text="The official GitHub CLI is missing from the owner toolchain.";return;}
        Process.Start(new ProcessStartInfo(Gh){UseShellExecute=true,ArgumentList={"auth","login","--hostname","github.com","--git-protocol","https","--web","--skip-ssh-key"}});
        log.Text="Complete GitHub's sign-in yourself in the opened window/browser. Then publish. Never enter a password or token in Holy Lois.";
    }
    private async Task Publish()
    {
        if(prepared is null)return;publish.IsEnabled=prepare.IsEnabled=false;
        try
        {
            var published=await OwnerPublishing.PublishAsync(root,prepared,message=>log.Text=message);
            log.Text="Published "+published+". Friends see it on their next check. Server updates are separate and keep the world.";prepared=null;
        }
        catch(Exception ex){log.Text="Publishing stopped. "+ex.Message+"\nIf defaults were already published, keep that tag and upload the matching metadata pair to pack-stable.";publish.IsEnabled=true;}finally{prepare.IsEnabled=true;}
    }
}
