"""ProtoBooru mascot: a mochi cat with a booru-tag earring.

Run from meta/tools:  python3 mascot.py   -> writes ic_launcher_foreground.xml (copy to app/src/main/res/drawable/)
and icon.svg (a preview of the launcher icon).
"""
OUT = "#2A1F2E"   # ink outline
CREAM = "#FFF4E6"
PINK = "#F7A8B8"
BLUSH = "#FF8FA8"
AMBER = "#7DD3C0"
EYE = "#2A1F2E"
IRIS = "#C77DFF"

def _n(v):
    return f"{round(v, 2):g}"

def ell(cx, cy, rx, ry):
    a, b = _n(cx - rx), _n(cx + rx)
    return f"M{a},{_n(cy)} A{_n(rx)},{_n(ry)} 0 1,0 {b},{_n(cy)} A{_n(rx)},{_n(ry)} 0 1,0 {a},{_n(cy)} Z"

shapes = [
    # (path, fill, stroke, strokeWidth, fillAlpha)
    # ears
    ("M29,47 L31.5,23.5 Q32.5,18.5 37,21.5 L51,33 Z", CREAM, OUT, 2.4, 1),
    ("M79,47 L76.5,23.5 Q75.5,18.5 71,21.5 L57,33 Z", CREAM, OUT, 2.4, 1),
    ("M33.5,40 L35,27.5 Q35.6,25.2 37.6,26.6 L45,33 Z", PINK, None, 0, 1),
    ("M74.5,40 L73,27.5 Q72.4,25.2 70.4,26.6 L63,33 Z", PINK, None, 0, 1),
    # head (mochi)
    (ell(54, 58, 27.5, 23), CREAM, OUT, 2.4, 1),
    # ahoge curl
    ("M53,35.5 Q50,27 56,25.5 Q61,24.5 59,29", None, OUT, 2.2, 1),
    # eyes
    (ell(43.5, 57.5, 5.6, 7.2), EYE, None, 0, 1),
    (ell(64.5, 57.5, 5.6, 7.2), EYE, None, 0, 1),
    (ell(43.5, 60.6, 3.9, 3.6), IRIS, None, 0, 1),
    (ell(64.5, 60.6, 3.9, 3.6), IRIS, None, 0, 1),
    (ell(41.6, 54.6, 2.2, 2.4), "#FFFFFF", None, 0, 1),
    (ell(62.6, 54.6, 2.2, 2.4), "#FFFFFF", None, 0, 1),
    (ell(45.6, 61.8, 1.0, 1.0), "#FFFFFF", None, 0, 1),
    (ell(66.6, 61.8, 1.0, 1.0), "#FFFFFF", None, 0, 1),
    # blush
    (ell(35.2, 67.2, 4.6, 2.4), BLUSH, None, 0, 0.55),
    (ell(72.8, 67.2, 4.6, 2.4), BLUSH, None, 0, 0.55),
    # mouth  ω
    ("M49.8,66.4 Q51.9,69.6 54,66.6 Q56.1,69.6 58.2,66.4", None, OUT, 1.8, 1),
    # tag charm hanging from right ear
    ("M77.5,30 L82,37.5", None, OUT, 1.6, 1),
    ("M79.6,36.4 L86.6,36.4 L90.6,41.5 L86.6,46.6 L79.6,46.6 Q77.6,46.6 77.6,44.6 L77.6,38.4 Q77.6,36.4 79.6,36.4 Z", AMBER, OUT, 1.8, 1),
    (ell(81.6, 41.5, 1.3, 1.3), OUT, None, 0, 1),
]

def svg_group(scale=1.0, tx=0, ty=0):
    parts = []
    for d, fill, stroke, sw, a in shapes:
        attrs = [f'd="{d}"', f'fill="{fill or "none"}"']
        if a != 1: attrs.append(f'fill-opacity="{a}"')
        if stroke: attrs += [f'stroke="{stroke}"', f'stroke-width="{sw}"', 'stroke-linecap="round"', 'stroke-linejoin="round"']
        parts.append(f'<path {" ".join(attrs)}/>')
    return f'<g transform="translate({tx},{ty}) scale({scale})">' + "".join(parts) + "</g>"

HALO = "#F2A93B"
ICON_SCALE = 0.8

def vector_xml():
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- ProtoBooru mascot: a mochi cat with a booru tag earring. Generated from mascot.py. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">',
           f'    <path android:pathData="{ell(54, 54, 33, 33)}" android:fillColor="{HALO}" />',
           f'    <group android:pivotX="54" android:pivotY="56" android:scaleX="{ICON_SCALE}" android:scaleY="{ICON_SCALE}" android:translateY="-1">']
    for d, fill, stroke, sw, a in shapes:
        attrs = [f'android:pathData="{d}"']
        attrs.append(f'android:fillColor="{fill or "#00000000"}"')
        if a != 1: attrs.append(f'android:fillAlpha="{a}"')
        if stroke:
            attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{sw}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        out.append('        <path ' + ' '.join(attrs) + ' />')
    out.append('    </group>')
    out.append('</vector>')
    return "\n".join(out) + "\n"

if __name__ == "__main__":
    import sys
    bg = "#0E1013"
    # Preview what a launcher shows: the inner 72dp of the 108dp layers, circle-masked.
    inner = f'<rect width="108" height="108" fill="{bg}"/><circle cx="54" cy="54" r="33" fill="{HALO}"/><g transform="translate(54,55) scale({ICON_SCALE}) translate(-54,-56)">{svg_group()}</g>'
    icon = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="432" height="432"><defs><clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath></defs><g clip-path="url(#c)">{inner}</g></svg>'
    open("icon.svg", "w").write(icon)
    open("ic_launcher_foreground.xml", "w").write(vector_xml())
