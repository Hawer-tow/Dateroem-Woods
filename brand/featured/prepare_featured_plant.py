"""The Featured column's young tree with its roots in a ball of soil (25 October
2026), cut out of the owner's own picture ("completely remove the test tube
like this and even make this image even slimmer").

    python3 prepare_featured_plant.py [picture] [--save-source] [--preview folder]

From the picture (default: brand/featured/plant-source.png):
  1. The cream background is worked out across the whole picture (it is
     lighter in the middle) and removed, so the plant sits on any theme.
  2. The plant is made slimmer, centred on the stem: the leafy crown to 74%
     of its width, the root ball to 92%, eased in between along the stem.
  3. It is split on the stem, so the crown can sway and a tag can hang there.
  4. A soft window inside the root ball, below the top of the soil and inside
     its edge, where the campaign's picture or video shows; and the roots
     lifted out, to lie over it as if growing through it.

writes frontend/website/public/forest/:
  plant-crown.webp      the tree down to its bare stem
  plant-roots.webp      the stem and the ball of soil and roots
  plant-window.webp     where the campaign shows inside the ball (a mask)
  plant-rootlines.webp  the roots alone, laid over the campaign
and prints where the ball is, for styles/forest.css. Camera data is never
copied.
"""
import os
import sys

from PIL import Image, ImageChops, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "frontend", "website", "public", "forest")
SOURCE = os.path.join(HERE, "plant-source.png")

# In the owner's 1024 x 1536 picture: the plant with a margin, worked on at
# SCALE (the website shows it about 150 wide).
BOX = (212, 20, 808, 1504)
SCALE = 0.7
# Rows (in the picture): the lowest leaves end about LEAVES_END, the soil
# starts at BALL_TOP.
LEAVES_END = 625
BALL_TOP = 790
CROWN_WIDTH = 0.74
BALL_WIDTH = 0.92


