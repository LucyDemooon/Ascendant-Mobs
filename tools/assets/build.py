#!/usr/bin/env python3
"""Writes every generated texture, sound, model and data file into one or more version workspaces.

  python3 tools/assets/build.py 26.3            # one workspace
  python3 tools/assets/build.py all             # every workspace with a profile in data.PROFILES
"""
from __future__ import annotations

import json
import math
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import data  # noqa: E402
import icons  # noqa: E402
import items  # noqa: E402
import sounds  # noqa: E402
import synth  # noqa: E402
from lang import EN_US  # noqa: E402
from pixel import Rng, canvas, mix, put, rgb, strip  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = HERE.parent.parent
CACHE = HERE / '.cache'
NS = data.NS

# 2.x files and namespaces that 3.0 replaces.
OBSOLETE = ['assets/rpgmoblevelingsystem', 'data/rpgmoblevelingsystem', 'rpgmoblevelingsystem.mixins.json',
            'rpgmoblevelingsystem.png', 'logo.png']


def write_json(path: Path, value, ascii_only=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=ascii_only) + '\n', encoding='utf-8')


def save(img: Image.Image, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


def logo(size=64):
    """The mod icon: a horned skull with ember eyes before a blood moon, framed in tarnished gold."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2
    rng = Rng(42)
    for y in range(size):
        for x in range(size):
            ax, ay = abs(x - c), abs(y - c)
            corner = ax > c - 5 and ay > c - 5 and (ax - (c - 5)) ** 2 + (ay - (c - 5)) ** 2 > 25
            if corner:
                continue
            d = math.hypot(x - c, y - c * 0.9) / (size * 0.7)
            base = mix('#2a1218', '#0b0709', min(1.0, d))
            edge = max(ax, ay)
            if edge >= c - 1.2 or (corner is False and ax > c - 5 and ay > c - 5 and (ax - (c - 5)) ** 2 + (ay - (c - 5)) ** 2 > 16):
                base = rgb('#c99a3e')
            elif edge >= c - 2.2:
                base = rgb('#5e3d14')
            put(img, x, y, base)
    # blood moon
    mx, my, mr = c, 25.0, 17.5
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - mx, y - my)
            if d <= mr and img.getpixel((x, y))[3]:
                shade = mix('#e2553f', '#6e1418', min(1.0, max(0.0, (d / mr) ** 1.6 + (x - mx + y - my) / (mr * 5))))
                put(img, x, y, shade)
    for cx2, cy2, r2 in ((24, 18, 3.2), (40, 30, 2.4), (30, 33, 1.8), (36, 15, 1.6)):
        for y in range(size):
            for x in range(size):
                if math.hypot(x - cx2, y - cy2) <= r2 and math.hypot(x - mx, y - my) < mr - 1:
                    put(img, x, y, mix(img.getpixel((x, y)), '#5a1014', 0.35))
    glow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - mx, y - my)
            if mr < d < mr + 7:
                a = (1 - (d - mr) / 7) ** 2
                glow.putpixel((x, y), (230, 80, 50, int(110 * a)))
    img.alpha_composite(glow)
    # horned skull
    skull = """
..HH..................HH..
.HhH..................HhH.
.Hhh..................hhH.
.HhhH................HhhH.
..HhhH..............HhhH..
...HhhH..oooooooo..HhhH...
....HhhoBBBBBBBBBBohhH....
.....oBBBBBBBBBBBBBBo.....
....oBBBBBBBBBBBBBBBBo....
....oBBBBBBBBBBBBBBBBo....
...oBBBBBBBBBBBBBBBBBBo...
...oBBEEEEBBBBBBEEEEBBo...
...oBEEeeEEBBBBEEeeEEBo...
...oBEeLLeEBBBBEeLLeEBo...
...oBEEeeEEBBBBEEeeEEBo...
...oBBEEEEBBBBBBEEEEBBo...
....oBBBBBBBnnBBBBBBBo....
....ooBBBBBBnnBBBBBBoo....
.....oooBBBBBBBBBBooo.....
.......oBtBtBtBtBto.......
.......oBtBtBtBtBto.......
........oooooooooo........"""
    palette = {'o': '#1a0f0c', 'B': '#e6dcc8', 'H': '#2a1a10', 'h': '#8a6a48', 'E': '#1c0c08', 'e': '#c8541e',
               'L': '#ffd27a', 'n': '#3a2a20', 't': '#6e5e4c'}
    rows = skull.strip('\n').split('\n')
    ox, oy = (size - len(rows[0])) // 2, 30
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != '.':
                color = palette[ch]
                if ch == 'B':
                    color = mix('#f2ead8', '#a8987e', min(1.0, max(0.0, (dy - 6) / 16 + dx / 60)))
                put(img, ox + dx, oy + dy, color)
    eye_glow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    for ex, ey in ((ox + 8.5, oy + 13.5), (ox + 17.5, oy + 13.5)):
        for y in range(size):
            for x in range(size):
                d = math.hypot(x - ex, y - ey)
                if d < 6:
                    eye_glow.putpixel((x, y), (255, 170, 60, int(90 * (1 - d / 6) ** 2)))
    img.alpha_composite(eye_glow)
    for x, y in ((12, 50), (51, 47), (9, 30), (55, 22)):
        put(img, x, y, '#f0a43a')
    return img


def advancement_background():
    img = canvas()
    rng = Rng(7)
    for y in range(16):
        for x in range(16):
            v = rng.random()
            base = mix('#1a1214', '#0f0a0b', v)
            if (x % 8 == 0) or (y % 4 == 0 and (x // 8 + y // 4) % 2 == 0):
                base = rgb('#0a0607')
            put(img, x, y, base)
    return img


def build(version: str):
    profile = data.PROFILES[version]
    res = ROOT / version / 'common' / 'src' / 'main' / 'resources'
    assets = res / 'assets' / NS
    datadir = res / 'data' / NS
    for rel in OBSOLETE:
        p = res / rel
        if p.is_dir():
            shutil.rmtree(p)
        elif p.exists():
            p.unlink()

    tex = assets / 'textures'
    for name in items.ANIMATED:
        frames = items.frames(name)
        path = tex / 'item' / f'{name}.png'
        save(strip(frames), path)
        write_json(path.with_suffix('.png.mcmeta'), {'animation': {'frametime': items.ANIMATED[name][2], 'interpolate': True}})
    for name, make in items.STATIC.items():
        save(make(), tex / 'item' / f'{name}.png')
    sheet, _ = icons.sheet()
    save(sheet, tex / 'font' / 'icons.png')
    write_json(assets / 'font' / 'icons.json', data.font_json(), ascii_only=True)
    save(advancement_background(), tex / 'gui' / 'advancements' / 'backgrounds' / 'remnants.png')
    icon = logo().resize((256, 256), Image.NEAREST)
    save(icon, res / f'{NS}.png')
    save(icon, res / 'logo.png')

    CACHE.mkdir(exist_ok=True)
    for name, make in sounds.SOUNDS.items():
        cached = CACHE / f'{name}.ogg'
        if not cached.exists():
            synth.write_ogg(make(), cached)
        target = assets / 'sounds' / f'{name}.ogg'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(cached, target)
    write_json(assets / 'sounds.json', data.sounds_json())

    model_dir = assets / 'models' / 'item'
    if model_dir.exists():
        shutil.rmtree(model_dir)
    for item_id, model in data.item_models().items():
        write_json(model_dir / f'{item_id}.json', model)
    items_dir = assets / 'items'
    if items_dir.exists():
        shutil.rmtree(items_dir)
    if profile.items_json:
        for item_id, definition in data.item_definitions().items():
            write_json(items_dir / f'{item_id}.json', definition)
    write_json(assets / 'lang' / 'en_us.json', dict(sorted(EN_US.items())))

    recipe_dir = datadir / profile.dir('recipe')
    if recipe_dir.exists():
        shutil.rmtree(recipe_dir)
    for name, recipe in data.recipes(profile).items():
        write_json(recipe_dir / f'{name}.json', recipe)
    for (kind, name), value in data.tags(profile).items():
        write_json(datadir / profile.dir(kind) / f'{name}.json', value)
    adv_dir = datadir / profile.dir('advancement')
    if adv_dir.exists():
        shutil.rmtree(adv_dir)
    for name, adv in data.advancements(profile).items():
        write_json(adv_dir / f'{name}.json', adv)
    write_json(res / 'pack.mcmeta', {'pack': profile.pack})
    print(f'{version}: assets and data written to {res}')


if __name__ == '__main__':
    targets = sys.argv[1:] or ['26.3']
    if targets == ['all']:
        targets = [v for v in data.PROFILES if (ROOT / v).is_dir()]
    for target in targets:
        build(target)
