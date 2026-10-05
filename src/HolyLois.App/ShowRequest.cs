using System.IO;
using System.Threading;

namespace HolyLois.App;

/// <summary>Lets a second start of the app ask the running one to show its window (it may be hidden while the game runs).</summary>
public static class ShowRequest
{
    private const string Name = @"Local\HolyLoisReborn.ShowWindow";

    public static void Send()
    {
        try { if (EventWaitHandle.TryOpenExisting(Name, out var signal)) using (signal) signal.Set(); }
        catch (Exception ex) when (ex is UnauthorizedAccessException or IOException or PlatformNotSupportedException) { }
    }

    public static void Listen(Action show)
    {
        EventWaitHandle signal;
        try { signal = new EventWaitHandle(false, EventResetMode.AutoReset, Name); }
        catch (Exception ex) when (ex is UnauthorizedAccessException or IOException or PlatformNotSupportedException or WaitHandleCannotBeOpenedException) { return; }
        new Thread(() => { while (signal.WaitOne()) show(); }) { IsBackground = true, Name = "Holy Lois show requests" }.Start();
    }
}
