#!/usr/bin/env python3
"""Generate the adaptive launcher icon layers from art/pbill-android.svg.

art/pbill-android.svg is the final icon on the 108 x 108 adaptive icon canvas
(1 user unit = 1 dp). Its layers, found by id, become vector drawables:

- background -> ic_launcher_background.xml
- foreground -> ic_launcher_foreground.xml
- monochrome (inside foreground) -> ic_launcher_monochrome.xml: its shapes
  merged into one path filled with the even-odd rule, so that a shape lying
  over another one cuts it out (Android 13+ themed icons use only the alpha)

Supported: <path>, <circle>, <ellipse> and <rect> (rounded or not), flat fill
colours and linear gradients, and translate/scale/rotate/matrix transforms
(flattened into the path coordinates). Strokes, clips, masks, filters and
unevenly scaled arcs are rejected: in Inkscape, use Path > Stroke to Path or
Object to Path first. Hidden elements (display:none) are ignored.

Usage: tools/make_icon.py [--preview DIR]  (run from the project root)
"""

import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SOURCE = Path("art/pbill-android.svg")
OUT = Path("app/src/main/res/drawable")
CANVAS = 108  # adaptive icon layer size (dp)
XLINK_HREF = "{http://www.w3.org/1999/xlink}href"
SHAPES = {"path", "circle", "ellipse", "rect"}
IGNORED = {"title", "desc", "metadata", "defs", "namedview"}
NUMBER = r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?"

Matrix = tuple[float, float, float, float, float, float]  # SVG a b c d e f
IDENTITY: Matrix = (1, 0, 0, 1, 0, 0)


def fail(message: str):
    sys.exit(f"{SOURCE}: {message}")


def fmt(v: float) -> str:
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def local(el) -> str:
    return el.tag.rsplit("}", 1)[-1]


# ── Transforms ───────────────────────────────────────────────────────────────

def multiply(m: Matrix, n: Matrix) -> Matrix:
    a, b, c, d, e, f = m
    a2, b2, c2, d2, e2, f2 = n
    return (a * a2 + c * b2, b * a2 + d * b2, a * c2 + c * d2, b * c2 + d * d2,
            a * e2 + c * f2 + e, b * e2 + d * f2 + f)


def parse_transform(text: str | None) -> Matrix:
    m = IDENTITY
    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", text or ""):
        v = [float(x) for x in re.findall(NUMBER, args)]
        if name == "matrix" and len(v) == 6:
            t = tuple(v)
        elif name == "translate" and v:
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == "scale" and v:
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == "rotate" and len(v) in (1, 3):
            r = math.radians(v[0])
            t = (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0, 0)
            if len(v) == 3:
                t = multiply(multiply((1, 0, 0, 1, v[1], v[2]), t), (1, 0, 0, 1, -v[1], -v[2]))
        elif name in ("skewX", "skewY") and len(v) == 1:
            k = math.tan(math.radians(v[0]))
            t = (1, 0, k, 1, 0, 0) if name == "skewX" else (1, k, 0, 1, 0, 0)
        else:
            fail(f"unsupported transform: {name}({args})")
        m = multiply(m, t)
    return m


def apply(m: Matrix, x: float, y: float) -> tuple[float, float]:
    a, b, c, d, e, f = m
    return a * x + c * y + e, b * x + d * y + f


# ── Path data ────────────────────────────────────────────────────────────────

ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def tokenize(d: str) -> list:
    """Commands (str) and numbers (float); arc flags may be written without separators."""
    tokens, pos, cmd, index = [], 0, "", 0
    while pos < len(d):
        ch = d[pos]
        if ch in " \t\r\n,":
            pos += 1
        elif ch.isalpha():
            if ch.upper() not in ARGS:
                fail(f"bad path command: {ch}")
            tokens.append(ch)
            cmd, index = ch.upper(), 0
            pos += 1
        elif cmd == "A" and index % 7 in (3, 4) and ch in "01":
            tokens.append(float(ch))
            index += 1
            pos += 1
        else:
            m = re.compile(NUMBER).match(d, pos)
            if not m:
                fail(f"bad path data near: {d[pos:pos + 20]}")
            tokens.append(float(m.group(0)))
            index += 1
            pos = m.end()
    return tokens


