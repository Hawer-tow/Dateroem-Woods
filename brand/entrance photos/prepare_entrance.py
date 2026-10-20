"""The phone app's entrance pictures, made from the board's two photos in
this folder (18 October 2026): "the board has seen it wise to use a real
forest instead of the drawn one we have to act as an entry".

    entrance_oak.webp     the old oak - the first shot
    entrance_forest.webp  the misty pine forest - the second shot, and the
                          sign-in screen's background
    entrance_fog.webp     soft mist, tiling left to right, that the app
                          drifts across both

The photos are sized for phones (1200 x 1800, their own 2:3) and graded
toward the app's dark forest look: calmer, deeper greens, a warm touch in
the light, darker edges; the forest's pale sky becomes a dusky grey that
darkens toward the top, so the gold emblem reads against it.

Needs Pillow (the desktop app's venv has it). From this folder:

    <desktop venv>/Scripts/python.exe prepare_entrance.py <phone project>/app/src/main/res/drawable-nodpi

Without a folder it writes next to this script, with small previews.

The desktop app plays the same entrance full screen (19 October 2026: "the
admins have reviewed it as a better entry"), opening on the forest - the
oak is tall and a wide screen could only show it cropped or with empty
sides. Its pictures are the same grade, as JPEG and PNG (Qt reads both
without extra plugins), the forest at twice the size (2400 x 3600) so it
stays sharp on large monitors:

    <desktop venv>/Scripts/python.exe prepare_entrance.py --desktop <desktop>/app/ui/assets/entrance
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageEnhance, ImageFilter, ImageOps

HERE = Path(__file__).resolve().parent
OAK = HERE / 'old oak (original).jpg'
FOREST = HERE / 'misty forest (original).jpg'
SIZE = (1200, 1800)
DESKTOP_SIZE = (2400, 3600)


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def vignette(size, strength, inner):
    """L mask, 255 in the middle darkening smoothly toward the edges and on
    into the corners: the middle 181 x 181 of Pillow's 256 radial gradient
    has its corners at the gradient's full radius, so there is no flat outer
    ring (which reads as a dark frame)."""
    g = Image.radial_gradient('L').crop((37, 37, 218, 218))
    g = g.point(lambda v: int(255 * smooth((v / 255 - inner) / (1 - inner))))
    g = g.resize(size, Image.BICUBIC).filter(ImageFilter.GaussianBlur(24))
    return g.point(lambda v: 255 - int(v * strength))


def top_dusk(size, top, until):
    """L mask: brightness `top` at the top edge, full by `until` (0..1 of the height)."""
    g = Image.linear_gradient('L').resize((1, 1024))
    g = g.point(lambda v: int(255 * (top + (1 - top) * smooth((v / 255) / until))))
    return g.resize(size, Image.BICUBIC)


def tone(img, shadow_rgb, highlight_rgb, shadow_amount, highlight_amount):
    """Shadows pulled toward one colour, highlights toward another."""
    lum = ImageOps.grayscale(img)
    shadow_mask = lum.point(lambda v: int((255 - v) * shadow_amount))
    high_mask = lum.point(lambda v: int(max(0, v - 128) * 2 * highlight_amount))
    img = Image.composite(Image.new('RGB', img.size, shadow_rgb), img, shadow_mask)
    return Image.composite(Image.new('RGB', img.size, highlight_rgb), img, high_mask)


def load(path, size=SIZE):
    return ImageOps.exif_transpose(Image.open(path)).convert('RGB').resize(size, Image.LANCZOS)


def oak(size=SIZE):
    img = ImageEnhance.Color(load(OAK, size)).enhance(0.72)
    # Lime spring greens -> deeper forest green.
    r, g, b = img.split()
    img = Image.merge('RGB', (
        r.point(lambda v: int(v * 0.86)),
        g.point(lambda v: int(255 * (v / 255) ** 1.12)),
        b.point(lambda v: int(v * 0.92)),
    ))
    img = tone(img, (8, 22, 14), (255, 226, 178), 0.38, 0.10)
    img = ImageEnhance.Contrast(ImageEnhance.Brightness(img).enhance(0.80)).enhance(1.10)
    return Image.composite(img, Image.new('RGB', img.size, (6, 12, 8)), vignette(img.size, 0.62, 0.30))


def forest(size=SIZE):
    img = ImageEnhance.Color(load(FOREST, size)).enhance(0.82)
    # The pink-white sky -> a cool, misty grey-green, darkening toward the top.
    img = tone(img, (6, 20, 12), (176, 196, 186), 0.30, 0.50)
    img = ImageEnhance.Contrast(ImageEnhance.Brightness(img).enhance(0.84)).enhance(1.06)
    dark = Image.new('RGB', img.size, (5, 13, 9))
    img = Image.composite(img, dark, top_dusk(img.size, 0.42, 0.62))
    return Image.composite(img, dark, vignette(img.size, 0.40, 0.45))


def fog():
    """Soft banks of mist, white with see-through gaps, 1024 x 512, tiling
    left to right and fading out at the band's top and bottom."""
    w, h = 1024, 512
    rnd = random.Random(18102026)

    def noise(cols, rows, blur):
        # A coarse grid of random values, enlarged (three copies side by
        # side so the edges wrap) and softened until no grid shows. Enlarging
        # keeps the full range of values, so nothing gets banded.
        small = Image.new('L', (cols, rows))
        small.putdata([rnd.randint(0, 255) for _ in range(cols * rows)])
        wide = Image.new('L', (cols * 3, rows))
        for i in range(3):
            wide.paste(small, (i * cols, 0))
        wide = wide.resize((w * 3, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(blur))
        return wide.crop((w, 0, w * 2, h))

    # Big rolling banks (wider than tall - mist lies in long banks) with
    # finer wisps inside them.
    tex = noise(5, 4, 38)
    for cols, rows, blur, weight in ((10, 7, 20, 0.40), (20, 12, 10, 0.22)):
        tex = Image.blend(tex, noise(cols, rows, blur), weight)
    tex = ImageOps.autocontrast(tex, cutoff=0.5)
    alpha = tex.point(lambda v: int(215 * smooth((v - 70) / (235 - 70))))
    profile = Image.new('L', (1, h))
    profile.putdata([int(255 * math.sin(math.pi * y / (h - 1)) ** 1.4) for y in range(h)])
    alpha = ImageChops.multiply(alpha, profile.resize((w, h)))
    # A whisper of grain so the soft gradients don't show steps on screens.
    alpha = ImageChops.add(alpha, Image.effect_noise((w, h), 3), scale=1.0, offset=-128)
    mist = Image.new('RGBA', (w, h), (226, 232, 228, 0))
    mist.putalpha(alpha)
    return mist


def desktop(out):
    out.mkdir(parents=True, exist_ok=True)
    for name, image in (('entrance_forest', forest(DESKTOP_SIZE)),):
        # Baseline (not progressive) JPEG: it decodes faster, and the intro waits for it.
        image.save(out / f'{name}.jpg', 'JPEG', quality=86, optimize=True)
        print(out / f'{name}.jpg', (out / f'{name}.jpg').stat().st_size // 1024, 'KB')
    fog().save(out / 'entrance_fog.png', 'PNG', optimize=True)
    print(out / 'entrance_fog.png', (out / 'entrance_fog.png').stat().st_size // 1024, 'KB')


if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == '--desktop':
        desktop(Path(sys.argv[2]))
        sys.exit(0)
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE
    out.mkdir(parents=True, exist_ok=True)
    for name, image, quality in (('entrance_oak', oak(), 78), ('entrance_forest', forest(), 82)):
        image.save(out / f'{name}.webp', 'WEBP', quality=quality, method=6)
        if out == HERE:
            image.resize((400, 600), Image.LANCZOS).save(out / f'{name} (preview).jpg', quality=88)
        print(out / f'{name}.webp', (out / f'{name}.webp').stat().st_size // 1024, 'KB')
    fog().save(out / 'entrance_fog.webp', 'WEBP', quality=80, alpha_quality=100, method=6)
    print(out / 'entrance_fog.webp', (out / 'entrance_fog.webp').stat().st_size // 1024, 'KB')
