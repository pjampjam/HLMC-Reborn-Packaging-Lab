"""Draws the boombox (1.9.0 model): block textures, the flat inventory icon, the block/item/cone models and a 16x preview.

Faces use Minecraft's automatic UVs (projected from the element position) unless a face sets "uv", so each texture is drawn
where its faces sit: north (front) u = 16 - x, v = 16 - y. Colours follow the launcher palette (dark plastic, gold accents,
silver metal). The inventory shows the flat icon (like vanilla items); hands, frames and the ground show the 3D model.
Usage: python make-texture.py [preview.png]
"""
import json, struct, sys, zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
TEX = HERE / "assets/holylois/textures/block"
ITEM_TEX = HERE / "assets/holylois/textures/item"
MODELS = HERE / "assets/holylois/models"

PLASTIC, PLASTIC_TOP, EDGE, SHADOW, DEEP = (42, 44, 50), (48, 50, 57), (70, 73, 82), (26, 27, 31), (17, 18, 21)
GOLD, GOLD_DIM, GOLD_LIGHT, ORANGE = (255, 226, 77), (201, 168, 58), (255, 240, 160), (255, 122, 26)
SILVER_LIGHT, SILVER, SILVER_DARK = (226, 229, 235), (184, 188, 197), (122, 126, 135)
CONE, CONE_RING, CONE_DEEP, GRIP, GRIP_RIDGE = (50, 52, 59), (74, 77, 86), (34, 35, 40), (30, 31, 36), (44, 46, 52)


def write_png(path, pixels):
    """pixels: rows of RGB tuples, None for transparent."""
    h, w = len(pixels), len(pixels[0])
    px = lambda p: b"\x00\x00\x00\x00" if p is None else bytes(p) + b"\xff"
    rows = b"".join(b"\x00" + b"".join(px(p) for p in row) for row in pixels)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def grid(fill):
    return [[fill for _ in range(16)] for _ in range(16)]


# Body. Rows 8..15 are the four sides (v = 16 - y): a light top edge, a gold trim line and a dark foot.
# Rows 0..5 are the top face (explicit uv [1, 0, 15, 6], front edge first), row 6 the bottom face.
body = grid(PLASTIC)
for x in range(16):
    body[8][x] = EDGE; body[13][x] = GOLD_DIM; body[15][x] = SHADOW
    for v in range(6):
        body[v][x] = PLASTIC_TOP
    body[0][x] = body[5][x] = EDGE
    body[6][x] = SHADOW
for v in range(6):
    body[v][1] = body[v][14] = EDGE

# Front: the body sides plus the strip between the speakers (u 7..8): gold display, cassette window with two silver reels.
front = [row[:] for row in body]
for u in (7, 8):
    front[9][u] = GOLD_LIGHT if u == 7 else GOLD
    front[10][u] = DEEP
    front[11][u] = SILVER
    front[12][u] = DEEP
    front[14][u] = SHADOW
for v in range(9, 15):
    front[v][1] = front[v][14] = EDGE  # the front corners catch light

# Speaker rims (north faces at u 2..6 and 9..13, v 10..14): bevelled silver ring, lit from the top left, rounded corners.
# The cone element covers the inside 3x3. Side faces of the rims use pixel (0, 0): dark silver for depth.
speaker = grid(SILVER_DARK)
for left in (2, 9):
    for v in range(10, 15):
        for u in range(left, left + 5):
            top, bottom, lside, rside = v == 10, v == 14, u == left, u == left + 4
            speaker[v][u] = SILVER_LIGHT if top or lside else SILVER_DARK if bottom or rside else CONE_DEEP
    for v, u in ((10, left), (10, left + 4), (14, left), (14, left + 4)):
        speaker[v][u] = PLASTIC
    speaker[10][left + 1] = speaker[11][left] = SILVER_LIGHT
    speaker[10][left + 3] = speaker[11][left + 4] = SILVER
    speaker[14][left + 1] = speaker[13][left] = SILVER

# Cones (north faces at u 3..5 and 10..12, v 11..13): dark corners, lighter ring, gold dust cap. Sides use pixel (0, 0).
cone = grid(CONE_DEEP)
for left in (3, 10):
    for v in range(11, 14):
        for u in range(left, left + 3):
            corner = v != 12 and u != left + 1
            cone[v][u] = CONE if corner else CONE_RING
    cone[12][left + 1] = GOLD
    cone[11][left + 1] = (88, 91, 101)