def parse_path(d: str) -> list[tuple[str, list[float]]]:
    """Path data as absolute commands among M L C Q A Z (H, V, S and T expanded)."""
    tokens = tokenize(d)
    out: list[tuple[str, list[float]]] = []
    x = y = sx = sy = 0.0
    last = None  # (command type, last control point) for S and T
    i, cmd = 0, None
    while i < len(tokens):
        if isinstance(tokens[i], str):
            cmd = tokens[i]
            i += 1
            if cmd in "Zz":
                out.append(("Z", []))
                x, y, last = sx, sy, None
                continue
        elif cmd is None or cmd in "Zz":
            fail("numbers without a path command")
        up = cmd.upper()
        v = tokens[i:i + ARGS[up]]
        if len(v) < ARGS[up] or any(isinstance(t, str) for t in v):
            fail(f"truncated path command {cmd}")
        i += ARGS[up]
        ox, oy = (x, y) if cmd.islower() else (0.0, 0.0)
        if up == "M":
            x, y = sx, sy = v[0] + ox, v[1] + oy
            out.append(("M", [x, y]))
            cmd, last = ("l" if cmd == "m" else "L"), None
        elif up in ("L", "H", "V"):
            if up == "H":
                x = v[0] + ox
            elif up == "V":
                y = v[0] + oy
            else:
                x, y = v[0] + ox, v[1] + oy
            out.append(("L", [x, y]))
            last = None
        elif up in ("C", "S"):
            if up == "C":
                c1, v = (v[0] + ox, v[1] + oy), v[2:]
            else:
                c1 = (2 * x - last[1][0], 2 * y - last[1][1]) if last and last[0] == "C" else (x, y)
            c2, end = (v[0] + ox, v[1] + oy), (v[2] + ox, v[3] + oy)
            out.append(("C", [*c1, *c2, *end]))
            last, (x, y) = ("C", c2), end
        elif up in ("Q", "T"):
            if up == "Q":
                c, v = (v[0] + ox, v[1] + oy), v[2:]
            else:
                c = (2 * x - last[1][0], 2 * y - last[1][1]) if last and last[0] == "Q" else (x, y)
            end = (v[0] + ox, v[1] + oy)
            out.append(("Q", [*c, *end]))
            last, (x, y) = ("Q", c), end
        else:  # A
            x, y = v[5] + ox, v[6] + oy
            out.append(("A", [*v[:5], x, y]))
            last = None
    return out


def transform_path(commands, m: Matrix) -> str:
    """Absolute path data in canvas coordinates."""
    a, b, c, d, _, _ = m
    scale = math.hypot(a, b)
    similar = abs(a * c + b * d) < 1e-9 and abs(scale - math.hypot(c, d)) < 1e-9 * max(scale, 1)
    flip = a * d - b * c < 0
    parts = []
    for op, v in commands:
        if op == "Z":
            parts.append("Z")
        elif op == "A":
            rx, ry, rot, large, sweep, x, y = v
            if not similar:
                fail("arcs cannot be scaled unevenly or skewed: use Path > Object to Path")
            px, py = apply(m, x, y)
            angle = (rot + math.degrees(math.atan2(b, a))) % 360
            sweep = 1 - int(sweep) if flip else int(sweep)
            parts.append(f"A{fmt(rx * scale)},{fmt(ry * scale)} {fmt(angle)} {int(large)} {sweep} "
                         f"{fmt(px)},{fmt(py)}")
        else:
            points = (apply(m, v[k], v[k + 1]) for k in range(0, len(v), 2))
            parts.append(op + " ".join(f"{fmt(px)},{fmt(py)}" for px, py in points))
    return " ".join(parts)


def length(el, name: str, default: float | None = None) -> float:
    value = el.get(name)
    if value is None or value == "auto":
        if default is None:
            fail(f"<{local(el)}> without {name}")
        return default
    m = re.match(NUMBER, value.strip())
    if not m or value.strip()[m.end():] not in ("", "px"):
        fail(f"unsupported length {name}={value!r}")
    return float(m.group(0))


