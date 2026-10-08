"""Draws the boombox (1.9.0 model): block textures, the block/item/cone models, and a 16x preview of the front.

Faces use Minecraft's automatic UVs (projected from the element position), so each texture is drawn where its faces sit:
north (front) u = 16 - x, v = 16 - y. Colours follow the launcher palette (dark plastic, gold accents, silver metal).
Usage: python make-texture.py [preview.png]
"""
import json, struct, sys, zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
TEX = HERE / "assets/holylois/textures/block"
MODELS = HERE / "assets/holylois/models"

PLASTIC, EDGE, SHADOW, DEEP = (42, 44, 50), (64, 67, 75), (26, 27, 31), (17, 18, 21)
GOLD, GOLD_DIM, ORANGE = (255, 226, 77), (201, 168, 58), (255, 122, 26)
SILVER, SILVER_DARK, GRILL, CONE = (190, 194, 202), (128, 132, 141), (33, 34, 39), (52, 54, 61)


def png(path, pixels):
    rows = b"".join(b"\x00" + b"".join(bytes(pixels[y][x]) + b"\xff" for x in range(16)) for y in range(16))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def grid(fill):
    return [[fill for _ in range(16)] for _ in range(16)]


# Body sides, back, top and bottom: plastic with a light top edge (v 8) and a dark bottom (v 15).
body = grid(PLASTIC)
for x in range(16):
    body[8][x] = EDGE; body[15][x] = SHADOW; body[13][x] = GOLD_DIM
for y in range(16):
    body[y][0] = body[y][15] = SHADOW

# Front: plastic, gold LCD and a cassette window between the speakers (u 7..8), the speakers sit on top as elements.
front = [row[:] for row in body]
for u in (7, 8):
    front[9][u] = GOLD
    front[10][u] = DEEP
    front[11][u] = SILVER_DARK
    front[12][u] = DEEP
    front[14][u] = GOLD_DIM

# Speaker rims (north faces at u 2..6 and 9..13, v 10..14): silver ring, dark grill inside; silver everywhere else (rim edges).
speaker = grid(SILVER)
for left in (2, 9):
    for v in range(10, 15):
        for u in range(left, left + 5):
            ring = v in (10, 14) or u in (left, left + 4)
            speaker[v][u] = SILVER if ring else GRILL
    speaker[10][left] = speaker[10][left + 4] = speaker[14][left] = speaker[14][left + 4] = SILVER_DARK

# Cones (north faces at u 3..5 and 10..12, v 11..13): charcoal with a gold dust cap; sides charcoal.
cone = grid(CONE)
for left in (3, 10):
    cone[12][left + 1] = GOLD
    cone[11][left + 1] = cone[13][left + 1] = cone[12][left] = cone[12][left + 2] = (70, 73, 82)

handle = grid(SILVER)
for x in range(16):
    handle[0][x] = handle[15][x] = SILVER_DARK
accent = grid(GOLD)
button = grid(ORANGE)

TEX.mkdir(parents=True, exist_ok=True)
for name, pixels in [("boombox_body", body), ("boombox_front", front), ("boombox_speaker", speaker),
                     ("boombox_cone", cone), ("boombox_handle", handle), ("boombox_accent", accent), ("boombox_button", button)]:
    png(TEX / f"{name}.png", pixels)
for old in ("boombox_side", "boombox_top"):
    (TEX / f"{old}.png").unlink(missing_ok=True)


def box(a, b, tex, faces=("north", "south", "east", "west", "up", "down"), **per_face):
    return {"from": a, "to": b, "faces": {f: {"texture": per_face.get(f, tex), **({"cullface": "down"} if f == "down" and a[1] == 0 else {})} for f in faces}}


elements = [
    box([1, 0, 5], [15, 8, 11], "#body", north="#front"),
    box([2, 1, 4.5], [7, 6, 5], "#speaker", faces=("north", "east", "west", "up", "down")),
    box([9, 1, 4.5], [14, 6, 5], "#speaker", faces=("north", "east", "west", "up", "down")),
    box([3, 2, 4.25], [6, 5, 4.5], "#cone", faces=("north", "east", "west", "up", "down")),
    box([10, 2, 4.25], [13, 5, 4.5], "#cone", faces=("north", "east", "west", "up", "down")),
    box([3, 8, 7.5], [4, 11, 8.5], "#handle"),
    box([12, 8, 7.5], [13, 11, 8.5], "#handle"),
    box([3, 11, 7.5], [13, 12, 8.5], "#handle"),
    box([6, 8, 6], [7, 8.5, 7], "#button", faces=("north", "south", "east", "west", "up")),
    box([9, 8, 6], [10, 8.5, 7], "#accent", faces=("north", "south", "east", "west", "up")),
    box([13.75, 8, 9.25], [14.25, 14.5, 9.75], "#handle", faces=("north", "south", "east", "west", "up")),
    box([13.5, 14.5, 9], [14.5, 15.5, 10], "#accent", faces=("north", "south", "east", "west", "up", "down")),
]
textures = {"particle": "holylois:block/boombox_body", **{k: f"holylois:block/boombox_{k}" for k in
            ("body", "front", "speaker", "cone", "handle", "accent", "button")}}
(MODELS / "block").mkdir(parents=True, exist_ok=True)
(MODELS / "item").mkdir(parents=True, exist_ok=True)
(MODELS / "block/boombox.json").write_text(json.dumps({"parent": "minecraft:block/block", "textures": textures, "elements": elements}, indent=1) + "\n")
# The cone that pulses with the music (BoomboxPulse): one cone centred on the block centre, front face only matters.
(MODELS / "block/boombox_cone.json").write_text(json.dumps({"textures": {"particle": "holylois:block/boombox_cone", "cone": "holylois:block/boombox_cone"},
    "elements": [{"from": [6.5, 6.5, 7.75], "to": [9.5, 9.5, 8.25], "faces": {
        "north": {"uv": [3, 11, 6, 14], "texture": "#cone"}, "east": {"uv": [0, 0, 1, 1], "texture": "#cone"},
        "west": {"uv": [0, 0, 1, 1], "texture": "#cone"}, "up": {"uv": [0, 0, 1, 1], "texture": "#cone"},
        "down": {"uv": [0, 0, 1, 1], "texture": "#cone"}}}]}, indent=1) + "\n")
(MODELS / "item/boombox.json").write_text(json.dumps({"parent": "holylois:block/boombox"}, indent=1) + "\n")
(HERE / "assets/holylois/items/boombox_cone.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": "holylois:block/boombox_cone"}}, indent=1) + "\n")


def preview(path):
    """The front as seen by a player: rims and cones painted over the body front, 16x."""
    pixels = [row[:] for row in front]
    for v in range(10, 15):
        for u in list(range(2, 7)) + list(range(9, 14)):
            pixels[v][u] = speaker[v][u]
    for v in range(11, 14):
        for u in list(range(3, 6)) + list(range(10, 13)):
            pixels[v][u] = cone[v][u]
    big = [[pixels[y // 16][x // 16] for x in range(256)] for y in range(256)]
    rows = b"".join(b"\x00" + b"".join(bytes(p) + b"\xff" for p in row) for row in big)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 256, 256, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


preview(sys.argv[1] if len(sys.argv) > 1 else HERE / "boombox-preview.png")
print("boombox textures and models written")
