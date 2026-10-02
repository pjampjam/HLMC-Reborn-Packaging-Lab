using System.Buffers.Binary;
using System.IO.Compression;
using System.Text;

namespace HolyLois.Core;

// A bounded NBT codec retains tags belonging to other saved servers.
public sealed record NbtTag(byte Type, object Value);
public sealed record NbtList(byte Type, List<NbtTag> Items);
public sealed record NbtDocument(string Name, Dictionary<string, NbtTag> Root);

public static class NbtCodec
{
    public static NbtDocument Read(byte[] bytes)
    {
        if (bytes.Length > 4 * 1024 * 1024) throw new InvalidDataException("Server list is too large.");
        using var source = new MemoryStream(bytes);
        using var stream = bytes.Length > 1 && bytes[0] == 0x1f && bytes[1] == 0x8b
            ? (Stream)new GZipStream(source, CompressionMode.Decompress) : source;
        using var bounded = new MemoryStream();
        var buffer = new byte[8192]; int n;
        while ((n = stream.Read(buffer)) > 0)
        { bounded.Write(buffer, 0, n); if (bounded.Length > 4 * 1024 * 1024) throw new InvalidDataException("Expanded server list is too large."); }
        bounded.Position = 0;
        using var reader = new BinaryReader(bounded, Encoding.UTF8, true);
        if (reader.ReadByte() != 10) throw new InvalidDataException("Server list root is not an NBT compound.");
        var name = ReadString(reader); var root = (Dictionary<string, NbtTag>)ReadPayload(reader, 10, 0);
        if (bounded.Position != bounded.Length) throw new InvalidDataException("Unexpected server list trailing data.");
        return new(name, root);
    }

    public static byte[] Write(NbtDocument doc)
    {
        using var stream = new MemoryStream(); using var writer = new BinaryWriter(stream, Encoding.UTF8, true);
        writer.Write((byte)10); WriteString(writer, doc.Name); WritePayload(writer, new(10, doc.Root));
        writer.Flush(); return stream.ToArray();
    }

    private static int Count(BinaryReader r, int width = 1)
    {
        var n = ReadInt(r);
        if (n < 0 || n > 100000 || (long)n * width > r.BaseStream.Length - r.BaseStream.Position)
            throw new InvalidDataException("Invalid NBT collection size.");
        return n;
    }
    private static object ReadPayload(BinaryReader r, byte type, int depth)
    {
        if (depth > 32) throw new InvalidDataException("NBT nesting is too deep.");
        switch (type)
        {
            case 1: return r.ReadByte();
            case 2: return ReadShort(r);
            case 3: return ReadInt(r);
            case 4: return ReadLong(r);
            case 5: return BitConverter.Int32BitsToSingle(ReadInt(r));
            case 6: return BitConverter.Int64BitsToDouble(ReadLong(r));
            case 7: return Exact(r, Count(r));
            case 8: return ReadString(r);
            case 9:
                var child = r.ReadByte(); var count = Count(r);
                if (child > 12 || child == 0 && count != 0) throw new InvalidDataException("Invalid NBT list tag.");
                var list = new List<NbtTag>();
                for (var i = 0; i < count; i++) list.Add(new(child, ReadPayload(r, child, depth + 1)));
                return new NbtList(child, list);
            case 10:
                var compound = new Dictionary<string, NbtTag>(StringComparer.Ordinal);
                while (true)
                {
                    var tag = r.ReadByte(); if (tag == 0) return compound;
                    var name = ReadString(r);
                    if (compound.Count >= 10000 || !compound.TryAdd(name, new(tag, ReadPayload(r, tag, depth + 1))))
                        throw new InvalidDataException("Invalid NBT compound.");
                }
            case 11:
                var ints = new int[Count(r, 4)]; for (var i = 0; i < ints.Length; i++) ints[i] = ReadInt(r); return ints;
            case 12:
                var longs = new long[Count(r, 8)]; for (var i = 0; i < longs.Length; i++) longs[i] = ReadLong(r); return longs;
            default: throw new InvalidDataException("Unknown NBT tag type.");
        }
    }

