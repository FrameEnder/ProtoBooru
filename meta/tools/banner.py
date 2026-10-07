"""Animated README banner for ProtoBooru: tag search narrowing a wall of posts.

The story, on a 10 second loop:
  1. A wall of 32 thumbnails: landscapes, characters and abstract pieces, warm and cool,
     each with a safety dot (green = safe, yellow = sketchy).
  2. Tags go into the search bar one at a time, each offered first by autocomplete:
       scenery   -> everything that isn't a landscape fades out
       sunset    -> the cool-toned landscapes fade out
       -sketchy  -> the sketchy ones fade out
     The result count drops with each tag: 12,408 -> 1,932 -> 214 -> 38.
  3. The six posts left reflow into a bigger grid, one gets favorited, and the mascot pops in.
  4. Everything resets and it runs again.

Run from meta/tools:  python3 banner.py ../main.svg
"""
import base64
import io
import random
import sys

from icon_from_art import layer

W, H = 720, 240
T = 10.0          # loop length in seconds
LOOP_DELAY = 1.3  # the loop starts once the intro has played

BG1, BG2 = "#12161c", "#0e1116"
TXT, AMB, GRN, RED = "#e8e2d4", "#f0a640", "#86c17a", "#e0705f"
PANEL, LINE, MONO, DIM = "#171c23", "#2a313c", "#8e97a4", "#252c36"
BLUE, YEL, VIO, PINK = "#6fa8f0", "#e6c15a", "#b394e8", "#f48fb1"

WARM = [("#f0a640", "#b85c8a"), ("#e0705f", "#f0c060"), ("#f48fb1", "#f0a640"),
        ("#e6c15a", "#e0705f"), ("#d9708f", "#f2b45a"), ("#f2915a", "#8a4fb0")]
COOL = [("#6fa8f0", "#86c17a"), ("#4f7fd0", "#b394e8"), ("#5fb8b0", "#3f5f9f"),
        ("#86c17a", "#4f9fa0"), ("#7f8fe0", "#5fb8b0"), ("#9fb0c8", "#4f6f9f")]


def pct(t: float) -> str:
    return f"{max(0.0, min(100.0, t / T * 100)):.2f}%"


# --------------------------------------------------------------------- tiles
GX, GY, TW, TH, GAP_X, GAP_Y, COLS, ROWS = 316, 34, 42, 38, 6, 6, 8, 4

# Who leaves at which tag. Survivors are warm, safe landscapes.
kinds = (["survive"] * 6 + ["sketchy"] * 4 + ["cool"] * 8 + ["other"] * 14)
rng = random.Random(11)
while True:
    rng.shuffle(kinds)
    # keep the survivors spread out: no two in the same column
    cols = [i % COLS for i, k in enumerate(kinds) if k == "survive"]
    if len(set(cols)) == len(cols):
        break

STAGES = {"other": 1, "cool": 2, "sketchy": 3}
# When each tag lands (seconds into the loop) and when its tiles fade.
TAG_AT = [1.2, 2.8, 4.4]
REFLOW_AT, REFLOW_DUR = 5.4, 0.75
RESET_AT, RESET_DUR = 8.6, 0.8

defs = []
for i, (a, b) in enumerate(WARM):
    defs.append(f'<linearGradient id="w{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>')
for i, (a, b) in enumerate(COOL):
    defs.append(f'<linearGradient id="c{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>')


def glyph(kind: str, x: float, y: float, w: float, h: float) -> str:
    if kind == "landscape":
        return (f'<circle cx="{x + w * .72:.1f}" cy="{y + h * .3:.1f}" r="{h * .12:.1f}" fill="#fff" fill-opacity=".8"/>'
                f'<path d="M{x},{y + h} L{x + w * .36:.1f},{y + h * .5:.1f} L{x + w * .6:.1f},{y + h * .78:.1f} '
                f'L{x + w * .78:.1f},{y + h * .6:.1f} L{x + w},{y + h} Z" fill="#0e1116" fill-opacity=".38"/>')
    if kind == "character":
        cx = x + w / 2
        return (f'<circle cx="{cx:.1f}" cy="{y + h * .4:.1f}" r="{h * .17:.1f}" fill="#fff" fill-opacity=".78"/>'
                f'<path d="M{cx - w * .26:.1f},{y + h} Q{cx - w * .24:.1f},{y + h * .62:.1f} {cx:.1f},{y + h * .62:.1f} '
                f'Q{cx + w * .24:.1f},{y + h * .62:.1f} {cx + w * .26:.1f},{y + h} Z" fill="#fff" fill-opacity=".6"/>')
    # abstract
    return (f'<circle cx="{x + w * .35:.1f}" cy="{y + h * .45:.1f}" r="{h * .22:.1f}" fill="#fff" fill-opacity=".35"/>'
            f'<rect x="{x + w * .5:.1f}" y="{y + h * .3:.1f}" width="{w * .3:.1f}" height="{h * .45:.1f}" rx="2" fill="#0e1116" fill-opacity=".3"/>')


