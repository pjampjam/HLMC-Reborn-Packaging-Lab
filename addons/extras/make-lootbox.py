"""Draws the Holy Lootbox (owner round 5): the website's hero gold block as an item. A gold cube with the Holy Lois logo
engraved in each side (lit lip below-right, dark wall above-left, deeper gold recess, like GoldBlock.tsx), a soft bevel, and
the slow glint band of the site sweeping across every few seconds (animated texture).
Faces are 32x32 so the logo reads. The logo comes from the website's vector rects (src/lib/logo-data.ts).
Usage: python make-lootbox.py [path to HLMC-Reborn-Website]
"""
import json, re, struct, sys, zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
# Shares the PNG writer of make-texture.py (no image library needed).
src = (HERE / "make-texture.py").read_text(encoding="utf-8")
exec(src[src.index("def write_png"):src.index("def grid")], globals())

SITE = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parents[2] / "HLMC-Reborn-Website"
TEX = HERE / "assets/holylois/textures/block"
MODELS = HERE / "assets/holylois/models"
ITEMS = HERE / "assets/holylois/items"
S = 32

logo = json.loads(re.search(r"FULL_LOGO: LogoRects = (\{.*?\});", (SITE / "src/lib/logo-data.ts").read_text(encoding="utf-8")).group(1))
LW, LH, RECTS = logo["w"], logo["h"], logo["rects"]

# Website colours: gold faces, engraving wall/recess/lip.
GOLD = (252, 219, 74)
GOLD_HI, GOLD_LO, GOLD_EDGE = (255, 239, 140), (226, 178, 32), (186, 132, 12)
WALL, RECESS, LIP = (138, 101, 0), (184, 138, 0), (255, 246, 184)

SIZE, OFF = 22, 5  # the logo covers 70% of the face, like the site's inset 15%


def inside(px, py):
    x, y = (px + 0.5) * LW / SIZE, (py + 0.5) * LH / SIZE
    return any(rx <= x < rx + rw and ry <= y < ry + rh for rx, ry, rw, rh in RECTS)


shape = [[inside(x, y) for x in range(SIZE)] for y in range(SIZE)]
at = lambda x, y: 0 <= x < SIZE and 0 <= y < SIZE and shape[y][x]


def noise(x, y):
    # A fixed, gentle sparkle in the metal (no randomness: the same bytes every run).
    return ((x * 7 + y * 13 + (x * y) % 5) % 9) - 4


def face(engraved):
    img = [[None] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            n = noise(x, y)
            c = tuple(max(0, min(255, v + n * 2)) for v in GOLD)
            # Bevel: light top and left rim, darker bottom and right, a dark outline at the very edge.
            if x == 0 or y == 0: c = GOLD_HI
            elif x == S - 1 or y == S - 1: c = GOLD_EDGE
            elif x == 1 or y == 1: c = tuple((a + b) // 2 for a, b in zip(c, GOLD_HI))
            elif x == S - 2 or y == S - 2: c = GOLD_LO
            img[y][x] = c
    if engraved:
        for y in range(SIZE):
            for x in range(SIZE):
                if at(x, y): img[y + OFF][x + OFF] = RECESS if at(x - 1, y - 1) else WALL
                elif at(x - 1, y - 1): img[y + OFF][x + OFF] = LIP
    return img


def glint(img, step, steps):
    """The site's glint: a soft diagonal band of light crossing the face from left to right."""
    out = [row[:] for row in img]
    centre = -12 + (S + 24) * step / (steps - 1)
    for y in range(S):
        for x in range(S):
            d = abs((x + y * 0.5) - centre)
            if d < 5:
                k = (1 - d / 5) * 0.55
                out[y][x] = tuple(int(v + (255 - v) * k) for v in out[y][x])
    return out


STEPS = 14


def animated(name, engraved):
    base = face(engraved)
    frames = [base] + [glint(base, i, STEPS) for i in range(STEPS)]
    write_png(TEX / f"{name}.png", [row for f in frames for row in f])
    # Idle for a while, then the band sweeps (one tick per frame), like the site's 7 s glint.
    meta = {"animation": {"frames": [{"index": 0, "time": 120}] + list(range(1, STEPS + 1))}}
    (TEX / f"{name}.png.mcmeta").write_text(json.dumps(meta) + "\n", encoding="utf-8")


animated("holy_lootbox_side", True)
animated("holy_lootbox_top", False)

model = {"parent": "minecraft:block/cube", "textures": {
    "particle": "holylois:block/holy_lootbox_side", "north": "holylois:block/holy_lootbox_side",
    "south": "holylois:block/holy_lootbox_side", "east": "holylois:block/holy_lootbox_side",
    "west": "holylois:block/holy_lootbox_side", "up": "holylois:block/holy_lootbox_top", "down": "holylois:block/holy_lootbox_top"}}
(MODELS / "item/holy_lootbox.json").write_text(json.dumps(model, indent=1) + "\n", encoding="utf-8")
(ITEMS / "holy_lootbox.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": "holylois:item/holy_lootbox"}}, indent=1) + "\n", encoding="utf-8")
print("logo pixels:", sum(map(sum, shape)), "of", SIZE * SIZE)