def shape_path(el) -> list[tuple[str, list[float]]]:
    """Any supported shape as absolute path commands, in its own coordinates."""
    tag = local(el)
    if tag == "path":
        return parse_path(el.get("d", ""))
    if tag in ("circle", "ellipse"):
        cx, cy = length(el, "cx", 0), length(el, "cy", 0)
        rx = length(el, "r") if tag == "circle" else length(el, "rx")
        ry = rx if tag == "circle" else length(el, "ry")
        return [("M", [cx - rx, cy]), ("A", [rx, ry, 0, 1, 0, cx + rx, cy]),
                ("A", [rx, ry, 0, 1, 0, cx - rx, cy]), ("Z", [])]
    x, y = length(el, "x", 0), length(el, "y", 0)
    w, h = length(el, "width"), length(el, "height")
    rx = length(el, "rx", -1)
    ry = length(el, "ry", -1)
    rx, ry = (rx if rx >= 0 else max(ry, 0)), (ry if ry >= 0 else max(rx, 0))
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    if not rx or not ry:
        return [("M", [x, y]), ("L", [x + w, y]), ("L", [x + w, y + h]), ("L", [x, y + h]), ("Z", [])]
    arc = [rx, ry, 0, 0, 1]
    return [("M", [x + rx, y]), ("L", [x + w - rx, y]), ("A", [*arc, x + w, y + ry]),
            ("L", [x + w, y + h - ry]), ("A", [*arc, x + w - rx, y + h]), ("L", [x + rx, y + h]),
            ("A", [*arc, x, y + h - ry]), ("L", [x, y + ry]), ("A", [*arc, x + rx, y]), ("Z", [])]


def bounding_box(commands) -> tuple[float, float, float, float]:
    """Box of the end and control points: exact enough to place a bounding-box gradient."""
    xs, ys = [], []
    for op, v in commands:
        coords = v[5:] if op == "A" else v
        xs += coords[0::2]
        ys += coords[1::2]
    return min(xs), min(ys), max(xs), max(ys)


# ── Styles and gradients ─────────────────────────────────────────────────────

def style(el) -> dict:
    props = {k: el.get(k) for k in ("fill", "fill-rule", "fill-opacity", "opacity", "stroke",
                                     "display", "visibility", "clip-path", "mask", "filter",
                                     "stop-color", "stop-opacity")
             if el.get(k) is not None}
    for item in (el.get("style") or "").split(";"):
        if ":" in item:
            k, v = item.split(":", 1)
            props[k.strip()] = v.strip()
    return props


def android_color(value: str, opacity: float = 1.0) -> str:
    value = value.strip().lower()
    if re.fullmatch(r"#[0-9a-f]{3}", value):
        value = "#" + "".join(ch * 2 for ch in value[1:])
    m = re.fullmatch(r"rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)", value)
    if m:
        value = "#" + "".join(f"{int(ch):02x}" for ch in m.groups())
    if not re.fullmatch(r"#[0-9a-f]{6}", value):
        fail(f"unsupported colour: {value}")
    return f"#{round(255 * opacity):02X}{value[1:].upper()}"


def fraction(value: str, extent: float = 1) -> float:
    return float(value[:-1]) / 100 * extent if value.endswith("%") else float(value)


def linear_gradient(by_id: dict, ref: str, m: Matrix, bbox) -> dict:
    """Start, end (canvas coordinates) and stops of a linear gradient, following its href chain."""
    chain = []
    while ref:
        el = by_id.get(ref)
        if el is None or local(el) not in ("linearGradient", "radialGradient"):
            fail(f"unknown gradient: #{ref}")
        chain.append(el)
        href = el.get(XLINK_HREF) or el.get("href") or ""
        ref = href[1:] if href.startswith("#") else ""
    if local(chain[0]) != "linearGradient":
        fail("only linear gradients are supported")

    def attr(name, default):
        return next((g.get(name) for g in chain if g.get(name) is not None), default)

    stops = next(([s for s in g if local(s) == "stop"] for g in chain if any(local(s) == "stop" for s in g)), [])
    if not stops:
        fail(f"gradient #{chain[0].get('id')} has no stops")
    gm = parse_transform(attr("gradientTransform", None))
    if attr("gradientUnits", "objectBoundingBox") == "objectBoundingBox":
        x0, y0, x1, y1 = bbox
        gm, extent = multiply((x1 - x0, 0, 0, y1 - y0, x0, y0), gm), 1
    else:
        extent = CANVAS
    full = multiply(m, gm)
    start = apply(full, fraction(attr("x1", "0%"), extent), fraction(attr("y1", "0%"), extent))
    end = apply(full, fraction(attr("x2", "100%"), extent), fraction(attr("y2", "0%"), extent))
    items = []
    for s in stops:
        st = style(s)
        items.append((min(max(fraction(s.get("offset", "0")), 0), 1),
                      android_color(st.get("stop-color", "#000000"), float(st.get("stop-opacity", 1)))))
    return {"start": start, "end": end, "stops": items}


