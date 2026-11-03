"""Item textures: Remnant Shard, Hunter's Lens, Brand of Challenge, Vital Ember, Mending Salve and Hunter's Draught
(16x16; the shard, brand and ember are animated)."""
from __future__ import annotations

import math

from PIL import Image

from pixel import canvas, mix, put, rgb, stamp

SHARD = """
................
.......a........
......aha.......
......ahba......
.....ahdba...a..
.....ahdcba.aha.
....ahdecba.ahba
....ahdecbba.aa.
...ahdeecbba....
...ahdecbbba....
..ahddcbbba.....
..abccbbbba.....
..aabbbbaa......
....aaaa........
................
................"""


def shard(frame=0, frames=1):
    """A splinter of something that was once alive: dark violet glass around an ember core that slowly pulses."""
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * frame / max(1, frames))
    palette = {
        'a': '#160b12',
        'b': '#2e1424',
        'c': '#5a2436',
        'h': mix('#b8584a', '#e07a52', pulse * 0.6),
        'd': mix('#d0702e', '#f0a43a', pulse),
        'e': mix('#f6c56a', '#fff1c4', pulse),
    }
    img = canvas()
    stamp(img, 0, 0, SHARD, palette)
    if pulse > 0.7:
        put(img, 6, 8, '#fffbe8')
    return img


LENS = """
................
............aaa.
...........aLLa.
..........aLlLa.
.........abLLa..
........abhca...
.......abhcca...
......akkkca....
.....akkkka.....
....abhcba......
...abhcba.......
..abhcba........
.aEhcba.........
.aeEEa..........
..aaa...........
................"""


def lens():
    """A brass spyglass wrapped in hunter's leather, its eyepiece glinting amber."""
    palette = {
        'a': '#1c120c',
        'b': '#6a4418',
        'c': '#a8722a',
        'h': '#e8bd62',
        'k': '#4a2c1c',
        'E': '#5c3a14',
        'e': '#c8862e',
        'L': '#e0602e',
        'l': '#ffd27a',
    }
    img = canvas()
    stamp(img, 0, 0, LENS, palette)
    put(img, 12, 2, '#fff2c8')
    return img


def brand(frame=0, frames=1):
    """A blackened iron seal with a gold rim; the crossed-blades rune in its face smoulders."""
    glow = 0.5 + 0.5 * math.sin(2 * math.pi * frame / max(1, frames))
    img = canvas()
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d > 7.2:
                continue
            if d > 6.2:
                put(img, x, y, '#1a0f0a')
            elif d > 5.2:
                light = (cx - x) + (cy - y) > 0
                put(img, x, y, '#e2b35a' if light else '#9a6a24')
            elif d > 4.6:
                put(img, x, y, '#5a3a16')
            else:
                shade = mix('#3a1418', '#1e0b0e', min(1.0, (x + y) / 22))
                put(img, x, y, shade)
    ember = mix('#b8341e', '#ff7a2e', glow)
    hot = mix('#ff9a3c', '#ffe08a', glow)
    rune = [(5, 5), (6, 6), (7, 7), (8, 8), (9, 9), (10, 10), (10, 5), (9, 6), (8, 7), (7, 8), (6, 9), (5, 10)]
    for x, y in rune:
        put(img, x, y, ember)
    for x, y in ((7, 7), (8, 8), (8, 7), (7, 8)):
        put(img, x, y, hot)
    for x, y in ((4, 6), (6, 4), (11, 9), (9, 11)):
        put(img, x, y, '#7a2a1c')
    put(img, 5, 3, '#fff0c0')
    return img


EMBER = """
................
................
...aa.....aa....
..abba...abba...
.abccba.abccba..
.abcddbabcddba..
.abcdeedcdeedba.
.abcdeeeeeeedba.
..abcdeefeedba..
...abcdeeedba...
....abcdedba....
.....abcdba.....
......abba......
.......aa.......
................
................"""


def ember(frame=0, frames=1):
    """A coal-dark heart split by veins of fire; its core beats slowly."""
    beat = max(0.0, math.sin(2 * math.pi * frame / max(1, frames))) ** 2
    palette = {
        'a': '#1a0a08',
        'b': '#3c1210',
        'c': mix('#7a1e14', '#9a2a18', beat),
        'd': mix('#c4421e', '#e05a24', beat),
        'e': mix('#f08a2c', '#ffb44a', beat),
        'f': mix('#ffd27a', '#fff6d8', beat),
    }
    img = canvas()
    stamp(img, 0, 0, EMBER, palette)
    put(img, 3, 4, '#d26a4a')
    return img


SALVE = """
................
................
................
................
.....aaaaaa.....
....abbbbbba....
...abccccccba...
...addddddda....
...aeffgfffea...
...aeffffgfea...
...aeefffffea...
...aeeeeeeeea...
....aeeeeeea....
.....aaaaaa.....
................
................"""


def salve():
    """A dented tin of green salve with a waxed brass lid."""
    palette = {
        'a': '#1a1410',
        'b': '#c89a4a',
        'c': '#8a6428',
        'd': '#5a4a3a',
        'e': '#7a6a58',
        'f': '#5aa04a',
        'g': '#a8e07a',
    }
    img = canvas()
    stamp(img, 0, 0, SALVE, palette)
    put(img, 5, 5, '#f0d48a')
    return img


DRAUGHT = """
................
.......aa.......
......abba......
.......cc.......
.......dd.......
......adda......
.....aeeeea.....
....aeffffea....
...aeffgggfea...
...afgghhggfa...
...afghhhhgfa...
...afgghhggfa...
....affgggfa....
.....affffa.....
......aaaa......
................"""


def draught():
    """A squat dark-glass flask of amber tonic, corked and tied with twine."""
    palette = {
        'a': '#120c0a',
        'b': '#8a5a2a',
        'c': '#c8a46a',
        'd': '#2a3a3a',
        'e': '#3a4a48',
        'f': '#6a2a12',
        'g': '#b8541a',
        'h': '#f0a43a',
    }
    img = canvas()
    stamp(img, 0, 0, DRAUGHT, palette)
    put(img, 5, 8, '#d8e8e0')
    put(img, 5, 9, '#a8c0b8')
    return img


ANIMATED = {'remnant_shard': (shard, 8, 3), 'brand_of_challenge': (brand, 8, 3), 'vital_ember': (ember, 8, 4)}
STATIC = {'hunters_lens': lens, 'mending_salve': salve, 'hunters_draught': draught}


def frames(name):
    make, count, _ = ANIMATED[name]
    return [make(i, count) for i in range(count)]