# Handle posts and antenna: silver, lit edges. Grip bar: dark rubber with ridges.
handle = grid(SILVER)
for v in range(16):
    for u in range(16):
        if (u + v) % 7 == 0:
            handle[v][u] = SILVER_LIGHT
grip = [[GRIP_RIDGE if u % 2 else GRIP for u in range(16)] for _ in range(16)]
accent = grid(GOLD)
for u in range(16):
    accent[0][u] = GOLD_LIGHT
button = grid(ORANGE)

TEX.mkdir(parents=True, exist_ok=True)
for name, pixels in [("boombox_body", body), ("boombox_front", front), ("boombox_speaker", speaker), ("boombox_cone", cone),
                     ("boombox_handle", handle), ("boombox_grip", grip), ("boombox_accent", accent), ("boombox_button", button)]:
    write_png(TEX / f"{name}.png", pixels)
for old in ("boombox_side", "boombox_top"):
    (TEX / f"{old}.png").unlink(missing_ok=True)

# Inventory icon: the front of the 3D model, drawn flat like a vanilla item (antenna, handle, two speakers, display).
# Like vanilla sprites it keeps a clear margin (1 px at the sides, 2 below); 12 px of body between the outlines.
ICON_ROWS = [
    "................",
    ".............g..",
    "....sSSSSSSs.s..",
    "....s......s.s..",
    "..OOOOOOOOOOOO..",
    ".OEEEEoEEgEEEEO.",
    ".OPlSsPggPlSsPO.",
    ".OlCcCsDDlCcCsO.",
    ".OScgCdrrScgCdO.",
    ".OsCcCdDDsCcCdO.",
    ".OPsddPPPPsddPO.",
    ".OttttttttttttO.",
    ".OHHHHHHHHHHHHO.",
    "..OOOOOOOOOOOO..",
    "................",
    "................",
]
ICON = {".": None, "O": (16, 17, 20), "E": EDGE, "P": PLASTIC, "H": SHADOW, "t": GOLD_DIM, "g": GOLD, "L": GOLD_LIGHT,
        "o": ORANGE, "l": SILVER_LIGHT, "S": SILVER, "s": SILVER_DARK, "d": SILVER_DARK, "C": CONE, "c": CONE_RING,
        "D": DEEP, "r": SILVER}
assert all(len(row) == 16 for row in ICON_ROWS) and len(ICON_ROWS) == 16
icon = [[ICON[ch] for ch in row] for row in ICON_ROWS]
ITEM_TEX.mkdir(parents=True, exist_ok=True)
write_png(ITEM_TEX / "boombox.png", icon)

SIDE_UV = [0, 0, 1, 1]


def box(a, b, tex, faces=("north", "south", "east", "west", "up", "down"), side_uv=None, **per_face):
    out = {}
    for f in faces:
        face = {"texture": per_face.get(f, tex)}
        if side_uv is not None and f != "north":
            face = {"uv": side_uv, **face}
        if f == "down" and a[1] == 0:
            face["cullface"] = "down"
        out[f] = face
    return {"from": a, "to": b, "faces": out}


body_box = box([1, 0, 5], [15, 8, 11], "#body", north="#front")
body_box["faces"]["up"] = {"uv": [1, 0, 15, 6], "texture": "#body"}
body_box["faces"]["down"] = {"uv": [1, 6, 15, 7], "texture": "#body", "cullface": "down"}
elements = [
    body_box,
    box([2, 1, 4.5], [7, 6, 5], "#speaker", faces=("north", "east", "west", "up", "down"), side_uv=SIDE_UV),
    box([9, 1, 4.5], [14, 6, 5], "#speaker", faces=("north", "east", "west", "up", "down"), side_uv=SIDE_UV),
    box([3, 2, 4.25], [6, 5, 4.5], "#cone", faces=("north", "east", "west", "up", "down"), side_uv=SIDE_UV),
    box([10, 2, 4.25], [13, 5, 4.5], "#cone", faces=("north", "east", "west", "up", "down"), side_uv=SIDE_UV),
    box([3, 8, 7.5], [4, 11, 8.5], "#handle"),
    box([12, 8, 7.5], [13, 11, 8.5], "#handle"),
    box([3, 11, 7.5], [13, 12, 8.5], "#grip"),
    box([6, 8, 6], [7, 8.5, 7], "#button", faces=("north", "south", "east", "west", "up")),
    box([9, 8, 6], [10, 8.5, 7], "#accent", faces=("north", "south", "east", "west", "up")),
    box([13.75, 8, 9.25], [14.25, 14.5, 9.75], "#handle", faces=("north", "south", "east", "west", "up")),
    box([13.5, 14.5, 9], [14.5, 15.5, 10], "#accent"),
]
textures = {"particle": "holylois:block/boombox_body", **{k: f"holylois:block/boombox_{k}" for k in
            ("body", "front", "speaker", "cone", "handle", "grip", "accent", "button")}}