tiles, css = [], []
survivor_slots = []
sv = 0
for i, kind in enumerate(kinds):
    c, r = i % COLS, i // COLS
    x, y = GX + c * (TW + GAP_X), GY + r * (TH + GAP_Y)
    if kind == "survive":
        art, grad, safety = "landscape", f"w{sv % len(WARM)}", GRN
    elif kind == "sketchy":
        art, grad, safety = "landscape", f"w{(i * 5) % len(WARM)}", YEL
    elif kind == "cool":
        art, grad, safety = "landscape", f"c{(i * 7) % len(COOL)}", rng.choice([GRN, GRN, YEL])
    else:
        art = rng.choice(["character", "character", "abstract"])
        grad = rng.choice([f"w{rng.randrange(len(WARM))}", f"c{rng.randrange(len(COOL))}"])
        safety = rng.choice([GRN, GRN, YEL])
    video = kind == "other" and rng.random() < .3
    body = (f'<rect x="{x}" y="{y}" width="{TW}" height="{TH}" rx="5" fill="url(#{grad})"/>'
            + glyph(art, x, y, TW, TH)
            + f'<circle cx="{x + TW - 5}" cy="{y + 5}" r="2.4" fill="{safety}" stroke="#0e1116" stroke-width=".8"/>')
    if video:
        body += f'<path d="M{x + 5},{y + 4} l6,3.5 l-6,3.5 z" fill="#fff" fill-opacity=".9"/>'

    if kind == "survive":
        cls = f"s{sv}"
        survivor_slots.append((cls, x, y))
        sv += 1
    else:
        cls = f"e{STAGES[kind]}"
    tiles.append(f'<g class="pop" style="animation-delay:{.25 + i * .025:.3f}s;transform-origin:{x + TW / 2}px {y + TH / 2}px">'
                 f'<g class="tile {cls}" style="transform-origin:{x}px {y}px">{body}</g></g>')

# Eliminated tiles: dim when their tag lands, vanish at the reflow, return at the reset.
for stage in (1, 2, 3):
    t = TAG_AT[stage - 1] + .2
    css.append(
        f"@keyframes e{stage}{{0%,{pct(t)}{{opacity:1;transform:none}}"
        f"{pct(t + .45)}{{opacity:.13;transform:scale(.88)}}"
        f"{pct(REFLOW_AT)}{{opacity:.13;transform:scale(.88)}}"
        f"{pct(REFLOW_AT + .4)}{{opacity:0;transform:scale(.6)}}"
        f"{pct(RESET_AT)}{{opacity:0;transform:scale(.6)}}"
        f"{pct(RESET_AT + RESET_DUR)},100%{{opacity:1;transform:none}}}}"
        f".e{stage}{{animation:e{stage} {T}s ease-in-out {LOOP_DELAY}s infinite both}}")