# ── SVG walk ─────────────────────────────────────────────────────────────────

def walk(el, m: Matrix, by_id: dict, out: list) -> None:
    """Collect (fill, path data in canvas coordinates, even-odd, gradient) of every visible shape."""
    st = style(el)
    if st.get("display") == "none" or st.get("visibility") == "hidden" or local(el) in IGNORED:
        return
    m = multiply(m, parse_transform(el.get("transform")))
    tag = local(el)
    for unsupported in ("clip-path", "mask", "filter"):
        if st.get(unsupported, "none") != "none":
            fail(f"{unsupported} is not supported (<{tag} id={el.get('id')}>)")
    if tag == "g":
        if float(st.get("opacity", 1)) != 1:
            fail(f"group opacity is not supported (<g id={el.get('id')}>)")
        for child in el:
            walk(child, m, by_id, out)
        return
    if tag not in SHAPES:
        fail(f"unsupported element <{tag}>: convert it to a path")
    if st.get("stroke", "none") != "none":
        fail(f"strokes are not supported (<{tag} id={el.get('id')}>): use Path > Stroke to Path")
    fill = st.get("fill", "#000000")
    if fill == "none":
        return
    commands = shape_path(el)
    d = transform_path(commands, m)
    even_odd = st.get("fill-rule") == "evenodd"
    ref = re.fullmatch(r"url\(\s*#([^)\s]+)\s*\)", fill)
    if ref:
        out.append((None, d, even_odd, linear_gradient(by_id, ref.group(1), m, bounding_box(commands))))
    else:
        opacity = float(st.get("fill-opacity", 1)) * float(st.get("opacity", 1))
        out.append((android_color(fill, opacity), d, even_odd, None))


def layer(root, layer_id: str) -> list:
    by_id = {el.get("id"): el for el in root.iter() if el.get("id")}
    target = by_id.get(layer_id)
    if target is None:
        fail(f'no layer with id="{layer_id}"')
    parents = {child: parent for parent in root.iter() for child in parent}
    ancestors, node = [], target
    while node in parents:
        node = parents[node]
        ancestors.append(node)
    m = IDENTITY
    for ancestor in reversed(ancestors[:-1]):  # the root <svg> carries the viewBox, checked apart
        m = multiply(m, parse_transform(ancestor.get("transform")))
    shapes: list = []
    walk(target, m, by_id, shapes)
    if not shapes:
        fail(f'layer "{layer_id}" has no visible shape')
    return shapes


# ── Android output ───────────────────────────────────────────────────────────

def android_path(fill, d, even_odd, gradient) -> str:
    rule = '\n        android:fillType="evenOdd"' if even_odd else ""
    if gradient is None:
        return f'    <path\n        android:fillColor="{fill}"{rule}\n        android:pathData="{d}" />\n'
    (x0, y0), (x1, y1) = gradient["start"], gradient["end"]
    stops = "".join(f'                <item android:offset="{fmt(o)}" android:color="{c}" />\n'
                    for o, c in gradient["stops"])
    return (f'    <path{rule}\n        android:pathData="{d}">\n'
            f'        <aapt:attr name="android:fillColor">\n'
            f'            <gradient\n                android:type="linear"\n'
            f'                android:startX="{fmt(x0)}"\n                android:startY="{fmt(y0)}"\n'
            f'                android:endX="{fmt(x1)}"\n                android:endY="{fmt(y1)}">\n'
            f"{stops}            </gradient>\n        </aapt:attr>\n    </path>\n")


def drawable(shapes) -> str:
    ns = ' xmlns:aapt="http://schemas.android.com/aapt"' if any(s[3] for s in shapes) else ""
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- Generated by tools/make_icon.py from art/pbill-android.svg: do not edit -->\n"
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="{CANVAS}dp"\n    android:height="{CANVAS}dp"\n'
            f'    android:viewportWidth="{CANVAS}"\n    android:viewportHeight="{CANVAS}">\n'
            f'{"".join(android_path(*s) for s in shapes)}</vector>\n')