(MODELS / "block").mkdir(parents=True, exist_ok=True)
(MODELS / "item").mkdir(parents=True, exist_ok=True)
(MODELS / "block/boombox.json").write_text(json.dumps({"parent": "minecraft:block/block", "textures": textures, "elements": elements}, indent=1) + "\n")
# The cone that pulses with the music (BoomboxPulse): one cone centred on the block centre, front face only matters.
(MODELS / "block/boombox_cone.json").write_text(json.dumps({"textures": {"particle": "holylois:block/boombox_cone", "cone": "holylois:block/boombox_cone"},
    "elements": [{"from": [6.5, 6.5, 7.75], "to": [9.5, 9.5, 8.25], "faces": {
        "north": {"uv": [3, 11, 6, 14], "texture": "#cone"}, "east": {"uv": SIDE_UV, "texture": "#cone"},
        "west": {"uv": SIDE_UV, "texture": "#cone"}, "up": {"uv": SIDE_UV, "texture": "#cone"},
        "down": {"uv": SIDE_UV, "texture": "#cone"}}}]}, indent=1) + "\n")

# Held: carried by the handle like a case, three quarters of the placed size, speakers facing out.
# Fitted on close-ups (owner round 5): the fist grips the middle of the handle bar, the bar a little up inside the fist.
CARRY = 0.75
third = {"rotation": [90, -90, 0], "translation": [0, -1.8, -1.12], "scale": [CARRY] * 3}
first = {"rotation": [0, 135, 0], "translation": [0, -3, 0], "scale": [0.5] * 3}
display = {
    "thirdperson_righthand": third, "thirdperson_lefthand": third,
    "firstperson_righthand": first, "firstperson_lefthand": first,
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4] * 3},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.75] * 3},
    "head": {"rotation": [0, 180, 0], "translation": [0, 6, 0], "scale": [0.8] * 3},
}
(MODELS / "item/boombox.json").write_text(json.dumps({"parent": "holylois:block/boombox", "display": display}, indent=1) + "\n")
(MODELS / "item/boombox_icon.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "holylois:item/boombox"}}, indent=1) + "\n")
(HERE / "assets/holylois/items/boombox.json").write_text(json.dumps({"model": {
    "type": "minecraft:select", "property": "minecraft:display_context",
    "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": "holylois:item/boombox_icon"}}],
    "fallback": {"type": "minecraft:model", "model": "holylois:item/boombox"}}}, indent=1) + "\n")
(HERE / "assets/holylois/items/boombox_cone.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": "holylois:block/boombox_cone"}}, indent=1) + "\n")


def preview(path):
    """The front as seen by a player (rims and cones painted over the body front) next to the icon, 16x."""
    pixels = [row[:] for row in front]
    for v in range(10, 15):
        for u in list(range(2, 7)) + list(range(9, 14)):
            pixels[v][u] = speaker[v][u]
    for v in range(11, 14):
        for u in list(range(3, 6)) + list(range(10, 13)):
            pixels[v][u] = cone[v][u]
    both = [pixels[y] + [None] + [p or (90, 90, 90) for p in icon[y]] for y in range(16)]
    big = [[both[y // 16][x // 16] for x in range(33 * 16)] for y in range(256)]
    write_png(path, [[p or (90, 90, 90) for p in row] for row in big])


preview(sys.argv[1] if len(sys.argv) > 1 else HERE / "boombox-preview.png")
print("boombox textures, icon and models written")
