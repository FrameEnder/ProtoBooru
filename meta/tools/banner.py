"""Animated README banner for ProtoBooru, in the style of Proto-Sync's meta/main.svg.

Run from meta/tools:  python3 banner.py ../main.svg
"""
import sys
import base64, io
from icon_from_art import layer

BG1, BG2 = "#12161c", "#0e1116"
TXT, AMB, GRN = "#e8e2d4", "#f0a640", "#86c17a"
PANEL, LINE, MONO, DIM = "#171c23", "#2a313c", "#8e97a4", "#252c36"
BLUE, YEL, VIO, PINKC = "#6fa8f0", "#e6c15a", "#b394e8", "#f48fb1"

thumbs = [("#f0a640", "#b85c8a"), ("#6fa8f0", "#86c17a"), ("#b394e8", "#f48fb1"),
          ("#86c17a", "#e6c15a"), ("#f48fb1", "#6fa8f0"), ("#e6c15a", "#e0705f")]

defs = "".join(
    f'<linearGradient id="t{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>'
    for i, (a, b) in enumerate(thumbs))

style = f"""
  text{{font-family:'Segoe UI',system-ui,-apple-system,sans-serif}}
  .mono{{font-family:'JetBrains Mono','Cascadia Mono',ui-monospace,Menlo,monospace;font-size:11px;fill:{MONO}}}
  .ttl{{font-size:52px;font-weight:800}}
  .mode{{font-family:'JetBrains Mono',ui-monospace,Menlo,monospace;font-size:9.5px;letter-spacing:2px;fill:{AMB};text-anchor:middle;opacity:.85}}
  .chipt{{font-size:12.5px;font-weight:600;fill:{GRN};text-anchor:middle}}
  .tagt{{font-size:9px;font-weight:600;text-anchor:start}}

  /* the two halves of the name slide together */
  .tl{{animation:inL .75s cubic-bezier(.22,1.2,.36,1) .15s both}}
  .tr{{animation:inR .75s cubic-bezier(.22,1.2,.36,1) .15s both}}
  @keyframes inL{{from{{opacity:0;transform:translateX(-70px)}}to{{opacity:1;transform:none}}}}
  @keyframes inR{{from{{opacity:0;transform:translateX(70px)}}to{{opacity:1;transform:none}}}}
  .dash{{animation:snap .5s cubic-bezier(.34,1.8,.64,1) .8s both;transform-origin:360px 58px}}
  @keyframes snap{{0%{{opacity:0;transform:scale(0)}}100%{{opacity:1;transform:scale(1)}}}}

  /* panels rise in */
  .pl{{animation:rise .6s ease-out .75s both}} .pr{{animation:rise .6s ease-out .9s both}} .trk{{animation:fade .5s ease 1.2s both}}
  @keyframes rise{{from{{opacity:0;transform:translateY(14px)}}to{{opacity:1;transform:none}}}}
  @keyframes fade{{from{{opacity:0}}to{{opacity:1}}}}

  /* gallery thumbnails pop in */
  .th{{animation:pop .4s cubic-bezier(.34,1.56,.64,1) both}}
  @keyframes pop{{0%{{opacity:0;transform:scale(.6)}}100%{{opacity:1;transform:scale(1)}}}}

  /* pictures travel left → right */
  .pk{{opacity:0}}
  .p0{{animation:fly .9s cubic-bezier(.45,0,.3,1) 1.55s both}} .p1{{animation:fly .9s cubic-bezier(.45,0,.3,1) 1.9s both}} .p2{{animation:fly .9s cubic-bezier(.45,0,.3,1) 2.25s both}}
  @keyframes fly{{0%{{opacity:0;transform:translateX(0)}}12%{{opacity:1}}85%{{opacity:1}}100%{{opacity:0;transform:translateX(129px)}}}}

  /* the post appears, then gets tagged chip by chip */
  .img{{animation:pop .5s cubic-bezier(.34,1.56,.64,1) 2.45s both;transform-origin:502px 171px}}
  .flash{{animation:flash 1s ease 2.45s both}}
  @keyframes flash{{0%{{opacity:.85}}100%{{opacity:0}}}}
  .g0{{animation:tag .45s cubic-bezier(.34,1.7,.64,1) 2.8s both}} .g1{{animation:tag .45s cubic-bezier(.34,1.7,.64,1) 3.05s both}}
  .g2{{animation:tag .45s cubic-bezier(.34,1.7,.64,1) 3.3s both}} .g3{{animation:tag .45s cubic-bezier(.34,1.7,.64,1) 3.55s both}}
  .g0,.g1,.g2,.g3{{transform-box:fill-box;transform-origin:0 50%}}
  @keyframes tag{{0%{{opacity:0;transform:translateX(-12px) scale(.7)}}100%{{opacity:1;transform:none}}}}

  /* favorited: heart pops, header dot goes green */
  .heart{{animation:beat .6s cubic-bezier(.34,1.8,.64,1) 3.95s both;transform-origin:531px 152px}}
  @keyframes beat{{0%{{opacity:0;transform:scale(0)}}60%{{opacity:1;transform:scale(1.35)}}100%{{opacity:1;transform:scale(1)}}}}
  .okdot{{animation:fade .4s ease 4.15s both}}
  .chip{{animation:snap2 .55s cubic-bezier(.34,1.56,.64,1) 4.25s both;transform-origin:360px 198px}}
  @keyframes snap2{{0%{{opacity:0;transform:scale(.5)}}100%{{opacity:1;transform:scale(1)}}}}

  /* she pops in once the post is done */
  .peek{{animation:peek .6s cubic-bezier(.34,1.7,.64,1) 4.6s both;transform-origin:606px 70px}}
  @keyframes peek{{from{{opacity:0;transform:scale(.3) rotate(-12deg)}}to{{opacity:1;transform:none}}}}
"""