def smoothstep(edge0, edge1, x):
    t = min(1.0, max(0.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3 - 2 * t)


def background_of(img):
    """The paper's colour everywhere, filled in behind the plant."""
    small = img.resize((128, 192), Image.BILINEAR)
    rough = small.getpixel((4, 4))
    paper = Image.new("L", small.size)
    px, mp = small.load(), paper.load()
    for y in range(small.size[1]):
        for x in range(small.size[0]):
            c = px[x, y]
            mp[x, y] = 255 if max(abs(c[i] - rough[i]) for i in range(3)) < 28 else 0
    paper = paper.filter(ImageFilter.MinFilter(5))
    kept = ImageChops.multiply(small, Image.merge("RGB", (paper, paper, paper)))
    kept = kept.filter(ImageFilter.GaussianBlur(14))
    weight = paper.filter(ImageFilter.GaussianBlur(14))
    out = Image.new("RGB", small.size)
    op, kp, wp = out.load(), kept.load(), weight.load()
    for y in range(small.size[1]):
        for x in range(small.size[0]):
            w = max(wp[x, y], 1) / 255
            op[x, y] = tuple(min(255, round(kp[x, y][i] / w)) for i in range(3))
    return out.resize(img.size, Image.BILINEAR)


def cut_out(img, paper):
    w, h = img.size
    src, bgp = img.load(), paper.load()
    out = Image.new("RGBA", (w, h))
    dst = out.load()
    for y in range(h):
        for x in range(w):
            c, bg = src[x, y], bgp[x, y]
            d = max(abs(c[i] - bg[i]) for i in range(3))
            removed = max(
                (c[i] - bg[i]) / (255 - bg[i]) if c[i] > bg[i] else (bg[i] - c[i]) / max(bg[i], 1) for i in range(3)
            )
            alpha = max(removed, smoothstep(18, 60, d)) * smoothstep(14, 30, d)
            # Nothing in the plant is lighter than the paper all over - that is
            # only paper (the floor under the roots), and it would glow.
            if all(c[i] >= bg[i] - 3 for i in range(3)):
                alpha = 0
            if alpha < 0.05:
                dst[x, y] = (0, 0, 0, 0)
                continue
            fg = tuple(min(255, max(0, round(bg[i] + (c[i] - bg[i]) / alpha))) for i in range(3))
            dst[x, y] = fg + (round(alpha * 255),)
    return out


def stem_x(cut, y0, y1):
    """The stem's centre over rows y0-y1 (only the stem is there)."""
    a = cut.getchannel("A").load()
    xs = [x for y in range(y0, y1) for x in range(cut.size[0]) if a[x, y] > 128]
    return sum(xs) / len(xs)


def slimmer(cut, centre, leaves_end, ball_top):
    """Narrows each row around the stem: the crown most, the ball a little."""
    w, h = cut.size
    out = Image.new("RGBA", (w, h))
    for y in range(h):
        if y <= leaves_end:
            f = CROWN_WIDTH
        elif y >= ball_top:
            f = BALL_WIDTH
        else:
            f = CROWN_WIDTH + (BALL_WIDTH - CROWN_WIDTH) * smoothstep(leaves_end, ball_top, y)
        row = cut.crop((0, y, w, y + 1))
        nw = max(1, round(w * f))
        out.paste(row.resize((nw, 1), Image.LANCZOS), (round(centre - centre * f), y))
    return out


def window_and_roots(roots, ball_top):
    """The campaign's window inside the ball, and the roots lifted out."""
    w, h = roots.size
    solid = roots.getchannel("A").point(lambda a: 255 if a > 225 else 0)
    solid = solid.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(7))
    inside = solid.filter(ImageFilter.MinFilter(9))
    ball_h = h - ball_top
    fade = Image.new("L", (w, h))
    fp = fade.load()
    for y in range(h):
        v = round(255 * smoothstep(ball_top + ball_h * 0.07, ball_top + ball_h * 0.2, y))
        for x in range(w):
            fp[x, y] = v
    window = ImageChops.multiply(inside, fade).filter(ImageFilter.GaussianBlur(1.6))
    near = solid.filter(ImageFilter.MaxFilter(5))
    lines = Image.new("RGBA", (w, h))
    src, lp, np_ = roots.load(), lines.load(), near.load()
    for y in range(ball_top, h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if not np_[x, y] or a < 40:
                continue
            lum = 0.3 * r + 0.59 * g + 0.11 * b
            alpha = smoothstep(80, 130, lum) * smoothstep(35, 80, r - b) * (a / 255)
            if alpha > 0.04:
                lp[x, y] = (r, g, b, round(alpha * 255))
    white = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    white.putalpha(window)
    return white, lines, window.getbbox()


def clean(img):
    """Pixels only - no EXIF or other data."""
    out = Image.new(img.mode, img.size)
    out.putdata(list(img.getdata()))
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    picture = next((a for a in args if os.path.isfile(a)), SOURCE)
    full = Image.open(picture).convert("RGB")
    if "--save-source" in sys.argv:
        clean(full).save(SOURCE)
        print("source:", SOURCE, full.size)
    paper = background_of(full)
    size = (round((BOX[2] - BOX[0]) * SCALE), round((BOX[3] - BOX[1]) * SCALE))
    img = full.crop(BOX).resize(size, Image.LANCZOS)
    paper = paper.crop(BOX).resize(size, Image.LANCZOS)
    cut = cut_out(img, paper)
    # The floor shadow under the root tip is a pale beige; the roots there
    # are orange and the soil dark, so only those stay in the last rows.
    a = cut.load()
    for y in range(cut.size[1] - 40, cut.size[1]):
        for x in range(cut.size[0]):
            r, g, b, al = a[x, y]
            if al and r - b < 70 and (al < 140 or 0.3 * r + 0.59 * g + 0.11 * b > 90):
                a[x, y] = (0, 0, 0, 0)

    leaves_end = round((LEAVES_END - BOX[1]) * SCALE)
    ball_top = round((BALL_TOP - BOX[1]) * SCALE)
    centre = stem_x(cut, leaves_end + 15, ball_top - 15)
    slim = slimmer(cut, centre, leaves_end, ball_top)
    left, top, right, bottom = slim.getbbox()
    slim = slim.crop((left, top, right, bottom))
    leaves_end, ball_top, centre = leaves_end - top, ball_top - top, centre - left
    split = leaves_end + 12
    crown = slim.crop((0, 0, slim.size[0], split))
    roots = slim.crop((0, split, slim.size[0], slim.size[1]))
    window, lines, box = window_and_roots(roots, ball_top - split)

    for part, name in (
        (crown, "plant-crown.webp"),
        (roots, "plant-roots.webp"),
        (window, "plant-window.webp"),
        (lines, "plant-rootlines.webp"),
    ):
        path = os.path.join(OUT, name)
        clean(part).save(path, "WEBP", quality=90, method=6)
        print(f"{name}: {part.size[0]} x {part.size[1]}, {os.path.getsize(path) // 1024} KB")
    rw, rh = roots.size
    print(f"stem at {centre / slim.size[0]:.1%} across; crown {crown.size[1]} of {slim.size[1]} tall")
    print(
        "window in plant-roots: left {:.1%}, top {:.1%}, width {:.1%}, height {:.1%}".format(
            box[0] / rw, box[1] / rh, (box[2] - box[0]) / rw, (box[3] - box[1]) / rh
        )
    )
    if "--preview" in sys.argv:
        folder = sys.argv[sys.argv.index("--preview") + 1]
        for name, colour in (("forest", (15, 36, 27)), ("light", (244, 241, 234))):
            board = Image.new("RGBA", (slim.size[0] + 20, slim.size[1] + 20), colour + (255,))
            board.alpha_composite(crown, (10, 10))
            board.alpha_composite(roots, (10, 10 + split))
            board.convert("RGB").save(os.path.join(folder, f"plant-preview-{name}.png"))


if __name__ == "__main__":
    main()
