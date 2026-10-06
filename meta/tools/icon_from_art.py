"""Build the launcher icon from the OC artwork (meta/oc.png) by cropping it around her head.

Run from meta/tools:  python3 icon_from_art.py
Writes app/src/main/res/mipmap-*/ic_launcher_foreground.png and meta/icon-preview.png.

An adaptive icon's layer is 108dp, but launchers only show the middle ~72dp (a circle or
squircle). CENTER and SPAN pick which part of the art lands in that visible middle.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "meta" / "oc.png"

CENTER = (135, 98)   # point of the art (px) that should sit in the middle of the icon
SPAN = 196           # how many art pixels should fill the visible 72dp circle
PAD = (14, 15, 20)   # fill for anything outside the artwork's edges (matches its dark background)

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def layer(size: int) -> Image.Image:
    art = Image.open(ART).convert("RGBA")
    full = SPAN * 108 / 72                      # art pixels covered by the whole 108dp layer
    left, top = CENTER[0] - full / 2, CENTER[1] - full / 2
    # Paste the art onto a padded canvas so the crop can extend past its edges.
    pad = int(full)
    canvas = Image.new("RGBA", (art.width + pad * 2, art.height + pad * 2), PAD + (255,))
    canvas.paste(art, (pad, pad), art)
    box = (round(left + pad), round(top + pad), round(left + pad + full), round(top + pad + full))
    return canvas.crop(box).resize((size, size), Image.LANCZOS)


def main():
    for name, px in DENSITIES.items():
        d = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        layer(px).save(d / "ic_launcher_foreground.png", optimize=True)

    # Preview: what a circular launcher shows, at three sizes.
    big = layer(432)
    vis = big.crop((72, 72, 360, 360))
    mask = Image.new("L", vis.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, *vis.size), fill=255)
    prev = Image.new("RGBA", (288 + 30 + 96 + 30 + 48 + 40, 308), (58, 63, 71, 255))
    x = 10
    for s in (288, 96, 48):
        icon = vis.resize((s, s), Image.LANCZOS)
        m = mask.resize((s, s), Image.LANCZOS)
        prev.paste(icon, (x, (308 - s) // 2), m)
        x += s + 30
    prev.save(ROOT / "meta" / "icon-preview.png")


if __name__ == "__main__":
    main()
