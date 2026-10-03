using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace HolyLois.Core;

public sealed record ServerPing(int Online, int Max, string[] Players, string Version);

// The same status ping the Minecraft server list uses. Bounded, read-only and never sends account data.
public static class ServerStatus
{
    public static async Task<ServerPing?> PingAsync(string address, CancellationToken cancel)
    {
        var (host, port) = Split(address);
        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancel);
            timeout.CancelAfter(TimeSpan.FromSeconds(5));
            using var client = new TcpClient();
            await client.ConnectAsync(host, port, timeout.Token);
            await using var stream = client.GetStream();
            var hostBytes = Encoding.UTF8.GetBytes(host);
            var handshake = new List<byte> { 0x00 };
            handshake.AddRange(VarInt(767)); handshake.AddRange(VarInt(hostBytes.Length)); handshake.AddRange(hostBytes);
            handshake.Add((byte)(port >> 8)); handshake.Add((byte)port); handshake.Add(0x01);
            var request = VarInt(handshake.Count).Concat(handshake).Concat(new byte[] { 0x01, 0x00 }).ToArray();
            await stream.WriteAsync(request, timeout.Token);
            var length = await ReadVarInt(stream, timeout.Token);
            if (length <= 0 || length > 256 * 1024) return null;
            var packet = new byte[length];
            await stream.ReadExactlyAsync(packet, timeout.Token);
            using var reader = new MemoryStream(packet);
            if (await ReadVarInt(reader, timeout.Token) != 0) return null;
            var jsonLength = await ReadVarInt(reader, timeout.Token);
            if (jsonLength <= 0 || jsonLength > packet.Length) return null;
            var json = new byte[jsonLength];
            await reader.ReadExactlyAsync(json, timeout.Token);
            return Parse(json);
        }
        catch (Exception error) when (error is IOException or SocketException or OperationCanceledException or JsonException or InvalidDataException)
        {
            if (cancel.IsCancellationRequested) throw;
            return null;
        }
    }

    public static ServerPing Parse(byte[] json)
    {
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;
        var players = root.GetProperty("players");
        var names = players.TryGetProperty("sample", out var sample) && sample.ValueKind == JsonValueKind.Array
            ? sample.EnumerateArray().Select(p => p.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "")
                .Where(n => n.Length is > 0 and <= 16 && n.All(c => char.IsAsciiLetterOrDigit(c) || c == '_')).Take(12).ToArray()
            : [];
        var version = root.TryGetProperty("version", out var v) && v.TryGetProperty("name", out var vn) ? vn.GetString() ?? "" : "";
        return new(players.GetProperty("online").GetInt32(), players.GetProperty("max").GetInt32(), names, version.Length > 40 ? version[..40] : version);
    }

    private static (string Host, int Port) Split(string address)
    {
        var colon = address.LastIndexOf(':');
        return colon > 0 && int.TryParse(address[(colon + 1)..], out var port) ? (address[..colon], port) : (address, 25565);
    }

    private static byte[] VarInt(int value)
    {
        var bytes = new List<byte>(); var unsigned = (uint)value;
        do { var b = (byte)(unsigned & 0x7F); unsigned >>= 7; bytes.Add(unsigned != 0 ? (byte)(b | 0x80) : b); } while (unsigned != 0);
        return bytes.ToArray();
    }

    private static async Task<int> ReadVarInt(Stream stream, CancellationToken cancel)
    {
        int value = 0; var one = new byte[1];
        for (int shift = 0; shift < 35; shift += 7)
        {
            await stream.ReadExactlyAsync(one, cancel);
            value |= (one[0] & 0x7F) << shift;
            if ((one[0] & 0x80) == 0) return value;
        }
        throw new InvalidDataException("Status length is too long.");
    }
}
