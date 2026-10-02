namespace HolyLois.Core;

// Report on the caller's thread; the outer UI progress object owns dispatching.
public sealed class InlineProgress<T>(Action<T> report) : IProgress<T>
{
    public void Report(T value) => report(value);
}
