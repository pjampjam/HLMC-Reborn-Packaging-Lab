"""Draws the 16x16 boombox texture: charcoal body, two speaker cones with gold rims, a level meter and a power light."""
import struct, sys, zlib
from pathlib import Path

C = {"k": (20, 20, 24), "b": (44, 46, 53), "h": (78, 81, 90), "g": (244, 197, 66), "G": (255, 232, 160),
     "d": (168, 130, 31), "c": (16, 17, 20), "o": (255, 122, 26), "s": (150, 154, 163)}
GRID = [
    "................",
    "................",
    "................",
    "....ssssssss....",
    "....s......s....",
    ".kkkkkkkkkkkkkk.",
    "khhhhhhhohhhhhhk",
    "kbgggbbbbbbgggbk",
    "kgcccghhhhgcccgk",
    "kgcGcghcchgcGcgk",
    "kdcccdhhhhdcccdk",
    "kbdddbbbbbbdddbk",
    ".kkkkkkkkkkkkkk.",
    "................",
    "................",
    "................",
]
assert all(len(row) == 16 for row in GRID) and len(GRID) == 16


def png(path, scale):
    rows = b"".join(b"\x00" + b"".join(bytes(C[GRID[y // scale][x // scale]] + (255,)) if GRID[y // scale][x // scale] != "." else b"\x00\x00\x00\x00"
                                     for x in range(16 * scale)) for y in range(16 * scale))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16 * scale, 16 * scale, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


png("assets/holylois/textures/item/boombox.png", 1)
png(sys.argv[1] if len(sys.argv) > 1 else "boombox-preview.png", 16)
print("texture written")