def picture(x, y, w, h, grad, rx=5):
    """A thumbnail with a little sun-and-hill glyph."""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="url(#{grad})"/>'
            f'<circle cx="{x + w * 0.72:.1f}" cy="{y + h * 0.32:.1f}" r="{min(w, h) * 0.12:.1f}" fill="#fff" fill-opacity=".75"/>'
            f'<path d="M{x + 2},{y + h - 2} L{x + w * 0.38:.1f},{y + h * 0.48:.1f} L{x + w * 0.62:.1f},{y + h * 0.78:.1f} L{x + w * 0.78:.1f},{y + h * 0.6:.1f} L{x + w - 2},{y + h - 2} Z" fill="#0e1116" fill-opacity=".35"/>')

# ---- left panel: gallery ----
grid = []
for i in range(6):
    c, r = i % 3, i // 3
    x, y = 74 + c * 65, 142 + r * 31
    grid.append(f'<g class="th" style="animation-delay:{1.0 + i * 0.08:.2f}s;transform-origin:{x + 28}px {y + 13}px">{picture(x, y, 56, 27, f"t{i}", 4)}</g>')
left = (f'<g class="pl"><rect x="62" y="104" width="210" height="104" rx="12" fill="{PANEL}" stroke="{LINE}" stroke-width="1.5"/>'
        f'<rect x="76" y="117" width="15" height="11" rx="2.5" fill="none" stroke="{MONO}" stroke-width="1.5"/><circle cx="86" cy="120.5" r="1.4" fill="{MONO}"/>'
        f'<path d="M77.5,126.5 L81.5,122.5 L84,125 L85.5,123.5 L89.5,126.5" fill="none" stroke="{MONO}" stroke-width="1.2" stroke-linejoin="round"/>'
        f'<text x="100" y="126.5" class="mono">Gallery</text><line x1="74" y1="136" x2="260" y2="136" stroke="{LINE}" stroke-width="1"/>'
        f'<circle cx="254" cy="122.5" r="4" fill="{GRN}"/>' + "".join(grid) + '</g>')

# ---- middle: upload track ----
packets = "".join(f'<g class="pk p{i}">{picture(282, 154, 18, 15, f"t{i * 2}", 3)}</g>' for i in range(3))
track = (f'<g class="trk"><line x1="282" y1="162" x2="432" y2="162" stroke="{LINE}" stroke-width="2" stroke-dasharray="3 5" stroke-linecap="round"/>'
         f'<path d="M428 157 L435 162 L428 167" fill="none" stroke="{AMB}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'
         f'<text x="360" y="146" class="mode">UPLOAD</text></g>')