    private static void WritePayload(BinaryWriter w, NbtTag tag)
    {
        switch (tag.Type)
        {
            case 1: w.Write((byte)tag.Value); break;
            case 2: WriteShort(w, (short)tag.Value); break;
            case 3: WriteInt(w, (int)tag.Value); break;
            case 4: WriteLong(w, (long)tag.Value); break;
            case 5: WriteInt(w, BitConverter.SingleToInt32Bits((float)tag.Value)); break;
            case 6: WriteLong(w, BitConverter.DoubleToInt64Bits((double)tag.Value)); break;
            case 7: var bytes = (byte[])tag.Value; WriteInt(w, bytes.Length); w.Write(bytes); break;
            case 8: WriteString(w, (string)tag.Value); break;
            case 9:
                var list = (NbtList)tag.Value; w.Write(list.Type); WriteInt(w, list.Items.Count);
                foreach (var item in list.Items) { if (item.Type != list.Type) throw new InvalidDataException("Mixed NBT list."); WritePayload(w, item); } break;
            case 10:
                foreach (var (name, item) in (Dictionary<string, NbtTag>)tag.Value)
                { w.Write(item.Type); WriteString(w, name); WritePayload(w, item); } w.Write((byte)0); break;
            case 11: var ints = (int[])tag.Value; WriteInt(w, ints.Length); foreach (var i in ints) WriteInt(w, i); break;
            case 12: var longs = (long[])tag.Value; WriteInt(w, longs.Length); foreach (var l in longs) WriteLong(w, l); break;
            default: throw new InvalidDataException("Unknown NBT tag type.");
        }
    }
    private static byte[] Exact(BinaryReader r, int n) { var data = r.ReadBytes(n); if (data.Length != n) throw new EndOfStreamException(); return data; }
    private static short ReadShort(BinaryReader r) => BinaryPrimitives.ReadInt16BigEndian(Exact(r, 2));
    private static int ReadInt(BinaryReader r) => BinaryPrimitives.ReadInt32BigEndian(Exact(r, 4));
    private static long ReadLong(BinaryReader r) => BinaryPrimitives.ReadInt64BigEndian(Exact(r, 8));
    private static void WriteShort(BinaryWriter w, short n) { Span<byte> b = stackalloc byte[2]; BinaryPrimitives.WriteInt16BigEndian(b, n); w.Write(b); }
    private static void WriteInt(BinaryWriter w, int n) { Span<byte> b = stackalloc byte[4]; BinaryPrimitives.WriteInt32BigEndian(b, n); w.Write(b); }
    private static void WriteLong(BinaryWriter w, long n) { Span<byte> b = stackalloc byte[8]; BinaryPrimitives.WriteInt64BigEndian(b, n); w.Write(b); }

    // NBT uses Java's modified UTF-8 (including UTF-16 surrogate code units).
    private static string ReadString(BinaryReader r)
    {
        var size = BinaryPrimitives.ReadUInt16BigEndian(Exact(r, 2)); var b = Exact(r, size); var chars = new List<char>();
        for (var i = 0; i < b.Length;)
        {
            var c = b[i++];
            if (c is > 0 and < 128) { chars.Add((char)c); continue; }
            if ((c & 0xe0) == 0xc0 && i < b.Length && (b[i] & 0xc0) == 0x80)
            { chars.Add((char)(((c & 31) << 6) | (b[i++] & 63))); continue; }
            if ((c & 0xf0) == 0xe0 && i + 1 < b.Length && (b[i] & 0xc0) == 0x80 && (b[i + 1] & 0xc0) == 0x80)
            { chars.Add((char)(((c & 15) << 12) | ((b[i++] & 63) << 6) | (b[i++] & 63))); continue; }
            throw new InvalidDataException("Invalid NBT string.");
        }
        return new(chars.ToArray());
    }
    private static void WriteString(BinaryWriter w, string text)
    {
        using var encoded = new MemoryStream();
        foreach (var c in text)
        {
            if (c is > '\0' and < '\u0080') encoded.WriteByte((byte)c);
            else if (c < '\u0800') { encoded.WriteByte((byte)(0xc0 | (c >> 6))); encoded.WriteByte((byte)(0x80 | (c & 63))); }
            else { encoded.WriteByte((byte)(0xe0 | (c >> 12))); encoded.WriteByte((byte)(0x80 | ((c >> 6) & 63))); encoded.WriteByte((byte)(0x80 | (c & 63))); }
        }
        if (encoded.Length > ushort.MaxValue) throw new InvalidDataException("NBT string is too long.");
        Span<byte> length = stackalloc byte[2]; BinaryPrimitives.WriteUInt16BigEndian(length, (ushort)encoded.Length); w.Write(length); w.Write(encoded.ToArray());
    }
}

public static class ServerList
{
    public static byte[] Upsert(byte[]? existing, string address, byte[] icon)
    {
        var doc = existing is null ? new NbtDocument("", []) : NbtCodec.Read(existing);
        if (!doc.Root.TryGetValue("servers", out var tag)) doc.Root["servers"] = tag = new(9, new NbtList(10, []));
        if (tag.Type != 9 || tag.Value is not NbtList { Type: 10 } servers)
            throw new InvalidDataException("Server list has an unexpected layout; it was preserved.");
        var entry = servers.Items.Select(t => (Dictionary<string, NbtTag>)t.Value).FirstOrDefault(
            e => e.TryGetValue("ip", out var ip) && ip.Type == 8 && ((string)ip.Value).Equals(address, StringComparison.OrdinalIgnoreCase));
        if (entry is null) { entry = []; servers.Items.Insert(0, new(10, entry)); }
        entry["name"] = new(8, "Holy Lois: Reborn"); entry["ip"] = new(8, address);
        entry["icon"] = new(8, Convert.ToBase64String(icon));
        return NbtCodec.Write(doc);
    }

    public static void Ensure(string instance, string address, byte[] icon)
    {
        var path = SafePaths.Resolve(instance, "servers.dat");
        var old = File.Exists(path) ? File.ReadAllBytes(path) : null;
        var data = Upsert(old, address, icon);
        if (old is not null && data.AsSpan().SequenceEqual(old)) return;
        if (old is not null) AtomicFiles.Write(SafePaths.Resolve(instance, "servers.dat.holylois-backup"), old);
        AtomicFiles.Write(path, data);
    }
}