# Survivors: reflow into a 3x2 grid of bigger tiles.
FS = 2.1
FW, FH = TW * FS, TH * FS
FGAP = 8
fx0 = (GX + (GX + COLS * (TW + GAP_X) - GAP_X)) / 2 - (3 * FW + 2 * FGAP) / 2
fy0 = (GY + (GY + ROWS * (TH + GAP_Y) - GAP_Y)) / 2 - (2 * FH + FGAP) / 2
final_pos = []
for k, (cls, x, y) in enumerate(survivor_slots):
    tx, ty = fx0 + (k % 3) * (FW + FGAP), fy0 + (k // 3) * (FH + FGAP)
    final_pos.append((tx, ty))
    moved = f"transform:translate({tx - x:.1f}px,{ty - y:.1f}px) scale({FS})"
    css.append(
        f"@keyframes {cls}{{0%,{pct(REFLOW_AT + .15)}{{transform:none}}"
        f"{pct(REFLOW_AT + .15 + REFLOW_DUR)}{{{moved}}}"
        f"{pct(RESET_AT)}{{{moved}}}"
        f"{pct(RESET_AT + RESET_DUR)},100%{{transform:none}}}}"
        f".{cls}{{animation:{cls} {T}s cubic-bezier(.5,0,.25,1) {LOOP_DELAY}s infinite both}}")

# The favorited post: amber outline and a heart on the top-middle survivor.
FAV_AT = REFLOW_AT + REFLOW_DUR + .5
hx, hy = final_pos[1]
fav = (f'<g class="fav">'
       f'<rect x="{hx - 2:.1f}" y="{hy - 2:.1f}" width="{FW + 4:.1f}" height="{FH + 4:.1f}" rx="9" fill="none" stroke="{AMB}" stroke-width="2.2"/>'
       f'<g class="heart" style="transform-origin:{hx + FW - 13:.1f}px {hy + FH - 13:.1f}px">'
       f'<circle cx="{hx + FW - 13:.1f}" cy="{hy + FH - 13:.1f}" r="10" fill="{BG2}" fill-opacity=".8"/>'
       f'<path transform="translate({hx + FW - 13:.1f},{hy + FH - 13:.1f}) scale(1.15)" '
       f'd="M0,4.2 C-6.5,-0.4 -5,-5.2 -2,-5.1 C-0.9,-5.1 -0.3,-4.4 0,-3.7 C0.3,-4.4 0.9,-5.1 2,-5.1 C5,-5.2 6.5,-0.4 0,4.2 Z" fill="{AMB}"/>'
       f'</g></g>')
css.append(
    f"@keyframes fav{{0%,{pct(FAV_AT)}{{opacity:0}}{pct(FAV_AT + .25)}{{opacity:1}}"
    f"{pct(RESET_AT)}{{opacity:1}}{pct(RESET_AT + .3)},100%{{opacity:0}}}}"
    f".fav{{animation:fav {T}s ease {LOOP_DELAY}s infinite both}}"
    f"@keyframes heart{{0%,{pct(FAV_AT)}{{transform:scale(0)}}{pct(FAV_AT + .3)}{{transform:scale(1.4)}}"
    f"{pct(FAV_AT + .55)},100%{{transform:scale(1)}}}}"
    f".heart{{animation:heart {T}s cubic-bezier(.34,1.8,.64,1) {LOOP_DELAY}s infinite both}}")

# --------------------------------------------------------------------- search bar
SX, SY, SW, SH = 30, 112, 256, 34
TAGS = [("scenery", GRN, "1.9k"), ("sunset", PINK, "1.2k"), ("-sketchy", RED, "exclude")]
CHAR_W = 6.3
chips, drops, caret_stops = [], [], []
cx = SX + 32
for k, (label, col, hint) in enumerate(TAGS):
    w = len(label) * CHAR_W + 14
    t = TAG_AT[k]
    chips.append(
        f'<g class="chip{k}" style="transform-origin:{cx + w / 2:.1f}px {SY + SH / 2}px">'
        f'<rect x="{cx:.1f}" y="{SY + 8}" width="{w:.1f}" height="18" rx="5" fill="{col}" fill-opacity=".15" stroke="{col}" stroke-opacity=".6"/>'
        f'<text x="{cx + 7:.1f}" y="{SY + 21}" class="chipt" fill="{col}">{label}</text></g>')
    css.append(
        f"@keyframes chip{k}{{0%,{pct(t)}{{opacity:0;transform:scale(.5)}}{pct(t + .25)}{{opacity:1;transform:scale(1.08)}}"
        f"{pct(t + .4)}{{opacity:1;transform:none}}{pct(RESET_AT)}{{opacity:1;transform:none}}"
        f"{pct(RESET_AT + .4)},100%{{opacity:0;transform:none}}}}"
        f".chip{k}{{animation:chip{k} {T}s cubic-bezier(.34,1.56,.64,1) {LOOP_DELAY}s infinite both}}")
    # Autocomplete: the suggestion shows for a moment before the chip lands.
    dy = SY + SH + 4
    typed = label.lstrip("-")[:3]
    drops.append(
        f'<g class="drop{k}">'
        f'<rect x="{SX}" y="{dy}" width="{SW}" height="26" rx="7" fill="{PANEL}" stroke="{LINE}"/>'
        f'<rect x="{SX + 4}" y="{dy + 4}" width="{SW - 8}" height="18" rx="5" fill="{col}" fill-opacity=".1"/>'
        f'<circle cx="{SX + 14}" cy="{dy + 13}" r="3" fill="{col}"/>'
        f'<text x="{SX + 24}" y="{dy + 17}" class="sugg"><tspan fill="{TXT}" font-weight="700">{typed}</tspan>'
        f'<tspan fill="{MONO}">{label.lstrip("-")[3:]}</tspan></text>'
        f'<text x="{SX + SW - 12}" y="{dy + 17}" class="sugg" text-anchor="end" fill="{MONO}">{hint}</text></g>')
    css.append(
        f"@keyframes drop{k}{{0%,{pct(t - .55)}{{opacity:0;transform:translateY(-4px)}}"
        f"{pct(t - .4)}{{opacity:1;transform:none}}{pct(t)}{{opacity:1;transform:none}}"
        f"{pct(t + .15)},100%{{opacity:0;transform:none}}}}"
        f".drop{k}{{animation:drop{k} {T}s ease {LOOP_DELAY}s infinite both}}")
    cx += w + 6
    caret_stops.append((t + .2, cx - SX - 32))

# Caret hops along after each chip; placeholder text clears on the first tag.
caret_kf = ["0%{transform:none}"]
prev = 0.0
for t, dx in caret_stops:
    caret_kf.append(f"{pct(t - .01)}{{transform:translateX({prev:.1f}px)}}")
    caret_kf.append(f"{pct(t)}{{transform:translateX({dx:.1f}px)}}")
    prev = dx
caret_kf.append(f"{pct(RESET_AT)}{{transform:translateX({caret_stops[-1][1]:.1f}px)}}")
caret_kf.append(f"{pct(RESET_AT + .4)},100%{{transform:none}}")
css.append("@keyframes caret{" + "".join(caret_kf) + "}"
           f".caret{{animation:caret {T}s steps(1,end) {LOOP_DELAY}s infinite both}}"
           "@keyframes blink{0%,49%{opacity:1}50%,100%{opacity:0}}.blink{animation:blink 1s step-end infinite}"
           f"@keyframes ph{{0%,{pct(TAG_AT[0] - .5)}{{opacity:1}}{pct(TAG_AT[0] - .45)}{{opacity:0}}"
           f"{pct(RESET_AT + .3)}{{opacity:0}}{pct(RESET_AT + .6)},100%{{opacity:1}}}}"
           f".ph{{animation:ph {T}s ease {LOOP_DELAY}s infinite both}}")

search = (f'<g class="rise">'
          f'<rect x="{SX}" y="{SY}" width="{SW}" height="{SH}" rx="10" fill="{PANEL}" stroke="{LINE}" stroke-width="1.5"/>'
          f'<circle cx="{SX + 15}" cy="{SY + 15.5}" r="5.2" fill="none" stroke="{MONO}" stroke-width="1.8"/>'
          f'<path d="M{SX + 19} {SY + 19.5} l4 4" stroke="{MONO}" stroke-width="1.8" stroke-linecap="round"/>'
          f'<text x="{SX + 32}" y="{SY + 21.5}" class="ph sugg" fill="{MONO}">Search tags…</text>'
          + "".join(chips)
          + f'<g class="caret"><rect class="blink" x="{SX + 32}" y="{SY + 9}" width="1.6" height="16" fill="{AMB}"/></g>'
          + '</g>')

# Result count.
COUNTS = ["12,408", "1,932", "214", "38"]
count_times = [0.0] + [t + .2 for t in TAG_AT]
counts = []
for k, n in enumerate(COUNTS):
    start = count_times[k]
    end = count_times[k + 1] if k + 1 < len(count_times) else RESET_AT + .4
    counts.append(f'<text x="{SX}" y="204" class="count n{k}" fill="{TXT}">{n}</text>')
    if k == 0:
        kf = (f"0%,{pct(end - .05)}{{opacity:1}}{pct(end)}{{opacity:0}}"
              f"{pct(RESET_AT + .4)}{{opacity:0}}{pct(RESET_AT + .45)},100%{{opacity:1}}")
    else:
        kf = (f"0%,{pct(start - .05)}{{opacity:0;transform:translateY(6px)}}{pct(start + .15)}{{opacity:1;transform:none}}"
              f"{pct(end - .05)}{{opacity:1;transform:none}}{pct(end)},100%{{opacity:0;transform:none}}")
    css.append(f"@keyframes n{k}{{{kf}}}.n{k}{{animation:n{k} {T}s ease {LOOP_DELAY}s infinite both}}")
count_block = (f'<g class="rise">{"".join(counts)}'
               f'<text x="{SX}" y="221" class="mono">posts match</text></g>')

# --------------------------------------------------------------------- mascot
_buf = io.BytesIO()
layer(432).crop((72, 72, 360, 360)).resize((140, 140)).save(_buf, "PNG", optimize=True)
_head = base64.b64encode(_buf.getvalue()).decode()
MX, MY, MR = 262, 192, 24
MASCOT_AT = FAV_AT + .35
mascot = (f'<g class="peek" style="transform-origin:{MX}px {MY}px">'
          f'<clipPath id="headclip"><circle cx="{MX}" cy="{MY}" r="{MR}"/></clipPath>'
          f'<circle cx="{MX}" cy="{MY}" r="{MR + 2.5}" fill="{AMB}"/>'
          f'<image href="data:image/png;base64,{_head}" x="{MX - MR}" y="{MY - MR}" width="{MR * 2}" height="{MR * 2}" clip-path="url(#headclip)"/></g>')
css.append(
    f"@keyframes peek{{0%,{pct(MASCOT_AT)}{{opacity:0;transform:scale(.3) rotate(-14deg)}}"
    f"{pct(MASCOT_AT + .4)}{{opacity:1;transform:none}}{pct(RESET_AT)}{{opacity:1;transform:none}}"
    f"{pct(RESET_AT + .35)},100%{{opacity:0;transform:scale(.6)}}}}"
    f".peek{{animation:peek {T}s cubic-bezier(.34,1.7,.64,1) {LOOP_DELAY}s infinite both}}")

# --------------------------------------------------------------------- title
title = "".join(
    f'<tspan class="ltr" style="animation-delay:{.05 + i * .045:.3f}s" fill="{TXT if i < 5 else AMB}">{ch}</tspan>'
    for i, ch in enumerate("ProtoBooru"))
# A little tag label hangs off the title, like the ones on every post.
tag_icon = (f'<g class="rise"><path d="M32,48 h15 l9,9 l-9,9 h-15 a3,3 0 0 1 -3,-3 v-12 a3,3 0 0 1 3,-3 z" '
            f'fill="{AMB}" fill-opacity=".16" stroke="{AMB}" stroke-width="1.8" stroke-linejoin="round"/>'
            f'<circle cx="37" cy="57" r="2.2" fill="{AMB}"/></g>')

style = f"""
  text{{font-family:'Segoe UI',system-ui,-apple-system,sans-serif}}
  .mono{{font-family:'JetBrains Mono','Cascadia Mono',ui-monospace,Menlo,monospace;font-size:11px;fill:{MONO}}}
  .ttl{{font-size:40px;font-weight:800}}
  .sub{{font-family:'JetBrains Mono',ui-monospace,Menlo,monospace;font-size:10px;letter-spacing:2px;fill:{MONO}}}
  .chipt{{font-family:'JetBrains Mono',ui-monospace,Menlo,monospace;font-size:10.5px;font-weight:600}}
  .sugg{{font-family:'JetBrains Mono',ui-monospace,Menlo,monospace;font-size:11px}}
  .count{{font-size:30px;font-weight:800}}
  .ltr{{animation:ltr .5s cubic-bezier(.34,1.56,.64,1) both}}
  @keyframes ltr{{from{{opacity:0}}to{{opacity:1}}}}
  .rise{{animation:rise .6s ease-out .55s both}}
  @keyframes rise{{from{{opacity:0;transform:translateY(10px)}}to{{opacity:1;transform:none}}}}
  .pop{{animation:pop .45s cubic-bezier(.34,1.56,.64,1) both}}
  @keyframes pop{{0%{{opacity:0;transform:scale(.4)}}100%{{opacity:1;transform:none}}}}
  {"".join(css)}
"""

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">
  <defs>
    <linearGradient id="bgGrad" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{BG1}"/>
      <stop offset="1" stop-color="{BG2}"/>
    </linearGradient>
    <pattern id="dots" width="14" height="14" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r=".9" fill="#fff" fill-opacity=".045"/></pattern>
    {"".join(defs)}
    <style>{style}</style>
  </defs>
  <rect width="{W}" height="{H}" rx="18" fill="url(#bgGrad)"/>
  <rect width="{W}" height="{H}" rx="18" fill="url(#dots)"/>

  <!-- Name -->
  {tag_icon}
  <text class="ttl" x="64" y="71">{title}</text>
  <text class="sub rise" x="32" y="97">SZURUBOORU · ANDROID</text>

  <!-- The wall of posts, filtered tag by tag -->
  {"".join(tiles)}
  {fav}

  <!-- Search bar with autocomplete, and the result count -->
  {count_block}
  {search}
  {"".join(drops)}
  {mascot}
</svg>
'''
open(sys.argv[1], "w").write(svg)
print(f"wrote {sys.argv[1]} ({len(svg) // 1024} KB)")
