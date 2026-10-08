"""Draws the boombox textures: the 16x16 item plus the block's front (same picture), side, top and handle."""
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


# Block faces sample sub-areas of these, so the patterns are even all over.
SIDE = ["k" * 16 if y in (0, 15) else "kbbbbb" + ("hhhh" if y in (6, 8, 10) else "bbbb") + "bbbbbk" for y in range(16)]
TOP = ["".join("k" if x in (0, 15) or y in (0, 15) else "o" if (x, y) == (11, 7) else "s" if y == 7 and 3 <= x <= 8 else "h" for x in range(16)) for y in range(16)]
HANDLE = ["s" * 16 for _ in range(16)]


def png(path, scale, grid=None):
    grid = grid or GRID
    rows = b"".join(b"\x00" + b"".join(bytes(C[grid[y // scale][x // scale]] + (255,)) if grid[y // scale][x // scale] != "." else b"\x00\x00\x00\x00"
                                     for x in range(16 * scale)) for y in range(16 * scale))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16 * scale, 16 * scale, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


png("assets/holylois/textures/item/boombox.png", 1)
Path("assets/holylois/textures/block").mkdir(parents=True, exist_ok=True)
# 26.3 keeps block and item textures in separate atlases: a block model may only use block textures, so the front is a copy.
for name, grid in [("boombox_front", GRID), ("boombox_side", SIDE), ("boombox_top", TOP), ("boombox_handle", HANDLE)]:
    assert all(len(row) == 16 for row in grid) and len(grid) == 16
    png(f"assets/holylois/textures/block/{name}.png", 1, grid)
png(sys.argv[1] if len(sys.argv) > 1 else "boombox-preview.png", 16)
print("texture written")
