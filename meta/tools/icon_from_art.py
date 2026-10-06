"""Build the launcher icon from the OC artwork (meta/oc.png).

meta/oc.png is already framed the way the icon should look, so it fills the part of the icon a
launcher actually shows (the middle 72dp of the 108dp adaptive-icon layer). The extra margin
around it is filled with a blurred, enlarged copy of the art, so squircle or square masks that
show a little more never reveal a hard edge.

Run from meta/tools:  python3 icon_from_art.py
Writes app/src/main/res/mipmap-*/ic_launcher_foreground.png and meta/icon-preview.png.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "meta" / "oc.png"

# Fine-tuning: nudge which point of the art sits in the middle (fractions of width/height),
# and zoom (>1 zooms in further).
FOCUS = (0.5, 0.5)
ZOOM = 1.0

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def visible_square(art: Image.Image) -> Image.Image:
    """Square crop of the art around FOCUS: this is what shows inside the launcher mask."""
    side = min(art.width, art.height) / ZOOM
    cx, cy = art.width * FOCUS[0], art.height * FOCUS[1]
    left = min(max(cx - side / 2, 0), art.width - side)
    top = min(max(cy - side / 2, 0), art.height - side)
    return art.crop((round(left), round(top), round(left + side), round(top + side)))


def layer(size: int) -> Image.Image:
    art = Image.open(ART).convert("RGBA")
    vis = visible_square(art)
    inner = round(size * 72 / 108)
    # Margin: the same art, stretched to the full layer and blurred.
    bg = vis.resize((size, size), Image.LANCZOS).filter(ImageFilter.GaussianBlur(size / 24))
    bg.alpha_composite(vis.resize((inner, inner), Image.LANCZOS), ((size - inner) // 2, (size - inner) // 2))
    return bg


def main():
    for name, px in DENSITIES.items():
        d = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        layer(px).save(d / "ic_launcher_foreground.png", optimize=True)

    # Preview: circle- and squircle-ish masks at launcher sizes.
    big = layer(432)
    vis = big.crop((72, 72, 360, 360))
    prev = Image.new("RGBA", (288 * 2 + 96 + 48 + 100, 308), (58, 63, 71, 255))
    x = 10
    for s, radius in ((288, None), (288, 72), (96, None), (48, None)):
        icon = vis.resize((s, s), Image.LANCZOS)
        m = Image.new("L", (s, s), 0)
        if radius is None:
            ImageDraw.Draw(m).ellipse((0, 0, s, s), fill=255)
        else:
            ImageDraw.Draw(m).rounded_rectangle((0, 0, s, s), radius=radius, fill=255)
        prev.paste(icon, (x, (308 - s) // 2), m)
        x += s + 20
    prev.save(ROOT / "meta" / "icon-preview.png")


if __name__ == "__main__":
    main()