# ---- right panel: the post being tagged ----
tags = [("artist: hollow_wren", BLUE, 92), ("character: mira", YEL, 80), ("scenery", GRN, 50), ("sunset", PINKC, 44)]
chips = []
for i, (label, col, w) in enumerate(tags):
    y = 142 + i * 15
    chips.append(f'<g class="g{i}"><rect x="552" y="{y}" width="{w}" height="12" rx="3.5" fill="{col}" fill-opacity=".14" stroke="{col}" stroke-opacity=".5" stroke-width="1"/>'
                 f'<text x="557" y="{y + 8.8}" class="tagt" fill="{col}">{label}</text></g>')
heart = (f'<g class="heart"><circle cx="531" cy="152" r="8" fill="{BG2}" fill-opacity=".75"/>'
         f'<path d="M531,156.2 C524.5,151.6 526,146.8 529,146.9 C530.1,146.9 530.7,147.6 531,148.3 C531.3,147.6 531.9,146.9 533,146.9 C536,146.8 537.5,151.6 531,156.2 Z" fill="{AMB}"/></g>')
right = (f'<g class="pr"><rect x="448" y="104" width="210" height="104" rx="12" fill="{PANEL}" stroke="{LINE}" stroke-width="1.5"/>'
         f'<path d="M463,117.5 h7 l5,5 l-5,5 h-7 a1.5,1.5 0 0 1 -1.5,-1.5 v-7 a1.5,1.5 0 0 1 1.5,-1.5 z" fill="none" stroke="{MONO}" stroke-width="1.4" stroke-linejoin="round"/>'
         f'<circle cx="465.5" cy="122.5" r="1.2" fill="{MONO}"/>'
         f'<text x="482" y="126.5" class="mono">Post #4821</text><line x1="460" y1="136" x2="646" y2="136" stroke="{LINE}" stroke-width="1"/>'
         f'<circle cx="640" cy="122.5" r="4" fill="{AMB}"/><circle class="okdot" cx="640" cy="122.5" r="4" fill="{GRN}"/>'
         f'<rect x="462" y="142" width="80" height="58" rx="7" fill="{DIM}"/>'
         f'<g class="img">{picture(462, 142, 80, 58, "t0", 7)}<rect class="flash" x="462" y="142" width="80" height="58" rx="7" fill="{GRN}"/></g>'
         + "".join(chips) + heart + '</g>')

chip = (f'<g class="chip"><rect x="302" y="186" width="116" height="24" rx="12" fill="{GRN}" fill-opacity="0.13" stroke="{GRN}" stroke-width="1.3"/>'
        f'<path d="M317 198 l3.5 3.5 l6.5 -7" fill="none" stroke="{GRN}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>'
        f'<text x="370" y="202.5" class="chipt">on the booru</text></g>')

# The OC (cropped from meta/oc.png, same framing as the app icon) pops in as a round badge
# above the post panel once the post is done.
_buf = io.BytesIO()
layer(432).crop((72, 72, 360, 360)).resize((160, 160)).save(_buf, "PNG", optimize=True)
_head = base64.b64encode(_buf.getvalue()).decode()
mascot = (f'<g class="peek"><clipPath id="headclip"><circle cx="606" cy="70" r="31"/></clipPath>'
          f'<circle cx="606" cy="70" r="33.5" fill="{AMB}"/>'
          f'<image href="data:image/png;base64,{_head}" x="575" y="39" width="62" height="62" clip-path="url(#headclip)"/></g>')

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 240" width="720" height="240">
  <defs>
    <linearGradient id="bgGrad" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{BG1}"/>
      <stop offset="1" stop-color="{BG2}"/>
    </linearGradient>
    {defs}
    <clipPath id="frame"><rect width="720" height="240" rx="18"/></clipPath>
    <style>{style}</style>
  </defs>
  <rect width="720" height="240" rx="18" fill="url(#bgGrad)"/>

  <!-- Name: "Proto" and "Booru" converge -->
  <text class="ttl tl" x="346" y="76" text-anchor="end" fill="{TXT}">Proto</text>
  <rect class="dash" x="351" y="54" width="18" height="7" rx="3.5" fill="{AMB}"/>
  <text class="ttl tr" x="374" y="76" text-anchor="start" fill="{AMB}">Booru</text>

  <!-- Gallery, upload track, and the post getting tagged -->
  {left}
  {right}
  {track}
  {packets}
  {chip}
  {mascot}
</svg>
'''
open(sys.argv[1], "w").write(svg)