def main() -> None:
    args = sys.argv[1:]
    if args and not (len(args) == 2 and args[0] == "--preview"):
        sys.exit(__doc__)

    root = ET.parse(SOURCE).getroot()
    view_box = [float(v) for v in re.split(r"[\s,]+", root.get("viewBox", "").strip()) if v]
    if view_box != [0, 0, CANVAS, CANVAS]:
        fail(f'the viewBox must be "0 0 {CANVAS} {CANVAS}" (1 unit = 1 dp)')
    background = layer(root, "background")
    foreground = layer(root, "foreground")
    symbol = " ".join(d for _, d, _, _ in layer(root, "monochrome"))

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "ic_launcher_background.xml").write_text(drawable(background))
    (OUT / "ic_launcher_foreground.xml").write_text(drawable(foreground))
    (OUT / "ic_launcher_monochrome.xml").write_text(drawable([("#FF000000", symbol, True, None)]))
    print(f"✓ icon layers written to {OUT}/")

    if args:
        write_previews(Path(args[1]), background + foreground, symbol)


# ── Previews ─────────────────────────────────────────────────────────────────

def write_previews(directory: Path, shapes, symbol: str) -> None:
    """SVG previews of the converted layers under common launcher masks (rendered by the Makefile)."""
    directory.mkdir(parents=True, exist_ok=True)
    defs, art = [], []
    for n, (fill, d, even_odd, gradient) in enumerate(shapes):
        if gradient:
            (x0, y0), (x1, y1) = gradient["start"], gradient["end"]
            stops = "".join(f'<stop offset="{o}" stop-color="#{c[3:]}" stop-opacity="{int(c[1:3], 16) / 255:.3f}"/>'
                            for o, c in gradient["stops"])
            defs.append(f'<linearGradient id="g{n}" gradientUnits="userSpaceOnUse" '
                        f'x1="{x0}" y1="{y0}" x2="{x1}" y2="{y1}">{stops}</linearGradient>')
            paint = f'fill="url(#g{n})"'
        else:
            paint = f'fill="#{fill[3:]}" fill-opacity="{int(fill[1:3], 16) / 255:.3f}"'
        rule = ' fill-rule="evenodd"' if even_odd else ""
        art.append(f'<path {paint}{rule} d="{d}"/>')
    inset = (CANVAS - 72) / 2
    masks = {
        "full": f'<rect width="{CANVAS}" height="{CANVAS}"/>',
        "circle": f'<circle cx="{CANVAS / 2}" cy="{CANVAS / 2}" r="36"/>',
        "rounded": f'<rect x="{inset}" y="{inset}" width="72" height="72" rx="16"/>',
        "square": f'<rect x="{inset}" y="{inset}" width="72" height="72" rx="4"/>',
    }
    head = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS} {CANVAS}" width="432" height="432">'
    for name, shape in masks.items():
        (directory / f"{name}.svg").write_text(
            f'{head}<defs>{"".join(defs)}<clipPath id="m">{shape}</clipPath></defs>'
            f'<rect width="{CANVAS}" height="{CANVAS}" fill="#888"/>'
            f'<g clip-path="url(#m)">{"".join(art)}</g>'
            f'<circle cx="{CANVAS / 2}" cy="{CANVAS / 2}" r="33" fill="none" stroke="red" stroke-width="0.4" '
            f'stroke-dasharray="1 1"/></svg>')
    (directory / "monochrome.svg").write_text(
        f'{head}<defs><clipPath id="m"><circle cx="{CANVAS / 2}" cy="{CANVAS / 2}" r="36"/></clipPath></defs>'
        f'<g clip-path="url(#m)"><rect width="{CANVAS}" height="{CANVAS}" fill="#d7e3f7"/>'
        f'<path fill="#1b3a6b" fill-rule="evenodd" d="{symbol}"/></g></svg>')
    # Store listing icon (F-Droid, fastlane): the rounded mask on a transparent
    # background, cropped to the 72 dp visible area, without the safe-zone guide
    (directory / "store.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{inset} {inset} 72 72" width="512" height="512">'
        f'<defs>{"".join(defs)}<clipPath id="m">{masks["rounded"]}</clipPath></defs>'
        f'<g clip-path="url(#m)">{"".join(art)}</g></svg>')


if __name__ == "__main__":
    main()
